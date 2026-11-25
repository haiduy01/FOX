package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = FoxBlue,
    onPrimary = Color.White,
    primaryContainer = FoxBlueSoft,
    onPrimaryContainer = FoxBlueDark,
    secondary = FoxOrange,
    onSecondary = Color.White,
    secondaryContainer = FoxOrangeSoft,
    onSecondaryContainer = FoxOrangeDeep,
    background = FoxBackground,
    onBackground = FoxTextDark,
    surface = FoxSurface,
    onSurface = FoxTextDark,
    surfaceVariant = Color(0xFFEAF0F8),
    onSurfaceVariant = FoxTextSecondary,
    outline = FoxLightGray,
    error = FoxRed,
  )

val FoxShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/**
 * FOX brand theme enforces high-contrast light surface tokens so brand pastel cards,
 * Royal Blue (#1453FF), and FOX Orange (#FF7448) remain crisp regardless of device theme.
 */
@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    shapes = FoxShapes,
    content = content,
  )
}
