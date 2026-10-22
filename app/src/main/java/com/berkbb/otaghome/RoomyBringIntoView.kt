package com.berkbb.otaghome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls a focused card into view with room to spare, so it never sits under a faded edge or
 * gets its lifted, scaled-up frame clipped; a card taller than the room is aligned to the top.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class RoomyBringIntoView(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - margin
        val end = offset + size + margin
        return when {
            start < 0f -> start
            end > containerSize -> minOf(end - containerSize, start)
            else -> 0f
        }
    }
}
