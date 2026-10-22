package com.berkbb.otaghome

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun primaryColoursConvertBothWays() {
        mapOf(
            0xFFFF0000.toInt() to Hsv(0f, 1f, 1f),
            0xFF00FF00.toInt() to Hsv(120f, 1f, 1f),
            0xFF0000FF.toInt() to Hsv(240f, 1f, 1f)
        ).forEach { (rgb, hsv) ->
            assertEquals(hsv, ColorMath.toHsv(rgb))
            assertEquals(rgb, ColorMath.toRgb(hsv))
        }
    }

    @Test
    fun everyColourSurvivesARoundTripThroughHsv() {
        listOf(0xFF7C4DFF, 0xFF1A1033, 0xFFE53935, 0xFF00ADB5, 0xFF808080, 0xFF000000, 0xFFFFFFFF).forEach {
            val color = it.toInt()
            assertEquals(HexColor.format(color), HexColor.format(ColorMath.toRgb(ColorMath.toHsv(color))))
        }
    }

    @Test
    fun greysKeepTheSpectrumWhereItWas() {
        assertEquals(200f, ColorMath.toHsv(0xFF808080.toInt(), keepHue = 200f).hue)
    }

    @Test
    fun channelsAreReadAndClamped() {
        val color = ColorMath.rgb(124, 77, 255)
        assertEquals(Triple(124, 77, 255), Triple(ColorMath.red(color), ColorMath.green(color), ColorMath.blue(color)))
        assertEquals(0xFFFF0000.toInt(), ColorMath.rgb(300, -5, 0))
    }

    @Test
    fun aPickedAccentReplacesTheThemesAndTintsTheFocusedCard() {
        val orange = 0xFFFF9800.toInt()
        val dark = Palette.of(ThemeMode.DARK, systemIsDark = false, colors = mapOf(ColorRole.ACCENT to orange))
        assertEquals(Color(orange), dark.accent)
        assertNotEquals(Palette.Dark.cardFocused, dark.cardFocused)
        assertEquals(Palette.Dark, Palette.of(ThemeMode.DARK, systemIsDark = false))
    }

    @Test
    fun everyThemeColourCanBeReplaced() {
        val picked = ColorRole.entries.mapIndexed { i, role -> role to ColorMath.rgb(10 * i, 20, 30) }.toMap()
        val palette = Palette.Light.withOverrides(picked)
        ColorRole.entries.forEach { role -> assertEquals(role.name, Color(picked.getValue(role)), role.of(palette)) }
        // The focused card's tint is recomputed from the new card and accent
        assertNotEquals(Palette.Light.cardFocused, palette.cardFocused)
    }

    @Test
    fun onlyTheAccentIsSharedBetweenThemes() {
        assertEquals("accent", ColorRole.ACCENT.prefKey(isDark = true))
        assertEquals("accent", ColorRole.ACCENT.prefKey(isDark = false))
        assertEquals("color_dark_card", ColorRole.CARD.prefKey(isDark = true))
        assertEquals("color_light_text_secondary", ColorRole.TEXT_SECONDARY.prefKey(isDark = false))
    }
}
