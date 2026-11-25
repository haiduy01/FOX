package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FoxDatabase
import com.example.data.local.FoxNotificationEntity
import com.example.data.local.FoxOrderEntity
import com.example.data.local.FoxWalletTxEntity
import com.example.ui.FoxServiceType
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Shared pricing engine (exact Long VND, zero Double arithmetic).
 * In production mode, authoritative pricing will be computed by the shared FOX backend API.
 */
object FoxDemoPricingEngine {
  fun estimateRide(vehicleType: String, distanceKm: Int, voucherCode: String?): PriceQuote {
    val (baseFare, perKm, minFare) =
      when (vehicleType) {
        "FOX Bike" -> Triple(12_000L, 4_500L, 15_000L)
        "FOX Car 4" -> Triple(24_000L, 11_000L, 30_000L)
        "FOX Car 7" -> Triple(30_000L, 14_000L, 40_000L)
        else -> Triple(15_000L, 6_000L, 20_000L)
      }
    val rawFare = (baseFare + perKm * distanceKm.coerceAtLeast(1)).coerceAtLeast(minFare)
    val discount =
      if (voucherCode == "FOXRIDE25") {
        ((rawFare * 25L) / 100L).coerceAtMost(30_000L)
      } else {
        0L
      }
    return PriceQuote(
      subtotalVnd = rawFare,
      feeVnd = 0L,
      discountVnd = discount,
      totalVnd = (rawFare - discount).coerceAtLeast(0L),
    )
  }

  fun estimateDelivery(
    weightTier: String,
    speed: String,
    voucherCode: String?,
    distanceKm: Int? = null,
  ): PriceQuote {
    val baseFee = if (speed == "Nhanh") 32_000L else 20_000L
    val weightSurcharge =
      when (weightTier) {
        "2–5 kg" -> 8_000L
        "5–10 kg" -> 18_000L
        else -> 0L
      }
    val distanceSurcharge =
      if (distanceKm != null && distanceKm > 3) {
        (distanceKm - 3).toLong() * 4_000L
      } else {
        0L
      }
    val rawFee = baseFee + weightSurcharge + distanceSurcharge
    val discount =
      if (voucherCode == "FOXSHIP15") {
        10_000L.coerceAtMost(rawFee)
      } else {
        0L
      }
    return PriceQuote(
      subtotalVnd = rawFee,
      feeVnd = 0L,
      discountVnd = discount,
      totalVnd = (rawFee - discount).coerceAtLeast(0L),
    )
  }

  fun estimateFood(subtotalVnd: Long, voucherCode: String?): PriceQuote {
    val deliveryFee = if (subtotalVnd > 0L) 15_000L else 0L
    val discount =
      if (voucherCode == "FOXFOOD30" && subtotalVnd >= 80_000L) {
        ((subtotalVnd * 30L) / 100L).coerceAtMost(30_000L)
      } else {
        0L
      }
    return PriceQuote(
      subtotalVnd = subtotalVnd,
      feeVnd = deliveryFee,
      discountVnd = discount,
      totalVnd = (subtotalVnd + deliveryFee - discount).coerceAtLeast(0L),
    )
  }

  fun estimateShopping(subtotalVnd: Long, voucherCode: String?): PriceQuote {
    val shippingFee = if (subtotalVnd >= 300_000L || subtotalVnd == 0L) 0L else 22_000L
    val discount =
      if (voucherCode == "FOXSHOP20" && subtotalVnd >= 150_000L) {
        20_000L
      } else {
        0L
      }
    return PriceQuote(
      subtotalVnd = subtotalVnd,
      feeVnd = shippingFee,
      discountVnd = discount,
      totalVnd = (subtotalVnd + shippingFee - discount).coerceAtLeast(0L),
    )
  }
}

data class PriceQuote(
  val subtotalVnd: Long,
  val feeVnd: Long,
  val discountVnd: Long,
  val totalVnd: Long,
)

data class OrderCreationResult(
  val success: Boolean,
  val orderCode: String? = null,
  val message: String,
)

/**
 * Backend configuration boundary keeping local DEMO storage strictly separated from future
 * shared Firebase / FastAPI production backend used across Customer, Driver, Merchant, and Admin.
 */
data class FoxBackendStatus(
  val isProductionBackendConnected: Boolean = false,
  val activeStorageLabel: String = "Room SQLite cục bộ (Chế độ DEMO)",
  val plannedSharedBackend: String = "Firebase Auth + Cloud Firestore / FOX API Adapter",
)

/**
 * Unified Repository contract for FOX Android.
 * Allows plugging in Firestore / FastAPI remote implementation in the next phase without changing UI.
 */
interface FoxRepository {
  val backendStatus: FoxBackendStatus
  fun observeOrders(): Flow<List<FoxOrderEntity>>
  fun observeNotifications(): Flow<List<FoxNotificationEntity>>
  fun observeWalletBalanceVnd(): Flow<Long>
  fun observeWalletTransactions(): Flow<List<FoxWalletTxEntity>>

  suspend fun ensureDemoSeeded()
  suspend fun createRideOrder(
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult

  suspend fun createDeliveryOrder(
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
  ): OrderCreationResult

  suspend fun createFoodOrder(
    address: String,
    itemLines: List<Pair<String, Int>>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult

  suspend fun createShoppingOrder(
    address: String,
    itemLines: List<Pair<String, Int>>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult

  suspend fun cancelPendingDemoOrder(orderCode: String): OrderCreationResult
  suspend fun markNotificationRead(id: String)
  suspend fun markAllNotificationsRead()
}

class RoomDemoFoxRepository(
  private val db: FoxDatabase,
) : FoxRepository {

  override val backendStatus: FoxBackendStatus = FoxBackendStatus()

  override fun observeOrders(): Flow<List<FoxOrderEntity>> = db.orderDao().observeAllOrders()

  override fun observeNotifications(): Flow<List<FoxNotificationEntity>> =
    db.notificationDao().observeNotifications()

  override fun observeWalletBalanceVnd(): Flow<Long> = db.walletDao().observeBalanceVnd()

  override fun observeWalletTransactions(): Flow<List<FoxWalletTxEntity>> =
    db.walletDao().observeTransactions()

  override suspend fun ensureDemoSeeded() {
    db.withTransaction {
      if (db.walletDao().countTransactions() == 0) {
        db.walletDao().insertTransaction(
          FoxWalletTxEntity(
            txId = "TX-SEED-001",
            title = "Khởi tạo số dư Ví FOX DEMO",
            amountVnd = 500_000L,
            txType = "SEED_DEMO",
            idempotencyKey = "seed_wallet_v1",
          )
        )
      }
      if (db.notificationDao().countNotifications() == 0) {
        db.notificationDao().insertNotification(
          FoxNotificationEntity(
            id = "notif_welcome",
            title = "Chào mừng đến với FOX – Chạm là tới!",
            body = "Đơn đặt xe, giao hàng, đồ ăn và mua sắm DEMO sẽ được lưu bền vững vào Room SQLite trên thiết bị.",
            isRead = false,
          )
        )
        db.notificationDao().insertNotification(
          FoxNotificationEntity(
            id = "notif_promo",
            title = "Ưu đãi DEMO sẵn sàng áp dụng",
            body = "Thử mã FOXRIDE25, FOXSHIP15, FOXFOOD30 hoặc FOXSHOP20 khi tạo đơn DEMO.",
            isRead = false,
          )
        )
      }
    }
  }

  override suspend fun createRideOrder(
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult {
    val quote = FoxDemoPricingEngine.estimateRide(vehicleType, distanceKm, voucherCode)
    val orderCode = generateCode("RIDE")
    val detail = buildString {
      append("$vehicleType • ~$distanceKm km (DEMO)")
      if (note.isNotBlank()) append(" • Ghi chú: $note")
    }
    return persistOrderAtomically(
      order = FoxOrderEntity(
        orderCode = orderCode,
        serviceType = FoxServiceType.RIDE.name,
        title = "$vehicleType → $destination",
        primaryAddress = pickup,
        secondaryAddress = destination,
        detailSummary = detail,
        subtotalVnd = quote.subtotalVnd,
        feeVnd = quote.feeVnd,
        discountVnd = quote.discountVnd,
        totalVnd = quote.totalVnd,
        paymentMethod = paymentMethod,
        voucherCode = voucherCode,
        status = "pending_demo",
        idempotencyKey = UUID.randomUUID().toString(),
      ),
      notifTitle = "Đã tạo chuyến $orderCode (DEMO)",
      notifBody = "Đón tại $pickup đến $destination. Đang chờ kết nối điều phối tài xế.",
    )
  }

  override suspend fun createDeliveryOrder(
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
  ): OrderCreationResult {
    val quote = FoxDemoPricingEngine.estimateDelivery(weightTier, speed, voucherCode)
    val orderCode = generateCode("DEL")
    val detail = buildString {
      append("$itemType • $weightTier • $speed")
      append(" | Gửi: $senderName ($senderPhone) → Nhận: $receiverName ($receiverPhone)")
      if (note.isNotBlank()) append(" | $note")
    }
    return persistOrderAtomically(
      order = FoxOrderEntity(
        orderCode = orderCode,
        serviceType = FoxServiceType.DELIVERY.name,
        title = "Vận đơn $itemType ($speed)",
        primaryAddress = pickupAddress,
        secondaryAddress = dropoffAddress,
        detailSummary = detail,
        subtotalVnd = quote.subtotalVnd,
        feeVnd = quote.feeVnd,
        discountVnd = quote.discountVnd,
        totalVnd = quote.totalVnd,
        paymentMethod = paymentMethod,
        voucherCode = voucherCode,
        status = "pending_demo",
        idempotencyKey = UUID.randomUUID().toString(),
      ),
      notifTitle = "Đã tạo vận đơn $orderCode (DEMO)",
      notifBody = "Giao $itemType từ $pickupAddress đến $dropoffAddress.",
    )
  }

  override suspend fun createFoodOrder(
    address: String,
    itemLines: List<Pair<String, Int>>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult {
    val quote = FoxDemoPricingEngine.estimateFood(subtotalVnd, voucherCode)
    val orderCode = generateCode("FOOD")
    val totalCount = itemLines.sumOf { it.second }
    val summary = itemLines.joinToString(", ") { "${it.second}x ${it.first}" }
    return persistOrderAtomically(
      order = FoxOrderEntity(
        orderCode = orderCode,
        serviceType = FoxServiceType.FOOD.name,
        title = "Đơn đồ ăn ($totalCount món)",
        primaryAddress = "Nhà hàng đối tác FOX (DEMO)",
        secondaryAddress = address,
        detailSummary = summary,
        subtotalVnd = quote.subtotalVnd,
        feeVnd = quote.feeVnd,
        discountVnd = quote.discountVnd,
        totalVnd = quote.totalVnd,
        paymentMethod = paymentMethod,
        voucherCode = voucherCode,
        status = "pending_demo",
        idempotencyKey = UUID.randomUUID().toString(),
      ),
      notifTitle = "Đã đặt đơn đồ ăn $orderCode (DEMO)",
      notifBody = "$summary • Giao đến: $address.",
    )
  }

  override suspend fun createShoppingOrder(
    address: String,
    itemLines: List<Pair<String, Int>>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
  ): OrderCreationResult {
    val quote = FoxDemoPricingEngine.estimateShopping(subtotalVnd, voucherCode)
    val orderCode = generateCode("SHOP")
    val totalCount = itemLines.sumOf { it.second }
    val summary = itemLines.joinToString(", ") { "${it.second}x ${it.first}" }
    return persistOrderAtomically(
      order = FoxOrderEntity(
        orderCode = orderCode,
        serviceType = FoxServiceType.SHOPPING.name,
        title = "Đơn mua sắm ($totalCount sản phẩm)",
        primaryAddress = "Gian hàng FOX Mall (DEMO)",
        secondaryAddress = address,
        detailSummary = summary,
        subtotalVnd = quote.subtotalVnd,
        feeVnd = quote.feeVnd,
        discountVnd = quote.discountVnd,
        totalVnd = quote.totalVnd,
        paymentMethod = paymentMethod,
        voucherCode = voucherCode,
        status = "pending_demo",
        idempotencyKey = UUID.randomUUID().toString(),
      ),
      notifTitle = "Đã tạo đơn mua sắm $orderCode (DEMO)",
      notifBody = "$summary • Giao đến: $address.",
    )
  }

  override suspend fun cancelPendingDemoOrder(orderCode: String): OrderCreationResult {
    return db.withTransaction {
      val order = db.orderDao().findByCode(orderCode)
        ?: return@withTransaction OrderCreationResult(false, null, "Không tìm thấy đơn $orderCode.")
      if (order.status != "pending_demo") {
        return@withTransaction OrderCreationResult(false, orderCode, "Chỉ có thể hủy đơn đang chờ xử lý.")
      }
      db.orderDao().updateOrderStatus(orderCode, "cancelled_demo")
      if (order.paymentMethod == "Ví FOX DEMO" && order.totalVnd > 0L) {
        db.walletDao().insertTransaction(
          FoxWalletTxEntity(
            txId = "TX-REF-${System.currentTimeMillis() % 100000}",
            title = "Hoàn tiền DEMO đơn $orderCode",
            amountVnd = order.totalVnd,
            txType = "REFUND_DEMO",
            relatedOrderCode = orderCode,
            idempotencyKey = "refund_$orderCode",
          )
        )
      }
      db.notificationDao().insertNotification(
        FoxNotificationEntity(
          id = "notif_cancel_$orderCode",
          title = "Đã hủy đơn $orderCode (DEMO)",
          body = if (order.paymentMethod == "Ví FOX DEMO") {
            "Đơn đã hủy và hoàn lại số dư Ví FOX DEMO."
          } else {
            "Đơn đã được chuyển sang trạng thái đã hủy."
          },
          relatedOrderCode = orderCode,
        )
      )
      OrderCreationResult(true, orderCode, "Đã hủy đơn $orderCode thành công.")
    }
  }

  override suspend fun markNotificationRead(id: String) = db.notificationDao().markRead(id)

  override suspend fun markAllNotificationsRead() = db.notificationDao().markAllRead()

  private suspend fun persistOrderAtomically(
    order: FoxOrderEntity,
    notifTitle: String,
    notifBody: String,
  ): OrderCreationResult {
    return db.withTransaction {
      if (order.paymentMethod == "Ví FOX DEMO") {
        val balance = db.walletDao().getCurrentBalanceVnd()
        if (balance < order.totalVnd) {
          return@withTransaction OrderCreationResult(
            success = false,
            message = "Số dư Ví FOX DEMO không đủ. Hãy chọn Tiền mặt DEMO hoặc giảm giá trị đơn.",
          )
        }
        db.walletDao().insertTransaction(
          FoxWalletTxEntity(
            txId = "TX-PAY-${System.currentTimeMillis() % 100000}",
            title = "Thanh toán DEMO ${order.orderCode}",
            amountVnd = -order.totalVnd,
            txType = "PAYMENT_DEMO",
            relatedOrderCode = order.orderCode,
            idempotencyKey = "pay_${order.orderCode}",
          )
        )
      }
      db.orderDao().insertOrder(order)
      db.notificationDao().insertNotification(
        FoxNotificationEntity(
          id = "notif_${order.orderCode}",
          title = notifTitle,
          body = notifBody,
          relatedOrderCode = order.orderCode,
        )
      )
      OrderCreationResult(
        success = true,
        orderCode = order.orderCode,
        message = "Đã lưu đơn ${order.orderCode} vào lịch sử (Room DEMO).",
      )
    }
  }

  private fun generateCode(prefix: String): String {
    val suffix = (1000..9999).random()
    return "FOX-$prefix-$suffix"
  }
}
