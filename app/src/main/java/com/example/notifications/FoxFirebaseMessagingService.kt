package com.example.notifications

import com.example.data.remote.FoxCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives Firebase Cloud Messaging (FCM) push notifications and token rotation callbacks.
 * Only writes rotated tokens to Firestore when a verified Google user is signed in.
 */
class FoxFirebaseMessagingService : FirebaseMessagingService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val user = Firebase.auth.currentUser ?: return
    if (token.isBlank()) return
    serviceScope.launch {
      try {
        val repo = FoxCloudRepository(applicationContext)
        val actualRole = repo.fetchUserRoleOrDefault(user.uid)
        repo.upsertFcmToken(
          userId = user.uid,
          rawToken = token,
          role = actualRole,
          enabled = FoxNotificationHelper.hasNotificationPermission(applicationContext),
        )
      } catch (_: Exception) {
        // Non-fatal: token will be synced again on next foreground app resume
      }
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val data = message.data
    val title = data["title"]?.ifBlank { null }
      ?: message.notification?.title
      ?: "Thông báo FOX"
    val body = data["body"]?.ifBlank { null }
      ?: message.notification?.body
      ?: "Bạn có cập nhật mới từ hệ thống FOX."
    val targetRole = data["targetRole"]
    val eventType = data["eventType"]
    val orderId = data["orderId"]
    val serviceType = data["serviceType"]

    FoxNotificationHelper.showRoleNotification(
      context = applicationContext,
      title = title,
      body = body,
      targetRole = targetRole,
      eventType = eventType,
      orderId = orderId,
      serviceType = serviceType,
    )
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
