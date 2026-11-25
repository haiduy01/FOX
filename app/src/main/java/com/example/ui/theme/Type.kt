package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val FoxDisplayFontFamily = FontFamily(
  Font(R.font.be_vietnam_pro, FontWeight.Normal),
  Font(R.font.be_vietnam_pro, FontWeight.SemiBold),
  Font(R.font.be_vietnam_pro, FontWeight.Bold),
  Font(R.font.be_vietnam_pro, FontWeight.ExtraBold),
)

val FoxBodyFontFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val Typography =
  Typography(
    displaySmall = TextStyle(
      fontFamily = FoxDisplayFontFamily,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 30.sp,
      lineHeight = 36.sp,
      letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
      fontFamily = FoxDisplayFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 24.sp,
      lineHeight = 30.sp,
      letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
      fontFamily = FoxDisplayFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 20.sp,
      lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
      fontFamily = FoxDisplayFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
      fontFamily = FoxDisplayFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 16.sp,
      lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 15.sp,
      lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 13.sp,
      lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
      lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 12.sp,
      lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
      fontFamily = FoxBodyFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      lineHeight = 14.sp,
    ),
  )
