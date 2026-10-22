package com.berkbb.otaghome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Asks for the full-screen progress ring while a change redraws the screen. */
val LocalBusy = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Drives the progress ring: [pulse] shows it for a short while after a change that redraws the
 * whole screen (theme, colours, background, card size, a restored backup); [holding] keeps it up
 * as long as something is still loading, such as the apps after the language changed.
 */
@Composable
internal fun rememberBusy(holding: Boolean): Pair<Boolean, () -> Unit> {
    var pulses by remember { mutableIntStateOf(0) }
    var pulsing by remember { mutableStateOf(false) }
    LaunchedEffect(pulses) {
        if (pulses == 0) return@LaunchedEffect
        pulsing = true
        delay(BUSY_PULSE_MILLIS)
        pulsing = false
    }
    return (holding || pulsing) to { pulses++ }
}

private const val BUSY_PULSE_MILLIS = 450L
private const val BUSY_FADE_MILLIS = 220

/**
 * Covers the screen with the theme's ground, the logo and a spinning accent ring while it
 * changes, so nothing half-drawn flickers; it fades in and out, and gently grows away as it goes,
 * as AynaCast's preparing card does.
 */
@Composable
internal fun BusyOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(BUSY_FADE_MILLIS)),
        exit = fadeOut(tween(BUSY_FADE_MILLIS)) + scaleOut(tween(BUSY_FADE_MILLIS), targetScale = 1.06f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(96.dp), color = palette.accent, strokeWidth = 5.dp)
                    Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(58.dp))
                }
                Text(text = stringResource(R.string.busy_preparing), color = palette.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
