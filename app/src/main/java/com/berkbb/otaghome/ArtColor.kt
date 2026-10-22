package com.berkbb.otaghome

/** Colours read from pictures: an app logo's own colour, or a background picture's average. */
object ArtColor {

    /**
     * Average of the logo's opaque pixels, leaving out near-white and near-black ones (backgrounds
     * and outlines), so the result is the logo's own colour. Null when nothing is left.
     */
    fun dominant(pixels: IntArray): Int? {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (p in pixels) {
            val a = p ushr 24 and 0xFF
            if (a < 200) continue
            val pr = p shr 16 and 0xFF
            val pg = p shr 8 and 0xFF
            val pb = p and 0xFF
            if (pr > 235 && pg > 235 && pb > 235) continue
            if (pr < 20 && pg < 20 && pb < 20) continue
            r += pr; g += pg; b += pb; count++
        }
        if (count == 0L) return null
        return (0xFF shl 24) or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }

    /** Plain average of a picture's opaque pixels, for judging text contrast over it. */
    fun average(pixels: IntArray): Int? {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (p in pixels) {
            if (p ushr 24 and 0xFF < 128) continue
            r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; count++
        }
        if (count == 0L) return null
        return (0xFF shl 24) or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }
}
