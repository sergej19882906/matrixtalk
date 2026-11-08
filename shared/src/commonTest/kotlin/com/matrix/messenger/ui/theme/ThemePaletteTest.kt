package com.matrix.messenger.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ThemePaletteTest {
    @Test
    fun fromStoredIsCaseInsensitiveAndFallsBackToDefault() {
        assertSame(ThemePalette.DRACULA, ThemePalette.fromStored("dracula"))
        assertSame(ThemePalette.CATPPUCCIN, ThemePalette.fromStored("Catppuccin"))
        assertSame(ThemePalette.DEFAULT, ThemePalette.fromStored(null))
        assertSame(ThemePalette.DEFAULT, ThemePalette.fromStored("unknown-garbage"))
    }

    @Test
    fun everyPaletteHasDistinctDarkAndLightVariants() {
        ThemePalette.entries.filter { it != ThemePalette.DEFAULT }.forEach { palette ->
            val dark = palette.colors(dark = true)
            val light = palette.colors(dark = false)
            assertNotEquals(dark.background, light.background, palette.name)
            // Primary must contrast with its own background in each variant
            // (some palettes like Solarized use the same primary in both).
            assertNotEquals(dark.background, dark.primary, palette.name)
            assertNotEquals(light.background, light.primary, palette.name)
        }
    }
}
