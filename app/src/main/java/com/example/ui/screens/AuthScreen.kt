package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.FoxBrandMark
import com.example.ui.components.FoxMascotIllustration
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxBlueDark
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong

@Composable
fun AuthScreen(
  isLoading: Boolean,
  errorMessage: String?,
  onGoogleSignInClick: () -> Unit,
  onEnterOfflineDemoClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.statusBars)
      .windowInsetsPadding(WindowInsets.navigationBars),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 560.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Hero Brand Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(FoxBlue, FoxBlueDark)))
            .padding(22.dp),
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FoxBrandMark(onDarkHeader = true)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Chạm là tới!",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = FoxOrange,
            )
            Spacer(modifier = Modifier.height(14.dp))
            FoxMascotIllustration(
              contentDesc = stringResource(R.string.cd_fox_mascot),
              size = 112.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Siêu ứng dụng đa vai trò kết nối Cloud Firestore",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Đặt xe • Giao hàng • Đặt đồ ăn • Mua sắm\nĐồng bộ đơn hàng thời gian thực giữa các thiết bị",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.88f),
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      // Sign-In Action Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.CloudDone,
              contentDescription = null,
              tint = FoxBlue,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Đăng nhập / Đăng ký Cloud FOX",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }

          Text(
            text = "Xác thực an toàn bằng tài khoản Google (Firebase Auth). Hồ sơ mới mặc định là Khách hàng; vai trò Tài xế & Gian hàng cần Admin duyệt trên Firestore Rules.",
            style = MaterialTheme.typography.bodySmall,
            color = FoxTextSecondary,
          )

          if (errorMessage != null) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = FoxRed.copy(alpha = 0.1f),
              border = BorderStroke(1.dp, FoxRed.copy(alpha = 0.35f)),
            ) {
              Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = FoxRed,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = onGoogleSignInClick,
            enabled = !isLoading,
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("google_sign_in_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = FoxBlue,
              contentColor = Color.White,
            ),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Đang xác thực Google...", fontWeight = FontWeight.Bold)
            } else {
              Icon(imageVector = Icons.Rounded.Login, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Tiếp tục với Google", fontWeight = FontWeight.Bold)
            }
          }

          OutlinedButton(
            onClick = onEnterOfflineDemoClick,
            enabled = !isLoading,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("offline_demo_mode_button"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, FoxOrange),
          ) {
            Icon(
              imageVector = Icons.Rounded.PhoneAndroid,
              contentDescription = null,
              tint = FoxOrangeDeep,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Dùng chế độ DEMO cục bộ (Room SQLite)",
              color = FoxOrangeDeep,
              fontWeight = FontWeight.Bold,
            )
          }

          Text(
            text = "Chế độ DEMO cục bộ tách biệt hoàn toàn với Cloud Firestore, không tự đồng bộ dữ liệu giả lên máy chủ.",
            style = MaterialTheme.typography.labelSmall,
            color = FoxTextSecondary,
          )
        }
      }

      // 4-Role Architecture Summary
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.Shield, contentDescription = null, tint = FoxBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Hệ thống 4 vai trò FOX (Zero-Trust RBAC)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxTextStrong,
            )
          }
          RoleBullet(Icons.Rounded.Person, "Khách hàng (customer)", "Đặt 4 dịch vụ, theo dõi trạng thái & hủy đơn chờ.")
          RoleBullet(Icons.Rounded.DirectionsBike, "Tài xế (driver)", "Đăng ký hồ sơ, nhận đơn qua Transaction chống trùng.")
          RoleBullet(Icons.Rounded.Storefront, "Gian hàng (merchant)", "Quản lý sản phẩm riêng, xác nhận & giao tài xế.")
          RoleBullet(Icons.Rounded.AdminPanelSettings, "Quản trị (admin)", "Duyệt đối tác & giám sát đơn hàng (chặn tự nâng quyền).")
        }
      }
    }
  }
}

@Composable
private fun RoleBullet(icon: ImageVector, title: String, subtitle: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(imageVector = icon, contentDescription = null, tint = FoxBlue, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = FoxTextStrong,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = FoxTextSecondary,
      )
    }
  }
}
