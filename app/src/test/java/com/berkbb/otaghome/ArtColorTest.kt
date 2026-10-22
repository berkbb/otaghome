package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtColorTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun ignoresTransparentWhiteAndBlackPixels() {
        val red = argb(255, 200, 30, 40)
        val pixels = intArrayOf(red, red, argb(0, 10, 200, 10), argb(255, 250, 250, 250), argb(255, 5, 5, 5))
        assertEquals(red, ArtColor.dominant(pixels))
    }

    @Test
    fun averagesTheLogoColours() {
        val pixels = intArrayOf(argb(255, 100, 40, 200), argb(255, 200, 60, 100))
        assertEquals(argb(255, 150, 50, 150), ArtColor.dominant(pixels))
    }

    @Test
    fun noColourWhenOnlyBackgroundIsLeft() {
        assertNull(ArtColor.dominant(intArrayOf(argb(255, 255, 255, 255), argb(0, 90, 90, 90))))
        assertNull(ArtColor.dominant(IntArray(0)))
    }
}
