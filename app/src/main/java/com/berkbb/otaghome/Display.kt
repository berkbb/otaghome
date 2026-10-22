package com.berkbb.otaghome

import androidx.annotation.StringRes
import org.json.JSONObject

/** The two tabs of the home screen. */
enum class LauncherTab { FAVORITES, ALL_APPS }

/** Clock format: the TV's own setting, or always 24-hour or 12-hour. */
enum class ClockFormat(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.clock_system),
    H24(R.string.clock_24),
    H12(R.string.clock_12);

    fun next(): ClockFormat = entries[(ordinal + 1) % entries.size]

    fun is24Hour(tvIs24Hour: Boolean): Boolean = when (this) {
        SYSTEM -> tvIs24Hour
        H24 -> true
        H12 -> false
    }

    companion object {
        fun fromName(name: String?): ClockFormat = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** How big the app cards are, as the number of columns in each tab. */
enum class CardSize(@param:StringRes val labelRes: Int, val allAppsColumns: Int, val favoriteColumns: Int) {
    SMALL(R.string.size_small, 6, 5),
    NORMAL(R.string.size_normal, 5, 4),
    LARGE(R.string.size_large, 4, 3);

    fun next(): CardSize = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): CardSize = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** Which tab the home screen opens on. */
enum class StartTab(@param:StringRes val labelRes: Int) {
    /** Favorites when there are any, otherwise All apps. */
    AUTO(R.string.start_auto),
    FAVORITES(R.string.tab_favorites),
    ALL_APPS(R.string.tab_all_apps),
    LAST(R.string.start_last);

    fun next(): StartTab = entries[(ordinal + 1) % entries.size]

    fun resolve(lastTab: LauncherTab?, hasFavorites: Boolean): LauncherTab = when (this) {
        FAVORITES -> LauncherTab.FAVORITES
        ALL_APPS -> LauncherTab.ALL_APPS
        LAST -> lastTab ?: auto(hasFavorites)
        AUTO -> auto(hasFavorites)
    }

    private fun auto(hasFavorites: Boolean) = if (hasFavorites) LauncherTab.FAVORITES else LauncherTab.ALL_APPS

    companion object {
        fun fromName(name: String?): StartTab = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * A backup of every OtagHome setting as one JSON file, to carry them to another TV. The weather
 * reading is left out (it is fetched again), and so is a picture of the user's own: its
 * permission belongs to this TV.
 */
object Backup {
    private const val FORMAT = "otaghome-backup"
    private const val VERSION = 1
    private val SKIPPED_KEYS = setOf("weather_cache")

    fun encode(prefs: Map<String, *>): String {
        val values = JSONObject()
        prefs.toSortedMap().forEach { (key, value) ->
            if (key in SKIPPED_KEYS || !portable(key, value)) return@forEach
            val (type, json) = when (value) {
                is String -> "s" to value
                is Int -> "i" to value
                is Long -> "l" to value
                is Boolean -> "b" to value
                is Float -> "f" to value.toDouble()
                else -> return@forEach
            }
            values.put(key, JSONObject().put("t", type).put("v", json))
        }
        return JSONObject().put("format", FORMAT).put("version", VERSION).put("prefs", values).toString(2)
    }

    /** The settings in [json], or null when it is not an OtagHome backup. */
    fun decode(json: String): Map<String, Any>? = runCatching {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) return null
        val values = root.getJSONObject("prefs")
        values.keys().asSequence().mapNotNull { key ->
            val entry = values.getJSONObject(key)
            val value: Any = when (entry.getString("t")) {
                "s" -> entry.getString("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "b" -> entry.getBoolean("v")
                "f" -> entry.getDouble("v").toFloat()
                else -> return@mapNotNull null
            }
            if (key in SKIPPED_KEYS || !portable(key, value)) null else key to value
        }.toMap()
    }.getOrNull()

    /** A picture chosen on this TV cannot be opened on another one. */
    private fun portable(key: String, value: Any?): Boolean =
        !(key == "background" && value is String && value.startsWith("image|"))
}
