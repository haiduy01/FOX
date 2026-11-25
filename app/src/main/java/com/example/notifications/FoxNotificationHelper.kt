package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object FoxNotificationHelper {
  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun ensureChannelsCreated(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channels = listOf(
      NotificationChannel(
        FoxNotificationChannels.CUSTOMER_ORDERS,
        "Đơn hàng Khách hàng FOX",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Thông báo trạng thái đơn hàng, thanh toán DEMO và hoàn tiền cho Khách hàng."
      },
      NotificationChannel(
        FoxNotificationChannels.DRIVER_ORDERS,
        "Đơn hàng Tài xế FOX",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Thông báo đơn Đặt xe, Giao hàng và đơn sẵn sàng lấy cho Tài xế trực tuyến."
      },
      NotificationChannel(
        FoxNotificationChannels.MERCHANT_ORDERS,
        "Đơn hàng Gian hàng FOX",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Thông báo đơn Đặt đồ ăn và Mua sắm mới cho Gian hàng."
      },
      NotificationChannel(
        FoxNotificationChannels.ADMIN_ALERTS,
        "Cảnh báo Quản trị FOX",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "Thông báo duyệt hồ sơ đối tác và tổng hợp hoàn thành đơn cho Quản trị viên."
      },
    )
    channels.forEach { manager.createNotificationChannel(it) }
  }

  fun showRoleNotification(
    context: Context,
    title: String,
    body: String,
    targetRole: String?,
    eventType: String?,
    orderId: String?,
    serviceType: String?,
  ) {
    ensureChannelsCreated(context)
    if (!hasNotificationPermission(context)) return

    val channelId = FoxNotificationRouter.channelForRole(targetRole)
    val targetRoute = FoxNotificationRouter.routeForNotification(targetRole, eventType, serviceType)

    val launchIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(FoxNotificationExtras.EXTRA_TARGET_ROUTE, targetRoute)
      if (!orderId.isNullOrBlank()) putExtra(FoxNotificationExtras.EXTRA_ORDER_ID, orderId)
      if (!targetRole.isNullOrBlank()) putExtra(FoxNotificationExtras.EXTRA_TARGET_ROLE, targetRole)
      if (!eventType.isNullOrBlank()) putExtra(FoxNotificationExtras.EXTRA_EVENT_TYPE, eventType)
    }

    val notificationId = ((orderId ?: title).hashCode() and 0x7fffffff).coerceAtLeast(1001)
    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val builder = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title.ifBlank { "FOX – Chạm là tới!" })
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)

    try {
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    } catch (_: SecurityException) {
      // Permission was revoked concurrently; fail gracefully without crashing
    }
  }
}
