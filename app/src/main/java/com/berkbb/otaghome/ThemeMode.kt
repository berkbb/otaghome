package com.berkbb.otaghome

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Theme choice cycled by the Theme row: Auto follows the TV's night mode, Match background follows
 * how dark the chosen background is.
 */
enum class ThemeMode(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BACKGROUND(R.string.theme_background);

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    /** [backgroundIsDark] is null while it is not known yet (a picture still loading); the TV decides then. */
    fun isDark(systemIsDark: Boolean, backgroundIsDark: Boolean? = null): Boolean = when (this) {
        SYSTEM -> systemIsDark
        DARK -> true
        LIGHT -> false
        BACKGROUND -> backgroundIsDark ?: systemIsDark
    }

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * The theme colours the user can change in Settings. The accent is shared by both themes; the
 * others are kept per theme, so a colour chosen for the dark theme cannot vanish on the light one.
 */
enum class ColorRole(@param:StringRes val labelRes: Int, val perTheme: Boolean = true) {
    ACCENT(R.string.settings_accent, perTheme = false),
    CARD(R.string.color_card),
    TEXT(R.string.color_text),
    TEXT_SECONDARY(R.string.color_text_secondary),
    BACKGROUND(R.string.color_background),
    FOCUS(R.string.color_focus),
    ON_FOCUS(R.string.color_on_focus);

    /** The preferences key; "accent" is the key earlier versions used for the accent. */
    fun prefKey(isDark: Boolean): String =
        if (!perTheme) "accent" else "color_${if (isDark) "dark" else "light"}_${name.lowercase()}"

    fun of(palette: Palette): Color = when (this) {
        ACCENT -> palette.accent
        CARD -> palette.card
        TEXT -> palette.textPrimary
        TEXT_SECONDARY -> palette.textSecondary
        BACKGROUND -> palette.background
        FOCUS -> palette.focus
        ON_FOCUS -> palette.onFocus
    }
}

/** Colors of one theme. The dark palette is Pitwall's; the light one mirrors it on a pale ground. */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    /** A focused card: the card tinted with the accent, as M3 surface tones do. */
    val cardFocused: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Fill of a focused button; its text uses [onFocus]. */
    val focus: Color,
    val onFocus: Color,
    val isDark: Boolean
) {
    /** The same theme with another accent; the focused card's tint follows it. */
    fun withAccent(accent: Int): Palette = copy(
        accent = Color(accent),
        cardFocused = Color(BackdropColors.blend(card.toArgb(), accent, if (isDark) 0.14f else 0.10f))
    )

    /** The user's colours (ARGB) laid over the theme's; the focused card's tint follows card and accent. */
    fun withOverrides(colors: Map<ColorRole, Int>): Palette {
        if (colors.isEmpty()) return this
        fun pick(role: ColorRole, own: Color) = colors[role]?.let { Color(it) } ?: own
        val custom = copy(
            card = pick(ColorRole.CARD, card),
            textPrimary = pick(ColorRole.TEXT, textPrimary),
            textSecondary = pick(ColorRole.TEXT_SECONDARY, textSecondary),
            background = pick(ColorRole.BACKGROUND, background),
            focus = pick(ColorRole.FOCUS, focus),
            onFocus = pick(ColorRole.ON_FOCUS, onFocus)
        )
        val tintNeeded = ColorRole.CARD in colors || ColorRole.ACCENT in colors
        return if (tintNeeded) custom.withAccent(colors[ColorRole.ACCENT] ?: accent.toArgb()) else custom
    }

    companion object {
        val Dark = Palette(
            background = Color(0xFF111318),
            card = Color(0xFF1C1F26),
            cardFocused = Color(0xFF29253A),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF949498),
            accent = Color(0xFF7C4DFF),
            focus = Color(0xFFE0E0E0),
            onFocus = Color(0xFF111318),
            isDark = true
        )
        val Light = Palette(
            background = Color(0xFFF2F3F7),
            card = Color(0xFFFFFFFF),
            cardFocused = Color(0xFFEFEAFF),
            textPrimary = Color(0xFF111318),
            textSecondary = Color(0xFF5E6068),
            accent = Color(0xFF6A3DF0),
            focus = Color(0xFF111318),
            onFocus = Color(0xFFFFFFFF),
            isDark = false
        )

        /** The theme's palette with the colours the user picked for it ([colors], ARGB) laid over. */
        fun of(
            mode: ThemeMode,
            systemIsDark: Boolean,
            colors: Map<ColorRole, Int> = emptyMap(),
            backgroundIsDark: Boolean? = null
        ): Palette = (if (mode.isDark(systemIsDark, backgroundIsDark)) Dark else Light).withOverrides(colors)
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.Dark }

/**
 * Material 3's shape scale, kept to four steps: small for chips, medium for buttons and rows,
 * large for app cards, extra large for dialogs and the settings sheet.
 */
object Corners {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(28.dp)
}
