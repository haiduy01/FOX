package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.DemoPromoBanner
import com.example.ui.FoxServiceType
import com.example.ui.FoxUiState
import com.example.ui.components.FoxBrandMark
import com.example.ui.components.FoxMascotIllustration
import com.example.ui.components.FoxServiceIllustration
import com.example.ui.formatVnd
import com.example.ui.mapServiceType
import com.example.ui.orderStatusLabel
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxBlueDark
import com.example.ui.theme.FoxDeliveryTint
import com.example.ui.theme.FoxDeliveryTintBottom
import com.example.ui.theme.FoxFoodAccent
import com.example.ui.theme.FoxFoodTint
import com.example.ui.theme.FoxFoodTintBottom
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRideTint
import com.example.ui.theme.FoxRideTintBottom
import com.example.ui.theme.FoxShoppingAccent
import com.example.ui.theme.FoxShoppingTint
import com.example.ui.theme.FoxShoppingTintBottom
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

@Composable
fun HomeScreen(
  uiState: FoxUiState,
  onSearchQueryChange: (String) -> Unit,
  onServiceClick: (FoxServiceType) -> Unit,
  onNotificationsClick: () -> Unit,
  onWalletClick: () -> Unit,
  onViewAllOrdersClick: () -> Unit,
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
      contentPadding = PaddingValues(bottom = 36.dp),
    ) {
      // 1. Royal Blue Hero Header + Clean Embedded Search Bar (no clipping negative offsets)
      item {
        FoxHeroWithSearch(
          unreadCount = uiState.unreadNotificationsCount,
          demoBalanceVnd = uiState.demoWalletBalanceVnd,
          isCloudMode = uiState.backendStatus.isProductionBackendConnected,
          searchQuery = uiState.searchQuery,
          onSearchQueryChange = onSearchQueryChange,
          onNotificationsClick = onNotificationsClick,
          onWalletClick = onWalletClick,
        )
      }

      // 2. Four High-Contrast Core FOX Service Cards
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.section_services),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color = FoxBlue.copy(alpha = 0.12f),
            ) {
              Text(
                text = "4 dịch vụ cốt lõi",
                style = MaterialTheme.typography.labelMedium,
                color = FoxBlue,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          val services = uiState.filteredServices
          if (services.isEmpty()) {
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
                Text(
                  text = "Không tìm thấy dịch vụ phù hợp",
                  style = MaterialTheme.typography.titleMedium,
                  color = FoxTextStrong,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Thử tìm \"Đặt xe\", \"Giao hàng\", \"Đồ ăn\" hoặc \"Mua sắm\".",
                  style = MaterialTheme.typography.bodyMedium,
                  color = FoxTextSecondary,
                )
              }
            }
          } else {
            val rows = services.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              rows.forEach { rowItems ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                  rowItems.forEach { service ->
                    FoxServiceCard(
                      serviceType = service,
                      onClick = { onServiceClick(service) },
                      modifier = Modifier.weight(1f),
                    )
                  }
                  if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }
        }
      }

      // 3. Unclipped Promotional Carousel Banner
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.section_promos),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
            Text(
              text = "Vuốt ngang →",
              style = MaterialTheme.typography.labelMedium,
              color = FoxBlue,
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            items(uiState.promos, key = { it.id }) { promo ->
              FoxPromoCard(
                promo = promo,
                onClick = { onServiceClick(promo.targetService) },
              )
            }
          }
        }
      }

      // 4. Recent Orders Snapshot (persisted via Room)
      item {
        Spacer(modifier = Modifier.height(22.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Đơn gần đây (${uiState.orders.size})",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
            TextButton(
              onClick = onViewAllOrdersClick,
              modifier = Modifier.testTag("home_view_all_orders_button"),
            ) {
              Text("Xem tất cả", color = FoxBlue, fontWeight = FontWeight.Bold)
            }
          }

          val latest = uiState.orders.firstOrNull()
          if (latest == null) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onServiceClick(FoxServiceType.RIDE) },
              shape = RoundedCornerShape(20.dp),
              color = Color.White,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                FoxServiceIllustration(serviceType = FoxServiceType.RIDE, size = 48.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Chưa có đơn DEMO nào được tạo",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Chọn 1 trong 4 dịch vụ phía trên để tạo đơn lưu bền vững vào thiết bị.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxTextSecondary,
                  )
                }
              }
            }
          } else {
            val svc = mapServiceType(latest.serviceType)
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("home_latest_activity_card")
                .clickable(onClick = onViewAllOrdersClick),
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(serviceCardTint(svc)),
                  contentAlignment = Alignment.Center,
                ) {
                  FoxServiceIllustration(serviceType = svc, size = 44.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${latest.orderCode} • ${latest.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = latest.detailSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = formatVnd(latest.totalVnd),
                      style = MaterialTheme.typography.labelLarge,
                      color = FoxBlue,
                    )
                    Text(
                      text = " • ${orderStatusLabel(latest.status)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = FoxOrangeDeep,
                    )
                  }
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                  contentDescription = null,
                  tint = FoxTextSecondary,
                )
              }
            }
          }
        }
      }

      // 5. Honest Architecture / Backend Readiness Status Banner
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          shape = RoundedCornerShape(18.dp),
          color = Color.White,
          border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = FoxBlue,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              val isCloud = uiState.backendStatus.isProductionBackendConnected
              Text(
                text = if (isCloud) {
                  "Chế độ Cloud: ${uiState.backendStatus.activeStorageLabel}"
                } else {
                  "Lưu trữ DEMO: ${uiState.backendStatus.activeStorageLabel}"
                },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (isCloud) {
                  "Đơn hàng lưu trên Cloud Firestore đa thiết bị. Ví FOX DEMO, thanh toán & hoa hồng được kết nối qua Cloud Functions v2 (DEMO — KHÔNG PHẢI TIỀN THẬT)."
                } else {
                  "4 luồng khách hàng DEMO lưu bền vững trên thiết bị (Room SQLite), tách biệt hoàn toàn với dữ liệu Cloud."
                },
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FoxHeroWithSearch(
  unreadCount: Int,
  demoBalanceVnd: Long,
  isCloudMode: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onNotificationsClick: () -> Unit,
  onWalletClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
      .background(
        Brush.linearGradient(
          colors = listOf(Color(0xFF1F63FF), FoxBlue, FoxBlueDark),
        )
      )
      .drawBehind {
        drawCircle(
          color = Color.White.copy(alpha = 0.08f),
          radius = size.width * 0.42f,
          center = Offset(size.width * 0.92f, size.height * 0.14f),
        )
        drawCircle(
          color = FoxOrange.copy(alpha = 0.22f),
          radius = size.width * 0.30f,
          center = Offset(size.width * 0.08f, size.height * 0.92f),
        )
      }
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Top row: FOX Logo + Slogan + Notifications bell
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          FoxBrandMark(onDarkHeader = true)
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = stringResource(R.string.fox_slogan),
            style = MaterialTheme.typography.labelLarge.copy(
              color = Color(0xFFFFC9B8),
              fontWeight = FontWeight.Bold,
            ),
          )
        }

        IconButton(
          onClick = onNotificationsClick,
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .testTag("home_notifications_button"),
        ) {
          BadgedBox(
            badge = {
              if (unreadCount > 0) {
                Badge(containerColor = FoxOrange, contentColor = Color.White) {
                  Text(unreadCount.toString())
                }
              }
            }
          ) {
            Icon(
              imageVector = Icons.Rounded.Notifications,
              contentDescription = stringResource(R.string.cd_notifications),
              tint = Color.White,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Greeting + Quick Wallet Pill + 3D FOX Mascot
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.90f),
          )
          Spacer(modifier = Modifier.height(12.dp))

          Surface(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .clickable(onClick = onWalletClick)
              .testTag("home_wallet_pill"),
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = Color(0xFFFFC2AC),
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = if (isCloudMode) "Ví FOX Cloud DEMO" else "Ví FOX DEMO",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                  text = formatVnd(demoBalanceVnd),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
          modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(26.dp)),
          contentAlignment = Alignment.Center,
        ) {
          FoxMascotIllustration(
            contentDesc = stringResource(R.string.cd_fox_mascot),
            size = 90.dp,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Clean high-contrast Search Bar embedded inside hero bottom
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_input"),
          placeholder = {
            Text(
              text = stringResource(R.string.home_search_placeholder),
              style = MaterialTheme.typography.bodyMedium,
              color = FoxTextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = stringResource(R.string.cd_search),
              tint = FoxBlue,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_clear_search),
                  tint = FoxTextSecondary,
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FoxTextStrong,
            unfocusedTextColor = FoxTextStrong,
            focusedBorderColor = FoxOrange,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
          ),
        )
      }
    }
  }
}

@Composable
private fun FoxServiceCard(
  serviceType: FoxServiceType,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val topTint = serviceCardTint(serviceType)
  val bottomTint = serviceCardTintBottom(serviceType)
  val accentColor = serviceAccentColor(serviceType)

  Card(
    modifier = modifier
      .heightIn(min = 192.dp)
      .testTag("service_card_${serviceType.route}")
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = topTint),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 192.dp)
        .background(Brush.verticalGradient(colors = listOf(topTint, bottomTint)))
        .border(1.5.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
        .padding(14.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 164.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          // Larger elevated 3D service illustration container
          Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 3.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              FoxServiceIllustration(serviceType = serviceType, size = 60.dp)
            }
          }

          // Solid high-contrast pill badge
          Surface(
            shape = RoundedCornerShape(50),
            color = accentColor,
          ) {
            Text(
              text = serviceType.badge,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              maxLines = 1,
            )
          }
        }

        Column {
          Text(
            text = serviceType.title,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 17.sp,
            ),
            color = FoxTextStrong,
            maxLines = 1,
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = serviceType.subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
              fontWeight = FontWeight.Medium,
              fontSize = 12.5.sp,
            ),
            color = FoxTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun FoxPromoCard(
  promo: DemoPromoBanner,
  onClick: () -> Unit,
) {
  val gradient =
    when (promo.targetService) {
      FoxServiceType.RIDE -> listOf(FoxBlue, FoxBlueDark)
      FoxServiceType.FOOD -> listOf(FoxOrange, FoxOrangeDeep)
      FoxServiceType.DELIVERY -> listOf(Color(0xFF1946D2), Color(0xFF0E2B8A))
      FoxServiceType.SHOPPING -> listOf(FoxShoppingAccent, Color(0xFF4C1D95))
    }

  Card(
    modifier = Modifier
      .width(304.dp)
      .heightIn(min = 166.dp)
      .testTag("promo_card_${promo.id}")
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(22.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 166.dp)
        .background(Brush.linearGradient(gradient))
        .padding(18.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.22f),
          ) {
            Text(
              text = promo.tag,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = promo.title,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
          ),
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = promo.subtitle,
          style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
          color = Color.White.copy(alpha = 0.92f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
          ) {
            Text(
              text = "Mã: ${promo.code}",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = FoxBlueDark,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
          Text(
            text = "Dùng ngay →",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
          )
        }
      }
    }
  }
}

fun serviceCardTint(serviceType: FoxServiceType): Color =
  when (serviceType) {
    FoxServiceType.RIDE -> FoxRideTint
    FoxServiceType.DELIVERY -> FoxDeliveryTint
    FoxServiceType.FOOD -> FoxFoodTint
    FoxServiceType.SHOPPING -> FoxShoppingTint
  }

fun serviceCardTintBottom(serviceType: FoxServiceType): Color =
  when (serviceType) {
    FoxServiceType.RIDE -> FoxRideTintBottom
    FoxServiceType.DELIVERY -> FoxDeliveryTintBottom
    FoxServiceType.FOOD -> FoxFoodTintBottom
    FoxServiceType.SHOPPING -> FoxShoppingTintBottom
  }

fun serviceAccentColor(serviceType: FoxServiceType): Color =
  when (serviceType) {
    FoxServiceType.RIDE -> FoxBlue
    FoxServiceType.DELIVERY -> FoxOrangeDeep
    FoxServiceType.FOOD -> FoxFoodAccent
    FoxServiceType.SHOPPING -> FoxShoppingAccent
  }
