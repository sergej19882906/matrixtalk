package com.matrix.messenger.ui.theme

import androidx.compose.ui.graphics.Color

/** Key colors of one palette variant (dark or light), taken from its Element theme. */
data class PaletteColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

/**
 * Color palettes ported from aaronraimist/element-themes (Nord, Dracula,
 * Catppuccin, Gruvbox, Solarized, Monokai Pro, Everforest). Every palette
 * defines both a dark and a light variant; [DEFAULT] keeps the base
 * Element-styled schemes from Theme.kt.
 */
enum class ThemePalette(val displayName: String) {
    DEFAULT("Element"),
    NORD("Nord"),
    DRACULA("Dracula"),
    CATPPUCCIN("Catppuccin"),
    GRUVBOX("Gruvbox"),
    SOLARIZED("Solarized"),
    MONOKAI("Monokai Pro"),
    EVERFOREST("Everforest");

    /** Preview colors for the settings picker. */
    val preview: List<Color>
        get() = when (this) {
            DEFAULT -> listOf(Color(0xFF0FA57F), Color(0xFF0FA57F).copy(alpha = 0.6f), Color(0xFF8A4A62))
            NORD -> listOf(nordDark.primary, nordDark.secondary, nordDark.tertiary)
            DRACULA -> listOf(draculaDark.primary, draculaDark.secondary, draculaDark.tertiary)
            CATPPUCCIN -> listOf(catppuccinMocha.primary, catppuccinMocha.secondary, catppuccinMocha.tertiary)
            GRUVBOX -> listOf(gruvboxDark.primary, gruvboxDark.secondary, gruvboxDark.tertiary)
            SOLARIZED -> listOf(solarizedDark.primary, solarizedDark.secondary, solarizedDark.tertiary)
            MONOKAI -> listOf(monokaiDark.primary, monokaiDark.secondary, monokaiDark.tertiary)
            EVERFOREST -> listOf(everforestDark.primary, everforestDark.secondary, everforestDark.tertiary)
        }

    /** Variant colors for the requested darkness. */
    fun colors(dark: Boolean): PaletteColors = when (this) {
        DEFAULT -> throw UnsupportedOperationException("DEFAULT keeps the base scheme")
        NORD -> if (dark) nordDark else nordLight
        DRACULA -> if (dark) draculaDark else draculaLight
        CATPPUCCIN -> if (dark) catppuccinMocha else catppuccinLatte
        GRUVBOX -> if (dark) gruvboxDark else gruvboxLight
        SOLARIZED -> if (dark) solarizedDark else solarizedLight
        MONOKAI -> if (dark) monokaiDark else monokaiLight
        EVERFOREST -> if (dark) everforestDark else everforestLight
    }

    companion object {
        fun fromStored(raw: String?): ThemePalette =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

private val nordDark = PaletteColors(
    primary = Color(0xFF88C0D0), onPrimary = Color(0xFF2E3440),
    secondary = Color(0xFF81A1C1), tertiary = Color(0xFFB48EAD),
    background = Color(0xFF2E3440), onBackground = Color(0xFFECEFF4),
    surfaceLow = Color(0xFF333947), surface = Color(0xFF3B4252), surfaceHigh = Color(0xFF434C5E),
    surfaceVariant = Color(0xFF4C566A), onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF69758C),
)

private val nordLight = PaletteColors(
    primary = Color(0xFF5E81AC), onPrimary = Color(0xFFECEFF4),
    secondary = Color(0xFF81A1C1), tertiary = Color(0xFFB48EAD),
    background = Color(0xFFECEFF4), onBackground = Color(0xFF2E3440),
    surfaceLow = Color(0xFFE5E9F0), surface = Color(0xFFE5E9F0), surfaceHigh = Color(0xFFD8DEE9),
    surfaceVariant = Color(0xFFD8DEE9), onSurfaceVariant = Color(0xFF3B4252),
    outline = Color(0xFF9BA6B5),
)

private val draculaDark = PaletteColors(
    primary = Color(0xFFBD93F9), onPrimary = Color(0xFF282A36),
    secondary = Color(0xFFFF79C6), tertiary = Color(0xFF8BE9FD),
    background = Color(0xFF282A36), onBackground = Color(0xFFF8F8F2),
    surfaceLow = Color(0xFF2E3040), surface = Color(0xFF343746), surfaceHigh = Color(0xFF424450),
    surfaceVariant = Color(0xFF44475A), onSurfaceVariant = Color(0xFFF8F8F2),
    outline = Color(0xFF6272A4),
)

private val draculaLight = PaletteColors(
    primary = Color(0xFF8A63D2), onPrimary = Color(0xFFFDFDF8),
    secondary = Color(0xFFD6519F), tertiary = Color(0xFF2E9EA9),
    background = Color(0xFFFDFDF8), onBackground = Color(0xFF282A36),
    surfaceLow = Color(0xFFF4F4EC), surface = Color(0xFFF4F4EC), surfaceHigh = Color(0xFFE6E6DC),
    surfaceVariant = Color(0xFFE6E6DC), onSurfaceVariant = Color(0xFF424450),
    outline = Color(0xFF9A9AB5),
)

private val catppuccinMocha = PaletteColors(
    primary = Color(0xFFCBA6F7), onPrimary = Color(0xFF1E1E2E),
    secondary = Color(0xFFF5C2E7), tertiary = Color(0xFF94E2D5),
    background = Color(0xFF1E1E2E), onBackground = Color(0xFFCDD6F4),
    surfaceLow = Color(0xFF262637), surface = Color(0xFF313244), surfaceHigh = Color(0xFF3C3D5A),
    surfaceVariant = Color(0xFF313244), onSurfaceVariant = Color(0xFFBAC2DE),
    outline = Color(0xFF585B70),
)

private val catppuccinLatte = PaletteColors(
    primary = Color(0xFF8839EF), onPrimary = Color(0xFFEFF1F5),
    secondary = Color(0xFFEA76CB), tertiary = Color(0xFF179299),
    background = Color(0xFFEFF1F5), onBackground = Color(0xFF4C4F69),
    surfaceLow = Color(0xFFE6E9EF), surface = Color(0xFFE6E9EF), surfaceHigh = Color(0xFFCCD0DA),
    surfaceVariant = Color(0xFFCCD0DA), onSurfaceVariant = Color(0xFF5C5F77),
    outline = Color(0xFF9CA0B0),
)

private val gruvboxDark = PaletteColors(
    primary = Color(0xFF83A598), onPrimary = Color(0xFF282828),
    secondary = Color(0xFFB8BB26), tertiary = Color(0xFFFE8019),
    background = Color(0xFF282828), onBackground = Color(0xFFEBDBB2),
    surfaceLow = Color(0xFF32302F), surface = Color(0xFF3C3836), surfaceHigh = Color(0xFF504945),
    surfaceVariant = Color(0xFF504945), onSurfaceVariant = Color(0xFFD5C4A1),
    outline = Color(0xFF665C54),
)

private val gruvboxLight = PaletteColors(
    primary = Color(0xFF427B58), onPrimary = Color(0xFFFBF1C7),
    secondary = Color(0xFF79740E), tertiary = Color(0xFFAF3A03),
    background = Color(0xFFFBF1C7), onBackground = Color(0xFF3C3836),
    surfaceLow = Color(0xFFEBDBB2), surface = Color(0xFFEBDBB2), surfaceHigh = Color(0xFFD5C4A1),
    surfaceVariant = Color(0xFFD5C4A1), onSurfaceVariant = Color(0xFF504945),
    outline = Color(0xFFA89984),
)

private val solarizedDark = PaletteColors(
    primary = Color(0xFF268BD2), onPrimary = Color(0xFF002B36),
    secondary = Color(0xFF2AA198), tertiary = Color(0xFFB58900),
    background = Color(0xFF002B36), onBackground = Color(0xFFEEE8D5),
    surfaceLow = Color(0xFF043543), surface = Color(0xFF073642), surfaceHigh = Color(0xFF0E4855),
    surfaceVariant = Color(0xFF0E4855), onSurfaceVariant = Color(0xFF93A1A1),
    outline = Color(0xFF586E75),
)

private val solarizedLight = PaletteColors(
    primary = Color(0xFF268BD2), onPrimary = Color(0xFFFDF6E3),
    secondary = Color(0xFF2AA198), tertiary = Color(0xFFCB4B16),
    background = Color(0xFFFDF6E3), onBackground = Color(0xFF073642),
    surfaceLow = Color(0xFFEEE8D5), surface = Color(0xFFEEE8D5), surfaceHigh = Color(0xFFDFDCBE),
    surfaceVariant = Color(0xFFEEE8D5), onSurfaceVariant = Color(0xFF657B83),
    outline = Color(0xFF93A1A1),
)

private val monokaiDark = PaletteColors(
    primary = Color(0xFFFF6188), onPrimary = Color(0xFF2D2A2E),
    secondary = Color(0xFFA9DC76), tertiary = Color(0xFFFC9867),
    background = Color(0xFF2D2A2E), onBackground = Color(0xFFFCFCFA),
    surfaceLow = Color(0xFF363237), surface = Color(0xFF3E3A40), surfaceHigh = Color(0xFF4A454D),
    surfaceVariant = Color(0xFF4A454D), onSurfaceVariant = Color(0xFFE6E6E1),
    outline = Color(0xFF727072),
)

private val monokaiLight = PaletteColors(
    primary = Color(0xFFD81B60), onPrimary = Color(0xFFFAFAF7),
    secondary = Color(0xFF648A18), tertiary = Color(0xFFC05A1F),
    background = Color(0xFFFAFAF7), onBackground = Color(0xFF2D2A2E),
    surfaceLow = Color(0xFFF0EFEB), surface = Color(0xFFF0EFEB), surfaceHigh = Color(0xFFE3E2DB),
    surfaceVariant = Color(0xFFE3E2DB), onSurfaceVariant = Color(0xFF57534F),
    outline = Color(0xFFA5A29B),
)

private val everforestDark = PaletteColors(
    primary = Color(0xFFA7C080), onPrimary = Color(0xFF2D353B),
    secondary = Color(0xFF7FBBB3), tertiary = Color(0xFFD699B6),
    background = Color(0xFF2D353B), onBackground = Color(0xFFD3C6AA),
    surfaceLow = Color(0xFF34424A), surface = Color(0xFF3D4855), surfaceHigh = Color(0xFF475258),
    surfaceVariant = Color(0xFF3D4855), onSurfaceVariant = Color(0xFFA7B0A2),
    outline = Color(0xFF4A5560),
)

private val everforestLight = PaletteColors(
    primary = Color(0xFF8DA101), onPrimary = Color(0xFFFDF6E3),
    secondary = Color(0xFF35A77C), tertiary = Color(0xFFD699B6),
    background = Color(0xFFFDF6E3), onBackground = Color(0xFF5C6A72),
    surfaceLow = Color(0xFFF4F0D9), surface = Color(0xFFF4F0D9), surfaceHigh = Color(0xFFE5E1CB),
    surfaceVariant = Color(0xFFE5E1CB), onSurfaceVariant = Color(0xFF6B7B84),
    outline = Color(0xFFA8A48E),
)
