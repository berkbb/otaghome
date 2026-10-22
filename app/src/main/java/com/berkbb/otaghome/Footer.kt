package com.berkbb.otaghome

import android.content.Context
import android.os.SystemClock
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween

/** A footer message with the Material icon shown before it. */
data class Notice(val text: String, @param:DrawableRes val iconRes: Int)

/** Shows a short message in the footer bar; provided by the launcher screen. */
val LocalNotice = staticCompositionLocalOf<(Notice) -> Unit> { {} }

/** Starts a screen of another app; false when this TV has none for it. */
internal fun Context.tryStart(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

/** Opens a system screen, or tells the user in the footer that [label] could not be opened. */
@Composable
internal fun rememberOpener(): (Intent, String) -> Unit {
    val context = LocalContext.current
    val notify = LocalNotice.current
    return remember(context, notify) {
        { intent, label ->
            if (!context.tryStart(intent)) notify(Notice(context.getString(R.string.msg_launch_failed, label), R.drawable.ic_error))
        }
    }
}

/** How long a footer message stays, chosen in Settings. */
object NoticeTimer {
    val OPTIONS = listOf(3, 5, 8, 10, 15, 30)
    const val DEFAULT = 5

    /** A stored value that is not one of the options (or nothing stored) gives the default. */
    fun fromStored(seconds: Int?): Int = seconds?.takeIf { it in OPTIONS } ?: DEFAULT
}

/**
 * Full-width bar that slides up from the bottom while there is something to say (a favorite added,
 * an app being moved, a screen that could not open) and hides otherwise, each message led by its
 * Material icon. It is a theme surface like the cards and panels, lifted by a soft shadow and an
 * accent line along its top. On the right a close button is ringed by the countdown, with the
 * seconds left beside it; a message without [durationMillis] (the move instructions) has no
 * countdown and stays until closed or done.
 */
@Composable
internal fun FooterBar(
    notice: Notice?,
    noticeKey: Long?,
    durationMillis: Long?,
    onClose: () -> Unit,
    /** Called when the countdown reaches zero; the countdown is the only clock, so "1 s" really is the last second. */
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    // Keeps the last message on screen while the bar slides away
    val last = remember { mutableStateOf<Notice?>(null) }
    SideEffect { if (notice != null) last.value = notice }
    val shown = notice ?: last.value
    // The countdown runs on the clock, not as an animation: with animations turned off or sped up
    // on the TV, an animation would end at once and close the message before its time
    var left by remember { mutableFloatStateOf(1f) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(noticeKey, durationMillis) {
        left = 1f
        if (notice != null && durationMillis != null) {
            val start = SystemClock.uptimeMillis()
            while (true) {
                val elapsed = SystemClock.uptimeMillis() - start
                left = (1f - elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                if (elapsed >= durationMillis) break
                delay(TICK_MILLIS)
            }
            timeout()
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        // Grows from the bottom so the apps above give it room smoothly; short, fixed timings so the
        // bar is gone right after the countdown ends
        enter = expandVertically(tween(ENTER_MILLIS), expandFrom = Alignment.Top) + fadeIn(tween(ENTER_MILLIS)),
        exit = shrinkVertically(tween(EXIT_MILLIS), shrinkTowards = Alignment.Top) + fadeOut(tween(EXIT_MILLIS)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(palette.accent))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .padding(start = 48.dp, end = 32.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shown != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(palette.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(shown.iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                }
                Text(
                    text = shown?.text.orEmpty(),
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (durationMillis != null) {
                    Text(
                        text = stringResource(R.string.notice_seconds, ceil(left * durationMillis / 1000f).toInt().coerceAtLeast(1)),
                        color = palette.textSecondary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
                CloseRing(progress = if (durationMillis != null) left else null, onClose = onClose)
            }
        }
    }
}

private const val ENTER_MILLIS = 250
/** How often the countdown ring and seconds are updated. */
private const val TICK_MILLIS = 50L
private const val EXIT_MILLIS = 200

/** The close button; [progress] (1 → 0) draws the countdown as a shrinking accent ring around it. */
@Composable
private fun CloseRing(progress: Float?, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val label = stringResource(R.string.btn_close)
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = palette.accent,
                trackColor = palette.textSecondary.copy(alpha = 0.2f),
                strokeWidth = 3.dp
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isFocused) palette.focus else palette.background)
                .semantics { contentDescription = label }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✕", color = if (isFocused) palette.onFocus else palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
