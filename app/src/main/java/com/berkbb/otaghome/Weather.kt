package com.berkbb.otaghome

import androidx.annotation.DrawableRes
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The kinds of weather shown, from the WMO codes Open-Meteo reports. Shown as icons only, so the
 * weather reads the same in every language the TV may use.
 */
enum class WeatherKind(@param:DrawableRes private val dayIcon: Int, @param:DrawableRes private val nightIcon: Int = dayIcon) {
    CLEAR(R.drawable.ic_w_clear_day, R.drawable.ic_w_clear_night),
    PARTLY_CLOUDY(R.drawable.ic_w_partly, R.drawable.ic_w_clear_night),
    OVERCAST(R.drawable.ic_w_cloudy),
    FOG(R.drawable.ic_w_fog),
    DRIZZLE(R.drawable.ic_w_drizzle),
    RAIN(R.drawable.ic_w_rain),
    SNOW(R.drawable.ic_w_snow),
    THUNDERSTORM(R.drawable.ic_w_thunder),
    UNKNOWN(R.drawable.ic_w_cloudy);

    @DrawableRes
    fun icon(isDay: Boolean): Int = if (isDay) dayIcon else nightIcon

    companion object {
        /** WMO weather interpretation codes, as listed in Open-Meteo's documentation. */
        fun fromWmo(code: Int): WeatherKind = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/** A place to show the weather for; [label] is how it reads in a list ("Ankara, Türkiye"). */
data class Place(val name: String, val latitude: Double, val longitude: Double, val label: String = name)

/** The weather right now at [place], fetched at [fetchedAt] (epoch millis). */
data class WeatherNow(
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val place: String,
    val fetchedAt: Long
) {
    val kind: WeatherKind get() = WeatherKind.fromWmo(code)

    /** "18°" in the chosen [unit], rounded, with a proper minus sign below zero. */
    fun temperatureText(unit: TempUnit): String =
        "${Math.round(unit.fromCelsius(temperature)).toString().replace('-', '−')}°"
}

/** The unit the temperature is shown in; Open-Meteo always answers in Celsius. */
enum class TempUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    fun next(): TempUnit = entries[(ordinal + 1) % entries.size]

    fun fromCelsius(celsius: Double): Double = if (this == FAHRENHEIT) celsius * 9 / 5 + 32 else celsius

    companion object {
        fun fromName(name: String?): TempUnit = entries.firstOrNull { it.name == name } ?: CELSIUS
    }
}

/**
 * The weather settings: shown or not; where (a null [place] means the place is found from the
 * TV's internet connection, which needs [ipConsent]: null while the user has not been asked);
 * and how often it is refreshed.
 */
data class WeatherSettings(
    val enabled: Boolean = true,
    val place: Place? = null,
    val ipConsent: Boolean? = null,
    val intervalMinutes: Int = WeatherInterval.DEFAULT,
    val unit: TempUnit = TempUnit.CELSIUS
) {
    /** The user must be asked before the internet address is sent to find the place. */
    val needsConsent: Boolean get() = enabled && place == null && ipConsent == null

    /** Whether there is somewhere to fetch the weather for. */
    val canFetch: Boolean get() = enabled && (place != null || ipConsent == true)
}

/** How often the weather is refreshed, chosen in Settings. */
object WeatherInterval {
    val OPTIONS = listOf(15, 30, 60, 120, 180)
    const val DEFAULT = 30

    fun next(minutes: Int): Int = OPTIONS[(OPTIONS.indexOf(minutes).coerceAtLeast(0) + 1) % OPTIONS.size]

    fun fromStored(minutes: Int?): Int = minutes?.takeIf { it in OPTIONS } ?: DEFAULT
}

/** Kept in preferences as plain strings; '|' never appears in coordinates and is dropped from names. */
object WeatherStore {

    fun encodePlace(place: Place): String =
        listOf(place.name.replace("|", " "), place.latitude, place.longitude, place.label.replace("|", " ")).joinToString("|")

    fun decodePlace(raw: String?): Place? {
        val parts = raw?.split('|') ?: return null
        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(2)?.toDoubleOrNull() ?: return null
        val name = parts[0].takeIf { it.isNotBlank() } ?: return null
        return Place(name, lat, lon, parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: name)
    }

    fun encodeWeather(now: WeatherNow): String =
        listOf(now.temperature, now.code, if (now.isDay) 1 else 0, now.place.replace("|", " "), now.fetchedAt).joinToString("|")

    fun decodeWeather(raw: String?): WeatherNow? {
        val p = raw?.split('|') ?: return null
        if (p.size < 5) return null
        return WeatherNow(
            temperature = p[0].toDoubleOrNull() ?: return null,
            code = p[1].toIntOrNull() ?: return null,
            isDay = p[2] == "1",
            place = p[3],
            fetchedAt = p[4].toLongOrNull() ?: return null
        )
    }
}

/** Reading the services' replies; kept apart from the network so they can be tested. */
object WeatherParser {

    /** Open-Meteo's forecast reply with `current=temperature_2m,weather_code,is_day`. */
    fun forecast(json: String, place: String, now: Long): WeatherNow? = runCatching {
        val current = JSONObject(json).getJSONObject("current")
        WeatherNow(
            temperature = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            place = place,
            fetchedAt = now
        )
    }.getOrNull()

    /** An IP location reply (ipwho.is or ipapi.co): city, latitude and longitude. */
    fun ipLocation(json: String): Place? = runCatching {
        val o = JSONObject(json)
        if (o.has("success") && !o.getBoolean("success")) return null
        if (o.optBoolean("error", false)) return null
        val city = o.optString("city").takeIf { it.isNotBlank() } ?: return null
        Place(city, o.getDouble("latitude"), o.getDouble("longitude"))
    }.getOrNull()

    /** Open-Meteo's geocoding search reply, read as places labelled "City, Region, Country". */
    fun cities(json: String): List<Place> = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val name = r.getString("name")
            val label = listOf(name, r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(name, r.getDouble("latitude"), r.getDouble("longitude"), label)
        }
    }.getOrDefault(emptyList())
}

/** The few HTTPS calls the weather needs; all are free services that need no key. */
object WeatherClient {

    private const val TIMEOUT_MILLIS = 8_000
    /** Tried in turn when the place is found from the internet connection (only with the user's consent). */
    val IP_LOCATION_URLS = listOf("https://ipwho.is/", "https://ipapi.co/json/")

    fun forecastUrl(place: Place): String = String.format(
        Locale.ROOT,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
        place.latitude, place.longitude
    )

    fun searchUrl(query: String, language: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=8&language=$language&format=json"

    fun fetchWeather(place: Place, now: Long): WeatherNow? =
        get(forecastUrl(place))?.let { WeatherParser.forecast(it, place.name, now) }

    fun locate(): Place? = IP_LOCATION_URLS.firstNotNullOfOrNull { url -> get(url)?.let(WeatherParser::ipLocation) }

    /** Null when the search could not be made; an empty list when nothing matched. */
    fun search(query: String, language: String): List<Place>? =
        get(searchUrl(query, language))?.let(WeatherParser::cities)

    private fun get(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "OtagHome/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
