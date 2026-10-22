package com.berkbb.otaghome

import kotlin.math.abs
import kotlin.math.roundToInt

/** A colour as hue (0–360), saturation (0–1) and brightness (0–1), the colour picker's own model. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** Colour conversions for the picker, in plain Kotlin so they are unit-testable off the device. */
object ColorMath {

    fun red(color: Int): Int = color shr 16 and 0xFF
    fun green(color: Int): Int = color shr 8 and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

    fun toRgb(hsv: Hsv): Int {
        val h = ((hsv.hue % 360f) + 360f) % 360f
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** [keepHue] is used for greys, which have no hue of their own, so the spectrum does not jump. */
    fun toHsv(color: Int, keepHue: Float = 0f): Hsv {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> keepHue
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { (it + 360f) % 360f }
        val saturation = if (max == 0f) 0f else delta / max
        return Hsv(hue, saturation, max)
    }
}
