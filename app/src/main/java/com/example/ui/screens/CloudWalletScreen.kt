package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.DEMO_CURRENCY_NOTICE
import com.example.data.remote.canCustomerCancelOrder
import com.example.data.remote.cloudPaymentStatusLabel
import com.example.data.remote.cloudWalletTxTypeLabel
import com.example.ui.FoxCloudUiState
import com.example.ui.WalletOperationStage
import com.example.ui.formatOrderTime
import com.example.ui.formatVnd
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxBlueDark
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

/**
 * Cloud DEMO Wallet Screen backed exclusively by Firebase Cloud Functions v2 + read-only Firestore ledger.
 * Strictly displays "DEMO — KHÔNG PHẢI TIỀN THẬT" and never connects real banking rails.
 */
@Composable
fun CloudWalletScreen(
  cloudState: FoxCloudUiState,
  onTopUpDemo: (Long) -> Unit,
  onPayOrderWithDemoWallet: (String) -> Unit,
  onRefundOrderDemo: (String) -> Unit,
  onRefreshWallet: () -> Unit,
  onOpenOfflineDemo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val wallet = cloudState.cloudWallet
  val unpaidActiveOrders = remember(cloudState.customerOrders) {
    cloudState.customerOrders.filter {
      it.status != "cancelled" &&
        it.status != "completed" &&
        it.paymentStatus != "paid_demo" &&
        it.paymentStatus != "refunded_demo"
    }
  }
  val refundableOrders = remember(cloudState.customerOrders) {
    cloudState.customerOrders.filter {
      it.paymentStatus == "paid_demo" &&
        it.refundStatus != "refunded_demo" &&
        canCustomerCancelOrder(it)
    }
  }

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
      // 1. Branded FOX DEMO Wallet Header + Mandatory DEMO Notice
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF1F63FF), FoxBlue, FoxBlueDark)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.AccountBalanceWallet,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Ví FOX Cloud DEMO",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                  color = Color.White,
                )
              }
              IconButton(
                onClick = onRefreshWallet,
                enabled = !cloudState.isBusy,
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.16f))
                  .testTag("cloud_wallet_refresh_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = "Đồng bộ số dư từ Cloud Functions",
                  tint = Color.White,
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(50),
              color = FoxOrange,
            ) {
              Text(
                text = DEMO_CURRENCY_NOTICE,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                modifier = Modifier
                  .padding(horizontal = 12.dp, vertical = 5.dp)
                  .testTag("cloud_wallet_demo_badge"),
              )
            }

            Column {
              Text(
                text = "Số dư Ví DEMO (số nguyên VND • đồng bộ từ backend)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = formatVnd(wallet.balanceVnd),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                modifier = Modifier.testTag("cloud_wallet_balance_text"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              WalletMetricPill(
                label = "Đã nạp DEMO",
                value = formatVnd(wallet.totalTopUpVnd),
                modifier = Modifier.weight(1f),
              )
              WalletMetricPill(
                label = "Đã chi tiêu",
                value = formatVnd(wallet.totalSpentVnd),
                modifier = Modifier.weight(1f),
              )
              WalletMetricPill(
                label = "Hoa hồng/Hoàn",
                value = formatVnd(wallet.totalEarnedVnd + wallet.totalRefundedVnd),
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      // 2. Transaction Operation Status Banner (Pending / Success / Failure)
      if (cloudState.walletActionStatus.stage != WalletOperationStage.IDLE) {
        item {
          val status = cloudState.walletActionStatus
          val (bg, border, fg, label) = when (status.stage) {
            WalletOperationStage.PENDING ->
              QuadStyle(FoxBlue.copy(alpha = 0.08f), FoxBlue.copy(alpha = 0.35f), FoxBlue, "ĐANG XỬ LÝ")
            WalletOperationStage.SUCCESS ->
              QuadStyle(FoxGreen.copy(alpha = 0.10f), FoxGreen.copy(alpha = 0.40f), FoxGreen, "THÀNH CÔNG")
            WalletOperationStage.FAILURE ->
              QuadStyle(FoxRed.copy(alpha = 0.09f), FoxRed.copy(alpha = 0.40f), FoxRed, "THẤT BẠI")
            WalletOperationStage.IDLE ->
              QuadStyle(Color.White, MaterialTheme.colorScheme.outline, FoxTextStrong, "")
          }
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp)
              .testTag("cloud_wallet_status_banner"),
            shape = RoundedCornerShape(16.dp),
            color = bg,
            border = BorderStroke(1.dp, border),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              when (status.stage) {
                WalletOperationStage.PENDING -> CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.5.dp,
                  color = fg,
                )
                WalletOperationStage.SUCCESS -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = fg)
                WalletOperationStage.FAILURE -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = fg)
                WalletOperationStage.IDLE -> Unit
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg)
                Text(status.message, style = MaterialTheme.typography.bodySmall, color = FoxTextStrong)
              }
            }
          }
        }
      }

      // 3. Controlled DEMO Top-Up Panel
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = FoxBlue)
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Nạp tiền DEMO có kiểm soát",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = "Xử lý qua Cloud Function topUpDemoWallet (Transaction + Idempotency). Không liên kết ngân hàng thật.",
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(50_000L, 200_000L, 500_000L).forEach { amount ->
                Button(
                  onClick = { onTopUpDemo(amount) },
                  enabled = !cloudState.isBusy,
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("cloud_wallet_topup_$amount"),
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                  contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                  Text(
                    text = "+${amount / 1_000}K",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                  )
                }
              }
            }
          }
        }
      }

      // 4. Unpaid Cloud Orders -> Pay with Ví FOX DEMO
      if (unpaidActiveOrders.isNotEmpty()) {
        item {
          Text(
            text = "Đơn chờ thanh toán Ví DEMO (${unpaidActiveOrders.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
          )
        }
        items(unpaidActiveOrders, key = { "unpaid_${it.id}" }) { order ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 5.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("${order.orderCode} • ${order.title}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                Text(
                  text = "${formatVnd(order.totalVnd)} • ${cloudPaymentStatusLabel(order.paymentStatus, order.financialStatus)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = { onPayOrderWithDemoWallet(order.id) },
                enabled = !cloudState.isBusy,
                modifier = Modifier.height(44.dp).testTag("wallet_pay_order_${order.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = FoxOrange),
              ) {
                Text("Trả Ví", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 5. Eligible Paid Orders -> Cancel & Refund DEMO
      if (refundableOrders.isNotEmpty()) {
        item {
          Text(
            text = "Đơn đã thanh toán có thể hủy & hoàn tiền DEMO (${refundableOrders.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
          )
        }
        items(refundableOrders, key = { "ref_${it.id}" }) { order ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 5.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("${order.orderCode} • ${order.title}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                Text("Đã thanh toán: ${formatVnd(order.totalVnd)}", style = MaterialTheme.typography.bodySmall, color = FoxGreen)
              }
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = { onRefundOrderDemo(order.id) },
                enabled = !cloudState.isBusy,
                modifier = Modifier.height(44.dp).testTag("wallet_refund_order_${order.id}"),
                border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.55f)),
              ) {
                Text("Hoàn DEMO", color = FoxRed, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 6. Immutable Server Ledger History
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Lịch sử sổ cái Ví DEMO (${cloudState.cloudWalletTransactions.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = "Chỉ Cloud Functions được ghi sổ cái",
            tint = FoxBlue,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (cloudState.cloudWalletTransactions.isEmpty()) {
        item {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Chưa có giao dịch Ví FOX Cloud DEMO nào.",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = FoxTextStrong,
              )
              Text(
                text = "Bấm nút nạp DEMO ở trên sau khi triển khai Cloud Functions v2 để tạo bản ghi sổ cái an toàn. Client bị khóa hoàn toàn quyền ghi trực tiếp vào /wallets.",
                style = MaterialTheme.typography.bodySmall,
                color = FoxTextSecondary,
              )
            }
          }
        }
      } else {
        items(cloudState.cloudWalletTransactions, key = { it.txId.ifBlank { it.id } }) { tx ->
          val isCredit = tx.amountVnd >= 0L
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 5.dp)
              .testTag("cloud_wallet_tx_${tx.txId.ifBlank { tx.id }}"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(if (isCredit) FoxGreen.copy(alpha = 0.14f) else FoxRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (isCredit) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                  contentDescription = null,
                  tint = if (isCredit) FoxGreen else FoxRed,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = tx.title.ifBlank { cloudWalletTxTypeLabel(tx.txType) },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = "${cloudWalletTxTypeLabel(tx.txType)} • ${formatOrderTime(tx.effectiveTimeMillis())}",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxTextSecondary,
                )
                Text(
                  text = "Số dư sau GD: ${formatVnd(tx.balanceAfterVnd)} • Trạng thái: ${tx.status}",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxBlue,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = (if (isCredit) "+" else "-") + formatVnd(kotlin.math.abs(tx.amountVnd)),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isCredit) FoxGreen else FoxRed,
              )
            }
          }
        }
      }

      // 7. Isolated Offline Room SQLite DEMO Mode Shortcut
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Chế độ DEMO ngoại tuyến (Room SQLite riêng)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxTextStrong,
            )
            Text(
              text = "Dữ liệu Room SQLite ngoại tuyến hoàn toàn tách biệt với Ví Cloud và không tự đồng bộ lên Firebase.",
              style = MaterialTheme.typography.bodySmall,
              color = FoxTextSecondary,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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

@Composable
private fun WalletMetricPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = Color.White.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.80f))
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
      )
    }
  }
}

private data class QuadStyle(
  val bg: Color,
  val border: Color,
  val fg: Color,
  val label: String,
)
