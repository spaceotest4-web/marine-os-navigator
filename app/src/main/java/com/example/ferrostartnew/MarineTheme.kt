package com.example.ferrostartnew

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Marine OS look: always light (never follows system dark mode), ocean-blue
 * primary, soft slate surfaces - matches the web app's palette.
 */

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0369A1), // ocean blue
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF0C4A6E),
        secondary = Color(0xFF0284C7),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1F5F9),
        onSecondaryContainer = Color(0xFF334155),
        tertiary = Color(0xFF059669),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD1FAE5),
        onTertiaryContainer = Color(0xFF064E3B),
        background = Color(0xFFF6F9FC),
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
    )

private val MarineTypography =
    Typography().let { base ->
      base.copy(
          headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
          headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
          titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      )
    }

@Composable
fun MarineTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = LightColors, typography = MarineTypography, content = content)
}
