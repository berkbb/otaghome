package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomyBringIntoViewTest {

    private val spec = RoomyBringIntoView(margin = 40f)

    @Test
    fun aCardWellInsideStaysPut() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 100f, size = 200f, containerSize = 600f))
    }

    @Test
    fun aCardNearTheTopIsPulledDownToLeaveRoom() {
        // The top row touching the edge would sit under the fade: scroll back so 40 px show above it
        assertEquals(-30f, spec.calculateScrollDistance(offset = 10f, size = 200f, containerSize = 600f))
    }

    @Test
    fun aCardNearTheBottomIsPushedUpToLeaveRoom() {
        assertEquals(40f, spec.calculateScrollDistance(offset = 400f, size = 200f, containerSize = 600f))
    }

    @Test
    fun aCardTallerThanTheRoomIsAlignedToTheTop() {
        assertEquals(60f, spec.calculateScrollDistance(offset = 100f, size = 700f, containerSize = 600f))
    }
}
