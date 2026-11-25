package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.config.FoxEnvironmentConfig
import com.example.notifications.FoxFcmTokenHasher
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore repository for multi-device shared FOX operations.
 * ALWAYS connects to the configured named database (staging DB in STAGING builds, or R.string.firestore_database_id).
 * NEVER touches local Room DEMO records and NEVER mutates financial wallet balances client-side.
 */
class FoxCloudRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      FoxEnvironmentConfig.resolveFirestoreDatabaseId(context)
    )
  )

  private fun requireSignedInUser(): FirebaseUser =
    auth.currentUser
      ?: throw IllegalStateException("Vui lòng đăng nhập Google trước khi truy cập Cloud Firestore.")

  // --- USER PROFILE & RBAC REGISTRATION ---

  suspend fun ensureUserProfile(): Result<CloudUserProfile> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val uid = user.uid
    val path = "users/$uid"
    return try {
      val ref = db.collection("users").document(uid)
      val snap = ref.get().await()
      if (snap.exists()) {
        val existing =
          snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: CloudUserProfile(id = uid, userId = uid)
        Result.success(existing)
      } else {
        val isBootstrapAdmin =
          user.email == BOOTSTRAPPED_ADMIN_EMAIL && user.isEmailVerified
        val initialRole = if (isBootstrapAdmin) "admin" else "customer"
        val displayName =
          (user.displayName?.trim().takeIf { !it.isNullOrBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Thành viên FOX").take(80)

        val payload = mapOf(
          "userId" to uid,
          "displayName" to displayName,
          "email" to (user.email ?: "").take(120),
          "phone" to (user.phoneNumber ?: "").take(20),
          "defaultAddress" to "",
          "role" to initialRole,
          "requestedRole" to "none",
          "driverStatus" to "none",
          "merchantStatus" to "none",
          "accountStatus" to "active",
          "vehicleType" to "",
          "vehiclePlate" to "",
          "storeName" to "",
          "storeCategory" to "",
          "driverOnline" to false,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        ref.set(payload).await()
        val created =
          ref.get().await()
            .toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: CloudUserProfile(id = uid, userId = uid, displayName = displayName, role = initialRole)
        Result.success(created)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  fun observeUserProfile(userId: String): Flow<CloudUserProfile?> = flow {
    if (userId.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "users/$userId"
    emitAll(
      db.collection("users").document(userId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          handleFlowError(error, OperationType.GET, path, userId)
          throw error
        }
    )
  }

  suspend fun fetchUserRoleOrDefault(userId: String, defaultRole: String = "customer"): String {
    if (userId.isBlank()) return defaultRole
    return try {
      val snap = db.collection("users").document(userId).get().await()
      val role = snap.getString("role")?.trim()?.lowercase()
      if (role in listOf("customer", "driver", "merchant", "admin")) role!! else defaultRole
    } catch (_: Exception) {
      defaultRole
    }
  }

  suspend fun updatePersonalProfile(
    displayName: String,
    phone: String,
    defaultAddress: String,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "users/$uid"
    return try {
      val cleanName = displayName.trim()
      require(cleanName.length in 1..80) { "Tên hiển thị phải từ 1–80 ký tự." }
      db.collection("users").document(uid)
        .update(
          mapOf(
            "displayName" to cleanName,
            "phone" to phone.trim().take(20),
            "defaultAddress" to defaultAddress.trim().take(240),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun submitDriverApplication(vehicleType: String, vehiclePlate: String): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "users/$uid"
    return try {
      val cleanVehicle = vehicleType.trim()
      val cleanPlate = vehiclePlate.trim()
      require(cleanVehicle.length in 2..40) { "Vui lòng nhập loại xe (2–40 ký tự)." }
      require(cleanPlate.length in 4..20) { "Vui lòng nhập biển số hợp lệ (4–20 ký tự)." }
      db.collection("users").document(uid)
        .update(
          mapOf(
            "requestedRole" to "driver",
            "driverStatus" to "pending",
            "vehicleType" to cleanVehicle,
            "vehiclePlate" to cleanPlate,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun submitMerchantApplication(storeName: String, storeCategory: String): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "users/$uid"
    return try {
      val cleanStore = storeName.trim()
      val cleanCat = storeCategory.trim()
      require(cleanStore.length in 2..80) { "Vui lòng nhập tên gian hàng (2–80 ký tự)." }
      require(cleanCat.length in 2..40) { "Vui lòng chọn danh mục gian hàng." }
      db.collection("users").document(uid)
        .update(
          mapOf(
            "requestedRole" to "merchant",
            "merchantStatus" to "pending",
            "storeName" to cleanStore,
            "storeCategory" to cleanCat,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun toggleDriverOnline(online: Boolean): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "users/$uid"
    return try {
      db.collection("users").document(uid)
        .update(
          mapOf(
            "driverOnline" to online,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // --- ADMIN USER & ROLE MANAGEMENT ---

  fun observeAllUsersForAdmin(adminUid: String): Flow<List<CloudUserProfile>> = flow {
    if (adminUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("users")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "users", adminUid)
          throw error
        }
    )
  }

  suspend fun adminApproveDriver(targetUid: String, approve: Boolean): Result<Unit> {
    val path = "users/$targetUid"
    return try {
      requireSignedInUser()
      val updates =
        if (approve) {
          mapOf(
            "role" to "driver",
            "driverStatus" to "approved",
            "requestedRole" to "driver",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        } else {
          mapOf(
            "driverStatus" to "rejected",
            "driverOnline" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        }
      db.collection("users").document(targetUid).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun adminApproveMerchant(targetUid: String, approve: Boolean): Result<Unit> {
    val path = "users/$targetUid"
    return try {
      requireSignedInUser()
      val updates =
        if (approve) {
          mapOf(
            "role" to "merchant",
            "merchantStatus" to "approved",
            "requestedRole" to "merchant",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        } else {
          mapOf(
            "merchantStatus" to "rejected",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        }
      db.collection("users").document(targetUid).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun adminSetAccountStatus(targetUid: String, active: Boolean): Result<Unit> {
    val path = "users/$targetUid"
    return try {
      requireSignedInUser()
      db.collection("users").document(targetUid)
        .update(
          mapOf(
            "accountStatus" to if (active) "active" else "suspended",
            "driverOnline" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // --- SHARED MULTI-DEVICE ORDERS (ROLE-ISOLATED QUERIES) ---

  fun observeCustomerOrders(customerId: String): Flow<List<CloudOrder>> = flow {
    if (customerId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .whereEqualTo("customerId", customerId)
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders", customerId)
          throw error
        }
    )
  }

  /**
   * Approved Drivers ONLY: queries open orders targeted at 'driver' queue.
   * Rules reject this query for non-drivers or unapproved drivers.
   */
  fun observeOpenDriverDispatchOrders(driverUid: String): Flow<List<CloudOrder>> = flow {
    if (driverUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .whereEqualTo("dispatchOpen", true)
        .whereEqualTo("dispatchTarget", "driver")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders(driver_queue)", driverUid)
          throw error
        }
    )
  }

  /**
   * Approved Merchants ONLY: queries open FOOD/SHOPPING orders targeted at 'merchant' queue.
   */
  fun observeOpenMerchantDispatchOrders(merchantUid: String): Flow<List<CloudOrder>> = flow {
    if (merchantUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .whereEqualTo("dispatchOpen", true)
        .whereEqualTo("dispatchTarget", "merchant")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders(merchant_queue)", merchantUid)
          throw error
        }
    )
  }

  fun observeDriverAssignedOrders(driverId: String): Flow<List<CloudOrder>> = flow {
    if (driverId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .whereEqualTo("driverId", driverId)
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders(driver_assigned)", driverId)
          throw error
        }
    )
  }

  fun observeMerchantAssignedOrders(merchantId: String): Flow<List<CloudOrder>> = flow {
    if (merchantId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .whereEqualTo("merchantId", merchantId)
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders(merchant_assigned)", merchantId)
          throw error
        }
    )
  }

  fun observeAllOrdersForAdmin(adminUid: String): Flow<List<CloudOrder>> = flow {
    if (adminUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("orders")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "orders(admin)", adminUid)
          throw error
        }
    )
  }

  suspend fun createCloudOrder(
    serviceType: String,
    customerName: String,
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
  ): Result<String> {
    var path = "orders"
    return try {
      val user = requireSignedInUser()
      val uid = user.uid
      val orderCode = "FOX-$serviceType-${(1000..9999).random()}"
      val docRef = db.collection("orders").document(orderCode)
      path = docRef.path
      val dispatchTarget =
        if (serviceType in listOf("RIDE", "DELIVERY")) "driver" else "merchant"

      val payload = mapOf(
        "orderCode" to orderCode,
        "serviceType" to serviceType,
        "customerId" to uid,
        "customerName" to customerName.ifBlank { user.displayName ?: "Khách FOX" }.take(80),
        "driverId" to "",
        "driverName" to "",
        "merchantId" to "",
        "merchantName" to "",
        "title" to title.take(120),
        "primaryAddress" to primaryAddress.take(240),
        "secondaryAddress" to secondaryAddress.take(240),
        "detailSummary" to detailSummary.take(400),
        "subtotalVnd" to subtotalVnd.coerceAtLeast(0L),
        "feeVnd" to feeVnd.coerceAtLeast(0L),
        "discountVnd" to discountVnd.coerceAtLeast(0L),
        "totalVnd" to totalVnd.coerceAtLeast(0L),
        "paymentMethod" to paymentMethod,
        "voucherCode" to (voucherCode ?: "").take(32),
        "financialStatus" to FINANCIAL_STATUS_BACKEND_REQUIRED,
        "status" to "pending",
        "dispatchOpen" to true,
        "dispatchTarget" to dispatchTarget,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()

      val notifRef = db.collection("users").document(uid).collection("notifications").document("n_$orderCode")
      notifRef.set(
        mapOf(
          "title" to "Đã gửi đơn $orderCode lên Cloud",
          "body" to "$title • Ước tính: ${totalVnd}đ (Chờ backend xác thực thanh toán)",
          "relatedOrderCode" to orderCode,
          "readFlag" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      ).await()

      Result.success(orderCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  /**
   * Atomic Firestore Transaction preventing two drivers from accepting the same order concurrently.
   */
  suspend fun claimOrderAsDriver(orderId: String, driverName: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      val user = requireSignedInUser()
      val uid = user.uid
      val orderRef = db.collection("orders").document(orderId)

      db.runTransaction { tx ->
        val snap = tx.get(orderRef)
        if (!snap.exists()) throw IllegalStateException("Đơn hàng không tồn tại.")
        val currentDriverId = snap.getString("driverId") ?: ""
        val currentStatus = snap.getString("status") ?: ""
        val currentTarget = snap.getString("dispatchTarget") ?: ""
        val serviceType = snap.getString("serviceType") ?: ""
        if (currentDriverId.isNotEmpty()) {
          throw IllegalStateException("Đơn này đã có tài xế khác nhận trước đó.")
        }
        val validForDriver =
          currentTarget == "driver" &&
            ((serviceType in listOf("RIDE", "DELIVERY") && currentStatus == "pending") ||
              (serviceType in listOf("FOOD", "SHOPPING") && currentStatus == "ready_for_pickup"))
        if (!validForDriver) {
          throw IllegalStateException("Đơn chưa ở trạng thái sẵn sàng cho tài xế ($currentStatus).")
        }
        tx.update(
          orderRef,
          mapOf(
            "driverId" to uid,
            "driverName" to driverName.ifBlank { user.displayName ?: "Tài xế FOX" }.take(80),
            "status" to "driver_accepted",
            "dispatchOpen" to false,
            "dispatchTarget" to "none",
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun advanceDriverOrderStatus(orderId: String, nextStatus: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      requireSignedInUser()
      db.collection("orders").document(orderId)
        .update(
          mapOf(
            "status" to nextStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  /**
   * Atomic Firestore Transaction preventing two merchants from accepting the same FOOD/SHOPPING order.
   */
  suspend fun claimOrderAsMerchant(orderId: String, merchantName: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      val user = requireSignedInUser()
      val uid = user.uid
      val orderRef = db.collection("orders").document(orderId)
      db.runTransaction { tx ->
        val snap = tx.get(orderRef)
        if (!snap.exists()) throw IllegalStateException("Đơn hàng không tồn tại.")
        val currentMerchantId = snap.getString("merchantId") ?: ""
        val currentStatus = snap.getString("status") ?: ""
        val currentTarget = snap.getString("dispatchTarget") ?: ""
        if (currentMerchantId.isNotEmpty() || currentStatus != "pending" || currentTarget != "merchant") {
          throw IllegalStateException("Đơn đã được gian hàng khác tiếp nhận hoặc không còn mở.")
        }
        tx.update(
          orderRef,
          mapOf(
            "merchantId" to uid,
            "merchantName" to merchantName.ifBlank { "Gian hàng FOX" }.take(80),
            "status" to "merchant_confirmed",
            "dispatchOpen" to false,
            "dispatchTarget" to "none",
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun advanceMerchantOrderStatus(orderId: String, nextStatus: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      requireSignedInUser()
      val reopenForDriver = nextStatus == "ready_for_pickup"
      db.collection("orders").document(orderId)
        .update(
          mapOf(
            "status" to nextStatus,
            "dispatchOpen" to reopenForDriver,
            "dispatchTarget" to if (reopenForDriver) "driver" else "none",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  /**
   * Confirms a targeted Cloud catalog order already assigned to this merchant (pending -> merchant_confirmed).
   */
  suspend fun confirmAssignedMerchantOrder(orderId: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      requireSignedInUser()
      db.collection("orders").document(orderId)
        .update(
          mapOf(
            "status" to "merchant_confirmed",
            "dispatchOpen" to false,
            "dispatchTarget" to "none",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  /**
   * Cancels an order according to per-service rules enforced by firestore.rules
   * (Customer, assigned Driver, or assigned Merchant depending on service & status).
   */
  suspend fun cancelCloudOrder(orderId: String): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      requireSignedInUser()
      db.collection("orders").document(orderId)
        .update(
          mapOf(
            "status" to "cancelled",
            "dispatchOpen" to false,
            "dispatchTarget" to "none",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  /**
   * Allows the customer of a completed order to submit a single 1..5 star rating + optional note.
   * Enforced both client-side via Transaction and server-side via firestore.rules.
   */
  suspend fun rateCompletedOrder(
    orderId: String,
    rating: Int,
    reviewNote: String,
  ): Result<Unit> {
    val path = "orders/$orderId"
    return try {
      val user = requireSignedInUser()
      require(rating in 1..5) { "Số sao đánh giá phải từ 1 đến 5." }
      val cleanNote = reviewNote.trim().take(300)
      val orderRef = db.collection("orders").document(orderId)

      db.runTransaction { tx ->
        val snap = tx.get(orderRef)
        if (!snap.exists()) throw IllegalStateException("Không tìm thấy đơn hàng.")
        val ownerId = snap.getString("customerId") ?: ""
        val status = snap.getString("status") ?: ""
        val existingRating = (snap.getLong("customerRating") ?: 0L).toInt()
        if (ownerId != user.uid) {
          throw IllegalStateException("Chỉ khách hàng của đơn này mới có thể đánh giá.")
        }
        if (status != "completed") {
          throw IllegalStateException("Chỉ có thể đánh giá sau khi đơn hàng đã hoàn thành.")
        }
        if (existingRating > 0) {
          throw IllegalStateException("Đơn hàng này đã được đánh giá trước đó.")
        }
        tx.update(
          orderRef,
          mapOf(
            "customerRating" to rating,
            "customerReviewNote" to cleanNote,
            "ratedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // --- CLOUD MERCHANTS & CATALOG (LIVE FIRESTORE READS + ISOLATED PER-MERCHANT WRITES) ---

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeActiveCatalog(storeType: String, viewerUid: String): Flow<List<CloudMerchantWithProducts>> = flow {
    if (viewerUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("merchants")
        .whereEqualTo("active", true)
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudMerchantStore::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { it.storeType == storeType && it.active }
            .sortedWith(compareByDescending<CloudMerchantStore> { it.openNow }.thenBy { it.name })
        }
        .flatMapLatest { stores ->
          if (stores.isEmpty()) {
            flowOf(emptyList())
          } else {
            val perStoreFlows = stores.map { store ->
              val mId = store.effectiveMerchantId
              db.collection("merchants").document(mId).collection("products")
                .whereEqualTo("active", true)
                .snapshots()
                .map { prodSnap ->
                  val items = prodSnap.toObjects(
                    CloudMerchantProduct::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                  ).sortedBy { it.name }
                  CloudMerchantWithProducts(store = store, products = items)
                }
                .catch { error ->
                  handleFlowError(error, OperationType.LIST, "merchants/$mId/products", viewerUid)
                  emit(CloudMerchantWithProducts(store = store, products = emptyList()))
                }
            }
            combine(perStoreFlows) { arr -> arr.toList() }
          }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "merchants($storeType)", viewerUid)
          throw error
        }
    )
  }

  fun observeMerchantStoreProfile(merchantId: String): Flow<CloudMerchantStore?> = flow {
    if (merchantId.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "merchants/$merchantId"
    emitAll(
      db.collection("merchants").document(merchantId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(CloudMerchantStore::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          handleFlowError(error, OperationType.GET, path, merchantId)
          throw error
        }
    )
  }

  suspend fun saveMerchantStoreProfile(
    storeType: String,
    name: String,
    description: String,
    category: String,
    address: String,
    phone: String,
    imageUrl: String,
    openingHours: String,
    openNow: Boolean,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val docRef = db.collection("merchants").document(uid)
    val path = docRef.path
    return try {
      val cleanName = name.trim()
      val cleanAddress = address.trim()
      val cleanHours = openingHours.trim().ifBlank { "07:00 - 22:00" }
      val cleanType = if (storeType == "SHOPPING") "SHOPPING" else "FOOD"
      require(cleanName.length in 2..80) { "Tên gian hàng phải từ 2–80 ký tự." }
      require(cleanAddress.length in 1..240) { "Vui lòng nhập địa chỉ gian hàng." }

      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "storeType" to cleanType,
            "name" to cleanName,
            "description" to description.trim().take(300),
            "category" to category.trim().ifBlank { if (cleanType == "FOOD") "Đồ ăn" else "Mua sắm" }.take(40),
            "address" to cleanAddress.take(240),
            "phone" to phone.trim().take(20),
            "imageUrl" to imageUrl.trim().take(500),
            "openingHours" to cleanHours.take(60),
            "openNow" to openNow,
            "active" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "merchantId" to uid,
            "ownerUid" to uid,
            "storeType" to cleanType,
            "name" to cleanName,
            "description" to description.trim().take(300),
            "category" to category.trim().ifBlank { if (cleanType == "FOOD") "Đồ ăn" else "Mua sắm" }.take(40),
            "address" to cleanAddress.take(240),
            "phone" to phone.trim().take(20),
            "imageUrl" to imageUrl.trim().take(500),
            "openingHours" to cleanHours.take(60),
            "openNow" to openNow,
            "ratingTenths" to 48L,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun toggleMerchantStoreOpen(openNow: Boolean): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "merchants/$uid"
    return try {
      db.collection("merchants").document(uid)
        .update(
          mapOf(
            "openNow" to openNow,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  fun observeMerchantProducts(merchantId: String): Flow<List<CloudMerchantProduct>> = flow {
    if (merchantId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "merchants/$merchantId/products"
    emitAll(
      db.collection("merchants").document(merchantId).collection("products")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudMerchantProduct::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, path, merchantId)
          throw error
        }
    )
  }

  suspend fun createMerchantProduct(
    storeName: String,
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val docRef = db.collection("merchants").document(uid).collection("products").document()
    val path = docRef.path
    return try {
      require(name.trim().length in 1..100) { "Tên sản phẩm phải từ 1–100 ký tự." }
      require(priceVnd >= 1000L) { "Giá bán tối thiểu 1.000đ." }
      require(stock >= 0L) { "Tồn kho không hợp lệ." }
      val cleanType = if (serviceType == "SHOPPING") "SHOPPING" else "FOOD"
      docRef.set(
        mapOf(
          "merchantId" to uid,
          "storeName" to storeName.ifBlank { "Gian hàng FOX" }.take(80),
          "serviceType" to cleanType,
          "name" to name.trim().take(100),
          "description" to description.trim().take(300),
          "imageUrl" to imageUrl.trim().take(500),
          "category" to category.ifBlank { "Khác" }.take(40),
          "priceVnd" to priceVnd,
          "stock" to stock,
          "active" to true,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateMerchantProduct(
    productId: String,
    storeName: String,
    serviceType: String,
    name: String,
    description: String,
    imageUrl: String,
    category: String,
    priceVnd: Long,
    stock: Long,
    active: Boolean,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "merchants/$uid/products/$productId"
    return try {
      require(name.trim().length in 1..100) { "Tên sản phẩm phải từ 1–100 ký tự." }
      require(priceVnd >= 1000L) { "Giá bán tối thiểu 1.000đ." }
      require(stock >= 0L) { "Tồn kho không hợp lệ." }
      val cleanType = if (serviceType == "SHOPPING") "SHOPPING" else "FOOD"
      db.collection("merchants").document(uid).collection("products").document(productId)
        .update(
          mapOf(
            "storeName" to storeName.ifBlank { "Gian hàng FOX" }.take(80),
            "serviceType" to cleanType,
            "name" to name.trim().take(100),
            "description" to description.trim().take(300),
            "imageUrl" to imageUrl.trim().take(500),
            "category" to category.ifBlank { "Khác" }.take(40),
            "priceVnd" to priceVnd,
            "stock" to stock,
            "active" to active,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun setMerchantProductActive(productId: String, active: Boolean): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "merchants/$uid/products/$productId"
    return try {
      db.collection("merchants").document(uid).collection("products").document(productId)
        .update(
          mapOf(
            "active" to active,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // --- CLOUD NOTIFICATIONS ---

  fun observeCloudNotifications(userId: String): Flow<List<CloudNotification>> = flow {
    if (userId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "users/$userId/notifications"
    emitAll(
      db.collection("users").document(userId).collection("notifications")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  suspend fun markCloudNotificationRead(userId: String, notificationId: String): Result<Unit> {
    val path = "users/$userId/notifications/$notificationId"
    return try {
      requireSignedInUser()
      db.collection("users").document(userId).collection("notifications").document(notificationId)
        .update("readFlag", true)
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun markAllCloudNotificationsRead(userId: String, notificationIds: List<String>): Result<Unit> {
    if (userId.isBlank() || notificationIds.isEmpty()) return Result.success(Unit)
    val path = "users/$userId/notifications"
    return try {
      requireSignedInUser()
      val batch = db.batch()
      notificationIds.take(100).forEach { notifId ->
        val ref = db.collection("users").document(userId).collection("notifications").document(notifId)
        batch.update(ref, "readFlag", true)
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // --- FCM DEVICE TOKENS (STRICT OWNER-ONLY ACCESS IN FIRESTORE RULES) ---

  suspend fun upsertFcmToken(
    userId: String,
    rawToken: String,
    role: String,
    enabled: Boolean,
  ): Result<String> {
    val cleanToken = rawToken.trim()
    if (userId.isBlank() || cleanToken.length < 16) {
      return Result.failure(IllegalArgumentException("FCM token không hợp lệ."))
    }
    val docId = FoxFcmTokenHasher.tokenDocId(cleanToken)
    val path = "users/$userId/fcm_tokens/$docId"
    return try {
      requireSignedInUser()
      val safeRole = if (role in listOf("customer", "driver", "merchant", "admin")) role else "customer"
      val payload = mapOf(
        "token" to cleanToken.take(512),
        "platform" to "android",
        "role" to safeRole,
        "enabled" to enabled,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      db.collection("users").document(userId)
        .collection("fcm_tokens").document(docId)
        .set(payload)
        .await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteFcmToken(userId: String, tokenDocId: String): Result<Unit> {
    if (userId.isBlank() || tokenDocId.isBlank()) return Result.success(Unit)
    return try {
      db.collection("users").document(userId)
        .collection("fcm_tokens").document(tokenDocId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  // --- READ-ONLY DEMO WALLETS, LEDGER & PLATFORM REVENUE (WRITTEN EXCLUSIVELY BY CLOUD FUNCTIONS) ---

  fun observeUserWallet(userId: String): Flow<CloudDemoWallet?> = flow {
    if (userId.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "wallets/$userId"
    emitAll(
      db.collection("wallets").document(userId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(CloudDemoWallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          handleFlowError(error, OperationType.GET, path, userId)
          throw error
        }
    )
  }

  fun observeUserWalletTransactions(userId: String): Flow<List<CloudWalletTx>> = flow {
    if (userId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "wallets/$userId/transactions"
    emitAll(
      db.collection("wallets").document(userId).collection("transactions")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudWalletTx::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.effectiveTimeMillis() }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observePlatformRevenueForAdmin(adminUid: String): Flow<List<CloudPlatformRevenueEntry>> = flow {
    if (adminUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    emitAll(
      db.collection("platform_revenue")
        .snapshots()
        .map { snap ->
          snap.toObjects(CloudPlatformRevenueEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          handleFlowError(error, OperationType.LIST, "platform_revenue", adminUid)
          throw error
        }
    )
  }

  // --- LIVE DRIVER LOCATION (SINGLE OVERWRITTEN DOC PER ACTIVE ORDER — ZERO LOCATION HISTORY) ---

  suspend fun publishDriverLiveLocation(
    orderId: String,
    driverName: String,
    lat: Double,
    lng: Double,
    bearing: Double = 0.0,
    speedMps: Double = 0.0,
    accuracyMeters: Double = 0.0,
    isDemo: Boolean = false,
  ): Result<Unit> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
    val path = "orders/$orderId/live_location/driver"
    return try {
      require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0) {
        "Tọa độ vị trí không hợp lệ."
      }
      val payload = mapOf(
        "orderId" to orderId,
        "driverId" to user.uid,
        "driverName" to driverName.ifBlank { user.displayName ?: "Tài xế FOX" }.take(80),
        "lat" to lat,
        "lng" to lng,
        "bearing" to bearing.coerceIn(0.0, 360.0),
        "speedMps" to speedMps.coerceIn(0.0, 100.0),
        "accuracyMeters" to accuracyMeters.coerceIn(0.0, 5000.0),
        "isDemo" to isDemo,
        "updatedAtMillis" to System.currentTimeMillis(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      db.collection("orders").document(orderId)
        .collection("live_location").document("driver")
        .set(payload)
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun clearDriverLiveLocation(orderId: String): Result<Unit> {
    val path = "orders/$orderId/live_location/driver"
    return try {
      requireSignedInUser()
      db.collection("orders").document(orderId)
        .collection("live_location").document("driver")
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Deleting an already-absent or post-completion location doc should not break UI flow
      Result.failure(e)
    }
  }

  fun observeDriverLiveLocation(orderId: String, viewerUid: String): Flow<CloudDriverLiveLocation?> = flow {
    if (orderId.isBlank() || viewerUid.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "orders/$orderId/live_location/driver"
    emitAll(
      db.collection("orders").document(orderId)
        .collection("live_location").document("driver")
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(CloudDriverLiveLocation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          handleFlowError(error, OperationType.GET, path, viewerUid)
          emit(null)
        }
    )
  }

  private fun handleFlowError(
    error: Throwable,
    op: OperationType,
    path: String,
    expectedUid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(toLog, op, path)
    }
  }
}
