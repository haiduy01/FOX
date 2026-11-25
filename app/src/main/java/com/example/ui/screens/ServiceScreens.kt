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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.repository.FoxDemoPricingEngine
import com.example.data.repository.PriceQuote
import com.example.ui.FoxServiceType
import com.example.ui.FoxUiState
import com.example.ui.components.FoxServiceIllustration
import com.example.ui.formatVnd
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceScaffold(
  title: String,
  serviceType: FoxServiceType,
  onBack: () -> Unit,
  content: @Composable (PaddingValues) -> Unit,
) {
  BackHandler(onBack = onBack)
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            FoxServiceIllustration(serviceType = serviceType, size = 32.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("service_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    content = content,
  )
}

@Composable
private fun DemoModeNotice(text: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = FoxOrange.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, FoxOrange.copy(alpha = 0.35f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
      color = FoxOrangeDeep,
      modifier = Modifier.padding(12.dp),
    )
  }
}

@Composable
private fun PaymentAndVoucherCard(
  voucherCode: String,
  voucherDescription: String,
  applyVoucher: Boolean,
  onToggleVoucher: (Boolean) -> Unit,
  paymentMethod: String,
  onSelectPayment: (String) -> Unit,
  demoWalletBalanceVnd: Long,
  isCloudMode: Boolean = false,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Rounded.ConfirmationNumber,
            contentDescription = null,
            tint = FoxOrange,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Voucher DEMO: $voucherCode",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxTextStrong,
            )
            Text(
              text = voucherDescription,
              style = MaterialTheme.typography.bodySmall,
              color = FoxTextSecondary,
            )
          }
        }
        FilterChip(
          selected = applyVoucher,
          onClick = { onToggleVoucher(!applyVoucher) },
          label = { Text(if (applyVoucher) "Đã áp" else "Áp mã") },
          modifier = Modifier.testTag("voucher_toggle_$voucherCode"),
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      Text(
        text = if (isCloudMode) "Phương thức thanh toán (DEMO — KHÔNG PHẢI TIỀN THẬT)" else "Phương thức thanh toán (DEMO)",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = FoxTextStrong,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
          "Ví FOX DEMO" to "Số dư: ${formatVnd(demoWalletBalanceVnd)}",
          "Tiền mặt DEMO" to "Trả sau khi nhận",
        ).forEach { (method, sub) ->
          val selected = paymentMethod == method
          Surface(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .clickable { onSelectPayment(method) }
              .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) FoxBlue else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp),
              ),
            color = if (selected) FoxBlue.copy(alpha = 0.08f) else Color.White,
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = method,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) FoxBlue else FoxTextStrong,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
            }
          }
        }
      }
      if (isCloudMode) {
        Text(
          text = "Giá cước, mã giảm giá và trừ số dư Ví DEMO được xác thực phía server qua Cloud Functions v2 (không phải tiền thật).",
          style = MaterialTheme.typography.labelSmall,
          color = FoxOrangeDeep,
        )
      }
    }
  }
}

@Composable
private fun PriceSummaryCard(
  quote: PriceQuote,
  subtotalLabel: String,
  feeLabel: String? = null,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Chi tiết thanh toán (VND)",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = FoxTextStrong,
      )
      SummaryLine(subtotalLabel, formatVnd(quote.subtotalVnd))
      if (feeLabel != null) {
        SummaryLine(
          feeLabel,
          if (quote.feeVnd == 0L) "Miễn phí" else formatVnd(quote.feeVnd),
        )
      }
      if (quote.discountVnd > 0L) {
        SummaryLine("Giảm giá voucher DEMO", "-${formatVnd(quote.discountVnd)}", FoxGreen)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Tổng thanh toán",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxTextStrong,
        )
        Text(
          text = formatVnd(quote.totalVnd),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxBlue,
        )
      }
    }
  }
}

@Composable
private fun SummaryLine(label: String, value: String, valueColor: Color = FoxTextStrong) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = FoxTextSecondary)
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      color = valueColor,
    )
  }
}

@Composable
fun RideScreen(
  uiState: FoxUiState,
  onBack: () -> Unit,
  onOrderCreated: () -> Unit,
  onSubmitRide: (
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) -> Unit,
) {
  var pickup by remember { mutableStateOf("") }
  var destination by remember { mutableStateOf("") }
  var selectedVehicle by remember { mutableStateOf("FOX Car 4") }
  var distanceKm by remember { mutableIntStateOf(7) }
  var note by remember { mutableStateOf("") }
  var applyVoucher by remember { mutableStateOf(true) }
  var paymentMethod by remember {
    mutableStateOf(
      if (uiState.backendStatus.isProductionBackendConnected) "Tiền mặt DEMO" else "Ví FOX DEMO",
    )
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val activeVoucher = if (applyVoucher) "FOXRIDE25" else null
  val quote = FoxDemoPricingEngine.estimateRide(selectedVehicle, distanceKm, activeVoucher)

  val vehicleOptions = listOf(
    Triple("FOX Bike", "Nhanh gọn • 1 chỗ", FoxDemoPricingEngine.estimateRide("FOX Bike", distanceKm, null).subtotalVnd),
    Triple("FOX Car 4", "Thoải mái • 4 chỗ", FoxDemoPricingEngine.estimateRide("FOX Car 4", distanceKm, null).subtotalVnd),
    Triple("FOX Car 7", "Rộng rãi • 7 chỗ", FoxDemoPricingEngine.estimateRide("FOX Car 7", distanceKm, null).subtotalVnd),
  )

  ServiceScaffold(
    title = "FOX Đặt xe",
    serviceType = FoxServiceType.RIDE,
    onBack = onBack,
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          DemoModeNotice(
            "Chế độ bản đồ & khoảng cách DEMO (không giả lập GPS trực tiếp). Đơn đặt xe sẽ lưu bền vững vào Room SQLite cục bộ."
          )
        }

        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                "Lộ trình chuyến đi",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              // Quick preset route chips for rapid pilot testing
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(
                  "Q.1 → Sân bay TSN" to Triple("Chợ Bến Thành, Q.1", "Sân bay Tân Sơn Nhất", 8),
                  "Thủ Đức → Landmark 81" to Triple("Khu Công Nghệ Cao, Thủ Đức", "Landmark 81, Bình Thạnh", 12),
                )
                items(presets) { (label, route) ->
                  FilterChip(
                    selected = pickup == route.first && destination == route.second,
                    onClick = {
                      pickup = route.first
                      destination = route.second
                      distanceKm = route.third
                      errorText = null
                    },
                    label = { Text(label) },
                  )
                }
              }

              OutlinedTextField(
                value = pickup,
                onValueChange = {
                  pickup = it
                  errorText = null
                },
                label = { Text("Điểm đón *") },
                placeholder = { Text("Ví dụ: 123 Nguyễn Huệ, Quận 1") },
                leadingIcon = { Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = FoxBlue) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("ride_pickup_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = destination,
                onValueChange = {
                  destination = it
                  errorText = null
                },
                label = { Text("Điểm đến *") },
                placeholder = { Text("Ví dụ: Sân bay Tân Sơn Nhất") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = FoxOrange) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("ride_destination_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú cho tài xế (tùy chọn)") },
                placeholder = { Text("Ví dụ: Đứng trước cổng chính") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              Text(
                "Quãng đường ước lượng (DEMO)",
                style = MaterialTheme.typography.labelLarge,
                color = FoxTextStrong,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(3, 7, 12, 18)) { km ->
                  FilterChip(
                    selected = distanceKm == km,
                    onClick = { distanceKm = km },
                    label = { Text("~$km km") },
                  )
                }
              }
              if (errorText != null) {
                Text(
                  text = errorText!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }

        item {
          Text(
            "Chọn loại xe",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = FoxTextStrong,
          )
        }

        items(vehicleOptions) { (name, desc, baseTotal) ->
          val isSelected = selectedVehicle == name
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .clickable { selectedVehicle = name }
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) FoxBlue else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp),
              )
              .testTag("ride_vehicle_$name"),
            color = if (isSelected) FoxBlue.copy(alpha = 0.08f) else Color.White,
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = null,
                tint = if (isSelected) FoxBlue else FoxTextSecondary,
                modifier = Modifier.size(30.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  name,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(desc, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
              }
              Text(
                text = formatVnd(baseTotal),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = FoxBlue,
              )
            }
          }
        }

        item {
          PaymentAndVoucherCard(
            voucherCode = "FOXRIDE25",
            voucherDescription = "Giảm 25%, tối đa 30.000đ",
            applyVoucher = applyVoucher,
            onToggleVoucher = { applyVoucher = it },
            paymentMethod = paymentMethod,
            onSelectPayment = { paymentMethod = it },
            demoWalletBalanceVnd = uiState.demoWalletBalanceVnd,
            isCloudMode = uiState.backendStatus.isProductionBackendConnected,
          )
        }

        item {
          PriceSummaryCard(
            quote = quote,
            subtotalLabel = "Giá cước ước tính ($selectedVehicle • ~$distanceKm km)",
          )
        }

        item {
          Button(
            enabled = !uiState.isSubmitting,
            onClick = {
              if (pickup.isBlank() || destination.isBlank()) {
                errorText = "Vui lòng nhập đầy đủ điểm đón và điểm đến."
              } else {
                onSubmitRide(
                  pickup.trim(),
                  destination.trim(),
                  selectedVehicle,
                  distanceKm,
                  note.trim(),
                  activeVoucher,
                  paymentMethod,
                  onOrderCreated,
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("ride_submit_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
          ) {
            Text(
              text = "Xác nhận đặt xe (DEMO) • ${formatVnd(quote.totalVnd)}",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@Composable
fun DeliveryScreen(
  uiState: FoxUiState,
  onBack: () -> Unit,
  onOrderCreated: () -> Unit,
  onSubmitDelivery: (
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
    onSuccess: () -> Unit,
  ) -> Unit,
) {
  var senderName by remember { mutableStateOf("") }
  var senderPhone by remember { mutableStateOf("") }
  var pickupAddress by remember { mutableStateOf("") }
  var receiverName by remember { mutableStateOf("") }
  var receiverPhone by remember { mutableStateOf("") }
  var dropoffAddress by remember { mutableStateOf("") }
  var selectedItemType by remember { mutableStateOf("Tài liệu") }
  var selectedWeight by remember { mutableStateOf("< 2 kg") }
  var selectedSpeed by remember { mutableStateOf("Tiêu chuẩn") }
  var note by remember { mutableStateOf("") }
  var applyVoucher by remember { mutableStateOf(true) }
  var paymentMethod by remember {
    mutableStateOf(
      if (uiState.backendStatus.isProductionBackendConnected) "Tiền mặt DEMO" else "Ví FOX DEMO",
    )
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val activeVoucher = if (applyVoucher) "FOXSHIP15" else null
  val quote = FoxDemoPricingEngine.estimateDelivery(selectedWeight, selectedSpeed, activeVoucher)

  ServiceScaffold(
    title = "FOX Giao hàng",
    serviceType = FoxServiceType.DELIVERY,
    onBack = onBack,
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          DemoModeNotice(
            "Tạo vận đơn DEMO lưu bền vững vào Room SQLite cục bộ. Điều phối tài xế thật sẽ tích hợp ở bước kết nối backend."
          )
        }

        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  "Thông tin gửi & nhận",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                FilterChip(
                  selected = false,
                  onClick = {
                    senderName = "Nguyễn Minh Anh"
                    senderPhone = "0901234567"
                    pickupAddress = "45 Lê Lợi, Quận 1, TP.HCM"
                    receiverName = "Trần Hoàng Nam"
                    receiverPhone = "0912345678"
                    dropoffAddress = "88 Cách Mạng Tháng 8, Quận 3"
                    errorText = null
                  },
                  label = { Text("Điền mẫu DEMO") },
                )
              }

              OutlinedTextField(
                value = senderName,
                onValueChange = { senderName = it; errorText = null },
                label = { Text("Tên người gửi *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_sender_name"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = senderPhone,
                onValueChange = { senderPhone = it; errorText = null },
                label = { Text("SĐT người gửi *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_sender_phone"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it; errorText = null },
                label = { Text("Địa chỉ lấy hàng *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_pickup_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline)
              OutlinedTextField(
                value = receiverName,
                onValueChange = { receiverName = it; errorText = null },
                label = { Text("Tên người nhận *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_receiver_name"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = receiverPhone,
                onValueChange = { receiverPhone = it; errorText = null },
                label = { Text("SĐT người nhận *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_receiver_phone"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = dropoffAddress,
                onValueChange = { dropoffAddress = it; errorText = null },
                label = { Text("Địa chỉ giao hàng *") },
                modifier = Modifier.fillMaxWidth().testTag("delivery_dropoff_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú bưu kiện (tùy chọn)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
              )
              if (errorText != null) {
                Text(
                  text = errorText!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }

        item {
          Text("Loại hàng hóa & khối lượng", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Tài liệu", "Quần áo", "Đồ điện tử", "Khác")) { type ->
              FilterChip(
                selected = selectedItemType == type,
                onClick = { selectedItemType = type },
                label = { Text(type) },
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("< 2 kg", "2–5 kg", "5–10 kg")) { tier ->
              FilterChip(
                selected = selectedWeight == tier,
                onClick = { selectedWeight = tier },
                label = { Text(tier) },
              )
            }
          }
        }

        item {
          Text("Gói giao hàng", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Tiêu chuẩn" to "60–90 phút", "Nhanh" to "Siêu tốc 30 phút").forEach { (speed, eta) ->
              val isSelected = selectedSpeed == speed
              val baseQuote = FoxDemoPricingEngine.estimateDelivery(selectedWeight, speed, null)
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(16.dp))
                  .clickable { selectedSpeed = speed }
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) FoxOrange else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                  ),
                color = if (isSelected) FoxOrange.copy(alpha = 0.08f) else Color.White,
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Icon(
                    imageVector = Icons.Rounded.LocalShipping,
                    contentDescription = null,
                    tint = if (isSelected) FoxOrange else FoxTextSecondary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(speed, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                  Text(eta, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    formatVnd(baseQuote.subtotalVnd),
                    style = MaterialTheme.typography.labelLarge,
                    color = FoxOrangeDeep,
                  )
                }
              }
            }
          }
        }

        item {
          PaymentAndVoucherCard(
            voucherCode = "FOXSHIP15",
            voucherDescription = "Giảm trực tiếp 10.000đ phí vận đơn",
            applyVoucher = applyVoucher,
            onToggleVoucher = { applyVoucher = it },
            paymentMethod = paymentMethod,
            onSelectPayment = { paymentMethod = it },
            demoWalletBalanceVnd = uiState.demoWalletBalanceVnd,
            isCloudMode = uiState.backendStatus.isProductionBackendConnected,
          )
        }

        item {
          PriceSummaryCard(
            quote = quote,
            subtotalLabel = "Cước giao hàng ($selectedSpeed • $selectedWeight)",
          )
        }

        item {
          Button(
            enabled = !uiState.isSubmitting,
            onClick = {
              if (senderName.isBlank() || senderPhone.isBlank() || pickupAddress.isBlank() ||
                receiverName.isBlank() || receiverPhone.isBlank() || dropoffAddress.isBlank()
              ) {
                errorText = "Vui lòng điền đầy đủ thông tin người gửi, người nhận và địa chỉ."
              } else {
                onSubmitDelivery(
                  senderName.trim(),
                  senderPhone.trim(),
                  pickupAddress.trim(),
                  receiverName.trim(),
                  receiverPhone.trim(),
                  dropoffAddress.trim(),
                  selectedItemType,
                  selectedWeight,
                  selectedSpeed,
                  note.trim(),
                  activeVoucher,
                  paymentMethod,
                  onOrderCreated,
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("delivery_submit_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
          ) {
            Text(
              text = "Tạo vận đơn (DEMO) • ${formatVnd(quote.totalVnd)}",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@Composable
fun FoodScreen(
  uiState: FoxUiState,
  onUpdateCart: (dishId: String, delta: Int) -> Unit,
  onOrderCreated: () -> Unit,
  onSubmitFood: (address: String, voucherCode: String?, paymentMethod: String, onSuccess: () -> Unit) -> Unit,
  onBack: () -> Unit,
) {
  var selectedCategory by remember { mutableStateOf("Tất cả") }
  var deliveryAddress by remember { mutableStateOf("123 Nguyễn Huệ, Quận 1, TP.HCM") }
  var applyVoucher by remember { mutableStateOf(true) }
  var paymentMethod by remember {
    mutableStateOf(
      if (uiState.backendStatus.isProductionBackendConnected) "Tiền mặt DEMO" else "Ví FOX DEMO",
    )
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val categories = listOf("Tất cả", "Món nước", "Cơm", "Đồ uống", "Ăn nhanh")
  val filteredDishes =
    if (selectedCategory == "Tất cả") uiState.foodMenu
    else uiState.foodMenu.filter { it.category == selectedCategory }

  val cartCount = uiState.foodCart.values.sum()
  val cartSubtotal =
    uiState.foodCart.entries.sumOf { (id, qty) ->
      (uiState.foodMenu.firstOrNull { it.id == id }?.priceVnd ?: 0L) * qty
    }
  val activeVoucher = if (applyVoucher) "FOXFOOD30" else null
  val quote = FoxDemoPricingEngine.estimateFood(cartSubtotal, activeVoucher)

  ServiceScaffold(
    title = "FOX Đặt đồ ăn",
    serviceType = FoxServiceType.FOOD,
    onBack = onBack,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          DemoModeNotice(
            "Thực đơn nhà hàng DEMO. Giỏ hàng & đơn xác nhận được lưu bền vững vào Room SQLite."
          )
        }

        item {
          OutlinedTextField(
            value = deliveryAddress,
            onValueChange = { deliveryAddress = it; errorText = null },
            label = { Text("Địa chỉ giao đồ ăn *") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = FoxOrange) },
            modifier = Modifier.fillMaxWidth().testTag("food_address_input"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
          )
          if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }

        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
              FilterChip(
                selected = selectedCategory == cat,
                onClick = { selectedCategory = cat },
                label = { Text(cat) },
              )
            }
          }
        }

        items(filteredDishes, key = { it.id }) { dish ->
          val qty = uiState.foodCart[dish.id] ?: 0
          Card(
            shape = RoundedCornerShape(18.dp),
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
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = dish.restaurantName,
                  style = MaterialTheme.typography.labelMedium,
                  color = FoxBlue,
                )
                Text(
                  text = dish.dishName,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = FoxOrange,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "${dish.rating} • ${formatVnd(dish.priceVnd)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = FoxTextStrong,
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                if (qty > 0) {
                  IconButton(
                    onClick = { onUpdateCart(dish.id, -1) },
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant),
                  ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Giảm số lượng", tint = FoxTextStrong)
                  }
                  Text(
                    text = qty.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                    modifier = Modifier.padding(horizontal = 10.dp),
                  )
                }
                IconButton(
                  onClick = { onUpdateCart(dish.id, 1) },
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FoxBlue)
                    .testTag("food_add_${dish.id}"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = "Thêm món", tint = Color.White)
                }
              }
            }
          }
        }

        if (cartCount > 0) {
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = FoxBlue.copy(alpha = 0.06f)),
              border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = FoxBlue)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    "Giỏ hàng đồ ăn ($cartCount món)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                  )
                }
                uiState.foodCart.forEach { (id, qty) ->
                  val dish = uiState.foodMenu.firstOrNull { it.id == id }
                  if (dish != null) {
                    SummaryLine("${qty}x ${dish.dishName}", formatVnd(dish.priceVnd * qty))
                  }
                }
              }
            }
          }

          item {
            PaymentAndVoucherCard(
              voucherCode = "FOXFOOD30",
              voucherDescription = "Giảm 30% (tối đa 30K) cho đơn từ 80.000đ",
              applyVoucher = applyVoucher,
              onToggleVoucher = { applyVoucher = it },
              paymentMethod = paymentMethod,
              onSelectPayment = { paymentMethod = it },
              demoWalletBalanceVnd = uiState.demoWalletBalanceVnd,
              isCloudMode = uiState.backendStatus.isProductionBackendConnected,
            )
          }

          item {
            PriceSummaryCard(
              quote = quote,
              subtotalLabel = "Tạm tính ($cartCount món)",
              feeLabel = "Phí giao hàng DEMO",
            )
          }

          item {
            Button(
              enabled = !uiState.isSubmitting,
              onClick = {
                if (deliveryAddress.isBlank()) {
                  errorText = "Vui lòng nhập địa chỉ giao đồ ăn."
                } else {
                  onSubmitFood(deliveryAddress.trim(), activeVoucher, paymentMethod, onOrderCreated)
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("food_submit_button"),
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
            ) {
              Text(
                text = "Xác nhận đặt đồ ăn (DEMO) • ${formatVnd(quote.totalVnd)}",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ShoppingScreen(
  uiState: FoxUiState,
  onUpdateCart: (productId: String, delta: Int) -> Unit,
  onOrderCreated: () -> Unit,
  onSubmitShopping: (address: String, voucherCode: String?, paymentMethod: String, onSuccess: () -> Unit) -> Unit,
  onBack: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Tất cả") }
  var shippingAddress by remember { mutableStateOf("123 Nguyễn Huệ, Quận 1, TP.HCM") }
  var applyVoucher by remember { mutableStateOf(true) }
  var paymentMethod by remember {
    mutableStateOf(
      if (uiState.backendStatus.isProductionBackendConnected) "Tiền mặt DEMO" else "Ví FOX DEMO",
    )
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val categories = listOf("Tất cả", "Thực phẩm", "Gia dụng", "Điện tử", "Thời trang", "Làm đẹp")
  val filteredProducts =
    uiState.shopProducts.filter { product ->
      val matchesCat = selectedCategory == "Tất cả" || product.category == selectedCategory
      val q = searchQuery.trim().lowercase(Locale.ROOT)
      val matchesQuery = q.isEmpty() ||
        product.name.lowercase(Locale.ROOT).contains(q) ||
        product.storeName.lowercase(Locale.ROOT).contains(q)
      matchesCat && matchesQuery
    }

  val cartCount = uiState.shopCart.values.sum()
  val cartSubtotal =
    uiState.shopCart.entries.sumOf { (id, qty) ->
      (uiState.shopProducts.firstOrNull { it.id == id }?.priceVnd ?: 0L) * qty
    }
  val activeVoucher = if (applyVoucher) "FOXSHOP20" else null
  val quote = FoxDemoPricingEngine.estimateShopping(cartSubtotal, activeVoucher)

  ServiceScaffold(
    title = "FOX Mua sắm",
    serviceType = FoxServiceType.SHOPPING,
    onBack = onBack,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          DemoModeNotice(
            "Marketplace DEMO lưu đơn vào Room SQLite. Kiểm soát tồn kho nguyên tử đa người dùng sẽ kết nối trên backend."
          )
        }

        item {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm sản phẩm hoặc gian hàng...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = FoxBlue) },
            modifier = Modifier.fillMaxWidth().testTag("shop_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
          )
        }

        item {
          OutlinedTextField(
            value = shippingAddress,
            onValueChange = { shippingAddress = it; errorText = null },
            label = { Text("Địa chỉ nhận hàng *") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = FoxOrange) },
            modifier = Modifier.fillMaxWidth().testTag("shop_address_input"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
          )
          if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }

        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
              FilterChip(
                selected = selectedCategory == cat,
                onClick = { selectedCategory = cat },
                label = { Text(cat) },
              )
            }
          }
        }

        items(filteredProducts, key = { it.id }) { product ->
          val qty = uiState.shopCart[product.id] ?: 0
          Card(
            shape = RoundedCornerShape(18.dp),
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
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${product.storeName} • Tồn mẫu: ${product.stock}",
                  style = MaterialTheme.typography.labelMedium,
                  color = FoxOrangeDeep,
                )
                Text(
                  text = product.name,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Star, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "${product.rating} • ${formatVnd(product.priceVnd)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = FoxBlue,
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                if (qty > 0) {
                  IconButton(
                    onClick = { onUpdateCart(product.id, -1) },
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant),
                  ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Giảm số lượng", tint = FoxTextStrong)
                  }
                  Text(
                    text = qty.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                    modifier = Modifier.padding(horizontal = 10.dp),
                  )
                }
                IconButton(
                  onClick = { onUpdateCart(product.id, 1) },
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FoxOrange)
                    .testTag("shop_add_${product.id}"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = "Thêm vào giỏ", tint = Color.White)
                }
              }
            }
          }
        }

        if (cartCount > 0) {
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = FoxOrange.copy(alpha = 0.08f)),
              border = BorderStroke(1.dp, FoxOrange.copy(alpha = 0.3f)),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = FoxOrangeDeep)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    "Giỏ hàng mua sắm ($cartCount sản phẩm)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FoxTextStrong,
                  )
                }
                uiState.shopCart.forEach { (id, qty) ->
                  val product = uiState.shopProducts.firstOrNull { it.id == id }
                  if (product != null) {
                    SummaryLine("${qty}x ${product.name}", formatVnd(product.priceVnd * qty))
                  }
                }
              }
            }
          }

          item {
            PaymentAndVoucherCard(
              voucherCode = "FOXSHOP20",
              voucherDescription = "Giảm 20.000đ cho đơn từ 150.000đ • Freeship từ 300K",
              applyVoucher = applyVoucher,
              onToggleVoucher = { applyVoucher = it },
              paymentMethod = paymentMethod,
              onSelectPayment = { paymentMethod = it },
              demoWalletBalanceVnd = uiState.demoWalletBalanceVnd,
              isCloudMode = uiState.backendStatus.isProductionBackendConnected,
            )
          }

          item {
            PriceSummaryCard(
              quote = quote,
              subtotalLabel = "Tiền hàng ($cartCount sản phẩm)",
              feeLabel = "Phí vận chuyển DEMO",
            )
          }

          item {
            Button(
              enabled = !uiState.isSubmitting,
              onClick = {
                if (shippingAddress.isBlank()) {
                  errorText = "Vui lòng nhập địa chỉ nhận hàng."
                } else {
                  onSubmitShopping(shippingAddress.trim(), activeVoucher, paymentMethod, onOrderCreated)
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("shop_submit_button"),
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
            ) {
              Text(
                text = "Xác nhận mua sắm (DEMO) • ${formatVnd(quote.totalVnd)}",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}
