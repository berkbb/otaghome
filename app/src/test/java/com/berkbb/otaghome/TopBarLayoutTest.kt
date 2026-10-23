package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopBarLayoutTest {

    // Pixel sizes of a 1080p TV (2 px per dp): clock card 420, tabs 696, gap 48
    private val clock = 420
    private val gap = 48

    @Test
    fun tabsStayAtTheScreenCentreWhenTheyFit() {
        // Four buttons: 4 × 96 + 3 × 24
        val placement = placeTabs(barWidth = 1728, clockWidth = clock, buttonsWidth = 456, tabsWidth = 696, gap = gap)
        assertEquals(1f, placement.scale, 0f)
        assertEquals((1728 - 696) / 2f, placement.x, 0.01f)
    }

    @Test
    fun tabsMoveLeftBeforeTheyShrink() {
        // Five buttons leave room for the tabs, but not around the screen's centre
        val placement = placeTabs(barWidth = 1728, clockWidth = clock, buttonsWidth = 576, tabsWidth = 600, gap = gap)
        assertEquals(1f, placement.scale, 0f)
        assertEquals(1728f - 576 - gap - 600, placement.x, 0.01f)
    }

    @Test
    fun tabsShrinkToTheFreeSpaceAndNeverOverlap() {
        val placement = placeTabs(barWidth = 1728, clockWidth = clock, buttonsWidth = 576, tabsWidth = 696, gap = gap)
        val free = 1728f - 576 - gap - (clock + gap)
        assertTrue(placement.scale < 1f)
        assertEquals(free, 696 * placement.scale, 0.01f)
        assertEquals((clock + gap).toFloat(), placement.x, 0.01f)
    }

    @Test
    fun noRoomAtAllScalesToZeroInsteadOfOverlapping() {
        val placement = placeTabs(barWidth = 800, clockWidth = clock, buttonsWidth = 576, tabsWidth = 696, gap = gap)
        assertEquals(0f, placement.scale, 0f)
        assertEquals((clock + gap).toFloat(), placement.x, 0.01f)
    }
}
