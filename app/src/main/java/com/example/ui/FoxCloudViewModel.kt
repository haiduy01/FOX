package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.location.DriverLocationRateLimiter
import com.example.data.location.DriverTrackingStopReason
import com.example.data.location.FoxLocationProvider
import com.example.data.location.FoxMapRepository
import com.example.data.location.FusedFoxLocationRepository
import com.example.data.location.GeoPointData
import com.example.data.location.GpsLocationState
import com.example.data.location.MapConfigStatus
import com.example.data.location.RouteComputationState
import com.example.data.remote.BOOTSTRAPPED_ADMIN_EMAIL
import com.example.data.remote.CloudDemoWallet
import com.example.data.remote.CloudDriverLiveLocation
import com.example.data.remote.CloudMerchantProduct
import com.example.data.remote.CloudMerchantStore
import com.example.data.remote.CloudMerchantWithProducts
import com.example.data.remote.CloudNotification
import com.example.data.remote.CloudOrder
import com.example.data.remote.CloudPlatformRevenueEntry
import com.example.data.remote.CloudUserProfile
import com.example.data.remote.CloudWalletTx
import com.example.data.remote.FoxCloudRepository
import com.example.data.remote.FoxFunctionsClient
import com.example.data.remote.GeoCoordsParam
import com.example.data.remote.OrderItemParam
import com.example.data.remote.cloudOrderStatusLabel
import com.example.data.repository.FoxDemoPricingEngine
import com.example.notifications.FoxFcmTokenManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WalletOperationStage {
  IDLE,
  PENDING,
  SUCCESS,
  FAILURE,
}

data class WalletActionStatus(
  val stage: WalletOperationStage = WalletOperationStage.IDLE,
  val message: String = "",
)

data class FoxCloudUiState(
  val currentUserId: String = "",
  val authEmail: String = "",
  val authEmailVerified: Boolean = false,
  val isVerifiedAdmin: Boolean = false,
  val profile: CloudUserProfile? = null,
  val isLoadingProfile: Boolean = true,
  val cloudWallet: CloudDemoWallet = CloudDemoWallet(),
  val cloudWalletTransactions: List<CloudWalletTx> = emptyList(),
  val walletActionStatus: WalletActionStatus = WalletActionStatus(),
  val customerOrders: List<CloudOrder> = emptyList(),
  val openDriverOrders: List<CloudOrder> = emptyList(),
  val openMerchantOrders: List<CloudOrder> = emptyList(),
  val driverAssignedOrders: List<CloudOrder> = emptyList(),
  val merchantAssignedOrders: List<CloudOrder> = emptyList(),
  val merchantStoreProfile: CloudMerchantStore? = null,
  val merchantProducts: List<CloudMerchantProduct> = emptyList(),
  val foodCatalog: List<CloudMerchantWithProducts> = emptyList(),
  val shoppingCatalog: List<CloudMerchantWithProducts> = emptyList(),
  val isLoadingCatalog: Boolean = true,
  val adminUsers: List<CloudUserProfile> = emptyList(),
  val adminAllOrders: List<CloudOrder> = emptyList(),
  val adminPlatformRevenue: List<CloudPlatformRevenueEntry> = emptyList(),
  val cloudNotifications: List<CloudNotification> = emptyList(),
  val mapConfigStatus: MapConfigStatus = MapConfigStatus.fromBuildConfig(),
  val isDriverSharingLocation: Boolean = false,
  val driverTrackingOrderId: String? = null,
  val driverTrackingIsDemo: Boolean = false,
  val driverTrackingStatusMessage: String? = null,
  val activeDriverLocations: Map<String, CloudDriverLiveLocation> = emptyMap(),
  val fcmTokenRegistered: Boolean = false,
  val fcmSyncStatus: String = "Đang kiểm tra trạng thái FCM...",
  val isBusy: Boolean = false,
  val actionNotice: String? = null,
  val cloudError: String? = null,
) {
  val unreadCloudNotificationsCount: Int
    get() = cloudNotifications.count { !it.readFlag }

  val totalAdminPlatformRevenueVnd: Long
    get() = adminPlatformRevenue.sumOf { it.platformRevenueVnd }

  val totalAdminDriverCommissionVnd: Long
    get() = adminPlatformRevenue.sumOf { it.driverCommissionVnd }

  val totalAdminMerchantCommissionVnd: Long
    get() = adminPlatformRevenue.sumOf { it.merchantCommissionVnd }

  val driverKpis: DriverKpiSummary
    get() = FoxDashboardAnalytics.computeDriverKpis(driverAssignedOrders)

  val merchantKpis: MerchantKpiSummary
    get() = FoxDashboardAnalytics.computeMerchantKpis(merchantAssignedOrders, merchantProducts)

  val adminKpis: AdminKpiSummary
    get() = FoxDashboardAnalytics.computeAdminKpis(adminUsers, adminAllOrders, adminPlatformRevenue)
}

private data class CloudControlState(
  val isBusy: Boolean = false,
  val actionNotice: String? = null,
  val cloudError: String? = null,
  val walletActionStatus: WalletActionStatus = WalletActionStatus(),
  val isDriverSharingLocation: Boolean = false,
  val driverTrackingOrderId: String? = null,
  val driverTrackingIsDemo: Boolean = false,
  val driverTrackingStatusMessage: String? = null,
  val fcmTokenRegistered: Boolean = false,
  val fcmSyncStatus: String = "Đang kiểm tra trạng thái FCM...",
)

private data class BaseUserBundle(
  val profile: CloudUserProfile? = null,
  val customerOrders: List<CloudOrder> = emptyList(),
  val notifications: List<CloudNotification> = emptyList(),
  val wallet: CloudDemoWallet? = null,
  val walletTxs: List<CloudWalletTx> = emptyList(),
)

private data class PartnerDataBundle(
  val openDriver: List<CloudOrder> = emptyList(),
  val openMerchant: List<CloudOrder> = emptyList(),
  val driverAssigned: List<CloudOrder> = emptyList(),
  val merchantAssigned: List<CloudOrder> = emptyList(),
  val merchantProducts: List<CloudMerchantProduct> = emptyList(),
  val merchantStoreProfile: CloudMerchantStore? = null,
)

private data class CatalogDataBundle(
  val foodCatalog: List<CloudMerchantWithProducts> = emptyList(),
  val shoppingCatalog: List<CloudMerchantWithProducts> = emptyList(),
  val loaded: Boolean = false,
)

private data class AdminDataBundle(
  val users: List<CloudUserProfile> = emptyList(),
  val allOrders: List<CloudOrder> = emptyList(),
  val platformRevenue: List<CloudPlatformRevenueEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class FoxCloudViewModel(
  private val appContext: Context,
  private val cloudRepo: FoxCloudRepository,
  private val functionsClient: FoxFunctionsClient,
  private val locationRepo: FoxLocationProvider,
  private val mapRepo: FoxMapRepository,
  private val currentUserId: String,
  private val authEmail: String?,
  private val authEmailVerified: Boolean,
) : ViewModel() {

  private val controlState = MutableStateFlow(CloudControlState())
  private val driverLocationRateLimiter = DriverLocationRateLimiter(minIntervalMs = 5_000L, minDistanceMeters = 15.0)
  private var driverTrackingJob: Job? = null

  private fun <T> Flow<T>.unwrapCloudErrors(fallbackValue: T, silentPermission: Boolean = false): Flow<T> =
    catch { error ->
      val firestoreError =
        generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      if (!silentPermission || firestoreError?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        val readable =
          when (firestoreError?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
              "Từ chối quyền truy cập Firestore Rules. Vui lòng kiểm tra vai trò tài khoản."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
              "Không thể kết nối Cloud Firestore. Vui lòng kiểm tra mạng."
            else -> firestoreError?.localizedMessage ?: error.localizedMessage ?: "Lỗi đồng bộ Cloud Firestore."
          }
        controlState.update { it.copy(cloudError = readable) }
      }
      emit(fallbackValue)
    }

  private val profileFlow: Flow<CloudUserProfile?> =
    cloudRepo.observeUserProfile(currentUserId).unwrapCloudErrors(null)

  private val customerOrdersFlow: Flow<List<CloudOrder>> =
    cloudRepo.observeCustomerOrders(currentUserId).unwrapCloudErrors(emptyList())

  private val notificationsFlow: Flow<List<CloudNotification>> =
    cloudRepo.observeCloudNotifications(currentUserId).unwrapCloudErrors(emptyList())

  private val walletFlow: Flow<CloudDemoWallet?> =
    cloudRepo.observeUserWallet(currentUserId).unwrapCloudErrors(null, silentPermission = true)

  private val walletTransactionsFlow: Flow<List<CloudWalletTx>> =
    cloudRepo.observeUserWalletTransactions(currentUserId).unwrapCloudErrors(emptyList(), silentPermission = true)

  private val baseStreams: Flow<BaseUserBundle> = combine(
    profileFlow,
    customerOrdersFlow,
    notificationsFlow,
    walletFlow,
    walletTransactionsFlow,
  ) { profile, orders, notifications, wallet, walletTxs ->
    BaseUserBundle(profile, orders, notifications, wallet, walletTxs)
  }

  private val partnerBundleFlow: Flow<PartnerDataBundle> =
    profileFlow.flatMapLatest { profile ->
      val isDriver = profile?.isApprovedDriver() == true
      val isMerchant = profile?.isApprovedMerchant() == true

      val openDriverFlow =
        if (isDriver) {
          cloudRepo.observeOpenDriverDispatchOrders(currentUserId).unwrapCloudErrors(emptyList())
        } else {
          flowOf(emptyList())
        }
      val openMerchantFlow =
        if (isMerchant) {
          cloudRepo.observeOpenMerchantDispatchOrders(currentUserId).unwrapCloudErrors(emptyList())
        } else {
          flowOf(emptyList())
        }
      val driverAssignedFlow =
        if (isDriver) {
          cloudRepo.observeDriverAssignedOrders(currentUserId).unwrapCloudErrors(emptyList())
        } else {
          flowOf(emptyList())
        }
      val merchantAssignedFlow =
        if (isMerchant) {
          cloudRepo.observeMerchantAssignedOrders(currentUserId).unwrapCloudErrors(emptyList())
        } else {
          flowOf(emptyList())
        }
      val merchantProductsFlow =
        if (isMerchant) {
          cloudRepo.observeMerchantProducts(currentUserId).unwrapCloudErrors(emptyList())
        } else {
          flowOf(emptyList())
        }
      val merchantStoreFlow =
        if (isMerchant) {
          cloudRepo.observeMerchantStoreProfile(currentUserId).unwrapCloudErrors(null, silentPermission = true)
        } else {
          flowOf(null)
        }
      combine(
        openDriverFlow,
        openMerchantFlow,
        driverAssignedFlow,
        merchantAssignedFlow,
        combine(merchantProductsFlow, merchantStoreFlow) { prods, store -> prods to store },
      ) { openDrv, openMerch, drvAssigned, merchAssigned, merchPair ->
        PartnerDataBundle(
          openDriver = openDrv,
          openMerchant = openMerch,
          driverAssigned = drvAssigned,
          merchantAssigned = merchAssigned,
          merchantProducts = merchPair.first,
          merchantStoreProfile = merchPair.second,
        )
      }
    }

  private val catalogBundleFlow: Flow<CatalogDataBundle> =
    combine(
      cloudRepo.observeActiveCatalog("FOOD", currentUserId).unwrapCloudErrors(emptyList(), silentPermission = true),
      cloudRepo.observeActiveCatalog("SHOPPING", currentUserId).unwrapCloudErrors(emptyList(), silentPermission = true),
    ) { foodList, shopList ->
      CatalogDataBundle(foodCatalog = foodList, shoppingCatalog = shopList, loaded = true)
    }

  private val adminBundleFlow: Flow<AdminDataBundle> =
    profileFlow.flatMapLatest { _ ->
      val canListAdmin = authEmail == BOOTSTRAPPED_ADMIN_EMAIL && authEmailVerified
      if (canListAdmin) {
        combine(
          cloudRepo.observeAllUsersForAdmin(currentUserId).unwrapCloudErrors(emptyList()),
          cloudRepo.observeAllOrdersForAdmin(currentUserId).unwrapCloudErrors(emptyList()),
          cloudRepo.observePlatformRevenueForAdmin(currentUserId).unwrapCloudErrors(emptyList(), silentPermission = true),
        ) { users, orders, revenue ->
          AdminDataBundle(users, orders, revenue)
        }
      } else {
        flowOf(AdminDataBundle())
      }
    }

  /**
   * Customer-isolated live driver tracking: observes /orders/{orderId}/live_location/driver ONLY
   * for active orders belonging to this customer that have an assigned driver.
   */
  private val activeDriverLocationsFlow: Flow<Map<String, CloudDriverLiveLocation>> =
    customerOrdersFlow.flatMapLatest { orders ->
      val activeTrackableIds = orders
        .filter { it.isActiveDriverTrackingEligible() }
        .map { it.id }
        .filter { it.isNotBlank() }
        .distinct()
      if (activeTrackableIds.isEmpty()) {
        flowOf(emptyMap())
      } else {
        val perOrderFlows = activeTrackableIds.map { oid ->
          cloudRepo.observeDriverLiveLocation(oid, currentUserId)
            .unwrapCloudErrors(null, silentPermission = true)
            .map { loc -> oid to loc }
        }
        combine(perOrderFlows) { pairs ->
          pairs.mapNotNull { (oid, loc) -> if (loc != null) oid to loc else null }.toMap()
        }
      }
    }

  val uiState: StateFlow<FoxCloudUiState> =
    combine(
      baseStreams,
      partnerBundleFlow,
      catalogBundleFlow,
      adminBundleFlow,
      combine(controlState, activeDriverLocationsFlow) { ctrl, locs -> ctrl to locs },
    ) { base, partner, catalog, admin, ctrlAndLocs ->
      val ctrl = ctrlAndLocs.first
      val driverLocs = ctrlAndLocs.second
      val verifiedAdmin =
        base.profile?.isVerifiedAdmin(authEmail, authEmailVerified)
          ?: (authEmail == BOOTSTRAPPED_ADMIN_EMAIL && authEmailVerified)
      FoxCloudUiState(
        currentUserId = currentUserId,
        authEmail = authEmail ?: "",
        authEmailVerified = authEmailVerified,
        isVerifiedAdmin = verifiedAdmin,
        profile = base.profile,
        isLoadingProfile = base.profile == null && ctrl.cloudError == null,
        cloudWallet = base.wallet ?: CloudDemoWallet(id = currentUserId, userId = currentUserId),
        cloudWalletTransactions = base.walletTxs,
        walletActionStatus = ctrl.walletActionStatus,
        customerOrders = base.customerOrders,
        openDriverOrders = partner.openDriver,
        openMerchantOrders = partner.openMerchant,
        driverAssignedOrders = partner.driverAssigned,
        merchantAssignedOrders = partner.merchantAssigned,
        merchantStoreProfile = partner.merchantStoreProfile,
        merchantProducts = partner.merchantProducts,
        foodCatalog = catalog.foodCatalog,
        shoppingCatalog = catalog.shoppingCatalog,
        isLoadingCatalog = !catalog.loaded && ctrl.cloudError == null,
        adminUsers = admin.users,
        adminAllOrders = admin.allOrders,
        adminPlatformRevenue = admin.platformRevenue,
        cloudNotifications = base.notifications,
        mapConfigStatus = mapRepo.mapConfigStatus,
        isDriverSharingLocation = ctrl.isDriverSharingLocation,
        driverTrackingOrderId = ctrl.driverTrackingOrderId,
        driverTrackingIsDemo = ctrl.driverTrackingIsDemo,
        driverTrackingStatusMessage = ctrl.driverTrackingStatusMessage,
        activeDriverLocations = driverLocs,
        fcmTokenRegistered = ctrl.fcmTokenRegistered,
        fcmSyncStatus = ctrl.fcmSyncStatus,
        isBusy = ctrl.isBusy,
        actionNotice = ctrl.actionNotice,
        cloudError = ctrl.cloudError,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = FoxCloudUiState(
        currentUserId = currentUserId,
        authEmail = authEmail ?: "",
        authEmailVerified = authEmailVerified,
        mapConfigStatus = mapRepo.mapConfigStatus,
      ),
    )

  private var lastSyncedFcmRole: String? = null

  init {
    viewModelScope.launch {
      cloudRepo.ensureUserProfile().onSuccess { prof ->
        syncFcmToken(prof.role)
      }.onFailure { err ->
        controlState.update {
          it.copy(cloudError = err.localizedMessage ?: "Không thể khởi tạo hồ sơ người dùng trên Cloud.")
        }
      }
    }
    viewModelScope.launch {
      profileFlow.collect { prof ->
        if (prof != null && prof.role != lastSyncedFcmRole) {
          syncFcmToken(prof.role)
        }
      }
    }
  }

  fun syncFcmToken(roleOverride: String? = null) {
    val role = roleOverride ?: uiState.value.profile?.role ?: "customer"
    lastSyncedFcmRole = role
    viewModelScope.launch {
      val syncRes = FoxFcmTokenManager.syncCurrentToken(
        context = appContext,
        cloudRepo = cloudRepo,
        userId = currentUserId,
        role = role,
      )
      controlState.update {
        it.copy(
          fcmTokenRegistered = syncRes.registered,
          fcmSyncStatus = syncRes.statusMessage,
        )
      }
    }
  }

  suspend fun cleanupFcmTokenBeforeSignOut() {
    if (controlState.value.isDriverSharingLocation) {
      stopDriverLocationSharing(DriverTrackingStopReason.SIGNED_OUT)
    }
    FoxFcmTokenManager.removeCurrentTokenOnSignOut(cloudRepo, currentUserId)
  }

  private fun notifyOrderEventBestEffort(orderId: String, eventType: String) {
    viewModelScope.launch {
      functionsClient.notifyOrderEvent(orderId, eventType)
    }
  }

  private fun notifyPartnerReviewBestEffort(targetUid: String, partnerType: String, eventType: String) {
    viewModelScope.launch {
      functionsClient.notifyPartnerReview(targetUid, partnerType, eventType)
    }
  }

  fun clearNotice() {
    controlState.update { it.copy(actionNotice = null) }
  }

  fun clearError() {
    controlState.update { it.copy(cloudError = null) }
  }

  private fun newIdempotencyKey(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"

  private fun runCloudAction(
    successMessage: String,
    block: suspend () -> Result<Unit>,
  ) {
    viewModelScope.launch {
      controlState.update { it.copy(isBusy = true, cloudError = null) }
      val result = block()
      controlState.update { state ->
        result.fold(
          onSuccess = { state.copy(isBusy = false, actionNotice = successMessage) },
          onFailure = { err ->
            state.copy(
              isBusy = false,
              actionNotice = err.localizedMessage ?: "Thao tác Cloud thất bại.",
            )
          },
        )
      }
    }
  }

  // --- CLOUD FUNCTIONS v2 FINANCIAL OPERATIONS (DEMO — KHÔNG PHẢI TIỀN THẬT) ---

  fun topUpDemoWallet(amountVnd: Long) {
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          walletActionStatus = WalletActionStatus(
            stage = WalletOperationStage.PENDING,
            message = "Đang gửi yêu cầu nạp ${amountVnd}đ DEMO tới Cloud Functions...",
          ),
        )
      }
      val key = newIdempotencyKey("topup")
      val result = functionsClient.topUpDemoWallet(amountVnd, key)
      controlState.update { state ->
        result.fold(
          onSuccess = { res ->
            val msg = "Nạp Ví FOX DEMO thành công +${res.amountVnd}đ (Số dư mới: ${res.balanceAfterVnd}đ)."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Nạp Ví FOX DEMO thất bại."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
            )
          },
        )
      }
    }
  }

  fun payOrderWithDemoWallet(orderId: String) {
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          walletActionStatus = WalletActionStatus(
            stage = WalletOperationStage.PENDING,
            message = "Đang thanh toán Ví DEMO cho đơn $orderId qua Cloud Functions...",
          ),
        )
      }
      val key = newIdempotencyKey("pay_$orderId")
      val result = functionsClient.payOrderWithDemoWallet(orderId, key)
      controlState.update { state ->
        result.fold(
          onSuccess = { res ->
            val msg = "Thanh toán DEMO đơn $orderId thành công (-${res.amountVnd}đ • Còn ${res.balanceAfterVnd}đ)."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Thanh toán Ví DEMO thất bại."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
            )
          },
        )
      }
    }
  }

  fun refundOrderDemo(orderId: String, reason: String = "Yêu cầu hủy & hoàn tiền DEMO") {
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          walletActionStatus = WalletActionStatus(
            stage = WalletOperationStage.PENDING,
            message = "Đang xử lý hủy & hoàn tiền DEMO cho đơn $orderId...",
          ),
        )
      }
      val key = newIdempotencyKey("refund_$orderId")
      val result = functionsClient.refundOrderDemo(orderId, key, reason)
      controlState.update { state ->
        result.fold(
          onSuccess = { res ->
            val stockNote = if (res.stockRestored) " • Đã hoàn tồn kho gian hàng." else ""
            val msg = if (res.refundedAmountVnd > 0L) {
              "Đã hủy đơn $orderId & hoàn +${res.refundedAmountVnd}đ về Ví FOX DEMO$stockNote"
            } else {
              "Đã hủy đơn $orderId (không phát sinh hoàn tiền Ví)$stockNote"
            }
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Hoàn tiền DEMO thất bại."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
            )
          },
        )
      }
    }
  }

  fun settleOrderCommissionDemo(orderId: String) {
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          walletActionStatus = WalletActionStatus(
            stage = WalletOperationStage.PENDING,
            message = "Đang đối soát hoa hồng DEMO cho đơn $orderId...",
          ),
        )
      }
      val result = functionsClient.settleOrderCommissionDemo(orderId)
      controlState.update { state ->
        result.fold(
          onSuccess = { res ->
            val msg =
              "Quyết toán DEMO $orderId: Tài xế +${res.driverCommissionVnd}đ • Gian hàng +${res.merchantCommissionVnd}đ • FOX +${res.platformRevenueVnd}đ."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Chưa thể quyết toán hoa hồng DEMO."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
            )
          },
        )
      }
    }
  }

  fun refreshWalletSummary() {
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          walletActionStatus = WalletActionStatus(
            stage = WalletOperationStage.PENDING,
            message = "Đang đồng bộ số dư & lịch sử giao dịch từ Cloud Functions...",
          ),
        )
      }
      val result = functionsClient.getWalletSummary()
      controlState.update { state ->
        result.fold(
          onSuccess = { (wallet, txs) ->
            val msg = "Đã đồng bộ Ví FOX DEMO: ${wallet.balanceVnd}đ (${txs.size} giao dịch)."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Chưa thể kết nối Cloud Functions Ví DEMO."
            state.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
            )
          },
        )
      }
    }
  }

  // --- PROFILE & PARTNER APPLICATIONS ---

  fun updatePersonalProfile(displayName: String, phone: String, defaultAddress: String) {
    runCloudAction("Đã cập nhật hồ sơ lên Cloud Firestore.") {
      cloudRepo.updatePersonalProfile(displayName, phone, defaultAddress)
    }
  }

  fun applyAsDriver(vehicleType: String, vehiclePlate: String) {
    runCloudAction("Đã gửi hồ sơ Tài xế (trạng thái: Chờ Admin duyệt).") {
      cloudRepo.submitDriverApplication(vehicleType, vehiclePlate).also { res ->
        if (res.isSuccess) {
          notifyPartnerReviewBestEffort(currentUserId, "driver", "partner_application_submitted")
        }
      }
    }
  }

  fun applyAsMerchant(storeName: String, storeCategory: String) {
    runCloudAction("Đã gửi hồ sơ Gian hàng (trạng thái: Chờ Admin duyệt).") {
      cloudRepo.submitMerchantApplication(storeName, storeCategory).also { res ->
        if (res.isSuccess) {
          notifyPartnerReviewBestEffort(currentUserId, "merchant", "partner_application_submitted")
        }
      }
    }
  }

  fun setDriverOnline(online: Boolean) {
    if (!online && controlState.value.isDriverSharingLocation) {
      stopDriverLocationSharing(DriverTrackingStopReason.DRIVER_OFFLINE)
    }
    runCloudAction(if (online) "Đã bật trạng thái Tài xế trực tuyến." else "Đã tạm ngưng nhận đơn.") {
      cloudRepo.toggleDriverOnline(online)
    }
  }

  // --- GPS, MAP SEARCH, ROUTES & FOREGROUND DRIVER LIVE LOCATION ---

  fun hasLocationPermission(): Boolean = locationRepo.hasLocationPermission()

  suspend fun fetchCurrentGpsWithAddress(): GpsLocationState {
    val state = locationRepo.fetchCurrentLocationOnce()
    return if (state is GpsLocationState.Success) {
      GpsLocationState.Success(mapRepo.reverseGeocodeRealLocation(state.location))
    } else {
      state
    }
  }

  suspend fun searchPlaces(query: String, useDemoMode: Boolean): List<GeoPointData> =
    mapRepo.searchPlaces(query, useDemoMode)

  suspend fun computeRouteForBooking(
    origin: GeoPointData,
    destination: GeoPointData,
    vehicleType: String?,
  ): RouteComputationState =
    mapRepo.computeRoute(origin, destination, vehicleType, allowServerCall = true)

  /**
   * Starts foreground-only live driver location sharing for an active order assigned to this driver.
   * NEVER starts automatically — requires explicit user opt-in.
   */
  fun startDriverLocationSharing(orderId: String, useDemoMode: Boolean) {
    val profile = uiState.value.profile
    if (profile?.isApprovedDriver() != true) {
      controlState.update {
        it.copy(actionNotice = "Chỉ tài xế đã được Admin duyệt mới được chia sẻ vị trí trực tiếp.")
      }
      return
    }
    val order = uiState.value.driverAssignedOrders.firstOrNull { it.id == orderId }
    if (order == null || !order.isActiveDriverTrackingEligible()) {
      controlState.update {
        it.copy(actionNotice = "Chỉ có thể chia sẻ vị trí khi đơn đang hoạt động (đã nhận / đến lấy / đang giao).")
      }
      return
    }

    if (!useDemoMode) {
      if (!locationRepo.hasLocationPermission()) {
        controlState.update {
          it.copy(
            driverTrackingStatusMessage = DriverTrackingStopReason.PERMISSION_REVOKED.labelVi,
            actionNotice = "Vui lòng cấp quyền vị trí để chia sẻ GPS thật.",
          )
        }
        return
      }
      if (!locationRepo.isGpsEnabled()) {
        controlState.update {
          it.copy(
            driverTrackingStatusMessage = DriverTrackingStopReason.GPS_DISABLED.labelVi,
            actionNotice = "GPS đang tắt. Vui lòng bật vị trí trên thiết bị hoặc dùng chế độ DEMO.",
          )
        }
        return
      }
    }

    driverTrackingJob?.cancel()
    driverLocationRateLimiter.reset()
    val driverName = profile.displayName.ifBlank { "Tài xế FOX" }

    controlState.update {
      it.copy(
        isDriverSharingLocation = true,
        driverTrackingOrderId = orderId,
        driverTrackingIsDemo = useDemoMode,
        driverTrackingStatusMessage = if (useDemoMode) {
          "Đang chia sẻ vị trí DEMO (giả lập) cho đơn $orderId — giới hạn ≥ 5 giây/lần."
        } else {
          "Đang chia sẻ vị trí GPS thật (foreground) cho đơn $orderId — giới hạn ≥ 5 giây/lần."
        },
        actionNotice = if (useDemoMode) "Đã bật chia sẻ vị trí DEMO cho đơn $orderId." else "Đã bật chia sẻ vị trí GPS thật cho đơn $orderId.",
      )
    }

    driverTrackingJob = viewModelScope.launch {
      if (useDemoMode) {
        val startLat = order.pickupLat ?: 10.7725
        val startLng = order.pickupLng ?: 106.6980
        val endLat = order.dropoffLat ?: 10.7950
        val endLng = order.dropoffLng ?: 106.7218
        var step = 0
        while (isActive) {
          val liveOrder = uiState.value.driverAssignedOrders.firstOrNull { it.id == orderId }
          if (liveOrder == null || !liveOrder.isActiveDriverTrackingEligible()) {
            stopDriverLocationSharing(DriverTrackingStopReason.ORDER_COMPLETED)
            break
          }
          val fraction = ((step % 10) / 10.0)
          val lat = startLat + (endLat - startLat) * fraction
          val lng = startLng + (endLng - startLng) * fraction
          val nowMs = System.currentTimeMillis()
          if (driverLocationRateLimiter.shouldPublish(lat, lng, nowMs, force = step == 0)) {
            val pubRes = cloudRepo.publishDriverLiveLocation(
              orderId = orderId,
              driverName = driverName,
              lat = lat,
              lng = lng,
              bearing = 45.0,
              speedMps = 8.5,
              accuracyMeters = 10.0,
              isDemo = true,
            )
            if (pubRes.isSuccess) {
              driverLocationRateLimiter.recordPublished(lat, lng, nowMs)
            }
          }
          step++
          delay(5_500L)
        }
      } else {
        locationRepo.observeForegroundDriverLocation(intervalMs = 5_000L).collect { gps ->
          val liveOrder = uiState.value.driverAssignedOrders.firstOrNull { it.id == orderId }
          if (liveOrder == null || !liveOrder.isActiveDriverTrackingEligible()) {
            stopDriverLocationSharing(DriverTrackingStopReason.ORDER_COMPLETED)
            return@collect
          }
          when (gps) {
            GpsLocationState.PermissionDenied -> {
              stopDriverLocationSharing(DriverTrackingStopReason.PERMISSION_REVOKED)
            }
            GpsLocationState.GpsDisabled -> {
              stopDriverLocationSharing(DriverTrackingStopReason.GPS_DISABLED)
            }
            is GpsLocationState.Success -> {
              val loc = gps.location
              val nowMs = System.currentTimeMillis()
              if (driverLocationRateLimiter.shouldPublish(loc.latitude, loc.longitude, nowMs)) {
                val pubRes = cloudRepo.publishDriverLiveLocation(
                  orderId = orderId,
                  driverName = driverName,
                  lat = loc.latitude,
                  lng = loc.longitude,
                  accuracyMeters = loc.accuracyMeters.toDouble(),
                  isDemo = false,
                )
                if (pubRes.isSuccess) {
                  driverLocationRateLimiter.recordPublished(loc.latitude, loc.longitude, nowMs)
                }
              }
            }
            is GpsLocationState.Error -> {
              controlState.update { it.copy(driverTrackingStatusMessage = gps.message) }
            }
            else -> Unit
          }
        }
      }
    }
  }

  fun stopDriverLocationSharing(reason: DriverTrackingStopReason = DriverTrackingStopReason.USER_STOPPED) {
    val activeOrderId = controlState.value.driverTrackingOrderId
    driverTrackingJob?.cancel()
    driverTrackingJob = null
    driverLocationRateLimiter.reset()
    controlState.update {
      it.copy(
        isDriverSharingLocation = false,
        driverTrackingOrderId = null,
        driverTrackingIsDemo = false,
        driverTrackingStatusMessage = reason.labelVi,
      )
    }
    if (!activeOrderId.isNullOrBlank()) {
      viewModelScope.launch {
        cloudRepo.clearDriverLiveLocation(activeOrderId)
      }
    }
  }

  override fun onCleared() {
    driverTrackingJob?.cancel()
    driverTrackingJob = null
    super.onCleared()
  }

  // --- SHARED MULTI-DEVICE ORDER CREATION (SERVER-VERIFIED VIA CLOUD FUNCTIONS v2) ---

  fun submitCloudRideOrder(
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    pickupCoords: GeoPointData? = null,
    dropoffCoords: GeoPointData? = null,
    onSuccess: () -> Unit,
  ) {
    if (pickup.isBlank() || destination.isBlank()) {
      controlState.update { it.copy(actionNotice = "Vui lòng nhập đủ điểm đón và điểm đến.") }
      return
    }
    val quote = FoxDemoPricingEngine.estimateRide(vehicleType, distanceKm, voucherCode)
    val summary = "$vehicleType • ${distanceKm}km" + if (note.isNotBlank()) " • $note" else ""
    createVerifiedOrCashOrder(
      serviceType = "RIDE",
      title = "Đặt xe $vehicleType",
      primaryAddress = pickup.trim(),
      secondaryAddress = destination.trim(),
      detailSummary = summary,
      subtotalVnd = quote.subtotalVnd,
      feeVnd = quote.feeVnd,
      discountVnd = quote.discountVnd,
      totalVnd = quote.totalVnd,
      paymentMethod = paymentMethod,
      voucherCode = voucherCode,
      vehicleType = vehicleType,
      distanceKm = distanceKm,
      pickupCoords = pickupCoords,
      dropoffCoords = dropoffCoords,
      onSuccess = onSuccess,
    )
  }

  fun submitCloudDeliveryOrder(
    senderName: String,
    senderPhone: String,
    pickupAddress: String,
    receiverName: String,
    receiverPhone: String,
    dropoffAddress: String,
    itemType: String,
    weightTier: String,
    speed: String,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    distanceKm: Int? = null,
    pickupCoords: GeoPointData? = null,
    dropoffCoords: GeoPointData? = null,
    onSuccess: () -> Unit,
  ) {
    if (pickupAddress.isBlank() || dropoffAddress.isBlank() || receiverName.isBlank()) {
      controlState.update { it.copy(actionNotice = "Vui lòng nhập đủ địa chỉ gửi/nhận và tên người nhận.") }
      return
    }
    val quote = FoxDemoPricingEngine.estimateDelivery(weightTier, speed, voucherCode, distanceKm)
    val distLabel = if (distanceKm != null) ", ~${distanceKm}km" else ""
    val summary =
      "$itemType ($weightTier, $speed$distLabel) • Gửi: ${senderName.ifBlank { "Khách FOX" }} ($senderPhone) -> Nhận: $receiverName ($receiverPhone)" +
        if (note.isNotBlank()) " • $note" else ""
    createVerifiedOrCashOrder(
      serviceType = "DELIVERY",
      title = "Giao hàng $speed",
      primaryAddress = pickupAddress.trim(),
      secondaryAddress = dropoffAddress.trim(),
      detailSummary = summary,
      subtotalVnd = quote.subtotalVnd,
      feeVnd = quote.feeVnd,
      discountVnd = quote.discountVnd,
      totalVnd = quote.totalVnd,
      paymentMethod = paymentMethod,
      voucherCode = voucherCode,
      distanceKm = distanceKm,
      weightTier = weightTier,
      speed = speed,
      pickupCoords = pickupCoords,
      dropoffCoords = dropoffCoords,
      onSuccess = onSuccess,
    )
  }

  fun submitCloudFoodOrder(
    merchantId: String,
    merchantName: String,
    merchantAddress: String,
    address: String,
    itemLines: List<Pair<String, Int>>,
    itemParams: List<OrderItemParam>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) {
    if (merchantId.isBlank() || address.isBlank() || itemLines.isEmpty() || itemParams.isEmpty()) {
      controlState.update { it.copy(actionNotice = "Vui lòng chọn quán, chọn món và nhập địa chỉ giao hàng.") }
      return
    }
    val quote = FoxDemoPricingEngine.estimateFood(subtotalVnd, voucherCode)
    val summary = itemLines.joinToString(", ") { "${it.first} x${it.second}" }
    createVerifiedOrCashOrder(
      serviceType = "FOOD",
      title = "$merchantName (${itemLines.sumOf { it.second }} món)",
      primaryAddress = merchantAddress.ifBlank { merchantName },
      secondaryAddress = address.trim(),
      detailSummary = summary,
      subtotalVnd = quote.subtotalVnd,
      feeVnd = quote.feeVnd,
      discountVnd = quote.discountVnd,
      totalVnd = quote.totalVnd,
      paymentMethod = paymentMethod,
      voucherCode = voucherCode,
      merchantId = merchantId,
      items = itemParams,
      onSuccess = onSuccess,
    )
  }

  fun submitCloudShoppingOrder(
    merchantId: String,
    merchantName: String,
    merchantAddress: String,
    address: String,
    itemLines: List<Pair<String, Int>>,
    itemParams: List<OrderItemParam>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) {
    if (merchantId.isBlank() || address.isBlank() || itemLines.isEmpty() || itemParams.isEmpty()) {
      controlState.update { it.copy(actionNotice = "Vui lòng chọn gian hàng, sản phẩm và nhập địa chỉ nhận hàng.") }
      return
    }
    val quote = FoxDemoPricingEngine.estimateShopping(subtotalVnd, voucherCode)
    val summary = itemLines.joinToString(", ") { "${it.first} x${it.second}" }
    createVerifiedOrCashOrder(
      serviceType = "SHOPPING",
      title = "$merchantName (${itemLines.sumOf { it.second }} sản phẩm)",
      primaryAddress = merchantAddress.ifBlank { merchantName },
      secondaryAddress = address.trim(),
      detailSummary = summary,
      subtotalVnd = quote.subtotalVnd,
      feeVnd = quote.feeVnd,
      discountVnd = quote.discountVnd,
      totalVnd = quote.totalVnd,
      paymentMethod = paymentMethod,
      voucherCode = voucherCode,
      merchantId = merchantId,
      items = itemParams,
      onSuccess = onSuccess,
    )
  }

  private fun createVerifiedOrCashOrder(
    serviceType: String,
    title: String,
    primaryAddress: String,
    secondaryAddress: String,
    detailSummary: String,
    subtotalVnd: Long,
    feeVnd: Long,
    discountVnd: Long,
    totalVnd: Long,
    paymentMethod: String,
    voucherCode: String?,
    vehicleType: String? = null,
    distanceKm: Int? = null,
    weightTier: String? = null,
    speed: String? = null,
    merchantId: String? = null,
    items: List<OrderItemParam>? = null,
    pickupCoords: GeoPointData? = null,
    dropoffCoords: GeoPointData? = null,
    onSuccess: () -> Unit,
  ) {
    val normalizedMethod =
      if (paymentMethod == "Ví FOX DEMO") "Ví FOX DEMO" else "Tiền mặt DEMO"
    val hasRouteCoords = pickupCoords != null && dropoffCoords != null
    viewModelScope.launch {
      controlState.update {
        it.copy(
          isBusy = true,
          cloudError = null,
          walletActionStatus = WalletActionStatus(
            WalletOperationStage.PENDING,
            when {
              !merchantId.isNullOrBlank() -> "Đang kiểm tra tồn kho, xác thực giá & tạo đơn qua Cloud Functions..."
              hasRouteCoords -> "Đang xác thực tọa độ, quãng đường & giá cước phía server..."
              else -> "Đang xác thực giá & tạo đơn qua Cloud Functions..."
            },
          ),
        )
      }
      val idemKey = newIdempotencyKey("ord_${serviceType.lowercase()}")
      val fnResult = functionsClient.createVerifiedOrderAndPayDemo(
        idempotencyKey = idemKey,
        serviceType = serviceType,
        paymentMethod = normalizedMethod,
        title = title,
        primaryAddress = primaryAddress,
        secondaryAddress = secondaryAddress,
        detailSummary = detailSummary,
        voucherCode = voucherCode,
        vehicleType = vehicleType,
        distanceKm = distanceKm,
        weightTier = weightTier,
        speed = speed,
        merchantId = merchantId,
        items = items,
        expectedTotalVnd = totalVnd,
        pickupCoords = pickupCoords?.let { GeoCoordsParam(it.latitude, it.longitude) },
        dropoffCoords = dropoffCoords?.let { GeoCoordsParam(it.latitude, it.longitude) },
      )

      fnResult.fold(
        onSuccess = { res ->
          val stockNote = if (res.stockDeducted) " • Đã giữ tồn kho" else ""
          val msg =
            if (res.paymentStatus == "paid_demo") {
              "Đã tạo & thanh toán Ví DEMO đơn ${res.orderCode} (${res.totalVnd}đ$stockNote)."
            } else {
              "Đã tạo đơn xác thực server ${res.orderCode} (${res.totalVnd}đ • Tiền mặt DEMO$stockNote)."
            }
          controlState.update {
            it.copy(
              isBusy = false,
              actionNotice = msg,
              walletActionStatus = WalletActionStatus(WalletOperationStage.SUCCESS, msg),
            )
          }
          onSuccess()
        },
        onFailure = { err ->
          val fnEx = err.cause as? FirebaseFunctionsException
          val isNotDeployed =
            fnEx?.code == FirebaseFunctionsException.Code.NOT_FOUND ||
              fnEx?.code == FirebaseFunctionsException.Code.UNIMPLEMENTED ||
              fnEx?.code == FirebaseFunctionsException.Code.UNAVAILABLE

          // NEVER fallback for Ví FOX DEMO, Cloud Catalog (merchantId), OR coordinate-verified routes:
          // wallet debit, atomic stock deduction, and route distance pricing MUST be verified strictly on the server!
          if (isNotDeployed && normalizedMethod == "Tiền mặt DEMO" && merchantId.isNullOrBlank() && !hasRouteCoords) {
            val customerName = uiState.value.profile?.displayName?.ifBlank { "Khách FOX" } ?: "Khách FOX"
            val fallback = cloudRepo.createCloudOrder(
              serviceType = serviceType,
              customerName = customerName,
              title = title,
              primaryAddress = primaryAddress,
              secondaryAddress = secondaryAddress,
              detailSummary = detailSummary,
              subtotalVnd = subtotalVnd,
              feeVnd = feeVnd,
              discountVnd = discountVnd,
              totalVnd = totalVnd,
              paymentMethod = normalizedMethod,
              voucherCode = voucherCode,
            )
            fallback.fold(
              onSuccess = { code ->
                val msg = "Đã tạo đơn Tiền mặt $code (Cloud Functions chưa deploy nên giá đang là ước tính)."
                controlState.update {
                  it.copy(
                    isBusy = false,
                    actionNotice = msg,
                    walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, err.localizedMessage ?: msg),
                  )
                }
                onSuccess()
              },
              onFailure = { fbErr ->
                val msg = "Không thể tạo đơn Cloud: ${fbErr.localizedMessage ?: "Lỗi quyền/mạng"}"
                controlState.update {
                  it.copy(
                    isBusy = false,
                    actionNotice = msg,
                    walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
                  )
                }
              },
            )
          } else {
            val msg = when {
              isNotDeployed && !merchantId.isNullOrBlank() ->
                "Không thể tạo đơn danh mục Cloud: Cloud Functions chưa được triển khai để trừ tồn kho và xác thực giá phía server."
              isNotDeployed && hasRouteCoords ->
                "Không thể chốt giá theo tọa độ: Cloud Functions chưa triển khai để xác thực quãng đường và giá cuối cùng phía server."
              else ->
                err.localizedMessage ?: "Không thể xác thực & tạo đơn qua Cloud Functions."
            }
            controlState.update {
              it.copy(
                isBusy = false,
                actionNotice = msg,
                walletActionStatus = WalletActionStatus(WalletOperationStage.FAILURE, msg),
              )
            }
          }
        },
      )
    }
  }

  fun cancelCloudOrder(orderId: String) {
    if (controlState.value.driverTrackingOrderId == orderId) {
      stopDriverLocationSharing(DriverTrackingStopReason.ORDER_CANCELLED)
    }
    val currentOrder =
      (uiState.value.customerOrders +
        uiState.value.driverAssignedOrders +
        uiState.value.merchantAssignedOrders +
        uiState.value.adminAllOrders)
        .firstOrNull { it.id == orderId || it.orderCode == orderId }

    // Route through Cloud Functions whenever wallet payment OR atomic stock restoration is needed!
    if (currentOrder?.paymentStatus == "paid_demo" || currentOrder?.stockDeducted == true) {
      refundOrderDemo(orderId)
      return
    }
    runCloudAction("Đã hủy đơn Cloud $orderId theo quy tắc dịch vụ.") {
      cloudRepo.cancelCloudOrder(orderId).also { res ->
        if (res.isSuccess) notifyOrderEventBestEffort(orderId, "cancelled")
      }
    }
  }

  fun rateCloudOrder(orderId: String, rating: Int, reviewNote: String) {
    runCloudAction("Cảm ơn bạn đã đánh giá $rating★ cho đơn $orderId!") {
      cloudRepo.rateCompletedOrder(orderId, rating, reviewNote)
    }
  }

  // --- DRIVER ACTIONS (ATOMIC TRANSACTION + FULL LIFECYCLE + COMMISSION SETTLEMENT) ---

  fun claimOrderAsDriver(orderId: String) {
    val driverName = uiState.value.profile?.displayName ?: "Tài xế FOX"
    runCloudAction("Nhận đơn $orderId thành công (Transaction chống trùng).") {
      cloudRepo.claimOrderAsDriver(orderId, driverName).also { res ->
        if (res.isSuccess) notifyOrderEventBestEffort(orderId, "driver_accepted")
      }
    }
  }

  fun advanceDriverOrder(orderId: String, nextStatus: String) {
    viewModelScope.launch {
      controlState.update { it.copy(isBusy = true, cloudError = null) }
      if (nextStatus == "completed" && controlState.value.driverTrackingOrderId == orderId) {
        stopDriverLocationSharing(DriverTrackingStopReason.ORDER_COMPLETED)
      }
      val result = cloudRepo.advanceDriverOrderStatus(orderId, nextStatus)
      result.fold(
        onSuccess = {
          if (nextStatus == "completed") {
            // Ensure live location doc is cleaned up even if sharing wasn't active in this session
            cloudRepo.clearDriverLiveLocation(orderId)
            val settleRes = functionsClient.settleOrderCommissionDemo(orderId)
            if (settleRes.isFailure) {
              notifyOrderEventBestEffort(orderId, "completed")
            }
            val msg = settleRes.fold(
              onSuccess = { s ->
                "Hoàn thành $orderId & quyết toán hoa hồng DEMO: Tài xế +${s.driverCommissionVnd}đ."
              },
              onFailure = {
                "Đã hoàn thành $orderId (Chờ Cloud Functions deploy để quyết toán hoa hồng DEMO)."
              },
            )
            controlState.update { it.copy(isBusy = false, actionNotice = msg) }
          } else {
            notifyOrderEventBestEffort(orderId, nextStatus)
            controlState.update {
              it.copy(
                isBusy = false,
                actionNotice = "Đã cập nhật $orderId -> ${cloudOrderStatusLabel(nextStatus)}.",
              )
            }
          }
        },
        onFailure = { err ->
          controlState.update {
            it.copy(
              isBusy = false,
              actionNotice = err.localizedMessage ?: "Cập nhật trạng thái thất bại.",
            )
          }
        },
      )
    }
  }

  // --- MERCHANT ACTIONS (STORE PROFILE, CATALOG CRUD, ATOMIC ORDERS) ---

  fun saveMerchantStoreProfile(
    storeType: String,
    name: String,
    description: String,
    category: String,
    address: String,
    phone: String,
    imageUrl: String,
    openingHours: String,
    openNow: Boolean,
  ) {
    runCloudAction("Đã lưu hồ sơ gian hàng \"$name\" lên Cloud Firestore.") {
      cloudRepo.saveMerchantStoreProfile(
        storeType = storeType,
        name = name,
        description = description,
        category = category,
        address = address,
        phone = phone,
        imageUrl = imageUrl,
        openingHours = openingHours,
        openNow = openNow,
      )
    }
  }

  fun setMerchantStoreOpen(openNow: Boolean) {
    runCloudAction(if (openNow) "Đã mở cửa gian hàng nhận đơn." else "Đã tạm đóng cửa gian hàng.") {
      cloudRepo.toggleMerchantStoreOpen(openNow)
    }
  }

  fun claimOrderAsMerchant(orderId: String) {
    val storeName =
      uiState.value.merchantStoreProfile?.name?.ifBlank { null }
        ?: uiState.value.profile?.storeName?.ifBlank { uiState.value.profile?.displayName }
        ?: "Gian hàng FOX"
    runCloudAction("Gian hàng đã xác nhận đơn $orderId.") {
      cloudRepo.claimOrderAsMerchant(orderId, storeName).also { res ->
        if (res.isSuccess) notifyOrderEventBestEffort(orderId, "merchant_confirmed")
      }
    }
  }

  fun confirmAssignedMerchantOrder(orderId: String) {
    runCloudAction("Đã xác nhận tiếp nhận đơn $orderId.") {
      cloudRepo.confirmAssignedMerchantOrder(orderId).also { res ->
        if (res.isSuccess) notifyOrderEventBestEffort(orderId, "merchant_confirmed")
      }
    }
  }

  fun advanceMerchantOrder(orderId: String, nextStatus: String) {
    runCloudAction("Đã cập nhật đơn $orderId -> ${cloudOrderStatusLabel(nextStatus)}.") {
      cloudRepo.advanceMerchantOrderStatus(orderId, nextStatus).also { res ->
        if (res.isSuccess) notifyOrderEventBestEffort(orderId, nextStatus)
      }
    }
  }

  fun addMerchantProduct(
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
  ) {
    val storeName =
      uiState.value.merchantStoreProfile?.name?.ifBlank { null }
        ?: uiState.value.profile?.storeName?.ifBlank { "Gian hàng FOX" }
        ?: "Gian hàng FOX"
    runCloudAction("Đã thêm \"$name\" vào danh mục Cloud.") {
      cloudRepo.createMerchantProduct(
        storeName = storeName,
        serviceType = serviceType,
        name = name,
        description = description,
        imageUrl = imageUrl,
        category = category,
        priceVnd = priceVnd,
        stock = stock,
      )
    }
  }

  fun updateMerchantProduct(
    productId: String,
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
    active: Boolean,
  ) {
    val storeName =
      uiState.value.merchantStoreProfile?.name?.ifBlank { null }
        ?: uiState.value.profile?.storeName?.ifBlank { "Gian hàng FOX" }
        ?: "Gian hàng FOX"
    runCloudAction("Đã cập nhật sản phẩm \"$name\".") {
      cloudRepo.updateMerchantProduct(
        productId = productId,
        storeName = storeName,
        serviceType = serviceType,
        name = name,
        description = description,
        imageUrl = imageUrl,
        category = category,
        priceVnd = priceVnd,
        stock = stock,
        active = active,
      )
    }
  }

  fun setMerchantProductActive(productId: String, active: Boolean) {
    runCloudAction(if (active) "Đã hiển thị lại sản phẩm trên gian hàng." else "Đã ẩn sản phẩm khỏi danh mục khách.") {
      cloudRepo.setMerchantProductActive(productId, active)
    }
  }

  // --- ADMIN ACTIONS ---

  fun adminApproveDriver(targetUid: String, approve: Boolean) {
    runCloudAction(if (approve) "Đã duyệt tài khoản thành Tài xế." else "Đã từ chối hồ sơ Tài xế.") {
      cloudRepo.adminApproveDriver(targetUid, approve).also { res ->
        if (res.isSuccess) {
          notifyPartnerReviewBestEffort(
            targetUid = targetUid,
            partnerType = "driver",
            eventType = if (approve) "partner_approved" else "partner_rejected",
          )
        }
      }
    }
  }

  fun adminApproveMerchant(targetUid: String, approve: Boolean) {
    runCloudAction(if (approve) "Đã duyệt tài khoản thành Gian hàng." else "Đã từ chối hồ sơ Gian hàng.") {
      cloudRepo.adminApproveMerchant(targetUid, approve).also { res ->
        if (res.isSuccess) {
          notifyPartnerReviewBestEffort(
            targetUid = targetUid,
            partnerType = "merchant",
            eventType = if (approve) "partner_approved" else "partner_rejected",
          )
        }
      }
    }
  }

  fun adminSetAccountActive(targetUid: String, active: Boolean) {
    runCloudAction(if (active) "Đã mở khóa tài khoản." else "Đã tạm khóa tài khoản.") {
      cloudRepo.adminSetAccountStatus(targetUid, active)
    }
  }

  // --- NOTIFICATIONS ---

  fun markCloudNotificationRead(notificationId: String) {
    viewModelScope.launch {
      cloudRepo.markCloudNotificationRead(currentUserId, notificationId)
    }
  }

  fun markAllCloudNotificationsRead() {
    val unreadIds = uiState.value.cloudNotifications.filter { !it.readFlag }.map { it.id }
    if (unreadIds.isEmpty()) return
    runCloudAction("Đã đánh dấu đọc ${unreadIds.size} thông báo Cloud.") {
      cloudRepo.markAllCloudNotificationsRead(currentUserId, unreadIds)
    }
  }

  class Factory(
    private val context: Context,
    private val user: FirebaseUser,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(FoxCloudViewModel::class.java)) {
        val appCtx = context.applicationContext
        val fnClient = FoxFunctionsClient()
        val locRepo = FusedFoxLocationRepository(appCtx)
        val mRepo = FoxMapRepository(
          appContext = appCtx,
          functionsClient = fnClient,
          locationProvider = locRepo,
        )
        return FoxCloudViewModel(
          appContext = appCtx,
          cloudRepo = FoxCloudRepository(appCtx),
          functionsClient = fnClient,
          locationRepo = locRepo,
          mapRepo = mRepo,
          currentUserId = user.uid,
          authEmail = user.email,
          authEmailVerified = user.isEmailVerified,
        ) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}
