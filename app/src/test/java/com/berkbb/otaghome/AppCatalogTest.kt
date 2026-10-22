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
        assertEquals(SortMode.CUSTOM, SortMode.NAME_DESC.next())
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
}
