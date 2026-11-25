package com.example.notifications

import android.content.Context
import com.example.data.remote.FoxCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class FcmSyncResult(
  val registered: Boolean,
  val tokenDocId: String = "",
  val statusMessage: String,
)

/**
 * Coordinates FCM registration token sync and clean removal on sign-out.
 * Never crashes when Google Play Services / FCM is unavailable on an emulator or offline device.
 */
object FoxFcmTokenManager {
  suspend fun syncCurrentToken(
    context: Context,
    cloudRepo: FoxCloudRepository,
    userId: String,
    role: String,
  ): FcmSyncResult {
    if (userId.isBlank()) {
      return FcmSyncResult(
        registered = false,
        statusMessage = "Chưa đăng nhập — chưa đồng bộ FCM token.",
      )
    }
    return try {
      val token = Firebase.messaging.token.await()
      if (token.isNullOrBlank()) {
        return FcmSyncResult(
          registered = false,
          statusMessage = "Thiết bị chưa cấp FCM token (chưa sẵn sàng dịch vụ đẩy).",
        )
      }
      val hasPerm = FoxNotificationHelper.hasNotificationPermission(context)
      val saveRes = cloudRepo.upsertFcmToken(
        userId = userId,
        rawToken = token,
        role = role,
        enabled = hasPerm,
      )
      saveRes.fold(
        onSuccess = { docId ->
          val msg = if (hasPerm) {
            "FCM đã kết nối (vai trò: $role) — sẵn sàng nhận thông báo đẩy."
          } else {
            "Đã lưu FCM token nhưng chưa cấp quyền thông báo hệ thống (POST_NOTIFICATIONS)."
          }
          FcmSyncResult(registered = true, tokenDocId = docId, statusMessage = msg)
        },
        onFailure = { err ->
          FcmSyncResult(
            registered = false,
            statusMessage = "Không thể lưu FCM token lên Firestore: ${err.localizedMessage ?: "Lỗi quyền/mạng"}",
          )
        },
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      FcmSyncResult(
        registered = false,
        statusMessage = "FCM tạm chưa khả dụng trên thiết bị này (${e.localizedMessage ?: "Play Services/mạng"}).",
      )
    }
  }

  suspend fun removeCurrentTokenOnSignOut(
    cloudRepo: FoxCloudRepository,
    userId: String,
  ) {
    if (userId.isBlank()) return
    try {
      val token = Firebase.messaging.token.await()
      if (!token.isNullOrBlank()) {
        val docId = FoxFcmTokenHasher.tokenDocId(token)
        cloudRepo.deleteFcmToken(userId, docId)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Best-effort cleanup; never block sign-out
    }
  }
}
