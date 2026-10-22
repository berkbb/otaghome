package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeTimerTest {

    @Test
    fun everyOptionIsKeptAsStored() {
        assertEquals(NoticeTimer.OPTIONS, NoticeTimer.OPTIONS.map(NoticeTimer::fromStored))
        assertEquals(NoticeTimer.OPTIONS.sorted(), NoticeTimer.OPTIONS)
    }

    @Test
    fun unknownStoredDurationsFallBackToTheDefault() {
        assertEquals(5, NoticeTimer.fromStored(null))
        assertEquals(5, NoticeTimer.fromStored(7))
        assertEquals(15, NoticeTimer.fromStored(15))
    }
}
