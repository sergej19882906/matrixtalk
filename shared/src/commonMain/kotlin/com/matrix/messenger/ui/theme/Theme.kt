package com.matrix.messenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun MatrixMessengerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
