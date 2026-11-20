package com.berkbb.otaghome

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Simplified playback state of a background media session. */
enum class MediaState { PLAYING, BUFFERING, PAUSED, STOPPED }

/** Pure snapshot of a single background media session. */
data class MediaSnapshot(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val state: MediaState,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val canStop: Boolean
)

/** Formatted display lines for a [MediaSnapshot]. */
data class MediaText(val headline: String?, val subtitle: String?)

/** Pure media-session selection, formatting and scaling rules, unit-tested without Android. */
object NowPlayingLogic {

    fun mapState(playbackState: Int?): MediaState = when (playbackState) {
        PlaybackState.STATE_PLAYING -> MediaState.PLAYING
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> MediaState.BUFFERING
        PlaybackState.STATE_PAUSED -> MediaState.PAUSED
        else -> MediaState.STOPPED
    }

    fun canPlay(actions: Long): Boolean =
        (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L

    fun canPause(actions: Long): Boolean =
        (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) != 0L

    fun canSkipPrevious(actions: Long): Boolean =
        (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L

    fun canSkipNext(actions: Long): Boolean =
        (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

    fun canStop(actions: Long): Boolean =
        (actions and PlaybackState.ACTION_STOP) != 0L

    /**
     * Eligible sessions ordered stably by priority: playing first, then buffering, then paused.
     * Excludes OtagHome itself, stopped sessions, and paused sessions the user dismissed.
     */
    fun orderedSessions(
        sessions: List<MediaSnapshot>,
        ownPackage: String,
        dismissedPausedIds: Set<String> = emptySet()
    ): List<MediaSnapshot> {
        val eligible = sessions.filter { session ->
            session.packageName != ownPackage &&
                session.state != MediaState.STOPPED &&
                !(session.state == MediaState.PAUSED && session.id in dismissedPausedIds)
        }
        return eligible.filter { it.state == MediaState.PLAYING } +
            eligible.filter { it.state == MediaState.BUFFERING } +
            eligible.filter { it.state == MediaState.PAUSED }
    }

    /** Picks the highest-priority eligible session. */
    fun pickSession(
        sessions: List<MediaSnapshot>,
        ownPackage: String,
        dismissedPausedIds: Set<String> = emptySet()
    ): MediaSnapshot? = orderedSessions(sessions, ownPackage, dismissedPausedIds).firstOrNull()

    /** Picks [preferredId] from [ordered] if still present, otherwise the first session, paired with its 0-based index. */
    fun pickSelectedSession(ordered: List<MediaSnapshot>, preferredId: String?): Pair<MediaSnapshot, Int>? {
        if (ordered.isEmpty()) return null
        val idx = if (preferredId != null) ordered.indexOfFirst { it.id == preferredId } else -1
        val chosen = if (idx >= 0) idx else 0
        return ordered[chosen] to chosen
    }

    /** Cycles to the next session ID after [currentId] in [ordered]. */
    fun nextSessionId(ordered: List<MediaSnapshot>, currentId: String?): String? {
        if (ordered.isEmpty()) return null
        val idx = if (currentId != null) ordered.indexOfFirst { it.id == currentId } else -1
        val next = if (idx >= 0) (idx + 1) % ordered.size else 0
        return ordered[next].id
    }

    /** Keeps dismissed IDs only while that session still exists in the PAUSED state. */
    fun pruneDismissed(dismissedPausedIds: Set<String>, sessions: List<MediaSnapshot>): Set<String> {
        if (dismissedPausedIds.isEmpty()) return emptySet()
        val pausedIds = sessions.filter { it.state == MediaState.PAUSED }.mapTo(mutableSetOf()) { it.id }
        return dismissedPausedIds.filterTo(mutableSetOf()) { it in pausedIds }
    }

    /** Formats the primary line and a deduplicated secondary line ("artist · app"). */
    fun formatDisplay(title: String, artist: String, appLabel: String): MediaText {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanApp = appLabel.trim()
        val headline = cleanTitle.ifEmpty { cleanArtist }.ifEmpty { cleanApp }.ifEmpty { null }
        val subtitleParts = mutableListOf<String>()
        for (candidate in listOf(cleanArtist, cleanApp)) {
            if (candidate.isEmpty()) continue
            if (headline != null && candidate.equals(headline, ignoreCase = true)) continue
            if (subtitleParts.any { it.equals(candidate, ignoreCase = true) }) continue
            subtitleParts.add(candidate)
        }
        val subtitle = subtitleParts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        return MediaText(headline = headline, subtitle = subtitle)
    }

    /** Target dimensions that fit within [maxEdge] while preserving aspect ratio. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 0 to 0
        if (width <= maxEdge && height <= maxEdge) return width to height
        val ratio = maxEdge.toDouble() / maxOf(width, height)
        val scaledWidth = if (width >= height) maxEdge else (width * ratio).roundToInt().coerceIn(1, maxEdge)
        val scaledHeight = if (height >= width) maxEdge else (height * ratio).roundToInt().coerceIn(1, maxEdge)
        return scaledWidth to scaledHeight
    }
}

/** Pure helpers for Notification Access settings and platform capability checks. */
object MediaAccess {
    const val ENABLED_LISTENERS_KEY = "enabled_notification_listeners"

    fun hasListener(enabled: String?, service: String): Boolean =
        enabled.orEmpty().split(':').any { it.isNotBlank() && sameComponent(it, service) }

    fun withoutListener(enabled: String?, service: String): String =
        enabled.orEmpty()
            .split(':')
            .filter { it.isNotBlank() && !sameComponent(it, service) }
            .joinToString(":")

    /**
     * Direct `Settings.Secure` writes only sync with NotificationManager on Android 8.0–8.1 (API 26–27).
     * API 28+ requires the Notification Access settings screen or `cmd notification allow_listener`.
     */
    fun canSwitchDirectly(sdkInt: Int, hasWriteSecureSettings: Boolean): Boolean =
        sdkInt < 28 && hasWriteSecureSettings

    fun adbCommand(sdkInt: Int, packageName: String, componentFlat: String): String =
        if (sdkInt >= 28) "adb shell cmd notification allow_listener $componentFlat"
        else "adb shell pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    private fun sameComponent(a: String, b: String): Boolean =
        fullName(a).equals(fullName(b), ignoreCase = true)

    private fun fullName(component: String): String {
        val pkg = component.substringBefore('/')
        val cls = component.substringAfter('/', "")
        return if (cls.startsWith('.')) "$pkg/$pkg$cls" else component
    }
}

internal fun Context.mediaListenerComponent(): ComponentName =
    ComponentName(this, MediaListenerService::class.java)

internal fun Context.hasNotificationAccess(): Boolean {
    val component = mediaListenerComponent()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return runCatching {
            getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component) == true
        }.getOrDefault(false)
    }
    val enabled = runCatching {
        Settings.Secure.getString(contentResolver, MediaAccess.ENABLED_LISTENERS_KEY)
    }.getOrNull()
    return MediaAccess.hasListener(enabled, component.flattenToString())
}

/** Notification Access screen in TV settings, or null when absent or backed by Google's stub. */
internal fun Context.notificationAccessScreen(): Intent? {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    val handler = runCatching {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()
    return intent.takeIf { HomeOverride.isRealScreen(handler) }
}

/** Turns on Notification Access directly on API 26–27 when allowed via `WRITE_SECURE_SETTINGS`. */
internal fun Context.turnOnNotificationAccessDirect(): Boolean {
    if (!MediaAccess.canSwitchDirectly(Build.VERSION.SDK_INT, canSwitchHomeOverride())) return false
    return runCatching {
        val service = mediaListenerComponent().flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, MediaAccess.ENABLED_LISTENERS_KEY)
        Settings.Secure.putString(
            contentResolver,
            MediaAccess.ENABLED_LISTENERS_KEY,
            HomeOverride.withService(enabled, service)
        )
    }.isSuccess
}

internal fun Context.mediaAccessAdbCommand(): String =
    MediaAccess.adbCommand(Build.VERSION.SDK_INT, packageName, mediaListenerComponent().flattenToString())

/**
 * Minimal NotificationListenerService required by Android so OtagHome can query
 * `MediaSessionManager.getActiveSessions`. Reads no notifications and stores nothing.
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }

    override fun onListenerConnected() {
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        _connected.value = false
    }

    override fun onDestroy() {
        _connected.value = false
        super.onDestroy()
    }
}

/** What the home screen's media bar currently displays. */
sealed interface NowPlaying {
    val isMuted: Boolean

    data class Session(
        val snapshot: MediaSnapshot,
        val headline: String?,
        val subtitle: String?,
        val artwork: ImageBitmap?,
        val sessionIndex: Int = 0,
        val sessionCount: Int = 1,
        override val isMuted: Boolean = false
    ) : NowPlaying

    data class Fallback(
        val isPlaying: Boolean,
        override val isMuted: Boolean = false
    ) : NowPlaying
}

/**
 * Watches background media playback on the main thread. Uses `MediaSessionManager` when Notification
 * Access is granted, or falls back to `AudioManager.AudioPlaybackCallback` + media key events so
 * basic Play/Pause/Stop controls work out of the box without special permissions.
 */
internal class NowPlayingMonitor(context: Context) {

    private data class ArtworkKey(
        val id: String,
        val packageName: String,
        val title: String,
        val artist: String,
        val bitmapIdentity: Int,
        val width: Int,
        val height: Int
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val listenerComponent = appContext.mediaListenerComponent()

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val _hasAccess = MutableStateFlow(appContext.hasNotificationAccess())
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private var enabled = false
    private var foreground = false
    private var sessionListenerRegistered = false
    private var audioCallbackRegistered = false
    private var volumeReceiverRegistered = false
    private var activeController: MediaController? = null
    private var trackedControllers: List<Pair<MediaController, MediaController.Callback>> = emptyList()
    private var dismissedPausedIds: Set<String> = emptySet()
    private var preferredSessionId: String? = null
    private val appLabelCache = mutableMapOf<String, String>()
    private var cachedArtworkKey: ArtworkKey? = null
    private var cachedArtwork: ImageBitmap? = null

    private var fallbackPausedByUser = false
    private var lastFallbackUserActionAt = 0L

    private val fallbackRecheck = Runnable {
        if (enabled && foreground && !sessionListenerRegistered) {
            evaluateFallback(fromRefresh = false)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (enabled && foreground && sessionListenerRegistered) {
            applyControllers(controllers)
        }
    }

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (!enabled || !foreground || sessionListenerRegistered) return
            evaluateFallback(fromRefresh = false)
            mainHandler.removeCallbacks(fallbackRecheck)
            mainHandler.postDelayed(fallbackRecheck, FALLBACK_RECHECK_MS)
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (enabled && foreground) refreshMuteState()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) {
            stopMonitoring()
            _state.value = null
            _hasAccess.value = appContext.hasNotificationAccess()
        } else {
            refresh()
        }
    }

    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        if (foreground) {
            refresh()
        } else {
            pauseMonitoring()
        }
    }

    fun refresh() {
        val access = appContext.hasNotificationAccess()
        _hasAccess.value = access
        if (!enabled) {
            stopMonitoring()
            _state.value = null
            return
        }
        if (!foreground) {
            pauseMonitoring()
            return
        }
        registerVolumeReceiver()
        if (access) {
            unregisterAudioCallback()
            if (!sessionListenerRegistered) {
                if (!registerSessionListener()) {
                    _hasAccess.value = false
                    registerAudioCallback()
                    evaluateFallback(fromRefresh = true)
                }
            } else {
                val current = runCatching { mediaSessionManager?.getActiveSessions(listenerComponent) }.getOrNull()
                if (current != null) {
                    applyControllers(current)
                } else {
                    unregisterSessionListener()
                    _hasAccess.value = false
                    registerAudioCallback()
                    evaluateFallback(fromRefresh = true)
                }
            }
        } else {
            unregisterSessionListener()
            registerAudioCallback()
            evaluateFallback(fromRefresh = true)
        }
    }

    fun togglePlayPause() {
        val current = _state.value ?: return
        when (current) {
            is NowPlaying.Session -> {
                val controls = activeController?.transportControls ?: return
                when (current.snapshot.state) {
                    MediaState.PLAYING, MediaState.BUFFERING -> {
                        if (current.snapshot.canPause) controls.pause()
                        else if (current.snapshot.canStop) controls.stop()
                    }
                    MediaState.PAUSED -> if (current.snapshot.canPlay) controls.play()
                    MediaState.STOPPED -> Unit
                }
            }
            is NowPlaying.Fallback -> {
                val nextPlaying = !current.isPlaying
                fallbackPausedByUser = !nextPlaying
                lastFallbackUserActionAt = SystemClock.uptimeMillis()
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                _state.value = NowPlaying.Fallback(isPlaying = nextPlaying, isMuted = isStreamMuted())
                mainHandler.removeCallbacks(fallbackRecheck)
                mainHandler.postDelayed(fallbackRecheck, FALLBACK_ACTION_RECHECK_MS)
            }
        }
    }

    fun skipPrevious() {
        if (_state.value is NowPlaying.Session) {
            activeController?.transportControls?.skipToPrevious()
        }
    }

    fun skipNext() {
        if (_state.value is NowPlaying.Session) {
            activeController?.transportControls?.skipToNext()
        }
    }

    fun switchSession() {
        if (!enabled || !foreground || !sessionListenerRegistered) return
        val pairs = trackedControllers.map { (controller, _) -> controller to toSnapshot(controller) }
        val ordered = NowPlayingLogic.orderedSessions(pairs.map { it.second }, appContext.packageName, dismissedPausedIds)
        if (ordered.size <= 1) return
        val currentId = (_state.value as? NowPlaying.Session)?.snapshot?.id ?: preferredSessionId
        preferredSessionId = NowPlayingLogic.nextSessionId(ordered, currentId)
        evaluateTrackedControllers()
    }

    fun toggleMute() {
        runCatching {
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        refreshMuteState()
    }

    fun stopOrDismiss() {
        val current = _state.value ?: return
        when (current) {
            is NowPlaying.Session -> {
                val snapshot = current.snapshot
                if (snapshot.canStop) {
                    activeController?.transportControls?.stop()
                }
                if (snapshot.state == MediaState.PAUSED || !snapshot.canStop) {
                    dismissedPausedIds = dismissedPausedIds + snapshot.id
                    evaluateTrackedControllers()
                }
            }
            is NowPlaying.Fallback -> {
                if (current.isPlaying) {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
                }
                fallbackPausedByUser = false
                lastFallbackUserActionAt = 0L
                mainHandler.removeCallbacks(fallbackRecheck)
                _state.value = null
            }
        }
    }

    fun openApp(): Boolean {
        val session = _state.value as? NowPlaying.Session ?: return false
        val pending = activeController?.sessionActivity
        if (pending != null && sendSessionActivity(pending)) return true
        val pm = appContext.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(session.snapshot.packageName)
            ?: pm.getLaunchIntentForPackage(session.snapshot.packageName)
            ?: return false
        return runCatching {
            appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun clear() {
        enabled = false
        foreground = false
        stopMonitoring()
        _state.value = null
    }

    private fun pauseMonitoring() {
        unregisterSessionListener()
        unregisterAudioCallback()
        unregisterVolumeReceiver()
        mainHandler.removeCallbacks(fallbackRecheck)
    }

    private fun stopMonitoring() {
        pauseMonitoring()
        fallbackPausedByUser = false
        lastFallbackUserActionAt = 0L
        dismissedPausedIds = emptySet()
        preferredSessionId = null
        cachedArtworkKey = null
        cachedArtwork = null
    }

    private fun registerSessionListener(): Boolean {
        val mgr = mediaSessionManager ?: return false
        return runCatching {
            mgr.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, mainHandler)
            sessionListenerRegistered = true
            val current = mgr.getActiveSessions(listenerComponent)
            applyControllers(current)
            true
        }.getOrElse {
            unregisterSessionListener()
            false
        }
    }

    private fun unregisterSessionListener() {
        clearTrackedControllers()
        if (sessionListenerRegistered) {
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            sessionListenerRegistered = false
        }
        activeController = null
    }

    private fun registerAudioCallback() {
        if (audioCallbackRegistered) return
        val mgr = audioManager ?: return
        runCatching {
            mgr.registerAudioPlaybackCallback(audioPlaybackCallback, mainHandler)
            audioCallbackRegistered = true
        }
    }

    private fun unregisterAudioCallback() {
        mainHandler.removeCallbacks(fallbackRecheck)
        if (audioCallbackRegistered) {
            runCatching { audioManager?.unregisterAudioPlaybackCallback(audioPlaybackCallback) }
            audioCallbackRegistered = false
        }
    }

    private fun registerVolumeReceiver() {
        if (volumeReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_STREAM_MUTE_CHANGED)
            addAction(ACTION_VOLUME_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(appContext, volumeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            volumeReceiverRegistered = true
        }
    }

    private fun unregisterVolumeReceiver() {
        if (volumeReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(volumeReceiver) }
            volumeReceiverRegistered = false
        }
    }

    private fun isStreamMuted(): Boolean =
        runCatching { audioManager?.isStreamMute(AudioManager.STREAM_MUSIC) == true }.getOrDefault(false)

    private fun refreshMuteState() {
        val muted = isStreamMuted()
        _state.value = when (val current = _state.value) {
            is NowPlaying.Session -> if (current.isMuted == muted) current else current.copy(isMuted = muted)
            is NowPlaying.Fallback -> if (current.isMuted == muted) current else current.copy(isMuted = muted)
            null -> null
        }
    }

    private fun clearTrackedControllers() {
        trackedControllers.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        trackedControllers = emptyList()
    }

    private fun applyControllers(controllers: List<MediaController>?) {
        clearTrackedControllers()
        val list = controllers.orEmpty()
        trackedControllers = list.map { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (enabled && sessionListenerRegistered) evaluateTrackedControllers()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (enabled && sessionListenerRegistered) evaluateTrackedControllers()
                }

                override fun onSessionDestroyed() {
                    if (!enabled || !sessionListenerRegistered) return
                    runCatching { controller.unregisterCallback(this) }
                    trackedControllers = trackedControllers.filterNot { it.first === controller }
                    evaluateTrackedControllers()
                }
            }
            runCatching { controller.registerCallback(callback, mainHandler) }
            controller to callback
        }
        evaluateTrackedControllers()
    }

    private fun evaluateTrackedControllers() {
        val pairs = trackedControllers.map { (controller, _) -> controller to toSnapshot(controller) }
        val snapshots = pairs.map { it.second }
        dismissedPausedIds = NowPlayingLogic.pruneDismissed(dismissedPausedIds, snapshots)
        val ordered = NowPlayingLogic.orderedSessions(snapshots, appContext.packageName, dismissedPausedIds)
        val selection = NowPlayingLogic.pickSelectedSession(ordered, preferredSessionId)
        if (selection == null) {
            preferredSessionId = null
            activeController = null
            _state.value = null
            return
        }
        val (picked, index) = selection
        preferredSessionId = picked.id
        val controller = pairs.firstOrNull { it.second.id == picked.id }?.first
        activeController = controller
        val metadata = runCatching { controller?.metadata }.getOrNull()
        val display = NowPlayingLogic.formatDisplay(picked.title, picked.artist, picked.appLabel)
        val artwork = extractArtwork(picked, metadata)
        _state.value = NowPlaying.Session(
            snapshot = picked,
            headline = display.headline,
            subtitle = display.subtitle,
            artwork = artwork,
            sessionIndex = index,
            sessionCount = ordered.size,
            isMuted = isStreamMuted()
        )
    }

    private fun toSnapshot(controller: MediaController): MediaSnapshot {
        val pkg = controller.packageName
        val playback = runCatching { controller.playbackState }.getOrNull()
        val metadata = runCatching { controller.metadata }.getOrNull()
        val actions = playback?.actions ?: 0L
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: ""
        return MediaSnapshot(
            id = "$pkg#${controller.sessionToken.hashCode()}",
            packageName = pkg,
            appLabel = appLabel(pkg),
            title = title,
            artist = artist,
            state = NowPlayingLogic.mapState(playback?.state),
            canPlay = NowPlayingLogic.canPlay(actions),
            canPause = NowPlayingLogic.canPause(actions),
            canSkipPrevious = NowPlayingLogic.canSkipPrevious(actions),
            canSkipNext = NowPlayingLogic.canSkipNext(actions),
            canStop = NowPlayingLogic.canStop(actions)
        )
    }

    private fun appLabel(packageName: String): String = appLabelCache.getOrPut(packageName) {
        val pm = appContext.packageManager
        runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: packageName
    }

    private fun extractArtwork(snapshot: MediaSnapshot, metadata: MediaMetadata?): ImageBitmap? {
        val raw = runCatching {
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }.getOrNull()?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        val key = ArtworkKey(
            id = snapshot.id,
            packageName = snapshot.packageName,
            title = snapshot.title,
            artist = snapshot.artist,
            bitmapIdentity = if (raw != null) System.identityHashCode(raw) else 0,
            width = raw?.width ?: 0,
            height = raw?.height ?: 0
        )
        if (key == cachedArtworkKey) return cachedArtwork
        val rendered = if (raw != null) {
            renderMetadataBitmap(raw)
        } else {
            runCatching {
                appContext.packageManager.getApplicationIcon(snapshot.packageName)
                    .toBitmap(FALLBACK_ICON_PX, FALLBACK_ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
        cachedArtworkKey = key
        cachedArtwork = rendered
        return rendered
    }

    private fun renderMetadataBitmap(raw: Bitmap): ImageBitmap? = runCatching {
        val (targetW, targetH) = NowPlayingLogic.scaledSize(raw.width, raw.height, MAX_ARTWORK_PX)
        if (targetW <= 0 || targetH <= 0) return null
        val bitmap = when {
            targetW != raw.width || targetH != raw.height -> Bitmap.createScaledBitmap(raw, targetW, targetH, true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && raw.config == Bitmap.Config.HARDWARE ->
                raw.copy(Bitmap.Config.ARGB_8888, false)
            else -> raw
        }
        bitmap?.asImageBitmap()
    }.getOrNull()

    private fun evaluateFallback(fromRefresh: Boolean) {
        val active = runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)
        val elapsed = SystemClock.uptimeMillis() - lastFallbackUserActionAt
        if (active) {
            if (fallbackPausedByUser && elapsed < FALLBACK_GRACE_MS) {
                return
            }
            fallbackPausedByUser = false
            _state.value = NowPlaying.Fallback(isPlaying = true, isMuted = isStreamMuted())
            return
        }
        if (fromRefresh) {
            fallbackPausedByUser = false
            _state.value = null
            return
        }
        if (fallbackPausedByUser) {
            _state.value = NowPlaying.Fallback(isPlaying = false, isMuted = isStreamMuted())
        } else if (elapsed >= FALLBACK_GRACE_MS) {
            _state.value = null
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val mgr = audioManager ?: return
        runCatching {
            mgr.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            mgr.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun sendSessionActivity(pendingIntent: PendingIntent): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic().apply {
                val mode = if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                setPendingIntentBackgroundActivityStartMode(mode)
            }.toBundle()
            pendingIntent.send(appContext, 0, null, null, null, null, options)
        } else {
            pendingIntent.send()
        }
    }.isSuccess

    private companion object {
        const val MAX_ARTWORK_PX = 192
        const val FALLBACK_ICON_PX = 96
        const val FALLBACK_RECHECK_MS = 350L
        const val FALLBACK_ACTION_RECHECK_MS = 500L
        const val FALLBACK_GRACE_MS = 1_000L
        const val ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
