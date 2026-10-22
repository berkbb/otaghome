package com.berkbb.otaghome

import kotlin.math.abs

/**
 * Where the top bar's controls sit across the screen, so Up from the first row of cards goes to
 * the control right above the card: the tabs from the middle, the buttons from the right. The tabs
 * count as one target that focuses the selected tab, so going up never switches the tab.
 */
internal class UpTargets {
    private val targets = mutableMapOf<String, Pair<Float, () -> Unit>>()

    /** [centerX] in the root's coordinates; [focus] moves the focus there. */
    fun place(key: String, centerX: Float, focus: () -> Unit) {
        targets[key] = centerX to focus
    }

    /** The key of the control nearest to [x] across, or null before the bar is laid out. */
    fun nearestKey(x: Float): String? = targets.minByOrNull { abs(it.value.first - x) }?.key

    /** Focuses the control above [x]; false when there is none yet or it could not take focus. */
    fun focusAbove(x: Float): Boolean {
        val key = nearestKey(x) ?: return false
        return runCatching { targets.getValue(key).second() }.isSuccess
    }
}
