package com.example.notifications

import android.content.Intent
import java.security.MessageDigest

object FoxNotificationChannels {
  const val CUSTOMER_ORDERS = "fox_orders_customer"
  const val DRIVER_ORDERS = "fox_orders_driver"
  const val MERCHANT_ORDERS = "fox_orders_merchant"
  const val ADMIN_ALERTS = "fox_admin_alerts"
}

object FoxNotificationExtras {
  const val EXTRA_TARGET_ROUTE = "fox_target_route"
  const val EXTRA_ORDER_ID = "fox_order_id"
  const val EXTRA_TARGET_ROLE = "fox_target_role"
  const val EXTRA_EVENT_TYPE = "fox_event_type"
}

/**
 * Pure, deterministic routing & channel resolution for FOX role-based FCM notifications.
 */
object FoxNotificationRouter {
  const val ROUTE_ORDERS = "orders"
  const val ROUTE_DRIVER_DASHBOARD = "driver_dashboard"
  const val ROUTE_MERCHANT_DASHBOARD = "merchant_dashboard"
  const val ROUTE_ADMIN_DASHBOARD = "admin_dashboard"
  const val ROUTE_NOTIFICATIONS = "notifications"

  fun channelForRole(targetRole: String?): String =
    when (targetRole?.trim()?.lowercase()) {
      "driver" -> FoxNotificationChannels.DRIVER_ORDERS
      "merchant" -> FoxNotificationChannels.MERCHANT_ORDERS
      "admin" -> FoxNotificationChannels.ADMIN_ALERTS
      else -> FoxNotificationChannels.CUSTOMER_ORDERS
    }

  fun routeForNotification(
    targetRole: String?,
    eventType: String? = null,
    serviceType: String? = null,
  ): String {
    val normalizedRole = targetRole?.trim()?.lowercase().orEmpty()
    val normalizedEvent = eventType?.trim()?.lowercase().orEmpty()
    return when {
      normalizedRole == "admin" || normalizedEvent == "partner_application_submitted" -> ROUTE_ADMIN_DASHBOARD
      normalizedRole == "driver" -> ROUTE_DRIVER_DASHBOARD
      normalizedRole == "merchant" -> ROUTE_MERCHANT_DASHBOARD
      normalizedRole == "customer" -> ROUTE_ORDERS
      normalizedEvent in listOf("partner_approved", "partner_rejected") -> ROUTE_NOTIFICATIONS
      !serviceType.isNullOrBlank() -> ROUTE_ORDERS
      else -> ROUTE_NOTIFICATIONS
    }
  }

  fun roleBadgeLabel(targetRole: String?): String =
    when (targetRole?.trim()?.lowercase()) {
      "driver" -> "Tài xế"
      "merchant" -> "Gian hàng"
      "admin" -> "Quản trị"
      "customer" -> "Khách hàng"
      else -> "Hệ thống"
    }

  /**
   * Resolves a deep-link destination from either our explicit PendingIntent extra
   * (foreground notification tap) OR raw FCM data keys delivered by Android when the
   * user taps a notification received while the app was in the background.
   */
  fun extractRouteFromExtras(
    explicitRoute: String?,
    targetRole: String?,
    eventType: String?,
    serviceType: String?,
  ): String? {
    val direct = explicitRoute?.trim()
    if (!direct.isNullOrEmpty()) return direct
    val hasFcmData =
      !targetRole.isNullOrBlank() || !eventType.isNullOrBlank() || !serviceType.isNullOrBlank()
    if (!hasFcmData) return null
    return routeForNotification(targetRole, eventType, serviceType)
  }

  fun extractRouteFromIntent(intent: Intent?): String? {
    if (intent == null) return null
    return extractRouteFromExtras(
      explicitRoute = intent.getStringExtra(FoxNotificationExtras.EXTRA_TARGET_ROUTE),
      targetRole = intent.getStringExtra(FoxNotificationExtras.EXTRA_TARGET_ROLE)
        ?: intent.getStringExtra("targetRole"),
      eventType = intent.getStringExtra(FoxNotificationExtras.EXTRA_EVENT_TYPE)
        ?: intent.getStringExtra("eventType"),
      serviceType = intent.getStringExtra("serviceType"),
    )
  }
}

/**
 * Deterministic Firestore document ID generator for FCM registration tokens.
 * Matches firestore.rules ID constraints (^[a-zA-Z0-9_-]{1,128}$).
 */
object FoxFcmTokenHasher {
  private val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")

  fun tokenDocId(rawToken: String): String {
    val clean = rawToken.trim()
    require(clean.isNotEmpty()) { "FCM token must not be blank." }
    val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
    return "tok_$hex"
  }

  fun isValidFirestoreTokenDocId(docId: String): Boolean = VALID_DOC_ID_REGEX.matches(docId)
}
