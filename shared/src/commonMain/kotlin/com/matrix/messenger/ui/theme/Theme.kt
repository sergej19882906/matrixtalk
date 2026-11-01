package com.matrix.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    primaryContainer = ElementDarkPrimaryContainer,
    onPrimaryContainer = MatrixGreen,
    secondary = ElementDarkOnSurfaceVariant,
    onSecondary = MatrixBlack,
    secondaryContainer = ElementDarkSecondaryContainer,
    onSecondaryContainer = ElementDarkOnSurface,
    tertiary = MatrixGreen,
    background = ElementDarkBackground,
    onBackground = ElementDarkOnSurface,
    surface = ElementDarkBackground,
    onSurface = ElementDarkOnSurface,
    surfaceVariant = ElementDarkSurface,
    onSurfaceVariant = ElementDarkOnSurfaceVariant,
    surfaceContainerLowest = ElementDarkSurfaceLowest,
    surfaceContainerLow = ElementDarkSurfaceLow,
    surfaceContainer = ElementDarkSurface,
    surfaceContainerHigh = ElementDarkSurfaceHigh,
    surfaceContainerHighest = ElementDarkSurfaceHighest,
    surfaceTint = Color.Transparent,
    inverseSurface = ElementDarkOnSurface,
    inverseOnSurface = ElementDarkBackground,
    inversePrimary = MatrixGreenDark,
    outline = ElementDarkOutline,
    outlineVariant = ElementDarkOutlineVariant,
    error = ElementDarkError,
    onError = MatrixBlack,
    errorContainer = ElementDarkErrorContainer,
    onErrorContainer = ElementDarkOnSurface,
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = ElementLightPrimary,
    onPrimary = Color.White,
    primaryContainer = ElementLightPrimaryContainer,
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF5A655F),
    onSecondary = Color.White,
    secondaryContainer = ElementLightSecondaryContainer,
    onSecondaryContainer = ElementLightOnSurface,
    tertiary = Color(0xFF8A4A62),
    background = ElementLightBackground,
    onBackground = ElementLightOnSurface,
    surface = ElementLightBackground,
    onSurface = ElementLightOnSurface,
    surfaceVariant = ElementLightSurface,
    onSurfaceVariant = ElementLightOnSurfaceVariant,
    surfaceContainerLowest = ElementLightSurfaceLowest,
    surfaceContainerLow = ElementLightSurfaceLow,
    surfaceContainer = ElementLightSurface,
    surfaceContainerHigh = ElementLightSurfaceHigh,
    surfaceContainerHighest = ElementLightSurfaceHighest,
    surfaceTint = Color.Transparent,
    inverseSurface = ElementLightOnSurface,
    inverseOnSurface = ElementLightBackground,
    inversePrimary = MatrixGreen,
    outline = ElementLightOutline,
    outlineVariant = ElementLightOutlineVariant,
    error = ElementLightError,
    onError = Color.White,
    errorContainer = ElementLightErrorContainer,
    onErrorContainer = Color(0xFF4A0010),
    scrim = Color(0xFF000000)
)

// Element X corners: moderately rounded everywhere
private val ElementShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
        shapes = ElementShapes,
        content = content
    )
}
