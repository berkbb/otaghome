package com.berkbb.otaghome

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * Docked Pitwall-styled media bar at the bottom of the home screen while background audio plays.
 * Shows full song info and controls with Notification access, or basic Play/Pause/Stop controls
 * with a shortcut to enable song details when Notification access is off.
 */
@Composable
internal fun NowPlayingBar(
    nowPlaying: NowPlaying?,
    visible: Boolean,
    onOpenApp: () -> Unit,
    onOpenAccessSetup: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSwitchSession: () -> Unit,
    onToggleMute: () -> Unit,
    onStopOrDismiss: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val last = remember { mutableStateOf<NowPlaying?>(null) }
    SideEffect { if (nowPlaying != null) last.value = nowPlaying }
    val shown = nowPlaying ?: last.value

    AnimatedVisibility(
        visible = visible && shown != null,
        enter = expandVertically(tween(ENTER_MILLIS), expandFrom = Alignment.Top) + fadeIn(tween(ENTER_MILLIS)),
        exit = shrinkVertically(tween(EXIT_MILLIS), shrinkTowards = Alignment.Top) + fadeOut(tween(EXIT_MILLIS)),
        modifier = modifier
    ) {
        if (shown != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 20.dp)
                    .shadow(12.dp, Corners.large)
                    .clip(Corners.large)
                    .background(palette.card)
                    .onFocusChanged { onFocusChange(it.hasFocus) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(palette.accent, RoundedCornerShape(2.dp))
                )

                when (shown) {
                    is NowPlaying.Session -> {
                        val headline = shown.headline ?: shown.snapshot.appLabel
                        val openLabel = stringResource(R.string.cd_media_open, shown.snapshot.appLabel)
                        MediaInfoSection(
                            artwork = shown.artwork,
                            headline = headline,
                            subtitle = shown.subtitle,
                            contentDescription = openLabel,
                            onClick = onOpenApp,
                            modifier = Modifier.weight(1f)
                        )
                        if (shown.snapshot.canSkipPrevious) {
                            MediaControlButton(
                                iconRes = R.drawable.ic_skip_previous,
                                contentDescription = stringResource(R.string.cd_media_previous),
                                onClick = onPrevious
                            )
                        }
                        if (shown.snapshot.canPlay || shown.snapshot.canPause) {
                            val active = shown.snapshot.state == MediaState.PLAYING ||
                                shown.snapshot.state == MediaState.BUFFERING
                            MediaControlButton(
                                iconRes = if (active) R.drawable.ic_pause else R.drawable.ic_play,
                                contentDescription = stringResource(if (active) R.string.cd_media_pause else R.string.cd_media_play),
                                buffering = shown.snapshot.state == MediaState.BUFFERING,
                                onClick = onPlayPause
                            )
                        }
                        if (shown.snapshot.canSkipNext) {
                            MediaControlButton(
                                iconRes = R.drawable.ic_skip_next,
                                contentDescription = stringResource(R.string.cd_media_next),
                                onClick = onNext
                            )
                        }
                        if (shown.sessionCount > 1) {
                            MediaControlButton(
                                iconRes = R.drawable.ic_swap_horiz,
                                contentDescription = stringResource(
                                    R.string.cd_media_switch,
                                    shown.sessionIndex + 1,
                                    shown.sessionCount
                                ),
                                onClick = onSwitchSession
                            )
                        }
                        MediaControlButton(
                            iconRes = if (shown.isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                            contentDescription = stringResource(
                                if (shown.isMuted) R.string.cd_media_unmute else R.string.cd_media_mute
                            ),
                            onClick = onToggleMute
                        )
                        when {
                            shown.snapshot.canStop -> MediaControlButton(
                                iconRes = R.drawable.ic_stop,
                                contentDescription = stringResource(R.string.cd_media_stop),
                                onClick = onStopOrDismiss
                            )
                            shown.snapshot.state == MediaState.PAUSED -> MediaControlButton(
                                iconRes = R.drawable.ic_close,
                                contentDescription = stringResource(R.string.btn_close),
                                onClick = onStopOrDismiss
                            )
                        }
                    }
                    is NowPlaying.Fallback -> {
                        val headline = stringResource(
                            if (shown.isPlaying) R.string.media_playing else R.string.media_paused
                        )
                        val hint = stringResource(R.string.media_fallback_hint)
                        MediaInfoSection(
                            artwork = null,
                            headline = headline,
                            subtitle = hint,
                            contentDescription = hint,
                            onClick = onOpenAccessSetup,
                            modifier = Modifier.weight(1f)
                        )
                        MediaControlButton(
                            iconRes = if (shown.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = stringResource(
                                if (shown.isPlaying) R.string.cd_media_pause else R.string.cd_media_play
                            ),
                            onClick = onPlayPause
                        )
                        MediaControlButton(
                            iconRes = if (shown.isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                            contentDescription = stringResource(
                                if (shown.isMuted) R.string.cd_media_unmute else R.string.cd_media_mute
                            ),
                            onClick = onToggleMute
                        )
                        MediaControlButton(
                            iconRes = if (shown.isPlaying) R.drawable.ic_stop else R.drawable.ic_close,
                            contentDescription = stringResource(
                                if (shown.isPlaying) R.string.cd_media_stop else R.string.btn_close
                            ),
                            onClick = onStopOrDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    artwork: ImageBitmap?,
    headline: String,
    subtitle: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primary = if (isFocused) palette.onFocus else palette.textPrimary
    val secondary = if (isFocused) palette.onFocus.copy(alpha = 0.78f) else palette.textSecondary

    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(Corners.medium)
            .background(if (isFocused) palette.focus else Color.Transparent)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(Corners.small)
                .background(
                    if (isFocused) palette.onFocus.copy(alpha = 0.14f)
                    else palette.accent.copy(alpha = 0.16f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = if (isFocused) palette.onFocus else palette.accent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            key(headline, isFocused) {
                Text(
                    text = headline,
                    color = primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(
                        iterations = 2,
                        initialDelayMillis = if (isFocused) 600 else 1500
                    )
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaControlButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    buffering: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = Corners.medium
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (isFocused) palette.focus else palette.background)
            .then(if (isFocused) Modifier.border(3.dp, palette.focus, shape) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = if (isFocused) palette.onFocus else palette.accent,
                trackColor = Color.Transparent,
                strokeWidth = 2.dp
            )
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isFocused) palette.onFocus else palette.textPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Settings → Display row for Now Playing media controls. Shows Off, Basic (without Notification
 * access), or On (with full song info), and opens the setup dialog when access is still missing.
 */
@Composable
internal fun MediaControlsRow(viewModel: LauncherViewModel) {
    val display by viewModel.display.collectAsState()
    val hasAccess by viewModel.hasMediaAccess.collectAsState()
    var explaining by remember { mutableStateOf(false) }

    SettingsRow(
        iconRes = R.drawable.ic_music_note,
        title = stringResource(R.string.settings_media_controls),
        value = stringResource(
            when {
                !display.mediaControls -> R.string.state_off
                hasAccess -> R.string.state_on
                else -> R.string.media_state_basic
            }
        ),
        onClick = {
            when {
                !display.mediaControls -> {
                    viewModel.setMediaControls(true)
                    if (!hasAccess) explaining = true
                }
                !hasAccess -> explaining = true
                else -> viewModel.setMediaControls(false)
            }
        }
    )

    if (explaining) {
        MediaControlsSetupDialog(
            viewModel = viewModel,
            showTurnOff = true,
            onDismiss = { explaining = false }
        )
    }
}

/**
 * Explains Notification access for full song metadata and offers the best path on this TV:
 * direct switch on API 26–27 with `WRITE_SECURE_SETTINGS`, the TV's Notification access screen,
 * or the exact adb command on TVs without that screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaControlsSetupDialog(
    viewModel: LauncherViewModel,
    showTurnOff: Boolean,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val notify = LocalNotice.current
    val open = rememberOpener()
    val firstFocus = remember { FocusRequester() }

    val direct = MediaAccess.canSwitchDirectly(Build.VERSION.SDK_INT, context.canSwitchHomeOverride())
    val accessIntent = remember(context) { context.notificationAccessScreen() }
    val accessLabel = stringResource(R.string.settings_notification_access)
    val failedMessage = stringResource(R.string.msg_media_access_failed)

    val onTurnOn: (() -> Unit)? = when {
        direct -> {
            {
                if (context.turnOnNotificationAccessDirect()) {
                    viewModel.setMediaControls(true)
                    viewModel.refreshMedia()
                } else {
                    notify(Notice(failedMessage, R.drawable.ic_error))
                }
                onDismiss()
            }
        }
        accessIntent != null -> {
            {
                viewModel.setMediaControls(true)
                open(accessIntent, accessLabel)
                onDismiss()
            }
        }
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(560.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(palette.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.media_dialog_title),
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.media_dialog_text),
                color = palette.textSecondary,
                fontSize = 16.sp,
                lineHeight = 23.sp
            )
            when {
                direct -> Unit
                onTurnOn != null -> Text(
                    text = stringResource(R.string.media_steps_screen),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    lineHeight = 23.sp
                )
                else -> {
                    Text(
                        text = stringResource(R.string.media_steps_adb),
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        lineHeight = 23.sp
                    )
                    Text(
                        text = context.mediaAccessAdbCommand(),
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Corners.medium)
                            .background(palette.card)
                            .padding(12.dp)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showTurnOff) {
                    PillButton(
                        text = stringResource(R.string.media_turn_off),
                        primary = false,
                        onClick = {
                            viewModel.setMediaControls(false)
                            onDismiss()
                        }
                    )
                }
                PillButton(
                    text = stringResource(if (onTurnOn != null) R.string.btn_cancel else R.string.btn_close),
                    primary = false,
                    modifier = if (onTurnOn == null) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = onDismiss
                )
                if (onTurnOn != null) {
                    PillButton(
                        text = stringResource(if (direct) R.string.media_access_allow else R.string.media_access_open),
                        primary = true,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = onTurnOn
                    )
                }
            }
        }
    }
}

private const val ENTER_MILLIS = 250
private const val EXIT_MILLIS = 200
