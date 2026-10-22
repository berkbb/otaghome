package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTest {

    @Test
    fun clockFormatFollowsOrOverridesTheTv() {
        assertTrue(ClockFormat.SYSTEM.is24Hour(tvIs24Hour = true))
        assertFalse(ClockFormat.SYSTEM.is24Hour(tvIs24Hour = false))
        assertTrue(ClockFormat.H24.is24Hour(tvIs24Hour = false))
        assertFalse(ClockFormat.H12.is24Hour(tvIs24Hour = true))
        assertEquals(ClockFormat.SYSTEM, ClockFormat.H12.next())
    }

    @Test
    fun largerCardsMeanFewerColumns() {
        assertEquals(listOf(6, 5, 4), CardSize.entries.map { it.allAppsColumns })
        assertEquals(listOf(5, 4, 3), CardSize.entries.map { it.favoriteColumns })
        assertEquals(CardSize.NORMAL, CardSize.fromName(null))
    }

    @Test
    fun startTabResolvesFromTheChoiceTheLastTabAndTheFavorites() {
        assertEquals(LauncherTab.FAVORITES, StartTab.AUTO.resolve(null, hasFavorites = true))
        assertEquals(LauncherTab.ALL_APPS, StartTab.AUTO.resolve(LauncherTab.FAVORITES, hasFavorites = false))
        assertEquals(LauncherTab.ALL_APPS, StartTab.ALL_APPS.resolve(LauncherTab.FAVORITES, hasFavorites = true))
        assertEquals(LauncherTab.ALL_APPS, StartTab.LAST.resolve(LauncherTab.ALL_APPS, hasFavorites = true))
        // Nothing remembered yet: the automatic choice
        assertEquals(LauncherTab.FAVORITES, StartTab.LAST.resolve(null, hasFavorites = true))
    }

    @Test
    fun backupRoundTripsEveryTypeAndLeavesOutWhatCannotTravel() {
        val prefs = mapOf<String, Any>(
            "favorites" to "com.a\ncom.b",
            "notice_seconds" to 8,
            "weather_enabled" to false,
            "veil" to "LIGHT",
            "big" to 1_700_000_000_000L,
            "ratio" to 0.5f,
            "weather_cache" to "12|3|1|Ankara|0",
            "background" to "image|content://pictures/42"
        )
        val restored = Backup.decode(Backup.encode(prefs))!!
        assertEquals(prefs - "weather_cache" - "background", restored)
        // A ready-made picture travels fine
        assertEquals("preset|OCEAN", Backup.decode(Backup.encode(mapOf("background" to "preset|OCEAN")))!!["background"])
    }

    @Test
    fun foreignFilesAreNotBackups() {
        assertNull(Backup.decode("""{"format":"something-else","prefs":{}}"""))
        assertNull(Backup.decode("not json"))
    }
}
