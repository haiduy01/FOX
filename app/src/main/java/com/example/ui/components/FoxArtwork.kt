package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FoxServiceType
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxBlueDark
import com.example.ui.theme.FoxFoodAccent
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxShoppingAccent
import com.example.ui.theme.FoxTextDark

@Composable
fun FoxBrandMark(
  modifier: Modifier = Modifier,
  onDarkHeader: Boolean = true,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(
          Brush.linearGradient(
            colors = listOf(Color(0xFFFF8F68), FoxOrange, FoxOrangeDeep)
          )
        ),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val head = Path().apply {
          moveTo(w * 0.14f, h * 0.12f)
          lineTo(w * 0.38f, h * 0.34f)
          lineTo(w * 0.62f, h * 0.34f)
          lineTo(w * 0.86f, h * 0.12f)
          lineTo(w * 0.90f, h * 0.56f)
          lineTo(w * 0.50f, h * 0.90f)
          lineTo(w * 0.10f, h * 0.56f)
          close()
        }
        drawPath(path = head, color = Color.White)

        val leftEye = Offset(w * 0.37f, h * 0.53f)
        val rightEye = Offset(w * 0.63f, h * 0.53f)
        drawCircle(color = FoxBlueDark, radius = w * 0.06f, center = leftEye)
        drawCircle(color = FoxBlueDark, radius = w * 0.06f, center = rightEye)

        val nose = Path().apply {
          moveTo(w * 0.43f, h * 0.72f)
          lineTo(w * 0.57f, h * 0.72f)
          lineTo(w * 0.50f, h * 0.82f)
          close()
        }
        drawPath(path = nose, color = FoxOrangeDeep)
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = "FOX",
      style = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
      ),
      color = if (onDarkHeader) Color.White else FoxBlue,
    )
  }
}

/**
 * 3D-shaded FOX Mascot illustration wearing a Royal Blue FOX uniform.
 */
@Composable
fun FoxMascotIllustration(
  contentDesc: String,
  modifier: Modifier = Modifier,
  size: Dp = 94.dp,
) {
  Canvas(
    modifier = modifier
      .size(size)
      .semantics { contentDescription = contentDesc }
  ) {
    val w = this.size.width
    val h = this.size.height

    // Soft ambient radial glow behind mascot
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
        center = Offset(w * 0.5f, h * 0.48f),
        radius = w * 0.5f,
      ),
      radius = w * 0.5f,
      center = Offset(w * 0.5f, h * 0.48f),
    )

    // Royal Blue FOX Uniform Shoulders / Torso
    val uniformPath = Path().apply {
      moveTo(w * 0.20f, h * 0.96f)
      quadraticTo(w * 0.24f, h * 0.72f, w * 0.50f, h * 0.72f)
      quadraticTo(w * 0.76f, h * 0.72f, w * 0.80f, h * 0.96f)
      close()
    }
    drawPath(
      path = uniformPath,
      brush = Brush.linearGradient(
        colors = listOf(Color(0xFF3976FF), FoxBlue, FoxBlueDark),
        start = Offset(w * 0.2f, h * 0.72f),
        end = Offset(w * 0.8f, h * 0.98f),
      ),
    )
    // White collar V-trim on uniform
    val collarPath = Path().apply {
      moveTo(w * 0.38f, h * 0.74f)
      lineTo(w * 0.50f, h * 0.85f)
      lineTo(w * 0.62f, h * 0.74f)
    }
    drawPath(
      path = collarPath,
      color = Color.White,
      style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
    )

    // Left & Right Pointy Ears (3D orange gradient)
    val leftEar = Path().apply {
      moveTo(w * 0.22f, h * 0.44f)
      lineTo(w * 0.20f, h * 0.14f)
      lineTo(w * 0.45f, h * 0.30f)
      close()
    }
    val rightEar = Path().apply {
      moveTo(w * 0.78f, h * 0.44f)
      lineTo(w * 0.80f, h * 0.14f)
      lineTo(w * 0.55f, h * 0.30f)
      close()
    }
    val earBrush = Brush.linearGradient(
      colors = listOf(Color(0xFFFF9A73), FoxOrange, FoxOrangeDeep),
      start = Offset(0f, h * 0.12f),
      end = Offset(w, h * 0.55f),
    )
    drawPath(leftEar, brush = earBrush)
    drawPath(rightEar, brush = earBrush)

    // Warm cream inner ears
    val leftInnerEar = Path().apply {
      moveTo(w * 0.25f, h * 0.38f)
      lineTo(w * 0.25f, h * 0.21f)
      lineTo(w * 0.39f, h * 0.31f)
      close()
    }
    val rightInnerEar = Path().apply {
      moveTo(w * 0.75f, h * 0.38f)
      lineTo(w * 0.75f, h * 0.21f)
      lineTo(w * 0.61f, h * 0.31f)
      close()
    }
    drawPath(leftInnerEar, color = Color(0xFFFFD8C7))
    drawPath(rightInnerEar, color = Color(0xFFFFD8C7))

    // Main Fox Head (3D orange spherical gradient)
    val headPath = Path().apply {
      moveTo(w * 0.18f, h * 0.48f)
      quadraticTo(w * 0.18f, h * 0.24f, w * 0.50f, h * 0.24f)
      quadraticTo(w * 0.82f, h * 0.24f, w * 0.82f, h * 0.48f)
      quadraticTo(w * 0.82f, h * 0.66f, w * 0.50f, h * 0.75f)
      quadraticTo(w * 0.18f, h * 0.66f, w * 0.18f, h * 0.48f)
      close()
    }
    drawPath(
      path = headPath,
      brush = Brush.radialGradient(
        colors = listOf(Color(0xFFFFA582), FoxOrange, FoxOrangeDeep),
        center = Offset(w * 0.42f, h * 0.36f),
        radius = w * 0.48f,
      ),
    )

    // Soft white furry cheeks
    val leftCheek = Path().apply {
      moveTo(w * 0.18f, h * 0.50f)
      quadraticTo(w * 0.34f, h * 0.50f, w * 0.50f, h * 0.66f)
      lineTo(w * 0.50f, h * 0.75f)
      quadraticTo(w * 0.22f, h * 0.67f, w * 0.18f, h * 0.50f)
      close()
    }
    val rightCheek = Path().apply {
      moveTo(w * 0.82f, h * 0.50f)
      quadraticTo(w * 0.66f, h * 0.50f, w * 0.50f, h * 0.66f)
      lineTo(w * 0.50f, h * 0.75f)
      quadraticTo(w * 0.78f, h * 0.67f, w * 0.82f, h * 0.50f)
      close()
    }
    drawPath(leftCheek, color = Color(0xFFFFFBF8))
    drawPath(rightCheek, color = Color(0xFFFFFBF8))

    // Expressive glossy eyes
    val leftEyeCenter = Offset(w * 0.38f, h * 0.48f)
    val rightEyeCenter = Offset(w * 0.62f, h * 0.48f)
    drawCircle(color = FoxTextDark, radius = w * 0.055f, center = leftEyeCenter)
    drawCircle(color = FoxTextDark, radius = w * 0.055f, center = rightEyeCenter)
    // Specular eye catchlights
    drawCircle(
      color = Color.White,
      radius = w * 0.02f,
      center = Offset(leftEyeCenter.x - w * 0.015f, leftEyeCenter.y - h * 0.018f),
    )
    drawCircle(
      color = Color.White,
      radius = w * 0.02f,
      center = Offset(rightEyeCenter.x - w * 0.015f, rightEyeCenter.y - h * 0.018f),
    )

    // Friendly nose & smile
    val nosePath = Path().apply {
      moveTo(w * 0.45f, h * 0.59f)
      lineTo(w * 0.55f, h * 0.59f)
      lineTo(w * 0.50f, h * 0.65f)
      close()
    }
    drawPath(nosePath, color = FoxTextDark)

    val smile = Path().apply {
      moveTo(w * 0.43f, h * 0.67f)
      quadraticTo(w * 0.50f, h * 0.72f, w * 0.57f, h * 0.67f)
    }
    drawPath(
      path = smile,
      color = FoxTextDark,
      style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
    )
  }
}

/**
 * Dimensional 3D-inspired Compose Canvas illustrations for the 4 core FOX services.
 */
@Composable
fun FoxServiceIllustration(
  serviceType: FoxServiceType,
  modifier: Modifier = Modifier,
  size: Dp = 56.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height

    // Soft ground shadow
    drawOval(
      color = Color.Black.copy(alpha = 0.08f),
      topLeft = Offset(w * 0.14f, h * 0.80f),
      size = Size(w * 0.72f, h * 0.14f),
    )

    when (serviceType) {
      FoxServiceType.RIDE -> {
        // Modern 3D-shaded FOX Car
        val roof = Path().apply {
          moveTo(w * 0.24f, h * 0.46f)
          lineTo(w * 0.35f, h * 0.24f)
          lineTo(w * 0.66f, h * 0.24f)
          lineTo(w * 0.78f, h * 0.46f)
          close()
        }
        drawPath(
          roof,
          brush = Brush.linearGradient(
            listOf(Color(0xFF4D88FF), FoxBlue),
            start = Offset(0f, h * 0.2f),
            end = Offset(w, h * 0.5f),
          ),
        )
        // Windshield glass
        val glass = Path().apply {
          moveTo(w * 0.30f, h * 0.44f)
          lineTo(w * 0.38f, h * 0.29f)
          lineTo(w * 0.62f, h * 0.29f)
          lineTo(w * 0.70f, h * 0.44f)
          close()
        }
        drawPath(glass, color = Color(0xFFD9EBFF))
        // Car Body
        drawRoundRect(
          brush = Brush.linearGradient(
            listOf(Color(0xFF2B67FF), FoxBlue, FoxBlueDark),
            start = Offset(0f, h * 0.44f),
            end = Offset(w, h * 0.76f),
          ),
          topLeft = Offset(w * 0.10f, h * 0.44f),
          size = Size(w * 0.80f, h * 0.28f),
          cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )
        // Orange FOX stripe & headlight
        drawRoundRect(
          color = FoxOrange,
          topLeft = Offset(w * 0.76f, h * 0.52f),
          size = Size(w * 0.11f, h * 0.08f),
          cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
          color = Color.White.copy(alpha = 0.85f),
          topLeft = Offset(w * 0.15f, h * 0.52f),
          size = Size(w * 0.12f, h * 0.08f),
          cornerRadius = CornerRadius(4f, 4f),
        )
        // Wheels
        drawCircle(color = FoxTextDark, radius = w * 0.11f, center = Offset(w * 0.28f, h * 0.72f))
        drawCircle(color = FoxTextDark, radius = w * 0.11f, center = Offset(w * 0.72f, h * 0.72f))
        drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.28f, h * 0.72f))
        drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.72f))
      }

      FoxServiceType.DELIVERY -> {
        // 3D Isometric FOX Delivery Box
        val topFace = Path().apply {
          moveTo(w * 0.50f, h * 0.14f)
          lineTo(w * 0.86f, h * 0.30f)
          lineTo(w * 0.50f, h * 0.46f)
          lineTo(w * 0.14f, h * 0.30f)
          close()
        }
        val leftFace = Path().apply {
          moveTo(w * 0.14f, h * 0.30f)
          lineTo(w * 0.50f, h * 0.46f)
          lineTo(w * 0.50f, h * 0.82f)
          lineTo(w * 0.14f, h * 0.66f)
          close()
        }
        val rightFace = Path().apply {
          moveTo(w * 0.86f, h * 0.30f)
          lineTo(w * 0.50f, h * 0.46f)
          lineTo(w * 0.50f, h * 0.82f)
          lineTo(w * 0.86f, h * 0.66f)
          close()
        }
        drawPath(topFace, color = Color(0xFFFFA988))
        drawPath(leftFace, color = FoxOrange)
        drawPath(rightFace, color = FoxOrangeDeep)
        // Royal Blue packing tape ribbon across top
        val tape = Path().apply {
          moveTo(w * 0.34f, h * 0.21f)
          lineTo(w * 0.44f, h * 0.17f)
          lineTo(w * 0.74f, h * 0.35f)
          lineTo(w * 0.64f, h * 0.40f)
          close()
        }
        drawPath(tape, color = FoxBlue)
        // Shipping label on left face
        drawRoundRect(
          color = Color.White,
          topLeft = Offset(w * 0.21f, h * 0.49f),
          size = Size(w * 0.19f, h * 0.14f),
          cornerRadius = CornerRadius(4f, 4f),
        )
      }

      FoxServiceType.FOOD -> {
        // Steaming 3D Noodle / Gourmet Bowl
        val steam1 = Path().apply {
          moveTo(w * 0.36f, h * 0.30f)
          quadraticTo(w * 0.30f, h * 0.20f, w * 0.38f, h * 0.12f)
        }
        val steam2 = Path().apply {
          moveTo(w * 0.52f, h * 0.28f)
          quadraticTo(w * 0.46f, h * 0.18f, w * 0.54f, h * 0.10f)
        }
        val steam3 = Path().apply {
          moveTo(w * 0.66f, h * 0.30f)
          quadraticTo(w * 0.60f, h * 0.20f, w * 0.68f, h * 0.12f)
        }
        drawPath(steam1, color = FoxOrange.copy(alpha = 0.75f), style = Stroke(w * 0.045f, cap = StrokeCap.Round))
        drawPath(steam2, color = FoxFoodAccent.copy(alpha = 0.75f), style = Stroke(w * 0.045f, cap = StrokeCap.Round))
        drawPath(steam3, color = FoxOrange.copy(alpha = 0.75f), style = Stroke(w * 0.045f, cap = StrokeCap.Round))

        // Broth & garnishes
        drawOval(
          color = Color(0xFFFFC971),
          topLeft = Offset(w * 0.14f, h * 0.34f),
          size = Size(w * 0.72f, h * 0.22f),
        )
        drawCircle(color = Color(0xFF10B981), radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.44f))
        drawCircle(color = FoxOrange, radius = w * 0.065f, center = Offset(w * 0.60f, h * 0.43f))

        // Deep Bowl
        val bowl = Path().apply {
          moveTo(w * 0.12f, h * 0.45f)
          quadraticTo(w * 0.14f, h * 0.80f, w * 0.50f, h * 0.80f)
          quadraticTo(w * 0.86f, h * 0.80f, w * 0.88f, h * 0.45f)
          close()
        }
        drawPath(
          bowl,
          brush = Brush.linearGradient(
            listOf(Color(0xFFFF5C8A), FoxFoodAccent, Color(0xFFB51E4D)),
            start = Offset(w * 0.15f, h * 0.45f),
            end = Offset(w * 0.85f, h * 0.82f),
          ),
        )
        // White rim highlight
        drawLine(
          color = Color.White.copy(alpha = 0.6f),
          start = Offset(w * 0.22f, h * 0.54f),
          end = Offset(w * 0.78f, h * 0.54f),
          strokeWidth = w * 0.035f,
          cap = StrokeCap.Round,
        )
      }

      FoxServiceType.SHOPPING -> {
        // 3D FOX Orange Shopping Bag with Purple Accent Tag
        val handle = Path().apply {
          moveTo(w * 0.34f, h * 0.34f)
          quadraticTo(w * 0.34f, h * 0.14f, w * 0.50f, h * 0.14f)
          quadraticTo(w * 0.66f, h * 0.14f, w * 0.66f, h * 0.34f)
        }
        drawPath(
          handle,
          color = FoxShoppingAccent,
          style = Stroke(width = w * 0.065f, cap = StrokeCap.Round),
        )
        // Bag body
        drawRoundRect(
          brush = Brush.linearGradient(
            listOf(Color(0xFFFF9670), FoxOrange, FoxOrangeDeep),
            start = Offset(w * 0.16f, h * 0.30f),
            end = Offset(w * 0.84f, h * 0.82f),
          ),
          topLeft = Offset(w * 0.18f, h * 0.32f),
          size = Size(w * 0.64f, h * 0.48f),
          cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )
        // Side depth fold
        drawRoundRect(
          color = Color.White.copy(alpha = 0.22f),
          topLeft = Offset(w * 0.22f, h * 0.38f),
          size = Size(w * 0.14f, h * 0.34f),
          cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
        // Royal Blue price tag badge
        drawCircle(
          color = FoxShoppingAccent,
          radius = w * 0.13f,
          center = Offset(w * 0.70f, h * 0.64f),
        )
        drawCircle(
          color = Color.White,
          radius = w * 0.05f,
          center = Offset(w * 0.70f, h * 0.64f),
        )
      }
    }
  }
}
