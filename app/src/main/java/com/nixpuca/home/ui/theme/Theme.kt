package com.nixpuca.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nixpuca.home.ui.NixPalette

/**
 * NIX PUCA's official black-and-white identity, matching the console
 * dashboard: black ink on white paper with grey structure. Toggling the
 * dark theme in Settings swaps to the inverted family with the selected
 * accent tinting the primary slot.
 */
private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFEFEF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE4E4E4),
    error = Color(0xFFB00020),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A21),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFC9C9D2),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF22222A),
    onSecondaryContainer = Color(0xFFEDEDF2),
    background = Color(0xFF101014),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1A1A21),
    onSurfaceVariant = Color(0xFF9A9AA6),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF2C2C35),
    error = Color(0xFFFF6B7A),
    onError = Color.Black,
)

private val NixTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun NixHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (NixPalette.dark) DarkColors else LightColors,
        typography = NixTypography,
        content = content,
    )
}
