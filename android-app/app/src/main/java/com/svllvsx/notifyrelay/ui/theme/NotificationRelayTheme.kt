package com.svllvsx.notifyrelay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RelayShape {
    val cardSmall = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(28.dp)
    val hero = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(999.dp)
}

private val RelayDarkColors = darkColorScheme(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF0A2E6F),
    primaryContainer = Color(0xFF274777),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFB8DCC4),
    onTertiary = Color(0xFF23362A),
    tertiaryContainer = Color(0xFF354D3D),
    onTertiaryContainer = Color(0xFFD4F7DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF424753),
    onSurfaceVariant = Color(0xFFC2C6D0),
    outline = Color(0xFF8C919C),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
)

private val RelayLightColors = lightColorScheme(
    primary = Color(0xFF3F5F90),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565F71),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF4E654F),
    tertiaryContainer = Color(0xFFD0EBCF),
    background = Color(0xFFFAF9FD),
    surface = Color(0xFFFAF9FD),
    surfaceContainer = Color(0xFFEFEFF4),
    surfaceContainerHigh = Color(0xFFE9E9EF),
    surfaceContainerHighest = Color(0xFFE3E4EA),
)

private val RelayTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun NotificationRelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = if (darkTheme) RelayDarkColors else RelayLightColors,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RelayTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content,
    )
}
