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
            "background" to "image|content://pictures/42",
            "last_launched" to "com.a=1700000000000",
            "bg_rotation_day" to 20_000L,
            "custom_labels" to "com.a\tMy App",
            "bg_rotation" to "DAILY",
            "categories" to """[{"id":"c1","name":"Video","packages":["com.a"]}]""",
            "card_opacity" to "TRANSLUCENT",
            "number_shortcuts" to true,
            "auto_update_interval" to "WEEKLY",
            "custom_art" to "com.a\tfile:///local/cover.img",
            "pin_hash" to "deadbeef",
            "pin_salt" to "cafebabe",
            "lock_settings" to true,
            "locked_apps" to "com.a",
            "update_last_checked" to 1_700_000_000_000L,
            "update_last_notified" to "1.1.0"
        )
        val restored = Backup.decode(Backup.encode(prefs))!!
        assertEquals(
            prefs - "weather_cache" - "background" - "last_launched" - "bg_rotation_day" -
                "custom_art" - "pin_hash" - "pin_salt" - "lock_settings" - "locked_apps" -
                "update_last_checked" - "update_last_notified",
            restored
        )
        // A ready-made picture travels fine
        assertEquals("preset|OCEAN", Backup.decode(Backup.encode(mapOf("background" to "preset|OCEAN")))!!["background"])
    }

    @Test
    fun cardOpacityDigitShortcutsAndHomeTabCyclingWorkCleanly() {
        assertEquals(CardOpacity.TRANSLUCENT, CardOpacity.SOLID.next())
        assertEquals(CardOpacity.GLASS, CardOpacity.TRANSLUCENT.next())
        assertEquals(CardOpacity.SOLID, CardOpacity.GLASS.next())
        assertEquals(CardOpacity.SOLID, CardOpacity.fromName("UNKNOWN"))

        assertEquals(0, RemoteNavigation.favoriteIndexForDigit(1, favoriteCount = 3))
        assertEquals(2, RemoteNavigation.favoriteIndexForDigit(3, favoriteCount = 3))
        assertEquals(-1, RemoteNavigation.favoriteIndexForDigit(4, favoriteCount = 3))
        assertEquals(-1, RemoteNavigation.favoriteIndexForDigit(0, favoriteCount = 5))

        val cats = listOf("vid", "mus")
        assertEquals(HomeTab.AllApps, RemoteNavigation.nextTab(HomeTab.Favorites, cats, forward = true))
        assertEquals(HomeTab.Category("vid"), RemoteNavigation.nextTab(HomeTab.AllApps, cats, forward = true))
        assertEquals(HomeTab.Category("mus"), RemoteNavigation.nextTab(HomeTab.Category("vid"), cats, forward = true))
        assertEquals(HomeTab.Favorites, RemoteNavigation.nextTab(HomeTab.Category("mus"), cats, forward = true))
        assertEquals(HomeTab.Category("mus"), RemoteNavigation.nextTab(HomeTab.Favorites, cats, forward = false))
    }

    @Test
    fun tabSwitchesAndRemotePageJumpsClampToBounds() {
        assertEquals(LauncherTab.ALL_APPS, LauncherTab.FAVORITES.other())
        assertEquals(LauncherTab.FAVORITES, LauncherTab.ALL_APPS.other())

        assertEquals(-1, RemoteNavigation.pageJumpIndex(0, totalCount = 0, columns = 5, forward = true))
        assertEquals(10, RemoteNavigation.pageJumpIndex(0, totalCount = 24, columns = 5, forward = true))
        assertEquals(23, RemoteNavigation.pageJumpIndex(18, totalCount = 24, columns = 5, forward = true))
        assertEquals(2, RemoteNavigation.pageJumpIndex(12, totalCount = 24, columns = 5, forward = false))
        assertEquals(0, RemoteNavigation.pageJumpIndex(3, totalCount = 24, columns = 5, forward = false))
    }

    @Test
    fun foreignFilesAreNotBackups() {
        assertNull(Backup.decode("""{"format":"something-else","prefs":{}}"""))
        assertNull(Backup.decode("not json"))
    }
}
