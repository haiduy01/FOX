package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.CloudMerchantProduct
import com.example.data.remote.CloudOrder
import com.example.data.remote.CloudUserProfile
import com.example.data.remote.canDriverCancelOrder
import com.example.data.remote.canMerchantCancelOrder
import com.example.data.remote.cloudOrderStatusLabel
import com.example.data.remote.cloudRoleLabel
import com.example.data.remote.nextDriverStep
import com.example.data.remote.orderLifecycleSteps
import com.example.notifications.FoxNotificationHelper
import com.example.ui.FoxCloudUiState
import com.example.ui.FoxServiceType
import com.example.ui.formatOrderTime
import com.example.ui.formatVnd
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboardScreen(
  cloudState: FoxCloudUiState,
  hasLocationPermission: () -> Boolean = { false },
  onToggleOnline: (Boolean) -> Unit,
  onClaimOrder: (String) -> Unit,
  onAdvanceOrder: (String, String) -> Unit,
  onCancelOrder: (String) -> Unit,
  onSettleCommission: (String) -> Unit,
  onStartLocationSharing: (orderId: String, useDemoMode: Boolean) -> Unit = { _, _ -> },
  onStopLocationSharing: () -> Unit = {},
  onSyncFcmToken: () -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val profile = cloudState.profile
  val isApproved = profile?.isApprovedDriver() == true
  var serviceFilter by remember { mutableStateOf<FoxServiceType?>(null) }
  val activeFilter = serviceFilter
  var pendingGpsShareOrderId by remember { mutableStateOf<String?>(null) }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val granted =
      grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    val targetOrder = pendingGpsShareOrderId
    pendingGpsShareOrderId = null
    if (granted && !targetOrder.isNullOrBlank()) {
      onStartLocationSharing(targetOrder, false)
    } else if (!granted) {
      onStopLocationSharing()
    }
  }

  val claimableOrders = remember(cloudState.openDriverOrders, activeFilter) {
    cloudState.openDriverOrders.filter { order ->
      val matchesService = activeFilter == null || order.serviceType == activeFilter.name
      matchesService &&
        order.driverId.isBlank() &&
        order.dispatchOpen &&
        order.dispatchTarget == "driver" &&
        ((order.serviceType in listOf("RIDE", "DELIVERY") && order.status == "pending") ||
          (order.serviceType in listOf("FOOD", "SHOPPING") && order.status == "ready_for_pickup"))
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "FOX Tài xế (Cloud)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("driver_dashboard_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.DirectionsBike, contentDescription = null, tint = FoxBlue)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isApproved) "Tài xế đã xác minh" else "Chưa được duyệt quyền Tài xế",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = if (isApproved) {
                    "Xe: ${profile?.vehicleType} • ${profile?.vehiclePlate} • Hoa hồng/Ví Cloud chờ backend"
                  } else {
                    "Trạng thái hồ sơ: ${profile?.driverStatus ?: "none"}. Vui lòng chờ Admin duyệt."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
              if (isApproved) {
                Switch(
                  checked = profile?.driverOnline == true,
                  onCheckedChange = onToggleOnline,
                  modifier = Modifier.testTag("driver_online_switch"),
                )
              }
            }
          }
        }

        if (isApproved) {
          item {
            val kpis = cloudState.driverKpis
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("Đang chạy", "${kpis.activeOrdersCount}", Modifier.weight(1f))
                AdminStatCard("Hoàn thành", "${kpis.completedOrdersCount}", Modifier.weight(1f))
                AdminStatCard("Hoa hồng DEMO", formatVnd(kpis.settledCommissionVnd), Modifier.weight(1f))
              }
              RoleFcmStatusBanner(
                fcmRegistered = cloudState.fcmTokenRegistered,
                fcmStatusText = cloudState.fcmSyncStatus,
                roleHint = if (profile?.driverOnline == true) {
                  "Đang TRỰC TUYẾN — sẽ nhận thông báo đẩy FCM khi có đơn mở."
                } else {
                  "Đang NGOẠI TUYẾN — bật trực tuyến để nhận thông báo đẩy FCM đơn mới."
                },
                onSyncFcmToken = onSyncFcmToken,
              )
            }
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Đơn chờ tài xế (${claimableOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxTextStrong,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                  FilterChip(
                    selected = serviceFilter == null,
                    onClick = { serviceFilter = null },
                    modifier = Modifier.testTag("driver_filter_all"),
                    label = { Text("Tất cả") },
                  )
                }
                items(FoxServiceType.entries) { service ->
                  FilterChip(
                    selected = serviceFilter == service,
                    onClick = { serviceFilter = service },
                    modifier = Modifier.testTag("driver_filter_${service.name.lowercase()}"),
                    label = { Text(service.title) },
                  )
                }
              }
            }
          }

          if (claimableOrders.isEmpty()) {
            item {
              EmptyWorkspaceCard("Chưa có đơn mở phù hợp bộ lọc. Đơn Đặt xe/Giao hàng mới hoặc đơn Đồ ăn/Mua sắm đã sẵn sàng sẽ xuất hiện tức thì tại đây.")
            }
          } else {
            items(claimableOrders, key = { it.id }) { order ->
              CloudOrderRoleCard(
                order = order,
                actionLabel = "Nhận đơn (Transaction)",
                actionTag = "driver_claim_${order.id}",
                secondaryCancelLabel = null,
                isBusy = cloudState.isBusy,
                onAction = { onClaimOrder(order.id) },
                onCancel = {},
              )
            }
          }

          item {
            Text(
              text = "Đơn bạn đã nhận (${cloudState.driverAssignedOrders.size})",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }

          if (cloudState.driverAssignedOrders.isEmpty()) {
            item {
              EmptyWorkspaceCard("Bạn chưa nhận đơn nào. Chọn một đơn ở danh sách trên để bắt đầu.")
            }
          } else {
            items(cloudState.driverAssignedOrders, key = { "drv_${it.id}" }) { order ->
              val nextStep = nextDriverStep(order)
              val canCancel = canDriverCancelOrder(order)
              val needsSettlement = order.status == "completed" && !order.commissionSettled
              val canShareLiveLocation = order.isActiveDriverTrackingEligible()
              val isSharingThisOrder =
                cloudState.isDriverSharingLocation && cloudState.driverTrackingOrderId == order.id

              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudOrderRoleCard(
                  order = order,
                  actionLabel = nextStep?.second ?: if (needsSettlement) "Chốt hoa hồng DEMO" else null,
                  actionTag = if (needsSettlement) "driver_settle_${order.id}" else "driver_advance_${order.id}",
                  secondaryCancelLabel = if (canCancel) "Hủy đơn" else null,
                  isBusy = cloudState.isBusy,
                  onAction = {
                    if (nextStep != null) {
                      onAdvanceOrder(order.id, nextStep.first)
                    } else if (needsSettlement) {
                      onSettleCommission(order.id)
                    }
                  },
                  onCancel = { onCancelOrder(order.id) },
                )

                if (canShareLiveLocation) {
                  Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                      containerColor = if (isSharingThisOrder) Color(0xFFEFF6FF) else Color.White,
                    ),
                    border = BorderStroke(
                      1.dp,
                      if (isSharingThisOrder) FoxBlue.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.testTag("driver_tracking_card_${order.id}"),
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = if (isSharingThisOrder) Icons.Rounded.GpsFixed else Icons.Rounded.GpsOff,
                            contentDescription = null,
                            tint = if (isSharingThisOrder) FoxBlue else FoxTextSecondary,
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Text(
                            text = if (isSharingThisOrder) {
                              if (cloudState.driverTrackingIsDemo) "Đang chia sẻ vị trí DEMO" else "Đang chia sẻ GPS thật"
                            } else {
                              "Chia sẻ vị trí trực tiếp (chỉ khi bật)"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSharingThisOrder) FoxBlue else FoxTextStrong,
                          )
                        }
                      }
                      Text(
                        text = cloudState.driverTrackingStatusMessage
                          ?: "Chỉ khách hàng của đơn này xem được vị trí. Tự động dừng & xóa vị trí khi hoàn thành hoặc hủy đơn (không theo dõi nền).",
                        style = MaterialTheme.typography.labelSmall,
                        color = FoxTextSecondary,
                      )
                      if (isSharingThisOrder) {
                        OutlinedButton(
                          onClick = onStopLocationSharing,
                          modifier = Modifier.fillMaxWidth().height(44.dp).testTag("driver_stop_share_${order.id}"),
                          border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.6f)),
                        ) {
                          Text("Dừng chia sẻ vị trí ngay", color = FoxRed, fontWeight = FontWeight.Bold)
                        }
                      } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                          Button(
                            onClick = {
                              if (hasLocationPermission()) {
                                onStartLocationSharing(order.id, false)
                              } else {
                                pendingGpsShareOrderId = order.id
                                locationPermissionLauncher.launch(
                                  arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                  )
                                )
                              }
                            },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("driver_share_gps_${order.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                          ) {
                            Text("Bật GPS thật", fontWeight = FontWeight.Bold)
                          }
                          OutlinedButton(
                            onClick = { onStartLocationSharing(order.id, true) },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("driver_share_demo_${order.id}"),
                            border = BorderStroke(1.dp, FoxOrangeDeep.copy(alpha = 0.6f)),
                          ) {
                            Text("Phát vị trí DEMO", color = FoxOrangeDeep, fontWeight = FontWeight.Bold)
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDashboardScreen(
  cloudState: FoxCloudUiState,
  onSaveStoreProfile: (
    storeType: String,
    name: String,
    description: String,
    category: String,
    address: String,
    phone: String,
    imageUrl: String,
    openingHours: String,
    openNow: Boolean,
  ) -> Unit,
  onToggleStoreOpen: (Boolean) -> Unit,
  onClaimOrder: (String) -> Unit,
  onConfirmAssignedOrder: (String) -> Unit,
  onAdvanceOrder: (String, String) -> Unit,
  onCancelOrder: (String) -> Unit,
  onAddProduct: (
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
  ) -> Unit,
  onUpdateProduct: (
    productId: String,
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
    active: Boolean,
  ) -> Unit,
  onToggleProductActive: (productId: String, active: Boolean) -> Unit,
  onSyncFcmToken: () -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val profile = cloudState.profile
  val storeProfile = cloudState.merchantStoreProfile
  val isApproved = profile?.isApprovedMerchant() == true
  var serviceFilter by remember { mutableStateOf<FoxServiceType?>(null) }
  var assignedOrderStatusFilter by remember { mutableStateOf("ALL") }
  val activeFilter = serviceFilter
  val filteredAssignedOrders = remember(cloudState.merchantAssignedOrders, assignedOrderStatusFilter) {
    when (assignedOrderStatusFilter) {
      "PENDING" -> cloudState.merchantAssignedOrders.filter { it.status == "pending" }
      "ACTIVE" -> cloudState.merchantAssignedOrders.filter {
        it.status in listOf("merchant_confirmed", "preparing", "ready_for_pickup", "driver_accepted", "driver_arrived", "in_progress")
      }
      "DONE" -> cloudState.merchantAssignedOrders.filter { it.status in listOf("completed", "cancelled") }
      else -> cloudState.merchantAssignedOrders
    }
  }

  val openMerchantOrders = remember(cloudState.openMerchantOrders, activeFilter) {
    cloudState.openMerchantOrders.filter { order ->
      val matches = activeFilter == null || order.serviceType == activeFilter.name
      matches &&
        order.merchantId.isBlank() &&
        order.status == "pending" &&
        order.dispatchOpen &&
        order.dispatchTarget == "merchant" &&
        order.serviceType in listOf("FOOD", "SHOPPING")
    }
  }

  var storeType by remember(storeProfile?.storeType) { mutableStateOf(storeProfile?.storeType ?: "FOOD") }
  var storeName by remember(storeProfile?.name, profile?.storeName) {
    mutableStateOf(storeProfile?.name?.ifBlank { null } ?: profile?.storeName ?: "")
  }
  var storeDesc by remember(storeProfile?.description) { mutableStateOf(storeProfile?.description ?: "") }
  var storeCategory by remember(storeProfile?.category, profile?.storeCategory) {
    mutableStateOf(storeProfile?.category?.ifBlank { null } ?: profile?.storeCategory ?: "Đồ ăn")
  }
  var storeAddress by remember(storeProfile?.address, profile?.defaultAddress) {
    mutableStateOf(storeProfile?.address?.ifBlank { null } ?: profile?.defaultAddress ?: "")
  }
  var storePhone by remember(storeProfile?.phone, profile?.phone) {
    mutableStateOf(storeProfile?.phone?.ifBlank { null } ?: profile?.phone ?: "")
  }
  var storeImageUrl by remember(storeProfile?.imageUrl) { mutableStateOf(storeProfile?.imageUrl ?: "") }
  var storeHours by remember(storeProfile?.openingHours) { mutableStateOf(storeProfile?.openingHours ?: "07:00 - 22:00") }
  var storeOpenNow by remember(storeProfile?.openNow) { mutableStateOf(storeProfile?.openNow ?: true) }

  var prodName by remember { mutableStateOf("") }
  var prodDesc by remember { mutableStateOf("") }
  var prodImageUrl by remember { mutableStateOf("") }
  var prodCategory by remember { mutableStateOf("Đồ ăn") }
  var prodPriceText by remember { mutableStateOf("45000") }
  var prodStockText by remember { mutableStateOf("20") }

  var editingProduct by remember { mutableStateOf<CloudMerchantProduct?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "FOX Gian hàng (Cloud)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("merchant_dashboard_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.Storefront, contentDescription = null, tint = FoxOrangeDeep)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = storeProfile?.name?.ifBlank { null }
                    ?: profile?.storeName?.ifBlank { "Gian hàng FOX" }
                    ?: "Gian hàng FOX",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = if (isApproved) {
                    val statusLabel = if (storeProfile?.openNow == true) "Đang mở cửa" else if (storeProfile == null) "Chưa thiết lập hồ sơ" else "Đang đóng cửa"
                    "Đã xác minh • $statusLabel • ${storeProfile?.openingHours ?: "Chưa cấu hình giờ"}"
                  } else {
                    "Trạng thái duyệt: ${profile?.merchantStatus ?: "none"}. Vui lòng chờ Admin duyệt."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
              if (isApproved && storeProfile != null) {
                Switch(
                  checked = storeProfile.openNow,
                  onCheckedChange = onToggleStoreOpen,
                  modifier = Modifier.testTag("merchant_open_switch"),
                )
              }
            }
          }
        }

        if (isApproved) {
          item {
            val kpis = cloudState.merchantKpis
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("Đơn xử lý", "${kpis.pendingOrActiveOrdersCount}", Modifier.weight(1f))
                AdminStatCard("Hoàn thành", "${kpis.completedOrdersCount}", Modifier.weight(1f))
                AdminStatCard("Doanh thu DEMO", formatVnd(kpis.settledRevenueVnd), Modifier.weight(1f))
              }
              if (kpis.lowStockProductsCount > 0 || kpis.outOfStockProductsCount > 0) {
                Surface(
                  modifier = Modifier.fillMaxWidth().testTag("merchant_low_stock_alert"),
                  shape = RoundedCornerShape(14.dp),
                  color = Color(0xFFFFF7ED),
                  border = BorderStroke(1.dp, FoxOrange.copy(alpha = 0.55f)),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = FoxOrangeDeep, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Cảnh báo tồn kho: ${kpis.outOfStockProductsCount} SP hết hàng • ${kpis.lowStockProductsCount} SP sắp hết (≤ 5).",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = FoxOrangeDeep,
                    )
                  }
                }
              }
              RoleFcmStatusBanner(
                fcmRegistered = cloudState.fcmTokenRegistered,
                fcmStatusText = cloudState.fcmSyncStatus,
                roleHint = "Gian hàng nhận thông báo FCM tức thì khi khách đặt đơn vào gian hàng.",
                onSyncFcmToken = onSyncFcmToken,
              )
            }
          }

          // Store Profile Editor Card
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  text = "Hồ sơ gian hàng trên Cloud Firestore",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(
                    selected = storeType == "FOOD",
                    onClick = { storeType = "FOOD" },
                    modifier = Modifier.testTag("merchant_type_food"),
                    label = { Text("Nhà hàng (Đồ ăn)") },
                  )
                  FilterChip(
                    selected = storeType == "SHOPPING",
                    onClick = { storeType = "SHOPPING" },
                    modifier = Modifier.testTag("merchant_type_shopping"),
                    label = { Text("Gian hàng (Mua sắm)") },
                  )
                }
                OutlinedTextField(
                  value = storeName,
                  onValueChange = { storeName = it },
                  label = { Text("Tên gian hàng") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("merchant_store_name_input"),
                )
                OutlinedTextField(
                  value = storeDesc,
                  onValueChange = { storeDesc = it },
                  label = { Text("Mô tả gian hàng") },
                  modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = storeCategory,
                    onValueChange = { storeCategory = it },
                    label = { Text("Danh mục") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedTextField(
                    value = storeHours,
                    onValueChange = { storeHours = it },
                    label = { Text("Giờ hoạt động") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                }
                OutlinedTextField(
                  value = storeAddress,
                  onValueChange = { storeAddress = it },
                  label = { Text("Địa chỉ gian hàng") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("merchant_store_address_input"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = storePhone,
                    onValueChange = { storePhone = it },
                    label = { Text("Số điện thoại") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedTextField(
                    value = storeImageUrl,
                    onValueChange = { storeImageUrl = it },
                    label = { Text("URL ảnh đại diện") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = if (storeOpenNow) "Trạng thái: Đang mở nhận đơn" else "Trạng thái: Tạm đóng cửa",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (storeOpenNow) FoxGreen else FoxRed,
                  )
                  Switch(checked = storeOpenNow, onCheckedChange = { storeOpenNow = it })
                }
                Button(
                  onClick = {
                    onSaveStoreProfile(
                      storeType,
                      storeName,
                      storeDesc,
                      storeCategory,
                      storeAddress,
                      storePhone,
                      storeImageUrl,
                      storeHours,
                      storeOpenNow,
                    )
                  },
                  enabled = !cloudState.isBusy && storeName.trim().length >= 2 && storeAddress.isNotBlank(),
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("merchant_save_store_button"),
                  colors = ButtonDefaults.buttonColors(containerColor = FoxOrangeDeep),
                ) {
                  Text("Lưu hồ sơ gian hàng lên Cloud", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Assigned / Targeted Orders of this Merchant (includes targeted pending orders!)
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Đơn đặt tới gian hàng của tôi (${filteredAssignedOrders.size}/${cloudState.merchantAssignedOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxTextStrong,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                  FilterChip(
                    selected = assignedOrderStatusFilter == "ALL",
                    onClick = { assignedOrderStatusFilter = "ALL" },
                    label = { Text("Tất cả") },
                  )
                }
                item {
                  FilterChip(
                    selected = assignedOrderStatusFilter == "PENDING",
                    onClick = { assignedOrderStatusFilter = "PENDING" },
                    label = { Text("Chờ nhận") },
                  )
                }
                item {
                  FilterChip(
                    selected = assignedOrderStatusFilter == "ACTIVE",
                    onClick = { assignedOrderStatusFilter = "ACTIVE" },
                    label = { Text("Đang xử lý") },
                  )
                }
                item {
                  FilterChip(
                    selected = assignedOrderStatusFilter == "DONE",
                    onClick = { assignedOrderStatusFilter = "DONE" },
                    label = { Text("Hoàn thành/Hủy") },
                  )
                }
              }
            }
          }
          if (filteredAssignedOrders.isEmpty()) {
            item { EmptyWorkspaceCard("Không có đơn nào trong bộ lọc này.") }
          } else {
            items(filteredAssignedOrders, key = { "m_own_${it.id}" }) { order ->
              val nextPair =
                when (order.status) {
                  "pending" -> "merchant_confirmed" to "Xác nhận nhận đơn"
                  "merchant_confirmed" -> "preparing" to "Bắt đầu chuẩn bị món/hàng"
                  "preparing" -> "ready_for_pickup" to "Sẵn sàng bàn giao Tài xế"
                  else -> null
                }
              CloudOrderRoleCard(
                order = order,
                actionLabel = nextPair?.second,
                actionTag = "merchant_advance_${order.id}",
                secondaryCancelLabel = if (canMerchantCancelOrder(order)) "Hủy đơn" else null,
                isBusy = cloudState.isBusy,
                onAction = {
                  if (order.status == "pending") {
                    onConfirmAssignedOrder(order.id)
                  } else {
                    nextPair?.let { onAdvanceOrder(order.id, it.first) }
                  }
                },
                onCancel = { onCancelOrder(order.id) },
              )
            }
          }

          // Legacy open unassigned FOOD/SHOPPING queue
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Đơn mở chưa gắn gian hàng (${openMerchantOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxTextStrong,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = serviceFilter == null,
                  onClick = { serviceFilter = null },
                  label = { Text("Tất cả") },
                )
                FilterChip(
                  selected = serviceFilter == FoxServiceType.FOOD,
                  onClick = { serviceFilter = FoxServiceType.FOOD },
                  label = { Text("Đặt đồ ăn") },
                )
                FilterChip(
                  selected = serviceFilter == FoxServiceType.SHOPPING,
                  onClick = { serviceFilter = FoxServiceType.SHOPPING },
                  label = { Text("Mua sắm") },
                )
              }
            }
          }
          if (openMerchantOrders.isEmpty()) {
            item { EmptyWorkspaceCard("Không có đơn mở nào chưa gắn gian hàng.") }
          } else {
            items(openMerchantOrders, key = { "m_open_${it.id}" }) { order ->
              CloudOrderRoleCard(
                order = order,
                actionLabel = "Xác nhận nhận đơn (Transaction)",
                actionTag = "merchant_claim_${order.id}",
                secondaryCancelLabel = null,
                isBusy = cloudState.isBusy,
                onAction = { onClaimOrder(order.id) },
                onCancel = {},
              )
            }
          }

          // Add Product Card
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  text = "Thêm sản phẩm / món ăn riêng của gian hàng",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                OutlinedTextField(
                  value = prodName,
                  onValueChange = { prodName = it },
                  label = { Text("Tên món / sản phẩm") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("merchant_product_name_input"),
                )
                OutlinedTextField(
                  value = prodDesc,
                  onValueChange = { prodDesc = it },
                  label = { Text("Mô tả chi tiết") },
                  modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                  value = prodCategory,
                  onValueChange = { prodCategory = it },
                  label = { Text("Danh mục") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                  value = prodImageUrl,
                  onValueChange = { prodImageUrl = it },
                  label = { Text("URL ảnh sản phẩm (tùy chọn)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = prodPriceText,
                    onValueChange = { prodPriceText = it.filter(Char::isDigit) },
                    label = { Text("Giá (VND)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedTextField(
                    value = prodStockText,
                    onValueChange = { prodStockText = it.filter(Char::isDigit) },
                    label = { Text("Tồn kho") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                  )
                }
                Button(
                  onClick = {
                    val price = prodPriceText.toLongOrNull() ?: 0L
                    val stock = prodStockText.toLongOrNull() ?: 0L
                    onAddProduct(storeType, prodName, prodDesc, prodImageUrl, prodCategory, price, stock)
                    prodName = ""
                    prodDesc = ""
                    prodImageUrl = ""
                  },
                  enabled = !cloudState.isBusy && prodName.isNotBlank(),
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("merchant_add_product_button"),
                  colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Lưu sản phẩm lên Firestore", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Product List with Edit & Hide/Show Controls
          item {
            Text(
              text = "Danh mục sản phẩm của tôi (${cloudState.merchantProducts.size})",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }
          items(cloudState.merchantProducts, key = { it.id }) { prod ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(
                1.dp,
                if (prod.active) MaterialTheme.colorScheme.outline else FoxRed.copy(alpha = 0.35f),
              ),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Text(
                        prod.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = FoxTextStrong,
                      )
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = if (prod.active) FoxGreen.copy(alpha = 0.14f) else FoxRed.copy(alpha = 0.12f),
                      ) {
                        Text(
                          text = if (prod.active) "Đang bán" else "Đang ẩn",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = if (prod.active) FoxGreen else FoxRed,
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                      }
                    }
                    if (prod.description.isNotBlank()) {
                      Text(prod.description, style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
                    }
                    Text(
                      "${prod.category} • Kho: ${prod.stock}",
                      style = MaterialTheme.typography.bodySmall,
                      color = FoxTextSecondary,
                    )
                  }
                  Text(
                    text = formatVnd(prod.priceVnd),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = FoxBlue,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { editingProduct = prod },
                    enabled = !cloudState.isBusy,
                    modifier = Modifier.weight(1f).height(44.dp).testTag("merchant_edit_prod_${prod.id}"),
                  ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa", fontWeight = FontWeight.Bold)
                  }
                  OutlinedButton(
                    onClick = { onToggleProductActive(prod.id, !prod.active) },
                    enabled = !cloudState.isBusy,
                    modifier = Modifier.weight(1f).height(44.dp).testTag("merchant_toggle_prod_${prod.id}"),
                    border = BorderStroke(1.dp, if (prod.active) FoxRed.copy(alpha = 0.5f) else FoxGreen.copy(alpha = 0.5f)),
                  ) {
                    Icon(
                      imageVector = if (prod.active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                      contentDescription = null,
                      tint = if (prod.active) FoxRed else FoxGreen,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (prod.active) "Ẩn SP" else "Hiện SP",
                      color = if (prod.active) FoxRed else FoxGreen,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  editingProduct?.let { prod ->
    var editName by remember(prod.id) { mutableStateOf(prod.name) }
    var editDesc by remember(prod.id) { mutableStateOf(prod.description) }
    var editCategory by remember(prod.id) { mutableStateOf(prod.category) }
    var editImageUrl by remember(prod.id) { mutableStateOf(prod.imageUrl) }
    var editPrice by remember(prod.id) { mutableStateOf(prod.priceVnd.toString()) }
    var editStock by remember(prod.id) { mutableStateOf(prod.stock.toString()) }

    AlertDialog(
      onDismissRequest = { editingProduct = null },
      title = { Text("Chỉnh sửa sản phẩm", fontWeight = FontWeight.ExtraBold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Tên sản phẩm") }, singleLine = true)
          OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Mô tả") })
          OutlinedTextField(value = editCategory, onValueChange = { editCategory = it }, label = { Text("Danh mục") }, singleLine = true)
          OutlinedTextField(value = editImageUrl, onValueChange = { editImageUrl = it }, label = { Text("URL ảnh") }, singleLine = true)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = editPrice,
              onValueChange = { editPrice = it.filter(Char::isDigit) },
              label = { Text("Giá (VND)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = editStock,
              onValueChange = { editStock = it.filter(Char::isDigit) },
              label = { Text("Tồn kho") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateProduct(
              prod.id,
              prod.serviceType.ifBlank { storeType },
              editName,
              editDesc,
              editImageUrl,
              editCategory,
              editPrice.toLongOrNull() ?: prod.priceVnd,
              editStock.toLongOrNull() ?: prod.stock,
              prod.active,
            )
            editingProduct = null
          },
          enabled = editName.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
        ) {
          Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingProduct = null }) {
          Text("Hủy")
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
  cloudState: FoxCloudUiState,
  onApproveDriver: (String, Boolean) -> Unit,
  onApproveMerchant: (String, Boolean) -> Unit,
  onSetAccountActive: (String, Boolean) -> Unit,
  onCancelOrder: (String) -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var userFilter by remember { mutableStateOf("ALL") }
  var orderServiceFilter by remember { mutableStateOf<FoxServiceType?>(null) }

  val pendingPartners = remember(cloudState.adminUsers) {
    cloudState.adminUsers.filter { it.driverStatus == "pending" || it.merchantStatus == "pending" }
  }
  val filteredUsers = remember(cloudState.adminUsers, userFilter) {
    when (userFilter) {
      "PENDING" -> cloudState.adminUsers.filter { it.driverStatus == "pending" || it.merchantStatus == "pending" }
      "DRIVER" -> cloudState.adminUsers.filter { it.role == "driver" }
      "MERCHANT" -> cloudState.adminUsers.filter { it.role == "merchant" }
      "SUSPENDED" -> cloudState.adminUsers.filter { it.accountStatus == "suspended" }
      else -> cloudState.adminUsers
    }
  }
  val filteredOrders = remember(cloudState.adminAllOrders, orderServiceFilter) {
    val activeService = orderServiceFilter
    if (activeService == null) cloudState.adminAllOrders
    else cloudState.adminAllOrders.filter { it.serviceType == activeService.name }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "FOX Quản trị viên (Admin)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("admin_dashboard_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!cloudState.isVerifiedAdmin) {
          item {
            EmptyWorkspaceCard("Tài khoản hiện tại không có quyền Quản trị viên xác thực bởi Firestore Rules.")
          }
        } else {
          val adminKpis = cloudState.adminKpis
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("Người dùng", "${adminKpis.totalUsers}", Modifier.weight(1f))
                AdminStatCard("Chờ duyệt", "${adminKpis.pendingPartnersCount}", Modifier.weight(1f))
                AdminStatCard("Đơn Cloud", "${cloudState.adminAllOrders.size}", Modifier.weight(1f))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("TX Online", "${adminKpis.onlineDriversCount}/${adminKpis.approvedDriversCount}", Modifier.weight(1f))
                AdminStatCard("Gian hàng", "${adminKpis.approvedMerchantsCount}", Modifier.weight(1f))
                AdminStatCard("Đang xử lý", "${adminKpis.activeOrdersCount}", Modifier.weight(1f))
              }
            }
          }

          // Honest System & Pilot Readiness Overview Card
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, FoxOrange.copy(alpha = 0.45f)),
              modifier = Modifier.testTag("admin_system_readiness_card"),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "Trạng thái Pilot & Tích hợp hệ thống",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                  color = FoxTextStrong,
                )
                Text(
                  text = "• Auth & Firestore Rules: Sẵn sàng (đã kiểm thử Emulator)\n" +
                    "• Cloud Functions v2 (12 functions tài chính/catalog/routes/FCM): Đã build & kiểm thử Emulator, CHƯA triển khai live\n" +
                    "• FCM Push theo vai trò: ${if (cloudState.fcmTokenRegistered) "Đã đăng ký token thiết bị" else "Chưa đăng ký token trên thiết bị này"}\n" +
                    "• Google Maps SDK & Routes API thật: ${if (cloudState.mapConfigStatus.isRealMapsKeyConfigured) "Đã cấu hình MAPS_API_KEY" else "TẠM KHÓA (chưa có MAPS_API_KEY — đang chạy Bản đồ DEMO)"}",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxTextSecondary,
                )
              }
            }
          }

          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Tài chính & Doanh thu FOX DEMO",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = FoxTextStrong,
                  )
                  Surface(shape = RoundedCornerShape(50), color = FoxOrange.copy(alpha = 0.14f)) {
                    Text(
                      text = "DEMO — KHÔNG PHẢI TIỀN THẬT",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = FoxOrangeDeep,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  AdminStatCard("Doanh thu FOX", formatVnd(cloudState.totalAdminPlatformRevenueVnd), Modifier.weight(1f))
                  AdminStatCard("HH Tài xế", formatVnd(cloudState.totalAdminDriverCommissionVnd), Modifier.weight(1f))
                  AdminStatCard("HH Gian hàng", formatVnd(cloudState.totalAdminMerchantCommissionVnd), Modifier.weight(1f))
                }
                Text(
                  text = "Đã quyết toán: ${cloudState.adminPlatformRevenue.size} đơn • GMV hoàn thành: ${formatVnd(adminKpis.grossCompletedVolumeVnd)}.",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxTextSecondary,
                )
              }
            }
          }

          item {
            Text(
              text = "Hồ sơ đối tác chờ duyệt (${pendingPartners.size})",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }

          if (pendingPartners.isEmpty()) {
            item { EmptyWorkspaceCard("Hiện không có yêu cầu nâng vai trò Tài xế / Gian hàng nào đang chờ duyệt.") }
          } else {
            items(pendingPartners, key = { "pending_${it.userId}" }) { user ->
              AdminUserCard(
                user = user,
                isBusy = cloudState.isBusy,
                onApproveDriver = onApproveDriver,
                onApproveMerchant = onApproveMerchant,
                onSetAccountActive = onSetAccountActive,
              )
            }
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Danh sách tài khoản (${filteredUsers.size}/${cloudState.adminUsers.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxTextStrong,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(
                  "ALL" to "Tất cả",
                  "PENDING" to "Chờ duyệt",
                  "DRIVER" to "Tài xế",
                  "MERCHANT" to "Gian hàng",
                  "SUSPENDED" to "Đã khóa",
                )
                items(filters) { (key, label) ->
                  FilterChip(
                    selected = userFilter == key,
                    onClick = { userFilter = key },
                    label = { Text(label) },
                  )
                }
              }
            }
          }
          items(filteredUsers, key = { "user_${it.userId}" }) { user ->
            AdminUserCard(
              user = user,
              isBusy = cloudState.isBusy,
              onApproveDriver = onApproveDriver,
              onApproveMerchant = onApproveMerchant,
              onSetAccountActive = onSetAccountActive,
            )
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Giám sát đơn hàng Cloud (${filteredOrders.size}/${cloudState.adminAllOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxTextStrong,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                  FilterChip(
                    selected = orderServiceFilter == null,
                    onClick = { orderServiceFilter = null },
                    label = { Text("Tất cả") },
                  )
                }
                items(FoxServiceType.entries) { service ->
                  FilterChip(
                    selected = orderServiceFilter == service,
                    onClick = { orderServiceFilter = service },
                    label = { Text(service.title) },
                  )
                }
              }
            }
          }
          items(filteredOrders.take(20), key = { "adm_ord_${it.id}" }) { order ->
            val canAdminCancel = order.status !in listOf("completed", "cancelled")
            CloudOrderRoleCard(
              order = order,
              actionLabel = null,
              actionTag = "admin_order_${order.id}",
              secondaryCancelLabel = if (canAdminCancel) "Admin hủy đơn" else null,
              isBusy = cloudState.isBusy,
              onAction = {},
              onCancel = { onCancelOrder(order.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun OrderStepProgressStrip(serviceType: String, status: String) {
  if (status == "cancelled") {
    Surface(shape = RoundedCornerShape(10.dp), color = FoxRed.copy(alpha = 0.1f)) {
      Text(
        text = "Đơn hàng đã bị hủy",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = FoxRed,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      )
    }
    return
  }
  val steps = remember(serviceType) { orderLifecycleSteps(serviceType) }
  val activeIndex = steps.indexOfFirst { it.first == status }.coerceAtLeast(0)
  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    items(steps.indices.toList()) { idx ->
      val reached = idx <= activeIndex
      val isCurrent = idx == activeIndex
      Surface(
        shape = RoundedCornerShape(50),
        color = when {
          isCurrent -> FoxBlue
          reached -> FoxGreen.copy(alpha = 0.16f)
          else -> Color(0xFFF1F4F9)
        },
      ) {
        Text(
          text = "${idx + 1}. ${steps[idx].second}",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
          ),
          color = when {
            isCurrent -> Color.White
            reached -> FoxGreen
            else -> FoxTextSecondary
          },
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
        color = FoxBlue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = FoxTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun AdminUserCard(
  user: CloudUserProfile,
  isBusy: Boolean,
  onApproveDriver: (String, Boolean) -> Unit,
  onApproveMerchant: (String, Boolean) -> Unit,
  onSetAccountActive: (String, Boolean) -> Unit,
) {
  val isSuspended = user.accountStatus == "suspended"
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = FoxBlue)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(user.displayName.ifBlank { user.userId }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
          Text(user.email.ifBlank { user.userId }, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
        }
        Surface(
          shape = RoundedCornerShape(50),
          color = if (isSuspended) FoxRed.copy(alpha = 0.12f) else FoxBlue.copy(alpha = 0.12f),
        ) {
          Text(
            text = cloudRoleLabel(user.role),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSuspended) FoxRed else FoxBlue,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      if (user.driverStatus == "pending") {
        Text(
          text = "Đăng ký Tài xế: ${user.vehicleType} • Biển số: ${user.vehiclePlate}",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = FoxOrangeDeep,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { onApproveDriver(user.userId, true) },
            enabled = !isBusy,
            modifier = Modifier.weight(1f).testTag("admin_approve_driver_${user.userId}"),
            colors = ButtonDefaults.buttonColors(containerColor = FoxGreen),
          ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Duyệt Tài xế")
          }
          OutlinedButton(
            onClick = { onApproveDriver(user.userId, false) },
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
          ) {
            Text("Từ chối", color = FoxRed)
          }
        }
      }

      if (user.merchantStatus == "pending") {
        Text(
          text = "Đăng ký Gian hàng: ${user.storeName} (${user.storeCategory})",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = FoxOrangeDeep,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { onApproveMerchant(user.userId, true) },
            enabled = !isBusy,
            modifier = Modifier.weight(1f).testTag("admin_approve_merchant_${user.userId}"),
            colors = ButtonDefaults.buttonColors(containerColor = FoxGreen),
          ) {
            Text("Duyệt Gian hàng")
          }
          OutlinedButton(
            onClick = { onApproveMerchant(user.userId, false) },
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
          ) {
            Text("Từ chối", color = FoxRed)
          }
        }
      }

      if (user.role != "admin") {
        OutlinedButton(
          onClick = { onSetAccountActive(user.userId, isSuspended) },
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
          Icon(
            imageVector = if (isSuspended) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (isSuspended) FoxGreen else FoxRed,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isSuspended) "Mở khóa tài khoản" else "Tạm khóa tài khoản",
            color = if (isSuspended) FoxGreen else FoxRed,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun CloudOrderRoleCard(
  order: CloudOrder,
  actionLabel: String?,
  actionTag: String,
  secondaryCancelLabel: String?,
  isBusy: Boolean,
  onAction: () -> Unit,
  onCancel: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${order.orderCode} • ${order.serviceType}",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = FoxBlue,
        )
        Surface(shape = RoundedCornerShape(50), color = FoxOrange.copy(alpha = 0.14f)) {
          Text(
            text = cloudOrderStatusLabel(order.status),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = FoxOrangeDeep,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      OrderStepProgressStrip(serviceType = order.serviceType, status = order.status)

      Text(order.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
      Text(order.detailSummary, style = MaterialTheme.typography.bodySmall, color = FoxTextStrong)
      Text("Từ: ${order.primaryAddress}\nĐến: ${order.secondaryAddress}", style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
      if (order.distanceKm > 0L) {
        Text(
          text = "Lộ trình: ~${order.distanceKm} km • ${order.durationMinutes} phút (${if (order.isDemoRoute) "Ước tính DEMO" else "Google Routes v2"})",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = FoxBlue,
        )
      }
      if (order.driverName.isNotBlank() || order.merchantName.isNotBlank()) {
        Text(
          text = buildString {
            if (order.merchantName.isNotBlank()) append("Gian hàng: ${order.merchantName}  ")
            if (order.driverName.isNotBlank()) append("Tài xế: ${order.driverName}")
          },
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = FoxGreen,
        )
      }
      val payStatusText = com.example.data.remote.cloudPaymentStatusLabel(order.paymentStatus, order.financialStatus)
      Text(
        text = "Tổng: ${formatVnd(order.totalVnd)} • $payStatusText (${order.paymentMethod})",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (order.paymentStatus == "paid_demo") FoxGreen else FoxBlue,
      )
      if (order.commissionSettled) {
        Text(
          text = "Quyết toán DEMO: Tài xế +${formatVnd(order.driverCommissionVnd)} • Gian hàng +${formatVnd(order.merchantCommissionVnd)} • FOX +${formatVnd(order.platformRevenueVnd)}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = FoxOrangeDeep,
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        order.createdAt?.let { ts ->
          Text(formatOrderTime(ts.toDate().time), style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
        } ?: Spacer(modifier = Modifier.width(1.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (secondaryCancelLabel != null) {
            OutlinedButton(
              onClick = onCancel,
              enabled = !isBusy,
              modifier = Modifier.height(44.dp).testTag("role_cancel_${order.id}"),
              border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.5f)),
            ) {
              Text(secondaryCancelLabel, color = FoxRed, fontWeight = FontWeight.Bold)
            }
          }
          if (actionLabel != null) {
            Button(
              onClick = onAction,
              enabled = !isBusy,
              modifier = Modifier.height(44.dp).testTag(actionTag),
              colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
            ) {
              Text(actionLabel, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun RoleFcmStatusBanner(
  fcmRegistered: Boolean,
  fcmStatusText: String,
  roleHint: String,
  onSyncFcmToken: () -> Unit,
) {
  val context = LocalContext.current
  var hasPermission by remember { mutableStateOf(FoxNotificationHelper.hasNotificationPermission(context)) }
  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasPermission = granted
    onSyncFcmToken()
  }

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("role_fcm_status_banner"),
    shape = RoundedCornerShape(16.dp),
    color = if (fcmRegistered && hasPermission) Color(0xFFEFF6FF) else Color(0xFFFFF7ED),
    border = BorderStroke(
      1.dp,
      if (fcmRegistered && hasPermission) FoxBlue.copy(alpha = 0.35f) else FoxOrange.copy(alpha = 0.45f),
    ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = if (fcmRegistered && hasPermission) FoxBlue else FoxOrangeDeep,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (fcmRegistered && hasPermission) "FCM Push sẵn sàng" else "Thông báo đẩy FCM",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = if (fcmRegistered && hasPermission) FoxBlue else FoxOrangeDeep,
          )
        }
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          TextButton(
            onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.testTag("request_post_notifications_button"),
          ) {
            Text("Cấp quyền", color = FoxOrangeDeep, fontWeight = FontWeight.Bold)
          }
        } else {
          TextButton(
            onClick = onSyncFcmToken,
            modifier = Modifier.testTag("sync_fcm_token_button"),
          ) {
            Text("Đồng bộ FCM", color = FoxBlue, fontWeight = FontWeight.Bold)
          }
        }
      }
      Text(text = fcmStatusText, style = MaterialTheme.typography.labelSmall, color = FoxTextStrong)
      Text(text = roleHint, style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
    }
  }
}

@Composable
private fun EmptyWorkspaceCard(message: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = Color.White,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = FoxTextSecondary,
      modifier = Modifier.padding(16.dp),
    )
  }
}
