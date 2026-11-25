package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.config.FoxEnvironment
import com.example.config.FoxEnvironmentConfig
import com.example.data.location.FoxMapGeometry
import com.example.data.location.GeoPointData
import com.example.data.location.LocationDataSource
import com.example.data.remote.CloudNotification
import com.example.data.remote.cloudOrderStatusLabel
import com.example.data.remote.cloudRoleLabel
import com.example.notifications.FoxNotificationRouter
import com.example.ui.FoxCloudUiState
import com.example.ui.FoxServiceType
import com.example.ui.components.FoxInteractiveMap
import com.example.ui.components.FoxMascotIllustration
import com.example.ui.components.FoxServiceIllustration
import com.example.ui.formatOrderTime
import com.example.ui.formatVnd
import com.example.ui.mapServiceType
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

@Composable
fun CloudOrdersScreen(
  cloudState: FoxCloudUiState,
  onStartService: (FoxServiceType) -> Unit,
  onPayOrderWithDemoWallet: (String) -> Unit,
  onCancelCloudOrder: (String) -> Unit,
  onRateOrder: (orderId: String, rating: Int, reviewNote: String) -> Unit = { _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf<FoxServiceType?>(null) }
  var ratingTargetOrderId by remember { mutableStateOf<String?>(null) }
  var ratingTargetOrderCode by remember { mutableStateOf("") }
  var selectedStars by remember { mutableStateOf(5) }
  var reviewNoteText by remember { mutableStateOf("") }
  val activeFilter = selectedFilter
  val filtered =
    if (activeFilter == null) cloudState.customerOrders
    else cloudState.customerOrders.filter { it.serviceType == activeFilter.name }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.statusBars),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 640.dp),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 36.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          text = "Đơn hàng Cloud (${cloudState.customerOrders.size})",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxTextStrong,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Đồng bộ thời gian thực qua Cloud Firestore. Dữ liệu Room DEMO cục bộ được tách biệt hoàn toàn.",
          style = MaterialTheme.typography.bodySmall,
          color = FoxTextSecondary,
        )
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = selectedFilter == null,
              onClick = { selectedFilter = null },
              label = { Text("Tất cả") },
            )
          }
          items(FoxServiceType.entries) { service ->
            FilterChip(
              selected = selectedFilter == service,
              onClick = { selectedFilter = service },
              label = { Text(service.title) },
            )
          }
        }
      }

      if (filtered.isEmpty()) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              FoxServiceIllustration(serviceType = activeFilter ?: FoxServiceType.RIDE, size = 64.dp)
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Chưa có đơn Cloud nào",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Spacer(modifier = Modifier.height(6.dp))
              TextButton(onClick = { onStartService(activeFilter ?: FoxServiceType.RIDE) }) {
                Text("Tạo đơn Cloud ngay", color = FoxBlue, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
        items(filtered, key = { it.id }) { order ->
          val svc = mapServiceType(order.serviceType)
          val isCancelled = order.status == "cancelled"
          val isCompleted = order.status == "completed"
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.testTag("cloud_order_card_${order.id}"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(serviceCardTint(svc)),
                  contentAlignment = Alignment.Center,
                ) {
                  FoxServiceIllustration(serviceType = svc, size = 36.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(order.orderCode, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = FoxBlue)
                  Text(order.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                  order.createdAt?.let { ts ->
                    Text(formatOrderTime(ts.toDate().time), style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
                  }
                }
                Surface(
                  shape = RoundedCornerShape(50),
                  color = when {
                    isCancelled -> FoxRed.copy(alpha = 0.12f)
                    isCompleted -> FoxGreen.copy(alpha = 0.14f)
                    else -> FoxOrange.copy(alpha = 0.14f)
                  },
                ) {
                  Text(
                    text = cloudOrderStatusLabel(order.status),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                      isCancelled -> FoxRed
                      isCompleted -> FoxGreen
                      else -> FoxOrangeDeep
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outline)
              OrderStepProgressStrip(serviceType = order.serviceType, status = order.status)
              Text(order.detailSummary, style = MaterialTheme.typography.bodySmall, color = FoxTextStrong)
              Text("Từ: ${order.primaryAddress}\nĐến: ${order.secondaryAddress}", style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
              if (order.driverName.isNotBlank() || order.merchantName.isNotBlank()) {
                Text(
                  text = buildString {
                    if (order.merchantName.isNotBlank()) append("Gian hàng: ${order.merchantName}  ")
                    if (order.driverName.isNotBlank()) append("Tài xế: ${order.driverName}")
                  },
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxGreen,
                )
              }

              if (order.distanceKm > 0L) {
                Text(
                  text = "Lộ trình: ~${order.distanceKm} km • ${order.durationMinutes} phút (${if (order.isDemoRoute) "Ước tính DEMO" else "Google Routes v2"})",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = FoxBlue,
                )
              }

              if (order.isActiveDriverTrackingEligible()) {
                val liveLoc = cloudState.activeDriverLocations[order.id]
                val pickupPt = if (order.pickupLat != null && order.pickupLng != null) {
                  GeoPointData(
                    latitude = order.pickupLat,
                    longitude = order.pickupLng,
                    label = "Điểm đón / lấy hàng",
                    address = order.primaryAddress,
                    source = if (order.isDemoRoute) LocationDataSource.DEMO_PRESET else LocationDataSource.REAL_GEOCODER,
                  )
                } else null
                val dropoffPt = if (order.dropoffLat != null && order.dropoffLng != null) {
                  GeoPointData(
                    latitude = order.dropoffLat,
                    longitude = order.dropoffLng,
                    label = "Điểm đến / giao hàng",
                    address = order.secondaryAddress,
                    source = if (order.isDemoRoute) LocationDataSource.DEMO_PRESET else LocationDataSource.REAL_GEOCODER,
                  )
                } else null
                val decodedPoints = remember(order.encodedPolyline, pickupPt, dropoffPt) {
                  val dec = FoxMapGeometry.decodePolyline(order.encodedPolyline)
                  if (dec.size >= 2) dec else listOfNotNull(pickupPt, dropoffPt)
                }

                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = Color(0xFFF8FAFC),
                  border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
                  modifier = Modifier.fillMaxWidth().testTag("customer_live_tracking_${order.id}"),
                ) {
                  Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "Theo dõi tài xế trực tiếp",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = FoxTextStrong,
                      )
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = when {
                          liveLoc == null -> Color(0xFFE2E8F0)
                          liveLoc.isDemo -> FoxOrange.copy(alpha = 0.16f)
                          else -> FoxGreen.copy(alpha = 0.16f)
                        },
                      ) {
                        Text(
                          text = when {
                            liveLoc == null -> "Chờ tài xế bật GPS"
                            liveLoc.isDemo -> "Vị trí DEMO"
                            else -> "GPS thật trực tiếp"
                          },
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = when {
                            liveLoc == null -> FoxTextSecondary
                            liveLoc.isDemo -> FoxOrangeDeep
                            else -> FoxGreen
                          },
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                      }
                    }

                    FoxInteractiveMap(
                      mapConfigStatus = cloudState.mapConfigStatus,
                      preferDemoMode = !cloudState.mapConfigStatus.isRealMapsKeyConfigured,
                      pickup = pickupPt,
                      dropoff = dropoffPt,
                      routePoints = decodedPoints,
                      driverLocation = liveLoc,
                      height = 170.dp,
                    )

                    Text(
                      text = if (liveLoc != null) {
                        "Tài xế ${liveLoc.driverName}: (${String.format("%.4f", liveLoc.lat)}, ${String.format("%.4f", liveLoc.lng)}) • Cập nhật: ${formatOrderTime(liveLoc.effectiveUpdatedAtMillis())}"
                      } else {
                        "Tài xế chưa bật chia sẻ vị trí. Bản đồ sẽ tự cập nhật khi tài xế bật GPS cho đơn này."
                      },
                      style = MaterialTheme.typography.labelSmall,
                      color = FoxTextSecondary,
                    )
                  }
                }
              }

              val isPaidDemo = order.paymentStatus == "paid_demo"
              val isRefundedDemo = order.paymentStatus == "refunded_demo"
              val isVerifiedServer = order.financialStatus == "verified_demo" || order.financialStatus == "settled_demo"
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                  isPaidDemo -> FoxGreen.copy(alpha = 0.10f)
                  isRefundedDemo -> FoxBlue.copy(alpha = 0.10f)
                  else -> Color(0xFFFFF7ED)
                },
                border = BorderStroke(
                  1.dp,
                  when {
                    isPaidDemo -> FoxGreen.copy(alpha = 0.35f)
                    isRefundedDemo -> FoxBlue.copy(alpha = 0.35f)
                    else -> FoxOrange.copy(alpha = 0.35f)
                  },
                ),
              ) {
                Text(
                  text = "${com.example.data.remote.cloudPaymentStatusLabel(order.paymentStatus, order.financialStatus)} • ${order.paymentMethod} (${com.example.data.remote.DEMO_CURRENCY_NOTICE})",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = when {
                    isPaidDemo -> FoxGreen
                    isRefundedDemo -> FoxBlue
                    else -> FoxOrangeDeep
                  },
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = if (isVerifiedServer) "Tổng xác thực server (DEMO)" else "Ước tính (chờ backend)",
                    style = MaterialTheme.typography.labelSmall,
                    color = FoxTextSecondary,
                  )
                  Text(
                    text = formatVnd(order.totalVnd),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = FoxBlue,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (!isCancelled && !isCompleted && !isPaidDemo && !isRefundedDemo) {
                    Button(
                      onClick = { onPayOrderWithDemoWallet(order.id) },
                      enabled = !cloudState.isBusy,
                      modifier = Modifier.height(44.dp).testTag("pay_cloud_order_${order.id}"),
                      colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                    ) {
                      Text("Trả Ví DEMO", fontWeight = FontWeight.Bold)
                    }
                  }
                  if (com.example.data.remote.canCustomerCancelOrder(order)) {
                    OutlinedButton(
                      onClick = { onCancelCloudOrder(order.id) },
                      enabled = !cloudState.isBusy,
                      modifier = Modifier.height(44.dp).testTag("cancel_cloud_order_${order.id}"),
                      border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.5f)),
                    ) {
                      Text(if (isPaidDemo) "Hủy & Hoàn" else "Hủy đơn", color = FoxRed)
                    }
                  }
                  if (com.example.data.remote.canCustomerRateOrder(order)) {
                    Button(
                      onClick = {
                        ratingTargetOrderId = order.id
                        ratingTargetOrderCode = order.orderCode
                        selectedStars = 5
                        reviewNoteText = ""
                      },
                      enabled = !cloudState.isBusy,
                      modifier = Modifier.height(44.dp).testTag("rate_cloud_order_${order.id}"),
                      colors = ButtonDefaults.buttonColors(containerColor = FoxOrangeDeep),
                    ) {
                      Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Đánh giá", fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }

              if (order.customerRating in 1..5) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = FoxOrange.copy(alpha = 0.12f),
                  border = BorderStroke(1.dp, FoxOrange.copy(alpha = 0.35f)),
                  modifier = Modifier.fillMaxWidth().testTag("rated_badge_${order.id}"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Star,
                      contentDescription = null,
                      tint = FoxOrangeDeep,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = buildString {
                        append("Đánh giá của bạn: ${order.customerRating}/5 sao")
                        if (order.customerReviewNote.isNotBlank()) {
                          append(" • \"${order.customerReviewNote}\"")
                        }
                      },
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = FoxOrangeDeep,
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

  val targetOrderId = ratingTargetOrderId
  if (targetOrderId != null) {
    AlertDialog(
      onDismissRequest = { ratingTargetOrderId = null },
      title = {
        Text(
          text = "Đánh giá đơn $ratingTargetOrderCode",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = FoxTextStrong,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Trải nghiệm dịch vụ FOX của bạn thế nào?",
            style = MaterialTheme.typography.bodySmall,
            color = FoxTextSecondary,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            (1..5).forEach { star ->
              IconButton(
                onClick = { selectedStars = star },
                modifier = Modifier.size(48.dp).testTag("rating_star_$star"),
              ) {
                Icon(
                  imageVector = if (star <= selectedStars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                  contentDescription = "$star sao",
                  tint = if (star <= selectedStars) FoxOrange else FoxTextSecondary,
                  modifier = Modifier.size(32.dp),
                )
              }
            }
          }
          OutlinedTextField(
            value = reviewNoteText,
            onValueChange = { if (it.length <= 300) reviewNoteText = it },
            label = { Text("Nhận xét thêm (tùy chọn)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("rating_note_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onRateOrder(targetOrderId, selectedStars, reviewNoteText)
            ratingTargetOrderId = null
          },
          modifier = Modifier.testTag("submit_rating_button"),
          colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
        ) {
          Text("Gửi đánh giá", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { ratingTargetOrderId = null }) {
          Text("Để sau", color = FoxTextSecondary)
        }
      },
    )
  }
}

@Composable
fun CloudAccountScreen(
  cloudState: FoxCloudUiState,
  onSaveProfile: (displayName: String, phone: String, address: String) -> Unit,
  onApplyDriver: (vehicleType: String, vehiclePlate: String) -> Unit,
  onApplyMerchant: (storeName: String, storeCategory: String) -> Unit,
  onOpenDriverDashboard: () -> Unit,
  onOpenMerchantDashboard: () -> Unit,
  onOpenAdminDashboard: () -> Unit,
  onSwitchToOfflineDemo: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val profile = cloudState.profile
  var displayName by remember { mutableStateOf(profile?.displayName ?: "") }
  var phone by remember { mutableStateOf(profile?.phone ?: "") }
  var defaultAddress by remember { mutableStateOf(profile?.defaultAddress ?: "") }

  var vehicleType by remember { mutableStateOf(profile?.vehicleType?.ifBlank { "FOX Bike" } ?: "FOX Bike") }
  var vehiclePlate by remember { mutableStateOf(profile?.vehiclePlate ?: "") }
  var storeName by remember { mutableStateOf(profile?.storeName ?: "") }
  var storeCategory by remember { mutableStateOf(profile?.storeCategory?.ifBlank { "Đồ ăn & Đồ uống" } ?: "Đồ ăn & Đồ uống") }

  LaunchedEffect(profile?.userId, profile?.displayName, profile?.phone, profile?.defaultAddress) {
    if (profile != null) {
      displayName = profile.displayName
      phone = profile.phone
      defaultAddress = profile.defaultAddress
      if (profile.vehicleType.isNotBlank()) vehicleType = profile.vehicleType
      if (profile.vehiclePlate.isNotBlank()) vehiclePlate = profile.vehiclePlate
      if (profile.storeName.isNotBlank()) storeName = profile.storeName
      if (profile.storeCategory.isNotBlank()) storeCategory = profile.storeCategory
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.statusBars),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 640.dp),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 36.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Signed-In Identity Card
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(CircleShape)
                  .background(FoxBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
              ) {
                FoxMascotIllustration(contentDesc = stringResource(R.string.cd_fox_mascot), size = 50.dp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = profile?.displayName?.ifBlank { "Thành viên FOX" } ?: "Đang tải hồ sơ Cloud...",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = cloudState.authEmail.ifBlank { cloudState.currentUserId },
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(shape = RoundedCornerShape(50), color = FoxBlue.copy(alpha = 0.12f)) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = FoxBlue, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = cloudRoleLabel(profile?.role ?: "customer"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxBlue,
                  )
                }
              }
              if (cloudState.isVerifiedAdmin) {
                Surface(shape = RoundedCornerShape(50), color = FoxGreen.copy(alpha = 0.14f)) {
                  Text(
                    text = "Admin xác thực",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxGreen,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }

            val envInfo = remember { FoxEnvironmentConfig.current() }
            val envTint = when (envInfo.environment) {
              FoxEnvironment.STAGING -> FoxOrangeDeep
              FoxEnvironment.PRODUCTION -> FoxGreen
              FoxEnvironment.DEMO -> FoxBlue
            }
            val envSummary = remember(envInfo) {
              if (envInfo.environment == FoxEnvironment.STAGING) {
                val proj = FoxEnvironmentConfig.configuredStagingProjectId().ifBlank { "chưa gắn project" }
                "Môi trường: STAGING • DB: ${FoxEnvironmentConfig.configuredStagingDatabaseId()} • Project: $proj"
              } else {
                "Môi trường: ${envInfo.labelVi}"
              }
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = envTint.copy(alpha = 0.10f),
              modifier = Modifier.testTag("fox_environment_badge"),
            ) {
              Text(
                text = envSummary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = envTint,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      // Role Workspaces Launcher
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Không gian làm việc theo vai trò",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
            Text(
              text = "Quyền thực thi được Firestore Rules kiểm chứng theo hồ sơ thực tế, không thể tự nâng quyền từ thiết bị.",
              style = MaterialTheme.typography.bodySmall,
              color = FoxTextSecondary,
            )
            OutlinedButton(
              onClick = onOpenDriverDashboard,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_driver_dashboard"),
            ) {
              Icon(Icons.Rounded.DirectionsBike, contentDescription = null, tint = FoxBlue)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Bảng điều khiển Tài xế (${profile?.driverStatus ?: "none"})",
                color = FoxBlue,
                fontWeight = FontWeight.Bold,
              )
            }
            OutlinedButton(
              onClick = onOpenMerchantDashboard,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_merchant_dashboard"),
            ) {
              Icon(Icons.Rounded.Storefront, contentDescription = null, tint = FoxOrangeDeep)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Bảng điều khiển Gian hàng (${profile?.merchantStatus ?: "none"})",
                color = FoxOrangeDeep,
                fontWeight = FontWeight.Bold,
              )
            }
            if (cloudState.isVerifiedAdmin) {
              Button(
                onClick = onOpenAdminDashboard,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_admin_dashboard"),
                colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
              ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở Bảng điều khiển Quản trị (Admin)", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Personal Profile Editor
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Hồ sơ cá nhân (Cloud Firestore)",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = FoxTextStrong,
            )
            OutlinedTextField(
              value = displayName,
              onValueChange = { displayName = it },
              label = { Text("Họ và tên hiển thị") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
            )
            OutlinedTextField(
              value = phone,
              onValueChange = { phone = it },
              label = { Text("Số điện thoại") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = defaultAddress,
              onValueChange = { defaultAddress = it },
              label = { Text("Địa chỉ mặc định") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = { onSaveProfile(displayName, phone, defaultAddress) },
              enabled = !cloudState.isBusy && displayName.isNotBlank(),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_button"),
              colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
            ) {
              Text("Lưu hồ sơ Cloud", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Partner Role Registration (Driver & Merchant -> pending for Admin approval)
      if (!cloudState.isVerifiedAdmin) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                text = "Đăng ký đối tác FOX (Chờ Admin duyệt)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              OutlinedTextField(
                value = vehicleType,
                onValueChange = { vehicleType = it },
                label = { Text("Loại xe (VD: FOX Bike / Car 4)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = vehiclePlate,
                onValueChange = { vehiclePlate = it },
                label = { Text("Biển số xe (VD: 59X3-12345)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedButton(
                onClick = { onApplyDriver(vehicleType, vehiclePlate) },
                enabled = !cloudState.isBusy && vehicleType.length >= 2 && vehiclePlate.length >= 4,
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("apply_driver_button"),
              ) {
                Text("Gửi hồ sơ đăng ký Tài xế", color = FoxBlue, fontWeight = FontWeight.Bold)
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outline)

              OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("Tên nhà hàng / gian hàng") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = storeCategory,
                onValueChange = { storeCategory = it },
                label = { Text("Danh mục kinh doanh") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedButton(
                onClick = { onApplyMerchant(storeName, storeCategory) },
                enabled = !cloudState.isBusy && storeName.length >= 2 && storeCategory.length >= 2,
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("apply_merchant_button"),
              ) {
                Text("Gửi hồ sơ đăng ký Gian hàng", color = FoxOrangeDeep, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Local DEMO Switch & Sign Out
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onSwitchToOfflineDemo,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("switch_to_room_demo_button"),
            border = BorderStroke(1.dp, FoxOrange),
          ) {
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = FoxOrangeDeep)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mở dữ liệu DEMO cục bộ (Room SQLite)", color = FoxOrangeDeep, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
            colors = ButtonDefaults.buttonColors(containerColor = FoxRed, contentColor = Color.White),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Đăng xuất tài khoản Google", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudNotificationsScreen(
  cloudState: FoxCloudUiState,
  onMarkRead: (String) -> Unit,
  onMarkAllRead: () -> Unit = {},
  onSyncFcmToken: () -> Unit = {},
  onNotificationClick: (CloudNotification) -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Thông báo Cloud FOX",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("cloud_notifications_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        actions = {
          if (cloudState.unreadCloudNotificationsCount > 0) {
            TextButton(
              onClick = onMarkAllRead,
              modifier = Modifier.testTag("cloud_notifications_mark_all_read"),
            ) {
              Text("Đọc tất cả", color = FoxBlue, fontWeight = FontWeight.Bold)
            }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          RoleFcmStatusBanner(
            fcmRegistered = cloudState.fcmTokenRegistered,
            fcmStatusText = cloudState.fcmSyncStatus,
            roleHint = "Thông báo đơn hàng được định tuyến theo vai trò (${cloudRoleLabel(cloudState.profile?.role ?: "customer")}).",
            onSyncFcmToken = onSyncFcmToken,
          )
        }

        if (cloudState.cloudNotifications.isEmpty()) {
          item {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              color = Color.White,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Text(
                text = "Chưa có thông báo Cloud nào. Khi có cập nhật đơn hàng hoặc duyệt đối tác, thông báo sẽ đồng bộ tức thì tại đây.",
                style = MaterialTheme.typography.bodyMedium,
                color = FoxTextSecondary,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        } else {
          items(cloudState.cloudNotifications, key = { it.id }) { item ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (!item.readFlag) onMarkRead(item.id)
                  onNotificationClick(item)
                }
                .testTag("cloud_notification_${item.id}"),
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (item.readFlag) Color.White else FoxBlue.copy(alpha = 0.08f),
              ),
              border = BorderStroke(
                width = 1.dp,
                color = if (item.readFlag) MaterialTheme.colorScheme.outline else FoxBlue.copy(alpha = 0.28f),
              ),
            ) {
              Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  tint = if (item.readFlag) FoxTextSecondary else FoxOrange,
                  modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      item.title,
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = FoxTextStrong,
                      modifier = Modifier.weight(1f),
                    )
                    if (item.targetRole.isNotBlank()) {
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = FoxBlue.copy(alpha = 0.12f),
                      ) {
                        Text(
                          text = FoxNotificationRouter.roleBadgeLabel(item.targetRole),
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = FoxBlue,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                  Text(item.body, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
                  item.createdAt?.let { ts ->
                    Text(formatOrderTime(ts.toDate().time), style = MaterialTheme.typography.labelSmall, color = FoxBlue)
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

@Composable
fun CloudWalletStatusScreen(
  onOpenOfflineDemo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 640.dp),
      contentPadding = PaddingValues(bottom = 32.dp),
    ) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(FoxBlue)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Ví FOX & Thanh toán Cloud",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Trạng thái: CHƯA HOẠT ĐỘNG — chờ triển khai Cloud Functions đáng tin cậy.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.90f),
            )
          }
        }
      }

      item {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("cloud_wallet_inactive_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, FoxOrange.copy(alpha = 0.45f)),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(50),
                color = FoxOrange.copy(alpha = 0.14f),
              ) {
                Text(
                  text = "TÀI CHÍNH CLOUD: CHƯA HOẠT ĐỘNG",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                  color = FoxOrangeDeep,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
              }
              Text(
                text = "Khóa ghi số dư & hoa hồng ở phía ứng dụng",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(
                text = "Để bảo đảm tính toàn vẹn tài chính, ứng dụng di động KHÔNG được phép tự cộng/trừ số dư ví Cloud, tạo giao dịch thanh toán hoặc tính chiết khấu hoa hồng. Quy tắc bảo mật Firestore đang chặn toàn bộ quyền ghi trực tiếp từ client vào /wallets và /wallet_transactions.",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline)
              Row(verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Rounded.Info,
                  contentDescription = null,
                  tint = FoxBlue,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Giá cước hiển thị trên đơn Cloud hiện chỉ là ước tính tham khảo (unverified_backend_required) và sẽ được xác thực lại bởi backend đáng tin cậy khi triển khai Cloud Functions.",
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextStrong,
                )
              }
            }
          }

          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "Cần thử nạp/trừ Ví DEMO ngoại tuyến?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(
                text = "Chế độ DEMO ngoại tuyến lưu trên Room SQLite độc lập hoàn toàn với tài khoản Cloud và không bao giờ đồng bộ ngược lên Firebase.",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
              OutlinedButton(
                onClick = onOpenOfflineDemo,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("cloud_wallet_open_offline_demo"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(imageVector = Icons.Rounded.PhoneAndroid, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở chế độ DEMO ngoại tuyến (Room SQLite)", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

