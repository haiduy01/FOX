package com.example.data.remote

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class OrderItemParam(
  val itemId: String,
  val quantity: Int,
)

data class GeoCoordsParam(
  val lat: Double,
  val lng: Double,
)

data class FunctionsRouteResponse(
  val distanceMeters: Int,
  val distanceKm: Int,
  val durationMinutes: Int,
  val encodedPolyline: String,
  val routeSource: String,
  val isDemoRoute: Boolean,
  val disclaimer: String,
)

data class FunctionsOrderPayResponse(
  val orderId: String,
  val orderCode: String,
  val merchantId: String,
  val merchantName: String,
  val totalVnd: Long,
  val paymentStatus: String,
  val financialStatus: String,
  val stockDeducted: Boolean,
  val walletBalanceAfterVnd: Long?,
  val idempotentReplay: Boolean,
)

data class FunctionsWalletTxResponse(
  val txId: String,
  val amountVnd: Long,
  val balanceAfterVnd: Long,
  val status: String,
  val idempotentReplay: Boolean,
)

data class FunctionsRefundResponse(
  val orderId: String,
  val refundedAmountVnd: Long,
  val customerBalanceAfterVnd: Long?,
  val paymentStatus: String,
  val refundStatus: String,
  val stockRestored: Boolean,
  val idempotentReplay: Boolean,
)

data class FunctionsSettlementResponse(
  val orderId: String,
  val grossTotalVnd: Long,
  val driverCommissionVnd: Long,
  val merchantCommissionVnd: Long,
  val platformRevenueVnd: Long,
  val idempotentReplay: Boolean,
)

/**
 * Trusted Firebase Cloud Functions v2 (asia-southeast1) client for FOX DEMO financial operations.
 * All VND totals, vouchers, balances, refunds, and commissions are computed and enforced server-side.
 * NEVER fabricates client-side financial success when Cloud Functions are not yet deployed.
 */
class FoxFunctionsClient(
  private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) {
  companion object {
    const val FUNCTIONS_REGION = "asia-southeast1"
  }

  suspend fun topUpDemoWallet(
    amountVnd: Long,
    idempotencyKey: String,
  ): Result<FunctionsWalletTxResponse> =
    runCallable(
      name = "topUpDemoWallet",
      payload = mapOf(
        "amountVnd" to amountVnd,
        "idempotencyKey" to idempotencyKey,
      ),
    ) { map ->
      FunctionsWalletTxResponse(
        txId = map["txId"] as? String ?: "",
        amountVnd = map.longField("amountVnd"),
        balanceAfterVnd = map.longField("balanceAfterVnd"),
        status = map["status"] as? String ?: "succeeded",
        idempotentReplay = map["idempotentReplay"] as? Boolean ?: false,
      )
    }

  suspend fun computeVerifiedRoute(
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    vehicleType: String? = null,
  ): Result<FunctionsRouteResponse> {
    val payload = buildMap<String, Any> {
      put("origin", mapOf("lat" to originLat, "lng" to originLng))
      put("destination", mapOf("lat" to destLat, "lng" to destLng))
      if (!vehicleType.isNullOrBlank()) put("vehicleType", vehicleType)
    }
    return runCallable("computeVerifiedRoute", payload) { map ->
      FunctionsRouteResponse(
        distanceMeters = (map["distanceMeters"] as? Number)?.toInt() ?: 0,
        distanceKm = (map["distanceKm"] as? Number)?.toInt() ?: 1,
        durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 2,
        encodedPolyline = map["encodedPolyline"] as? String ?: "",
        routeSource = map["routeSource"] as? String ?: "server_haversine_demo_estimate",
        isDemoRoute = map["isDemoRoute"] as? Boolean ?: true,
        disclaimer = map["disclaimer"] as? String ?: "",
      )
    }
  }

  suspend fun createVerifiedOrderAndPayDemo(
    idempotencyKey: String,
    serviceType: String,
    paymentMethod: String,
    title: String,
    primaryAddress: String,
    secondaryAddress: String,
    detailSummary: String,
    voucherCode: String?,
    merchantId: String? = null,
    vehicleType: String? = null,
    distanceKm: Int? = null,
    weightTier: String? = null,
    speed: String? = null,
    items: List<OrderItemParam>? = null,
    expectedTotalVnd: Long? = null,
    pickupCoords: GeoCoordsParam? = null,
    dropoffCoords: GeoCoordsParam? = null,
  ): Result<FunctionsOrderPayResponse> {
    val payload = buildMap<String, Any> {
      put("idempotencyKey", idempotencyKey)
      put("serviceType", serviceType)
      put("paymentMethod", paymentMethod)
      put("title", title)
      put("primaryAddress", primaryAddress)
      put("secondaryAddress", secondaryAddress)
      put("detailSummary", detailSummary)
      if (!voucherCode.isNullOrBlank()) put("voucherCode", voucherCode.trim())
      if (!merchantId.isNullOrBlank()) put("merchantId", merchantId.trim())
      if (!vehicleType.isNullOrBlank()) put("vehicleType", vehicleType)
      if (distanceKm != null) put("distanceKm", distanceKm)
      if (!weightTier.isNullOrBlank()) put("weightTier", weightTier)
      if (!speed.isNullOrBlank()) put("speed", speed)
      if (!items.isNullOrEmpty()) {
        put(
          "items",
          items.map { mapOf("itemId" to it.itemId, "quantity" to it.quantity) },
        )
      }
      if (expectedTotalVnd != null) put("expectedTotalVnd", expectedTotalVnd)
      if (pickupCoords != null) {
        put("pickupCoords", mapOf("lat" to pickupCoords.lat, "lng" to pickupCoords.lng))
      }
      if (dropoffCoords != null) {
        put("dropoffCoords", mapOf("lat" to dropoffCoords.lat, "lng" to dropoffCoords.lng))
      }
    }
    return runCallable("createVerifiedOrderAndPayDemo", payload) { map ->
      val quoteMap = map["quote"] as? Map<*, *>
      FunctionsOrderPayResponse(
        orderId = map["orderId"] as? String ?: "",
        orderCode = map["orderCode"] as? String ?: "",
        merchantId = map["merchantId"] as? String ?: "",
        merchantName = map["merchantName"] as? String ?: "",
        totalVnd = (quoteMap?.get("totalVnd") as? Number)?.toLong() ?: 0L,
        paymentStatus = map["paymentStatus"] as? String ?: "pending_cash_demo",
        financialStatus = map["financialStatus"] as? String ?: "verified_demo",
        stockDeducted = map["stockDeducted"] as? Boolean ?: false,
        walletBalanceAfterVnd = (map["walletBalanceAfterVnd"] as? Number)?.toLong(),
        idempotentReplay = map["idempotentReplay"] as? Boolean ?: false,
      )
    }
  }

  suspend fun payOrderWithDemoWallet(
    orderId: String,
    idempotencyKey: String,
  ): Result<FunctionsWalletTxResponse> =
    runCallable(
      name = "payOrderWithDemoWallet",
      payload = mapOf(
        "orderId" to orderId,
        "idempotencyKey" to idempotencyKey,
      ),
    ) { map ->
      FunctionsWalletTxResponse(
        txId = map["txId"] as? String ?: "",
        amountVnd = map.longField("amountVnd"),
        balanceAfterVnd = map.longField("balanceAfterVnd"),
        status = map["status"] as? String ?: "succeeded",
        idempotentReplay = map["idempotentReplay"] as? Boolean ?: false,
      )
    }

  suspend fun refundOrderDemo(
    orderId: String,
    idempotencyKey: String,
    reason: String = "",
  ): Result<FunctionsRefundResponse> =
    runCallable(
      name = "refundOrderDemo",
      payload = mapOf(
        "orderId" to orderId,
        "idempotencyKey" to idempotencyKey,
        "reason" to reason,
      ),
    ) { map ->
      FunctionsRefundResponse(
        orderId = map["orderId"] as? String ?: orderId,
        refundedAmountVnd = map.longField("refundedAmountVnd"),
        customerBalanceAfterVnd = (map["customerBalanceAfterVnd"] as? Number)?.toLong(),
        paymentStatus = map["paymentStatus"] as? String ?: "refunded_demo",
        refundStatus = map["refundStatus"] as? String ?: "refunded_demo",
        stockRestored = map["stockRestored"] as? Boolean ?: false,
        idempotentReplay = map["idempotentReplay"] as? Boolean ?: false,
      )
    }

  suspend fun settleOrderCommissionDemo(
    orderId: String,
  ): Result<FunctionsSettlementResponse> =
    runCallable(
      name = "settleOrderCommissionDemo",
      payload = mapOf("orderId" to orderId),
    ) { map ->
      FunctionsSettlementResponse(
        orderId = map["orderId"] as? String ?: orderId,
        grossTotalVnd = map.longField("grossTotalVnd"),
        driverCommissionVnd = map.longField("driverCommissionVnd"),
        merchantCommissionVnd = map.longField("merchantCommissionVnd"),
        platformRevenueVnd = map.longField("platformRevenueVnd"),
        idempotentReplay = map["idempotentReplay"] as? Boolean ?: false,
      )
    }

  suspend fun getWalletSummary(): Result<Pair<CloudDemoWallet, List<CloudWalletTx>>> =
    runCallable("getWalletSummary", emptyMap()) { map ->
      val wallet = CloudDemoWallet(
        id = map["userId"] as? String ?: "",
        userId = map["userId"] as? String ?: "",
        balanceVnd = map.longField("balanceVnd"),
        totalTopUpVnd = map.longField("totalTopUpVnd"),
        totalSpentVnd = map.longField("totalSpentVnd"),
        totalEarnedVnd = map.longField("totalEarnedVnd"),
        totalRefundedVnd = map.longField("totalRefundedVnd"),
        currency = map["currency"] as? String ?: "VND",
        isDemo = map["isDemo"] as? Boolean ?: true,
        demoNotice = map["demoNotice"] as? String ?: DEMO_CURRENCY_NOTICE,
      )
      val rawList = map["transactions"] as? List<*> ?: emptyList<Any>()
      val txs = rawList.mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        CloudWalletTx(
          id = m["txId"] as? String ?: "",
          txId = m["txId"] as? String ?: "",
          userId = m["userId"] as? String ?: "",
          txType = m["txType"] as? String ?: "",
          title = m["title"] as? String ?: "",
          amountVnd = (m["amountVnd"] as? Number)?.toLong() ?: 0L,
          balanceAfterVnd = (m["balanceAfterVnd"] as? Number)?.toLong() ?: 0L,
          relatedOrderCode = m["relatedOrderCode"] as? String ?: "",
          status = m["status"] as? String ?: "succeeded",
          idempotencyKey = m["idempotencyKey"] as? String ?: "",
          isDemo = m["isDemo"] as? Boolean ?: true,
          createdAtMillis = (m["createdAtMillis"] as? Number)?.toLong() ?: 0L,
        )
      }
      wallet to txs
    }

  suspend fun notifyOrderEvent(
    orderId: String,
    eventType: String,
  ): Result<Int> =
    runCallable(
      name = "notifyOrderEvent",
      payload = mapOf(
        "orderId" to orderId,
        "eventType" to eventType,
      ),
    ) { map ->
      (map["notifiedCount"] as? Number)?.toInt() ?: 0
    }

  suspend fun notifyPartnerReview(
    targetUid: String,
    partnerType: String,
    eventType: String,
  ): Result<Int> =
    runCallable(
      name = "notifyPartnerReview",
      payload = mapOf(
        "targetUid" to targetUid,
        "partnerType" to partnerType,
        "eventType" to eventType,
      ),
    ) { map ->
      (map["notifiedCount"] as? Number)?.toInt() ?: 0
    }

  private suspend fun <T> runCallable(
    name: String,
    payload: Map<String, Any>,
    parser: (Map<*, *>) -> T,
  ): Result<T> {
    return try {
      val result = functions.getHttpsCallable(name).call(payload).await()
      val dataMap = result.getData() as? Map<*, *>
        ?: return Result.failure(IllegalStateException("Phản hồi từ Cloud Function $name không hợp lệ."))
      Result.success(parser(dataMap))
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFunctionsException) {
      Result.failure(IllegalStateException(mapFunctionsError(name, e), e))
    } catch (e: Exception) {
      Result.failure(IllegalStateException("Không thể kết nối Cloud Function $name: ${e.localizedMessage ?: "Lỗi mạng"}", e))
    }
  }

  private fun mapFunctionsError(name: String, e: FirebaseFunctionsException): String =
    when (e.code) {
      FirebaseFunctionsException.Code.NOT_FOUND,
      FirebaseFunctionsException.Code.UNIMPLEMENTED,
      FirebaseFunctionsException.Code.UNAVAILABLE,
      -> "Cloud Function \"$name\" chưa được triển khai lên dự án Firebase (cần bật gói Blaze và deploy functions). Dữ liệu ví không bị thay đổi."
      FirebaseFunctionsException.Code.UNAUTHENTICATED ->
        "Vui lòng đăng nhập Google để thực hiện giao dịch Ví DEMO."
      FirebaseFunctionsException.Code.PERMISSION_DENIED ->
        e.message ?: "Bạn không có quyền thực hiện thao tác tài chính này."
      FirebaseFunctionsException.Code.FAILED_PRECONDITION,
      FirebaseFunctionsException.Code.INVALID_ARGUMENT,
      FirebaseFunctionsException.Code.ALREADY_EXISTS,
      -> e.message ?: "Yêu cầu tài chính không hợp lệ."
      else -> e.message ?: "Lỗi Cloud Function ($name): ${e.code.name}"
    }

  private fun Map<*, *>.longField(key: String): Long =
    (this[key] as? Number)?.toLong() ?: 0L
}
