package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class AppCatalogTest {

    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))

    private fun tv(pkg: String, label: String, system: Boolean = false) = AppEntry(pkg, "$pkg.Main", label, isTvApp = true, isSystemApp = system)
    private fun phone(pkg: String, label: String) = AppEntry(pkg, "$pkg.Main", label, isTvApp = false)

    @Test
    fun storeUpdatedPreinstalledAppsCountAsTheUsers() {
        assertFalse(AppCatalog.isSystemImageApp(isSystem = false, isUpdatedSystem = false))
        // Preinstalled but updated from the store, e.g. Netflix or YouTube
        assertFalse(AppCatalog.isSystemImageApp(isSystem = true, isUpdatedSystem = true))
        assertTrue(AppCatalog.isSystemImageApp(isSystem = true, isUpdatedSystem = false))
    }

    @Test
    fun systemAppsSettingChoosesWhichSystemAppsAreListed() {
        val user = tv("com.netflix", "Netflix")
        val liveTv = tv("com.tvcenter", "TV", system = true)
        val factory = phone("com.factory", "Factory Menu").copy(isSystemApp = true)
        val apps = listOf(user, liveTv, factory)
        assertEquals(listOf(user, liveTv), AppCatalog.visible(apps, SystemAppsMode.TV_ONLY))
        assertEquals(listOf(user), AppCatalog.visible(apps, SystemAppsMode.HIDE))
        assertEquals(apps, AppCatalog.visible(apps, SystemAppsMode.ALL))
        assertEquals(SystemAppsMode.TV_ONLY, SystemAppsMode.fromName(null))
        assertEquals(SystemAppsMode.TV_ONLY, SystemAppsMode.ALL.next())
    }

    @Test
    fun mergePrefersTheTvActivityAndDropsSelf() {
        val merged = AppCatalog.merge(
            tvApps = listOf(tv("com.a", "A TV"), tv("com.self", "Self"), tv("com.live", "Live TV", system = true)),
            phoneApps = listOf(phone("com.a", "A phone"), phone("com.b", "B")),
            selfPackage = "com.self"
        )
        assertEquals(listOf("com.a", "com.live", "com.b"), merged.map { it.packageName })
        assertTrue(merged.first().isTvApp)
    }

    @Test
    fun allAppsAreSortedWithTheLanguageCollator() {
        val apps = listOf(tv("com.z", "Zeta"), tv("com.c", "Çay"), tv("com.b", "Beta"), phone("com.d", "Dosyalar"))
        // Turkish collation puts Ç after C and before D
        assertEquals(listOf("Beta", "Çay", "Dosyalar", "Zeta"), AppCatalog.allApps(apps, collator).map { it.label })
    }

    @Test
    fun favoritesKeepTheirOrderAndSkipUninstalledApps() {
        val apps = listOf(tv("com.z", "Zeta"), tv("com.b", "Beta"), phone("com.p", "Phone"))
        val favorites = AppCatalog.favoriteApps(apps, listOf("com.z", "com.missing", "com.p"))
        assertEquals(listOf("com.z", "com.p"), favorites.map { it.packageName })
    }

    @Test
    fun allAppsFollowTheUserOrderThenTheAlphabet() {
        val apps = listOf(tv("com.z", "Zeta"), tv("com.b", "Beta"), tv("com.c", "Çay"), phone("com.d", "Dosyalar"))
        // Untouched: alphabetical
        assertEquals(listOf("Beta", "Çay", "Dosyalar", "Zeta"), AppCatalog.ordered(apps, emptyList(), collator).map { it.label })
        // Zeta placed first; an uninstalled package in the order is skipped
        assertEquals(
            listOf("Zeta", "Dosyalar", "Beta", "Çay"),
            AppCatalog.ordered(apps, listOf("com.z", "com.gone", "com.d"), collator).map { it.label }
        )
    }

    @Test
    fun sortSettingUsesTheOwnOrderOrTheName() {
        val apps = listOf(tv("com.z", "Zeta"), tv("com.b", "Beta"), tv("com.c", "Çay"))
        val own = listOf(apps[2], apps[0], apps[1])
        assertEquals(own, AppCatalog.sorted(apps, SortMode.CUSTOM, collator) { own })
        assertEquals(listOf("Beta", "Çay", "Zeta"), AppCatalog.sorted(apps, SortMode.NAME_ASC, collator) { own }.map { it.label })
        assertEquals(listOf("Zeta", "Çay", "Beta"), AppCatalog.sorted(apps, SortMode.NAME_DESC, collator) { own }.map { it.label })
        assertEquals(SortMode.CUSTOM, SortMode.fromName("SIDEWAYS"))
        assertEquals(SortMode.RECENT, SortMode.NAME_DESC.next())
        assertEquals(SortMode.CUSTOM, SortMode.RECENT.next())
    }

    @Test
    fun recentSortOrdersByMostRecentLaunchThenAlphabet() {
        val apps = listOf(tv("com.z", "Zeta"), tv("com.b", "Beta"), tv("com.c", "Çay"), tv("com.a", "Alfa"))
        val launched = AppCatalog.recordLaunch(
            AppCatalog.recordLaunch(emptyMap(), "com.b", 1_000L),
            "com.z",
            2_000L
        )
        assertEquals(
            listOf("Zeta", "Beta", "Alfa", "Çay"),
            AppCatalog.sorted(apps, SortMode.RECENT, collator, launched) { apps }.map { it.label }
        )
        assertEquals(launched, AppCatalog.decodeTimestamps(AppCatalog.encodeTimestamps(launched) + "\nbad_line\ncom.x=-5\n"))
    }

    @Test
    fun customLabelsRoundTripAndOverrideDisplayNames() {
        val apps = listOf(tv("com.a", "Original A"), tv("com.b", "Original B"))
        val encoded = AppCatalog.encodeCustomLabels(mapOf("com.a" to "  My\tTV\nApp  ", "com.empty" to "   "))
        val decoded = AppCatalog.decodeCustomLabels(encoded + "\ninvalid\ncom.blank\t   ")
        assertEquals(mapOf("com.a" to "My TV App"), decoded)
        assertEquals(listOf("My TV App", "Original B"), AppCatalog.applyCustomLabels(apps, decoded).map { it.label })
    }

    @Test
    fun moveShiftsAnItemAndStopsAtTheEnds() {
        val list = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "c", "b", "d", "e"), AppCatalog.move(list, "b", 1))
        assertEquals(list, AppCatalog.move(list, "x", 1))
        assertEquals(listOf("b", "c", "d", "e", "a"), AppCatalog.move(list, "a", 10))
        assertEquals(listOf("c", "a", "b", "d", "e"), AppCatalog.move(list, "c", -4))
        assertEquals(list, AppCatalog.move(list, "a", -1))
    }

    @Test
    fun toggleFavoriteAddsAtTheEndAndRemoves() {
        val added = AppCatalog.toggleFavorite(listOf("com.a"), "com.b")
        assertEquals(listOf("com.a", "com.b"), added)
        assertEquals(listOf("com.b"), AppCatalog.toggleFavorite(added, "com.a"))
    }

    @Test
    fun favoritesRoundTripAndIgnoreBlankOrDuplicateLines() {
        val favorites = listOf("com.a", "com.b")
        assertEquals(favorites, AppCatalog.decodePackages(AppCatalog.encodePackages(favorites)))
        assertEquals(listOf("com.a"), AppCatalog.decodePackages("com.a\n\n com.a \n"))
        assertTrue(AppCatalog.decodePackages(null).isEmpty())
    }

    @Test
    fun customCategoriesManageLifecycleRoundTripAndOrdering() {
        var cats = AppCatalog.addCategory(emptyList(), " Video ", "c1")
        assertEquals(1, cats.size)
        // Duplicate names (case-insensitive) and blank names are rejected
        assertEquals(1, AppCatalog.addCategory(cats, "video", "c2").size)
        assertEquals(1, AppCatalog.addCategory(cats, "   ", "c2").size)

        cats = AppCatalog.addCategory(cats, "Müzik & TV", "c2")
        assertEquals(2, cats.size)

        cats = AppCatalog.toggleAppInCategory(cats, "c1", "com.yt")
        cats = AppCatalog.toggleAppInCategory(cats, "c1", "com.nf")
        val (moved, newIdx) = AppCatalog.moveInCategory(cats, "c1", "com.nf", -1, listOf("com.yt", "com.nf"))
        assertEquals(0, newIdx)
        assertEquals(listOf("com.nf", "com.yt"), moved.first().packages)

        val renamed = AppCatalog.renameCategory(moved, "c1", "Streaming")
        assertEquals("Streaming", renamed.first().name)
        // Rejects rename colliding with another category
        assertEquals("Streaming", AppCatalog.renameCategory(renamed, "c1", "müzik & tv").first().name)

        val decoded = AppCatalog.decodeCategories(AppCatalog.encodeCategories(renamed))
        assertEquals(renamed, decoded)

        val installed = listOf(AppEntry("com.yt", "Main", "YouTube", true), AppEntry("com.nf", "Main", "Netflix", true))
        assertEquals(listOf("Netflix", "YouTube"), AppCatalog.categoryApps(installed, decoded.first()).map { it.label })

        val deleted = AppCatalog.deleteCategory(decoded, "c1")
        assertEquals(listOf("c2"), deleted.map { it.id })
    }

    @Test
    fun customCategoriesRespectMaximumCount() {
        var cats = emptyList<AppCategory>()
        repeat(AppCatalog.MAX_CATEGORIES + 3) { idx ->
            cats = AppCatalog.addCategory(cats, "Cat $idx", "id_$idx")
        }
        assertEquals(AppCatalog.MAX_CATEGORIES, cats.size)
    }

    @Test
    fun customArtRoundTripsSafely() {
        val art = mapOf("com.a" to "file:///data/cover%20a.img", "com.b" to "file:///data/cover_b.img")
        assertEquals(art, AppCatalog.decodeCustomArt(AppCatalog.encodeCustomArt(art)))
    }
}
