package com.berkbb.otaghome

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Hex colour codes as typed by the user: #RGB, #RRGGBB or #AARRGGBB, with or without '#'. */
object HexColor {

    fun parse(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.isEmpty() || hex.any { it.digitToIntOrNull(16) == null }) return null
        val argb = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return argb.toLong(16).toInt()
    }

    /** #RRGGBB for opaque colours, #AARRGGBB otherwise. */
    fun format(color: Int): String {
        val alpha = color ushr 24
        return if (alpha == 0xFF) "#%06X".format(color and 0xFFFFFF) else "#%08X".format(color)
    }
}

enum class GradientDirection(@param:StringRes val labelRes: Int) {
    VERTICAL(R.string.dir_vertical),
    HORIZONTAL(R.string.dir_horizontal),
    DIAGONAL(R.string.dir_diagonal);

    fun next(): GradientDirection = entries[(ordinal + 1) % entries.size]
}

/**
 * How strongly a picture is veiled with the theme's ground so cards and text stand out: the full
 * veil, half of it, or the picture as it is. Solid colours and gradients are never veiled.
 */
enum class VeilLevel(@param:StringRes val labelRes: Int, val strength: Float) {
    NORMAL(R.string.veil_normal, 1f),
    LIGHT(R.string.veil_light, 0.5f),
    OFF(R.string.veil_off, 0f);

    fun next(): VeilLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): VeilLevel = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** Sections of the ready-made pictures page. */
enum class PresetGroup(@param:StringRes val labelRes: Int) {
    ABSTRACT(R.string.bg_presets_otag),
    TURKEY(R.string.bg_presets_turkey)
}

/**
 * The pictures that ship with OtagHome, all small vector drawings. [samples] are colours taken from
 * each (top, middle, bottom), used to choose readable colours for what is drawn over it.
 */
enum class PresetImage(
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int,
    val samples: List<Int>,
    val group: PresetGroup = PresetGroup.ABSTRACT
) {
    OTAG(R.drawable.bg_default, R.string.preset_otag, listOf(0xFF1B1236, 0xFF0B0D12, 0xFF3B2A78).map { it.toInt() }),
    AURORA(R.drawable.bg_aurora, R.string.preset_aurora, listOf(0xFF040816, 0xFF1C6B5E, 0xFF060A14).map { it.toInt() }),
    SUNSET(R.drawable.bg_sunset, R.string.preset_sunset, listOf(0xFF1E1046, 0xFFD9485F, 0xFF200B27).map { it.toInt() }),
    CIRCUIT(R.drawable.bg_circuit, R.string.preset_circuit, listOf(0xFF0B0D12, 0xFF1C212B, 0xFF2B2150).map { it.toInt() }),
    OCEAN(R.drawable.bg_ocean, R.string.preset_ocean, listOf(0xFF0A4766, 0xFF04263B, 0xFF010B14).map { it.toInt() }),
    CITY(R.drawable.bg_city, R.string.preset_city, listOf(0xFF0A0F2C, 0xFF2A1B4D, 0xFF0D0C1C).map { it.toInt() }),
    DUNES(R.drawable.bg_dunes, R.string.preset_dunes, listOf(0xFF3B1C4A, 0xFFF08A5D, 0xFF5C2219).map { it.toInt() }),
    FOREST(R.drawable.bg_forest, R.string.preset_forest, listOf(0xFFC9DCD3, 0xFF4E7A70, 0xFF1A332F).map { it.toInt() }),
    NEBULA(R.drawable.bg_nebula, R.string.preset_nebula, listOf(0xFF07041A, 0xFF5A1C8A, 0xFF02010A).map { it.toInt() }),
    TR_CAPPADOCIA(R.drawable.bg_tr_cappadocia, R.string.preset_tr_cappadocia, listOf(0xFF5B507D, 0xFFB17D8E, 0xFFAB6F57).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ISTANBUL(R.drawable.bg_tr_istanbul, R.string.preset_tr_istanbul, listOf(0xFF493663, 0xFF734257, 0xFF292147).map { it.toInt() }, PresetGroup.TURKEY),
    TR_PAMUKKALE(R.drawable.bg_tr_pamukkale, R.string.preset_tr_pamukkale, listOf(0xFF9CC8EB, 0xFFADCCCB, 0xFFAFDCE4).map { it.toInt() }, PresetGroup.TURKEY),
    TR_OLUDENIZ(R.drawable.bg_tr_oludeniz, R.string.preset_tr_oludeniz, listOf(0xFF73B0D7, 0xFF579694, 0xFF3F919B).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ARARAT(R.drawable.bg_tr_ararat, R.string.preset_tr_ararat, listOf(0xFF375C8E, 0xFF728DB5, 0xFF6D7764).map { it.toInt() }, PresetGroup.TURKEY),
    TR_NEMRUT(R.drawable.bg_tr_nemrut, R.string.preset_tr_nemrut, listOf(0xFF572C4A, 0xFF995254, 0xFF51372C).map { it.toInt() }, PresetGroup.TURKEY),
    TR_UZUNGOL(R.drawable.bg_tr_uzungol, R.string.preset_tr_uzungol, listOf(0xFFC7D9D2, 0xFF89A799, 0xFF4E776F).map { it.toInt() }, PresetGroup.TURKEY),
    TR_EPHESUS(R.drawable.bg_tr_ephesus, R.string.preset_tr_ephesus, listOf(0xFF8C5568, 0xFFC78E6B, 0xFFB88F6E).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ANITKABIR(R.drawable.bg_tr_anitkabir, R.string.preset_tr_anitkabir, listOf(0xFF242F59, 0xFF686A87, 0xFFA59689).map { it.toInt() }, PresetGroup.TURKEY),
    TR_KAS(R.drawable.bg_tr_kas, R.string.preset_tr_kas, listOf(0xFF644A71, 0xFFAE6B71, 0xFF453F5F).map { it.toInt() }, PresetGroup.TURKEY),
    TR_HACI_BAYRAM(R.drawable.bg_tr_haci_bayram, R.string.preset_tr_haci_bayram, listOf(0xFF40385A, 0xFF845C6A, 0xFF6B5753).map { it.toInt() }, PresetGroup.TURKEY),
    TR_IZMIR(R.drawable.bg_tr_izmir, R.string.preset_tr_izmir, listOf(0xFF7F4B6C, 0xFFDD9F7C, 0xFF644750).map { it.toInt() }, PresetGroup.TURKEY),
    TR_SIVAS(R.drawable.bg_tr_sivas, R.string.preset_tr_sivas, listOf(0xFF405682, 0xFF8896AF, 0xFFD1CEC9).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ANI(R.drawable.bg_tr_ani, R.string.preset_tr_ani, listOf(0xFF7B6472, 0xFFC2916E, 0xFF897447).map { it.toInt() }, PresetGroup.TURKEY),
    TR_MARDIN(R.drawable.bg_tr_mardin, R.string.preset_tr_mardin, listOf(0xFF8A7B7E, 0xFFDDB687, 0xFFBC9A70).map { it.toInt() }, PresetGroup.TURKEY),
    TR_SELIMIYE(R.drawable.bg_tr_selimiye, R.string.preset_tr_selimiye, listOf(0xFF5875A4, 0xFFA7B6C8, 0xFF5D795E).map { it.toInt() }, PresetGroup.TURKEY),
    TR_MAIDENS_TOWER(R.drawable.bg_tr_maidens_tower, R.string.preset_tr_maidens_tower, listOf(0xFF242D5A, 0xFF865E6D, 0xFF28294C).map { it.toInt() }, PresetGroup.TURKEY),
    TR_MEVLANA(R.drawable.bg_tr_mevlana, R.string.preset_tr_mevlana, listOf(0xFF6490C5, 0xFFA5C3DA, 0xFF6D8D71).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ISHAK_PASHA(R.drawable.bg_tr_ishak_pasha, R.string.preset_tr_ishak_pasha, listOf(0xFF4F7CB3, 0xFF92939C, 0xFF735E4F).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ERZURUM(R.drawable.bg_tr_erzurum, R.string.preset_tr_erzurum, listOf(0xFF425F8E, 0xFF9AADC5, 0xFFCAD0D7).map { it.toInt() }, PresetGroup.TURKEY),
    TR_SAFRANBOLU(R.drawable.bg_tr_safranbolu, R.string.preset_tr_safranbolu, listOf(0xFF79A1CB, 0xFFA8947B, 0xFFAA9173).map { it.toInt() }, PresetGroup.TURKEY),
    TR_SALDA(R.drawable.bg_tr_salda, R.string.preset_tr_salda, listOf(0xFF81B7E3, 0xFF76C0CA, 0xFFBCE5DF).map { it.toInt() }, PresetGroup.TURKEY),
    TR_VAN_AKDAMAR(R.drawable.bg_tr_van_akdamar, R.string.preset_tr_van_akdamar, listOf(0xFF5E8ABC, 0xFF90AECD, 0xFF245384).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ABANT(R.drawable.bg_tr_abant, R.string.preset_tr_abant, listOf(0xFFA2B5CC, 0xFFA49877, 0xFF46534C).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ULUDAG(R.drawable.bg_tr_uludag, R.string.preset_tr_uludag, listOf(0xFF4880C0, 0xFFAFC2CD, 0xFFE3E9EA).map { it.toInt() }, PresetGroup.TURKEY),
    TR_KOPRULU_KANYON(R.drawable.bg_tr_koprulu_kanyon, R.string.preset_tr_koprulu_kanyon, listOf(0xFF75A0C6, 0xFFAE9D8E, 0xFF7F897B).map { it.toInt() }, PresetGroup.TURKEY),
    TR_GOBEKLITEPE(R.drawable.bg_tr_gobeklitepe, R.string.preset_tr_gobeklitepe, listOf(0xFF73446A, 0xFFD78F7E, 0xFF9B7854).map { it.toInt() }, PresetGroup.TURKEY),
    TR_SUMELA(R.drawable.bg_tr_sumela, R.string.preset_tr_sumela, listOf(0xFFA7BBB4, 0xFF8BA093, 0xFF2A4B3B).map { it.toInt() }, PresetGroup.TURKEY),
    TR_ASPENDOS(R.drawable.bg_tr_aspendos, R.string.preset_tr_aspendos, listOf(0xFF9D8382, 0xFFC99E77, 0xFFC3996F).map { it.toInt() }, PresetGroup.TURKEY),
    TR_HASANKEYF(R.drawable.bg_tr_hasankeyf, R.string.preset_tr_hasankeyf, listOf(0xFF7A5C6E, 0xFFCF9871, 0xFF5F5F65).map { it.toInt() }, PresetGroup.TURKEY)
}

/** What fills the screen behind the launcher; kept in preferences as one string. */
sealed interface BackgroundSpec {
    /** A picture that ships with OtagHome; [PresetImage.OTAG] is the default. */
    data class Preset(val image: PresetImage) : BackgroundSpec
    data class Solid(val color: Int) : BackgroundSpec
    data class Gradient(val start: Int, val end: Int, val direction: GradientDirection) : BackgroundSpec
    /** A picture the user chose; [uri] is a content URI with a persisted read permission. */
    data class Image(val uri: String) : BackgroundSpec

    /**
     * The background's own colours: a picture's samples, the colour, both gradient ends, or the
     * average of the user's picture once it is loaded ([pictureAverage]); empty while unknown.
     */
    fun samples(pictureAverage: Int?): List<Int> = when (this) {
        is Preset -> image.samples
        is Image -> listOfNotNull(pictureAverage)
        is Solid -> listOf(color)
        is Gradient -> listOf(start, end)
    }

    /** True when light text reads better on it than dark text; null while that is not known. */
    fun isDark(pictureAverage: Int?): Boolean? =
        samples(pictureAverage).takeIf { it.isNotEmpty() }?.let(BackdropColors::prefersLightContent)

    fun encode(): String = when (this) {
        // "default" is what earlier versions stored for the default picture
        is Preset -> if (image == PresetImage.OTAG) "default" else "preset|${image.name}"
        is Solid -> "solid|${HexColor.format(color)}"
        is Gradient -> "gradient|${HexColor.format(start)}|${HexColor.format(end)}|${direction.name}"
        is Image -> "image|$uri"
    }

    companion object {
        val Default = Preset(PresetImage.OTAG)
        val DEFAULT_GRADIENT = Gradient(0xFF1A1033.toInt(), 0xFF7C4DFF.toInt(), GradientDirection.DIAGONAL)

        /** Anything unreadable falls back to the default picture. */
        fun decode(raw: String?): BackgroundSpec {
            val parts = raw.orEmpty().split('|')
            return when (parts.firstOrNull()) {
                "solid" -> parts.getOrNull(1)?.let(HexColor::parse)?.let(::Solid)
                "gradient" -> {
                    val start = parts.getOrNull(1)?.let(HexColor::parse)
                    val end = parts.getOrNull(2)?.let(HexColor::parse)
                    val direction = GradientDirection.entries.firstOrNull { it.name == parts.getOrNull(3) }
                    if (start != null && end != null && direction != null) Gradient(start, end, direction) else null
                }
                // A content URI never contains '|', but keep everything after the first one anyway
                "image" -> raw.orEmpty().substringAfter('|').takeIf { it.isNotBlank() }?.let(::Image)
                "preset" -> PresetImage.entries.firstOrNull { it.name == parts.getOrNull(1) }?.let(::Preset)
                else -> null
            } ?: Default
        }
    }
}
