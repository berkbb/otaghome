package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    @Test
    fun wmoCodesBecomeKindsAndNightsGetTheMoon() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.fromWmo(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherKind.fromWmo(2))
        assertEquals(WeatherKind.RAIN, WeatherKind.fromWmo(81))
        assertEquals(WeatherKind.SNOW, WeatherKind.fromWmo(75))
        assertEquals(WeatherKind.THUNDERSTORM, WeatherKind.fromWmo(99))
        assertEquals(WeatherKind.UNKNOWN, WeatherKind.fromWmo(42))
        assertEquals(R.drawable.ic_w_clear_night, WeatherKind.CLEAR.icon(isDay = false))
        assertEquals(R.drawable.ic_w_cloudy, WeatherKind.OVERCAST.icon(isDay = false))
    }

    @Test
    fun temperaturesAreRoundedWithATrueMinus() {
        assertEquals("18°", WeatherNow(17.6, 0, true, "Ankara", 0).temperatureText(TempUnit.CELSIUS))
        assertEquals("−3°", WeatherNow(-3.2, 0, true, "Kars", 0).temperatureText(TempUnit.CELSIUS))
        // 20 °C is 68 °F, −10 °C is 14 °F
        assertEquals("68°", WeatherNow(20.0, 0, true, "İzmir", 0).temperatureText(TempUnit.FAHRENHEIT))
        assertEquals("14°", WeatherNow(-10.0, 0, true, "Kars", 0).temperatureText(TempUnit.FAHRENHEIT))
        assertEquals(TempUnit.FAHRENHEIT, TempUnit.CELSIUS.next())
        assertEquals(TempUnit.CELSIUS, TempUnit.fromName("KELVIN"))
    }

    @Test
    fun forecastReplyIsRead() {
        val json = """{"current":{"time":"2026-10-22T22:45","temperature_2m":12.4,"weather_code":3,"is_day":0}}"""
        assertEquals(WeatherNow(12.4, 3, false, "Ankara", 99L), WeatherParser.forecast(json, "Ankara", 99L))
        assertNull(WeatherParser.forecast("""{"error":true}""", "Ankara", 0))
        assertNull(WeatherParser.forecast("not json", "Ankara", 0))
    }

    @Test
    fun ipLocationRepliesFromEitherServiceAreRead() {
        assertEquals(Place("Ankara", 39.93, 32.85), WeatherParser.ipLocation("""{"success":true,"city":"Ankara","latitude":39.93,"longitude":32.85}"""))
        assertEquals(Place("İzmir", 38.42, 27.14), WeatherParser.ipLocation("""{"city":"İzmir","latitude":38.42,"longitude":27.14}"""))
        assertNull(WeatherParser.ipLocation("""{"success":false,"message":"limit"}"""))
        assertNull(WeatherParser.ipLocation("""{"error":true,"reason":"RateLimited"}"""))
    }

    @Test
    fun citySearchLabelsSkipRepeatsAndBlanks() {
        val json = """{"results":[
            {"name":"Ankara","latitude":39.92,"longitude":32.85,"admin1":"Ankara","country":"Türkiye"},
            {"name":"Kars","latitude":40.6,"longitude":43.1,"country":"Türkiye"}]}"""
        val places = WeatherParser.cities(json)
        assertEquals(listOf("Ankara, Türkiye", "Kars, Türkiye"), places.map { it.label })
        assertTrue(WeatherParser.cities("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test
    fun placesAndWeatherSurviveStorage() {
        val place = Place("Ankara", 39.92, 32.85, "Ankara, Türkiye")
        assertEquals(place, WeatherStore.decodePlace(WeatherStore.encodePlace(place)))
        val now = WeatherNow(-2.5, 71, false, "Kars", 1_700_000_000_000)
        assertEquals(now, WeatherStore.decodeWeather(WeatherStore.encodeWeather(now)))
        assertNull(WeatherStore.decodePlace("Ankara|north|east"))
        assertNull(WeatherStore.decodeWeather("12|3"))
    }

    @Test
    fun theInternetAddressIsOnlyUsedWithConsent() {
        assertTrue(WeatherSettings().needsConsent)
        assertFalse(WeatherSettings().canFetch)
        assertTrue(WeatherSettings(ipConsent = true).canFetch)
        assertFalse(WeatherSettings(ipConsent = false).canFetch)
        // A chosen city needs no consent at all
        val city = WeatherSettings(place = Place("Ankara", 39.9, 32.8), ipConsent = false)
        assertFalse(city.needsConsent)
        assertTrue(city.canFetch)
        assertFalse(WeatherSettings(enabled = false).needsConsent)
    }

    @Test
    fun refreshIntervalsCycle() {
        assertEquals(listOf(30, 60, 120, 180, 15), WeatherInterval.OPTIONS.map(WeatherInterval::next))
        assertEquals(30, WeatherInterval.fromStored(45))
    }

    @Test
    fun requestsUseTheTvLanguageAndEncodeTheCity() {
        val url = WeatherClient.searchUrl("Kahramanmaraş", "de")
        assertTrue(url, url.contains("language=de"))
        assertTrue(url, url.contains("name=Kahramanmara%C5%9F"))
        assertTrue(WeatherClient.forecastUrl(Place("Ankara", 39.92, 32.85)).contains("latitude=39.9200&longitude=32.8500"))
    }
}
