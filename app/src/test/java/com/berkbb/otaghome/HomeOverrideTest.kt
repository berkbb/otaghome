package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOverrideTest {

    private val own = "com.berkbb.otaghome"
    private val otag = HomeCandidate(own, "$own.MainActivity", null, 0, isSystem = false)
    private val googleTv = HomeCandidate(
        "com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity", null, 2, isSystem = true
    )
    private val fallbackHome = HomeCandidate("com.android.tv.settings", "com.android.tv.settings.FallbackHome", null, -1000, isSystem = true)

    @Test
    fun theTvsOwnHomeIsATarget() {
        val targets = HomeOverride.targets(listOf(otag, googleTv), own)
        assertEquals(mapOf(googleTv.packageName to setOf(googleTv.activityName)), targets)
    }

    @Test
    fun otagHomeAndTheSettingsBootScreenAreNotTargets() {
        assertTrue(HomeOverride.targets(listOf(otag, fallbackHome), own).isEmpty())
    }

    @Test
    fun launchersTheUserInstalledAreLeftAlone() {
        val installed = HomeCandidate("com.example.launcher", "com.example.launcher.Home", null, 0, isSystem = false)
        assertTrue(HomeOverride.targets(listOf(otag, installed), own).isEmpty())
    }

    @Test
    fun anAliasCountsWithTheActivityBehindIt() {
        val alias = HomeCandidate("com.vendor.home", "com.vendor.home.HomeAlias", "com.vendor.home.Launcher", 0, isSystem = true)
        val targets = HomeOverride.targets(listOf(alias), own)
        assertEquals(setOf("com.vendor.home.HomeAlias", "com.vendor.home.Launcher"), targets["com.vendor.home"])
        assertTrue(HomeOverride.isHomeScreen("com.vendor.home", "com.vendor.home.Launcher", targets))
    }

    @Test
    fun onlyTheHomeActivityOfThatAppCounts() {
        val targets = HomeOverride.targets(listOf(googleTv), own)
        assertTrue(HomeOverride.isHomeScreen(googleTv.packageName, googleTv.activityName, targets))
        // The input panel and other screens of the same app open as usual
        assertFalse(HomeOverride.isHomeScreen(googleTv.packageName, "com.google.android.apps.tv.launcherx.inputs.InputsActivity", targets))
        assertFalse(HomeOverride.isHomeScreen(googleTv.packageName, "android.widget.FrameLayout", targets))
        assertFalse(HomeOverride.isHomeScreen("com.android.tv.settings", googleTv.activityName, targets))
    }

    @Test
    fun missingEventDetailsNeverRedirect() {
        val targets = HomeOverride.targets(listOf(googleTv), own)
        assertFalse(HomeOverride.isHomeScreen(null, googleTv.activityName, targets))
        assertFalse(HomeOverride.isHomeScreen(googleTv.packageName, null, targets))
        assertFalse(HomeOverride.isHomeScreen(googleTv.packageName, googleTv.activityName, emptyMap()))
    }
}
