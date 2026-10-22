package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpTargetsTest {

    private fun bar(log: MutableList<String> = mutableListOf()) = UpTargets().apply {
        // Laid out like the top bar at 1920 px: tabs in the middle, four buttons on the right
        place("tabs", 960f) { log += "tabs" }
        listOf(1416f, 1536f, 1656f, 1776f).forEachIndexed { i, x -> place("button$i", x) { log += "button$i" } }
    }

    @Test
    fun cardsUnderTheTabsGoToTheTabsAndCardsOnTheRightToTheButtons() {
        val targets = bar()
        assertEquals("tabs", targets.nearestKey(250f))
        assertEquals("tabs", targets.nearestKey(1000f))
        assertEquals("button0", targets.nearestKey(1400f))
        assertEquals("button3", targets.nearestKey(1820f))
    }

    @Test
    fun focusAboveRunsTheNearestControlsFocus() {
        val log = mutableListOf<String>()
        assertTrue(bar(log).focusAbove(1650f))
        assertEquals(listOf("button2"), log)
    }

    @Test
    fun nothingToFocusBeforeTheBarIsLaidOut() {
        assertNull(UpTargets().nearestKey(100f))
        assertFalse(UpTargets().focusAbove(100f))
    }
}
