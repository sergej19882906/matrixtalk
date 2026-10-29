package com.matrix.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MatrixGreen,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = MatrixDarkGray,
    surface = MatrixDarkGray,
    onPrimary = MatrixBlack,
    onBackground = MatrixLightGray,
    onSurface = MatrixLightGray,
    surfaceVariant = Color(0xFF2A2D31),
    primaryContainer = Color(0xFF0A4D38),
    onPrimaryContainer = MatrixGreen
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B8A66),
    secondary = Color(0xFF5A655F),
    tertiary = Color(0xFF8A4A62),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFF7F9F8),
    onPrimary = Color.White,
    onBackground = Color(0xFF191C1A),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DF),
    primaryContainer = Color(0xFFA7F2D6),
    onPrimaryContainer = Color(0xFF002117)
)

@Composable
fun MatrixMessengerTheme(
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val mode = themeMode ?: ThemeManager.mode.collectAsState().value
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
