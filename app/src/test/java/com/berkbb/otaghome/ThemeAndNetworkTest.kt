package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndNetworkTest {

    @Test
    fun themeCyclesAutoDarkLightBackground() {
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.LIGHT, ThemeMode.DARK.next())
        assertEquals(ThemeMode.BACKGROUND, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.BACKGROUND.next())
    }

    @Test
    fun matchBackgroundFollowsTheBackgroundAndWaitsOnTheTv() {
        assertTrue(ThemeMode.BACKGROUND.isDark(systemIsDark = false, backgroundIsDark = true))
        assertFalse(ThemeMode.BACKGROUND.isDark(systemIsDark = true, backgroundIsDark = false))
        // A picture still loading: the TV's own setting holds until its colour is known
        assertTrue(ThemeMode.BACKGROUND.isDark(systemIsDark = true, backgroundIsDark = null))
        // The fixed modes ignore the background
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false, backgroundIsDark = false))
    }

    @Test
    fun backgroundsKnowWhetherTheyAreDark() {
        assertEquals(true, BackgroundSpec.Default.isDark(null))
        assertEquals(false, BackgroundSpec.Preset(PresetImage.TR_PAMUKKALE).isDark(null))
        assertEquals(false, BackgroundSpec.Solid(0xFFF5F5F5.toInt()).isDark(null))
        assertEquals(true, BackgroundSpec.Gradient(0xFF000000.toInt(), 0xFF1A1033.toInt(), GradientDirection.VERTICAL).isDark(null))
        assertEquals(null, BackgroundSpec.Image("content://picture").isDark(null))
        assertEquals(false, BackgroundSpec.Image("content://picture").isDark(0xFFEFEFEF.toInt()))
    }

    @Test
    fun autoFollowsTheSystemAndFixedModesDoNot() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertEquals(Palette.Light, Palette.of(ThemeMode.LIGHT, systemIsDark = true))
        assertEquals(Palette.Dark, Palette.of(ThemeMode.DARK, systemIsDark = false))
    }

    @Test
    fun unknownStoredThemeFallsBackToAuto() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("PURPLE"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromName("LIGHT"))
    }

    @Test
    fun focusedTextDiffersFromItsFillInBothThemes() {
        listOf(Palette.Dark, Palette.Light).forEach { p ->
            assertFalse(p.focus == p.onFocus)
            assertFalse(p.textPrimary == p.background)
        }
    }

    @Test
    fun wifiSignalMapsToFourBars() {
        assertEquals(4, NetworkStatus.wifiBars(-40))
        assertEquals(3, NetworkStatus.wifiBars(-60))
        assertEquals(2, NetworkStatus.wifiBars(-70))
        assertEquals(1, NetworkStatus.wifiBars(-85))
        assertEquals(0, NetworkStatus.wifiBars(-127))
    }

    @Test
    fun everyNetworkStateHasItsOwnIcon() {
        val icons = listOf(NetworkStatus.Offline, NetworkStatus.Ethernet) + (0..4).map { NetworkStatus.Wifi(it) }
        assertEquals(icons.size, icons.map { it.iconRes }.toSet().size)
    }
}
