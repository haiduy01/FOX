package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.FoxRolePreview
import com.example.ui.FoxServiceType
import com.example.ui.FoxUiState
import com.example.ui.components.FoxMascotIllustration
import com.example.ui.components.FoxServiceIllustration
import com.example.ui.formatOrderTime
import com.example.ui.formatVnd
import com.example.ui.mapServiceType
import com.example.ui.orderStatusLabel
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxBlueDark
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

@Composable
fun OrdersScreen(
  uiState: FoxUiState,
  onStartService: (FoxServiceType) -> Unit,
  onCancelOrder: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf<FoxServiceType?>(null) }
  val activeFilter = selectedFilter
  val filtered =
    if (activeFilter == null) uiState.orders
    else uiState.orders.filter { it.serviceType == activeFilter.name }

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
          text = "Lịch sử đơn hàng (${uiState.orders.size})",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxTextStrong,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Lưu bền vững trên thiết bị qua Room SQLite (Chế độ DEMO).",
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
              FoxServiceIllustration(
                serviceType = selectedFilter ?: FoxServiceType.RIDE,
                size = 64.dp,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Chưa có đơn hàng nào trong mục này",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Spacer(modifier = Modifier.height(6.dp))
              TextButton(onClick = { onStartService(selectedFilter ?: FoxServiceType.RIDE) }) {
                Text("Tạo đơn DEMO ngay", color = FoxBlue, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
        items(filtered, key = { it.orderCode }) { order ->
          val svc = mapServiceType(order.serviceType)
          val isCancelled = order.status == "cancelled_demo"
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.testTag("order_card_${order.orderCode}"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(serviceCardTint(svc)),
                  contentAlignment = Alignment.Center,
                ) {
                  FoxServiceIllustration(serviceType = svc, size = 38.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = order.orderCode,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = FoxBlue,
                  )
                  Text(
                    text = order.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                  )
                  Text(
                    text = formatOrderTime(order.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = FoxTextSecondary,
                  )
                }
                Surface(
                  shape = RoundedCornerShape(50),
                  color = if (isCancelled) FoxRed.copy(alpha = 0.12f) else FoxOrange.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = orderStatusLabel(order.status),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCancelled) FoxRed else FoxOrangeDeep,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outline)

              Text(
                text = order.detailSummary,
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextStrong,
              )
              Text(
                text = "Từ: ${order.primaryAddress}\nĐến: ${order.secondaryAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = order.paymentMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = FoxTextSecondary,
                  )
                  Text(
                    text = formatVnd(order.totalVnd),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = FoxBlue,
                  )
                }

                if (order.status == "pending_demo") {
                  OutlinedButton(
                    onClick = { onCancelOrder(order.orderCode) },
                    modifier = Modifier.testTag("cancel_order_${order.orderCode}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.5f)),
                  ) {
                    Text("Hủy đơn", color = FoxRed)
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
fun WalletScreen(
  uiState: FoxUiState,
  modifier: Modifier = Modifier,
) {
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
      item {
        Text(
          text = "Ví FOX DEMO",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxTextStrong,
        )
      }

      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Brush.linearGradient(listOf(FoxBlue, FoxBlueDark)))
              .padding(20.dp),
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(shape = RoundedCornerShape(50), color = FoxOrange) {
                  Text(
                    text = "DEMO – KHÔNG PHẢI TIỀN THẬT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
                Icon(
                  imageVector = Icons.Rounded.AccountBalanceWallet,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = 0.9f),
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "Số dư mô phỏng (VND nguyên)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
              )
              Text(
                text = formatVnd(uiState.demoWalletBalanceVnd),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = stringResource(R.string.notice_wallet_demo),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
              )
            }
          }
        }
      }

      item {
        Text(
          text = "Sổ giao dịch DEMO (${uiState.walletTransactions.size})",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = FoxTextStrong,
        )
      }

      items(uiState.walletTransactions, key = { it.txId }) { tx ->
        val isCredit = tx.amountVnd >= 0L
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = tx.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(
                text = "${tx.txId} • ${formatOrderTime(tx.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
            }
            Text(
              text = (if (isCredit) "+" else "") + formatVnd(tx.amountVnd),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = if (isCredit) FoxGreen else FoxRed,
            )
          }
        }
      }
    }
  }
}

@Composable
fun AccountScreen(
  uiState: FoxUiState,
  onSelectRolePreview: (FoxRolePreview) -> Unit,
  onOpenCloudSignIn: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
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
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(FoxBlue.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center,
            ) {
              FoxMascotIllustration(
                contentDesc = stringResource(R.string.cd_fox_mascot),
                size = 56.dp,
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = "Chế độ DEMO cục bộ (Room SQLite)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(
                text = "Dữ liệu cục bộ tách biệt hoàn toàn, không tự đồng bộ lên Cloud Firestore.",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
            }
          }
        }
      }

      if (onOpenCloudSignIn != null) {
        item {
          OutlinedButton(
            onClick = onOpenCloudSignIn,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("return_to_cloud_auth_button"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, FoxBlue),
          ) {
            Text(
              text = "Chuyển sang FOX Cloud (Đăng nhập Google / 4 vai trò)",
              color = FoxBlue,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      item {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          color = Color.White,
          border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Kiến trúc lưu trữ & Backend",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxTextStrong,
            )
            Text(
              text = "• Đang chạy: ${uiState.backendStatus.activeStorageLabel}\n" +
                "• Cloud đã kết nối: Firebase Auth (Google) + Cloud Firestore\n" +
                "• Bảo mật: Dữ liệu DEMO tách biệt, không cấp quyền Admin/Driver/Merchant ở client.",
              style = MaterialTheme.typography.bodySmall,
              color = FoxTextSecondary,
            )
          }
        }
      }

      item {
        Text(
          text = "Khung phân vai FOX (Xem trước kiến trúc)",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = FoxTextStrong,
        )
      }

      items(FoxRolePreview.entries) { role ->
        val isSelected = uiState.selectedRolePreview == role
        val icon =
          when (role) {
            FoxRolePreview.CUSTOMER -> Icons.Rounded.Person
            FoxRolePreview.DRIVER -> Icons.Rounded.DirectionsBike
            FoxRolePreview.MERCHANT -> Icons.Rounded.Storefront
            FoxRolePreview.ADMIN -> Icons.Rounded.AdminPanelSettings
          }
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelectRolePreview(role) }
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) FoxBlue else MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(18.dp),
            )
            .testTag("role_preview_${role.name.lowercase()}"),
          color = if (isSelected) FoxBlue.copy(alpha = 0.08f) else Color.White,
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (isSelected) FoxBlue else FoxTextSecondary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                role.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(role.summary, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
  uiState: FoxUiState,
  onMarkRead: (String) -> Unit,
  onMarkAllRead: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Thông báo FOX",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("notifications_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        actions = {
          if (uiState.unreadNotificationsCount > 0) {
            TextButton(onClick = onMarkAllRead) {
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
        items(uiState.notifications, key = { it.id }) { item ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onMarkRead(item.id) }
              .testTag("notification_item_${item.id}"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (item.isRead) Color.White else FoxBlue.copy(alpha = 0.08f),
            ),
            border = BorderStroke(
              width = 1.dp,
              color = if (item.isRead) MaterialTheme.colorScheme.outline else FoxBlue.copy(alpha = 0.28f),
            ),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (item.isRead) FoxTextSecondary else FoxOrange,
                modifier = Modifier.padding(top = 2.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  item.title,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = formatOrderTime(item.createdAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxBlue,
                )
              }
            }
          }
        }
      }
    }
  }
}
