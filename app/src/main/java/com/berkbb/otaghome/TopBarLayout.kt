package com.berkbb.otaghome

/** Where the tabs go in the top bar: left edge in pixels and the scale they are drawn at. */
internal data class TabsPlacement(val x: Float, val scale: Float)

/**
 * Places the tabs between the clock (left) and the buttons (right), keeping [gap] from both.
 * They sit at the screen's centre while that fits, otherwise in the middle of the free space;
 * when even that is too narrow (small screen, large font, a long language) they are scaled down
 * just enough to fit.
 */
internal fun placeTabs(barWidth: Int, clockWidth: Int, buttonsWidth: Int, tabsWidth: Int, gap: Int): TabsPlacement {
    val left = (clockWidth + gap).toFloat()
    val right = (barWidth - buttonsWidth - gap).toFloat()
    val room = (right - left).coerceAtLeast(0f)
    val scale = if (tabsWidth <= 0) 1f else minOf(1f, room / tabsWidth)
    val shown = tabsWidth * scale
    val centred = (barWidth - shown) / 2f
    return TabsPlacement(x = centred.coerceIn(left, maxOf(left, right - shown)), scale = scale)
}
