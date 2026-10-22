package com.berkbb.otaghome

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Colours for things drawn straight on the background (card focus frames, status lines), picked
 * by contrast against the background's colours so they stay visible on any colour, gradient or
 * picture. Everything else sits on cards and uses the theme [Palette].
 */
@Immutable
data class BackdropColors(
    /** Text and icons on the background. */
    val content: Color,
    val contentSecondary: Color,
    /** Focus and move frames: the theme accent, or [content] where the accent would not show. */
    val accent: Color
) {
    companion object {
        private const val LIGHT = 0xFFFFFFFF.toInt()
        private const val DARK = 0xFF111318.toInt()
        /** WCAG's minimum for large text and UI parts such as frames. */
        private const val MIN_ACCENT_CONTRAST = 3.0

        /**
         * [samples] are the background's colours (one for a solid colour, both ends of a gradient,
         * the average of a picture); the choice has to work against all of them.
         */
        fun from(samples: List<Int>, accent: Int): BackdropColors {
            val content = if (prefersLightContent(samples)) LIGHT else DARK
            val accentShows = minContrast(accent, samples) >= MIN_ACCENT_CONTRAST
            return BackdropColors(
                content = Color(content),
                contentSecondary = Color(content).copy(alpha = 0.75f),
                accent = Color(if (accentShows) accent else content)
            )
        }

        /** Whether white beats dark text on all of [samples]; how "Match background" picks the theme. */
        fun prefersLightContent(samples: List<Int>): Boolean = minContrast(LIGHT, samples) >= minContrast(DARK, samples)

        private fun minContrast(color: Int, samples: List<Int>): Double =
            samples.minOfOrNull { contrast(color, it) } ?: Double.MAX_VALUE

        /** WCAG relative luminance of an opaque ARGB colour. */
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val c = (color shr shift and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        /** WCAG contrast ratio, 1 (none) to 21 (black on white). */
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** [top] laid over [bottom] with [alpha] (0–1), as the veil over pictures is drawn. */
        fun blend(bottom: Int, top: Int, alpha: Float): Int {
            fun mix(shift: Int): Int {
                val b = bottom shr shift and 0xFF
                val t = top shr shift and 0xFF
                return (b + (t - b) * alpha + 0.5f).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}

val LocalBackdrop = staticCompositionLocalOf {
    BackdropColors(content = Color.White, contentSecondary = Color.White.copy(alpha = 0.75f), accent = Color(0xFF7C4DFF))
}
