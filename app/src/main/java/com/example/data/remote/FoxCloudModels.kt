package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val BOOTSTRAPPED_ADMIN_EMAIL = "hahaiduy28@gmail.com"
const val FINANCIAL_STATUS_BACKEND_REQUIRED = "unverified_backend_required"
const val DEMO_CURRENCY_NOTICE = "DEMO — KHÔNG PHẢI TIỀN THẬT"

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val phone: String = "",
  val defaultAddress: String = "",
  val role: String = "customer", // customer, driver, merchant, admin
  val requestedRole: String = "none", // none, driver, merchant
  val driverStatus: String = "none", // none, pending, approved, rejected
  val merchantStatus: String = "none", // none, pending, approved, rejected
  val accountStatus: String = "active", // active, suspended
  val vehicleType: String = "",
  val vehiclePlate: String = "",
  val storeName: String = "",
  val storeCategory: String = "",
  val driverOnline: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isVerifiedAdmin(authEmail: String?, authEmailVerified: Boolean): Boolean =
    role == "admin" ||
      (authEmail == BOOTSTRAPPED_ADMIN_EMAIL && authEmailVerified)

  fun isApprovedDriver(): Boolean =
    role == "driver" && driverStatus == "approved" && accountStatus == "active"

  fun isApprovedMerchant(): Boolean =
    role == "merchant" && merchantStatus == "approved" && accountStatus == "active"
}

@Keep
@IgnoreExtraProperties
data class CloudOrder(
  @DocumentId val id: String = "",
  val orderCode: String = "",
  val serviceType: String = "RIDE", // RIDE, DELIVERY, FOOD, SHOPPING
  val customerId: String = "",
  val customerName: String = "",
  val driverId: String = "",
  val driverName: String = "",
  val merchantId: String = "",
  val merchantName: String = "",
  val title: String = "",
  val primaryAddress: String = "",
  val secondaryAddress: String = "",
  val detailSummary: String = "",
  val subtotalVnd: Long = 0L,
  val feeVnd: Long = 0L,
  val discountVnd: Long = 0L,
  val totalVnd: Long = 0L,
  val paymentMethod: String = "Tiền mặt DEMO",
  val voucherCode: String = "",
  val financialStatus: String = FINANCIAL_STATUS_BACKEND_REQUIRED,
  val paymentStatus: String = "pending_cash_demo", // pending_cash_demo, paid_demo, refunded_demo, settled_cash_demo
  val paymentId: String = "",
  val refundStatus: String = "none", // none, refunded_demo, not_applicable
  val driverCommissionVnd: Long = 0L,
  val merchantCommissionVnd: Long = 0L,
  val platformRevenueVnd: Long = 0L,
  val commissionSettled: Boolean = false,
  val stockDeducted: Boolean = false,
  val stockRestored: Boolean = false,
  val status: String = "pending",
  val dispatchOpen: Boolean = true,
  val dispatchTarget: String = "driver", // driver, merchant, none
  val pickupLat: Double? = null,
  val pickupLng: Double? = null,
  val dropoffLat: Double? = null,
  val dropoffLng: Double? = null,
  val distanceMeters: Long = 0L,
  val distanceKm: Long = 0L,
  val durationMinutes: Long = 0L,
  val routeSource: String = "",
  val isDemoRoute: Boolean = false,
  val encodedPolyline: String = "",
  val customerRating: Int = 0,
  val customerReviewNote: String = "",
  val ratedAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun hasRouteCoordinates(): Boolean =
    pickupLat != null && pickupLng != null && dropoffLat != null && dropoffLng != null

  fun isActiveDriverTrackingEligible(): Boolean =
    driverId.isNotBlank() && status in listOf("driver_accepted", "driver_arrived", "in_progress")
}

@Keep
@IgnoreExtraProperties
data class CloudDriverLiveLocation(
  @DocumentId val id: String = "",
  val orderId: String = "",
  val driverId: String = "",
  val driverName: String = "",
  val lat: Double = 0.0,
  val lng: Double = 0.0,
  val bearing: Double = 0.0,
  val speedMps: Double = 0.0,
  val accuracyMeters: Double = 0.0,
  val isDemo: Boolean = false,
  val updatedAtMillis: Long = 0L,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun effectiveUpdatedAtMillis(): Long =
    updatedAt?.toDate()?.time ?: updatedAtMillis
}

@Keep
@IgnoreExtraProperties
data class CloudDemoWallet(
  @DocumentId val id: String = "",
  val userId: String = "",
  val balanceVnd: Long = 0L,
  val totalTopUpVnd: Long = 0L,
  val totalSpentVnd: Long = 0L,
  val totalEarnedVnd: Long = 0L,
  val totalRefundedVnd: Long = 0L,
  val currency: String = "VND",
  val isDemo: Boolean = true,
  val demoNotice: String = DEMO_CURRENCY_NOTICE,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudWalletTx(
  @DocumentId val id: String = "",
  val txId: String = "",
  val userId: String = "",
  val txType: String = "",
  val title: String = "",
  val amountVnd: Long = 0L,
  val balanceAfterVnd: Long = 0L,
  val relatedOrderCode: String = "",
  val status: String = "succeeded",
  val idempotencyKey: String = "",
  val isDemo: Boolean = true,
  val createdAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  fun effectiveTimeMillis(): Long =
    createdAt?.toDate()?.time ?: createdAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
}

@Keep
@IgnoreExtraProperties
data class CloudPlatformRevenueEntry(
  @DocumentId val id: String = "",
  val orderId: String = "",
  val orderCode: String = "",
  val serviceType: String = "",
  val customerId: String = "",
  val driverId: String = "",
  val merchantId: String = "",
  val grossTotalVnd: Long = 0L,
  val driverCommissionVnd: Long = 0L,
  val merchantCommissionVnd: Long = 0L,
  val platformRevenueVnd: Long = 0L,
  val currency: String = "VND",
  val isDemo: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudNotification(
  @DocumentId val id: String = "",
  val title: String = "",
  val body: String = "",
  val relatedOrderCode: String = "",
  val orderId: String = "",
  val serviceType: String = "",
  val targetRole: String = "",
  val eventType: String = "",
  val readFlag: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudMerchantStore(
  @DocumentId val id: String = "",
  val merchantId: String = "",
  val ownerUid: String = "",
  val storeType: String = "FOOD", // FOOD or SHOPPING
  val name: String = "",
  val description: String = "",
  val category: String = "",
  val address: String = "",
  val phone: String = "",
  val imageUrl: String = "",
  val openingHours: String = "07:00 - 22:00",
  val openNow: Boolean = true,
  val ratingTenths: Long = 48L,
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val effectiveMerchantId: String
    get() = merchantId.ifBlank { id }

  val formattedRating: String
    get() = "${ratingTenths / 10}.${ratingTenths % 10}"
}

@Keep
@IgnoreExtraProperties
data class CloudMerchantProduct(
  @DocumentId val id: String = "",
  val merchantId: String = "",
  val storeName: String = "",
  val serviceType: String = "FOOD",
  val name: String = "",
  val description: String = "",
  val imageUrl: String = "",
  val category: String = "",
  val priceVnd: Long = 0L,
  val stock: Long = 0L,
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class CloudMerchantWithProducts(
  val store: CloudMerchantStore,
  val products: List<CloudMerchantProduct>,
)

fun cloudOrderStatusLabel(status: String): String =
  when (status) {
    "pending" -> "Đang chờ nhận"
    "merchant_confirmed" -> "Gian hàng đã xác nhận"
    "preparing" -> "Gian hàng đang chuẩn bị"
    "ready_for_pickup" -> "Sẵn sàng giao tài xế"
    "driver_accepted" -> "Tài xế đã nhận đơn"
    "driver_arrived" -> "Tài xế đã đến điểm lấy/đón"
    "in_progress" -> "Đang thực hiện / giao hàng"
    "completed" -> "Hoàn thành"
    "cancelled" -> "Đã hủy"
    else -> status
  }

fun cloudRoleLabel(role: String): String =
  when (role) {
    "customer" -> "Khách hàng (customer)"
    "driver" -> "Tài xế (driver)"
    "merchant" -> "Gian hàng (merchant)"
    "admin" -> "Quản trị viên (admin)"
    else -> role
  }

fun cloudPaymentStatusLabel(paymentStatus: String, financialStatus: String): String =
  when {
    paymentStatus == "paid_demo" -> "Đã thanh toán Ví DEMO"
    paymentStatus == "refunded_demo" -> "Đã hoàn tiền DEMO"
    paymentStatus == "settled_cash_demo" -> "Đã đối soát Tiền mặt DEMO"
    financialStatus == FINANCIAL_STATUS_BACKEND_REQUIRED -> "Chờ xác thực thanh toán"
    else -> "Chờ thanh toán (Tiền mặt DEMO)"
  }

fun cloudWalletTxTypeLabel(txType: String): String =
  when (txType) {
    "TOPUP_DEMO" -> "Nạp Ví DEMO"
    "PAYMENT_DEMO" -> "Thanh toán đơn DEMO"
    "REFUND_DEMO" -> "Hoàn tiền DEMO"
    "DRIVER_COMMISSION_DEMO" -> "Hoa hồng Tài xế DEMO"
    "MERCHANT_COMMISSION_DEMO" -> "Doanh thu Gian hàng DEMO"
    else -> txType
  }

fun canCustomerCancelOrder(order: CloudOrder): Boolean =
  when (order.serviceType) {
    "RIDE", "DELIVERY" -> order.status in listOf("pending", "driver_accepted")
    "FOOD", "SHOPPING" -> order.status == "pending"
    else -> false
  }

fun canDriverCancelOrder(order: CloudOrder): Boolean =
  order.serviceType in listOf("RIDE", "DELIVERY") &&
    order.status in listOf("driver_accepted", "driver_arrived")

fun canMerchantCancelOrder(order: CloudOrder): Boolean =
  order.serviceType in listOf("FOOD", "SHOPPING") &&
    order.status in listOf("pending", "merchant_confirmed")

fun canCustomerRateOrder(order: CloudOrder): Boolean =
  order.status == "completed" && order.customerRating == 0

fun nextDriverStep(order: CloudOrder): Pair<String, String>? =
  when (order.status) {
    "driver_accepted" -> "driver_arrived" to when (order.serviceType) {
      "RIDE" -> "Đã đến điểm đón khách"
      else -> "Đã đến điểm lấy hàng"
    }
    "driver_arrived" -> "in_progress" to when (order.serviceType) {
      "RIDE" -> "Bắt đầu chuyến đi"
      else -> "Đã lấy hàng • Bắt đầu giao"
    }
    "in_progress" -> "completed" to "Hoàn thành đơn"
    else -> null
  }

fun orderLifecycleSteps(serviceType: String): List<Pair<String, String>> =
  if (serviceType in listOf("FOOD", "SHOPPING")) {
    listOf(
      "pending" to "Chờ quán",
      "merchant_confirmed" to "Quán nhận",
      "preparing" to "Chuẩn bị",
      "ready_for_pickup" to "Tìm tài xế",
      "driver_accepted" to "Tài xế nhận",
      "driver_arrived" to "Đến lấy",
      "in_progress" to "Đang giao",
      "completed" to "Hoàn thành",
    )
  } else {
    listOf(
      "pending" to "Tìm tài xế",
      "driver_accepted" to "Đã nhận",
      "driver_arrived" to "Đến điểm lấy",
      "in_progress" to "Đang thực hiện",
      "completed" to "Hoàn thành",
    )
  }
