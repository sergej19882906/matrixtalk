package com.matrix.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    primary = MatrixGreenDark,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    primaryContainer = Color(0xFFE8FCEF),
    onPrimaryContainer = Color(0xFF002106)
)

@Composable
fun MatrixMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
