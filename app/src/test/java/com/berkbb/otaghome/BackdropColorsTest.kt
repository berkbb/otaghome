package com.berkbb.otaghome

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropColorsTest {

    private val accent = 0xFF7C4DFF.toInt()
    private val white = Color(0xFFFFFFFF)
    private val dark = Color(0xFF111318)

    @Test
    fun contrastFollowsWcag() {
        assertEquals(21.0, BackdropColors.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, BackdropColors.contrast(accent, accent), 0.001)
    }

    @Test
    fun darkBackgroundsGetLightContentAndKeepTheAccent() {
        val colors = BackdropColors.from(listOf(0xFF111318.toInt()), accent)
        assertEquals(white, colors.content)
        assertEquals(Color(accent), colors.accent)
    }

    @Test
    fun lightBackgroundsGetDarkContent() {
        assertEquals(dark, BackdropColors.from(listOf(0xFFF2F3F7.toInt()), accent).content)
    }

    @Test
    fun accentGivesWayWhereItWouldNotShow() {
        // A purple gradient: the purple frame would vanish into it, so it becomes the content colour
        val colors = BackdropColors.from(listOf(0xFF6A3DF0.toInt(), 0xFF7C4DFF.toInt()), accent)
        assertEquals(colors.content, colors.accent)
        assertTrue(BackdropColors.contrast(colors.content.toArgb(), 0xFF7C4DFF.toInt()) >= 3.0)
    }

    @Test
    fun blendLaysTheVeilOverTheColour() {
        assertEquals(0xFF808080.toInt(), BackdropColors.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(0xFF000000.toInt(), BackdropColors.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
    }

    @Test
    fun pictureAverageIgnoresTransparentPixels() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x00FF0000)
        assertEquals(0xFF7F7F7F.toInt(), ArtColor.average(pixels))
    }
}
