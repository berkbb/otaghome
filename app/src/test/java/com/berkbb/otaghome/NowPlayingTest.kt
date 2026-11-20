package com.berkbb.otaghome

import android.Manifest
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {

    private val own = "com.berkbb.otaghome"

    private fun snapshot(
        id: String = "s1",
        packageName: String = "tr.edu.ankara.ilef.radio",
        appLabel: String = "Radyo İLEF",
        title: String = "Song",
        artist: String = "Artist",
        state: MediaState = MediaState.PLAYING,
        canPlay: Boolean = true,
        canPause: Boolean = true,
        canSkipPrevious: Boolean = false,
        canSkipNext: Boolean = false,
        canStop: Boolean = true
    ) = MediaSnapshot(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        artist = artist,
        state = state,
        canPlay = canPlay,
        canPause = canPause,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        canStop = canStop
    )

    @Test
    fun mapsAllAndroidPlaybackStatesAndNull() {
        assertEquals(MediaState.PLAYING, NowPlayingLogic.mapState(PlaybackState.STATE_PLAYING))
        assertEquals(MediaState.BUFFERING, NowPlayingLogic.mapState(PlaybackState.STATE_BUFFERING))
        assertEquals(MediaState.BUFFERING, NowPlayingLogic.mapState(PlaybackState.STATE_CONNECTING))
        assertEquals(MediaState.PAUSED, NowPlayingLogic.mapState(PlaybackState.STATE_PAUSED))
        assertEquals(MediaState.STOPPED, NowPlayingLogic.mapState(PlaybackState.STATE_STOPPED))
        assertEquals(MediaState.STOPPED, NowPlayingLogic.mapState(PlaybackState.STATE_NONE))
        assertEquals(MediaState.STOPPED, NowPlayingLogic.mapState(PlaybackState.STATE_ERROR))
        assertEquals(MediaState.STOPPED, NowPlayingLogic.mapState(null))
    }

    @Test
    fun inspectsTransportActionFlags() {
        // Radyo İLEF live radio flags: no skip previous/next
        val liveRadio = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE
        assertTrue(NowPlayingLogic.canPlay(liveRadio))
        assertTrue(NowPlayingLogic.canPause(liveRadio))
        assertTrue(NowPlayingLogic.canStop(liveRadio))
        assertFalse(NowPlayingLogic.canSkipPrevious(liveRadio))
        assertFalse(NowPlayingLogic.canSkipNext(liveRadio))

        // ACTION_PLAY_PAUSE alone supports both play and pause
        assertTrue(NowPlayingLogic.canPlay(PlaybackState.ACTION_PLAY_PAUSE))
        assertTrue(NowPlayingLogic.canPause(PlaybackState.ACTION_PLAY_PAUSE))
        assertFalse(NowPlayingLogic.canStop(PlaybackState.ACTION_PLAY_PAUSE))

        // Music player with track navigation
        val music = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT
        assertTrue(NowPlayingLogic.canSkipPrevious(music))
        assertTrue(NowPlayingLogic.canSkipNext(music))
        assertFalse(NowPlayingLogic.canStop(music))
        assertFalse(NowPlayingLogic.canPlay(0L))
        assertFalse(NowPlayingLogic.canPause(0L))
    }

    @Test
    fun picksPlayingThenBufferingThenPausedAndSkipsOwnStoppedAndDismissed() {
        val ownPlaying = snapshot(id = "own", packageName = own, state = MediaState.PLAYING)
        val stopped = snapshot(id = "stop", state = MediaState.STOPPED)
        val paused = snapshot(id = "paused", state = MediaState.PAUSED)
        val buffering = snapshot(id = "buf", state = MediaState.BUFFERING)
        val playing = snapshot(id = "play", state = MediaState.PLAYING)

        assertEquals(playing, NowPlayingLogic.pickSession(listOf(ownPlaying, stopped, paused, buffering, playing), own))
        assertEquals(buffering, NowPlayingLogic.pickSession(listOf(ownPlaying, stopped, paused, buffering), own))
        assertEquals(paused, NowPlayingLogic.pickSession(listOf(ownPlaying, stopped, paused), own))
        assertNull(NowPlayingLogic.pickSession(listOf(ownPlaying, stopped), own))

        // Dismissed paused session stays hidden while paused, but reappears once playing or buffering
        assertNull(NowPlayingLogic.pickSession(listOf(paused), own, dismissedPausedIds = setOf("paused")))
        val resumed = paused.copy(state = MediaState.PLAYING)
        assertEquals(resumed, NowPlayingLogic.pickSession(listOf(resumed), own, dismissedPausedIds = setOf("paused")))
        val rebuffering = paused.copy(state = MediaState.BUFFERING)
        assertEquals(rebuffering, NowPlayingLogic.pickSession(listOf(rebuffering), own, dismissedPausedIds = setOf("paused")))
    }

    @Test
    fun prunesDismissedIdsWhenSessionEndsOrResumes() {
        val sessions = listOf(
            snapshot(id = "stillPaused", state = MediaState.PAUSED),
            snapshot(id = "resumed", state = MediaState.PLAYING),
            snapshot(id = "buffering", state = MediaState.BUFFERING),
            snapshot(id = "stopped", state = MediaState.STOPPED)
        )
        val kept = NowPlayingLogic.pruneDismissed(
            setOf("stillPaused", "resumed", "buffering", "stopped", "gone"),
            sessions
        )
        assertEquals(setOf("stillPaused"), kept)
        assertTrue(NowPlayingLogic.pruneDismissed(emptySet(), sessions).isEmpty())
    }

    @Test
    fun formatsDisplayLinesAndRemovesDuplicates() {
        assertEquals(
            MediaText("Bohemian Rhapsody", "Queen · Spotify"),
            NowPlayingLogic.formatDisplay("  Bohemian Rhapsody  ", " Queen ", " Spotify ")
        )
        assertEquals(
            MediaText("Radyo İLEF", null),
            NowPlayingLogic.formatDisplay("Radyo İLEF", "Radyo İLEF", "Radyo İLEF")
        )
        assertEquals(
            MediaText("Canlı Yayın", "Radyo İLEF"),
            NowPlayingLogic.formatDisplay("Canlı Yayın", "Radyo İLEF", "Radyo İLEF")
        )
        assertEquals(
            MediaText("Song Title", "Music App"),
            NowPlayingLogic.formatDisplay("Song Title", "song title", "Music App")
        )
        assertEquals(
            MediaText("Artist Only", "Music App"),
            NowPlayingLogic.formatDisplay("   ", "Artist Only", "Music App")
        )
        assertEquals(
            MediaText("App Only", null),
            NowPlayingLogic.formatDisplay("", "", "App Only")
        )
        assertEquals(
            MediaText(null, null),
            NowPlayingLogic.formatDisplay(" ", "", "  ")
        )
    }

    @Test
    fun scalesArtworkProportionallyWithinMaxEdge() {
        assertEquals(120 to 90, NowPlayingLogic.scaledSize(120, 90, 192))
        assertEquals(192 to 192, NowPlayingLogic.scaledSize(192, 192, 192))
        assertEquals(192 to 108, NowPlayingLogic.scaledSize(1920, 1080, 192))
        assertEquals(108 to 192, NowPlayingLogic.scaledSize(1080, 1920, 192))
        assertEquals(192 to 192, NowPlayingLogic.scaledSize(512, 512, 192))
        assertEquals(192 to 1, NowPlayingLogic.scaledSize(10_000, 10, 192))
        assertEquals(0 to 0, NowPlayingLogic.scaledSize(0, 100, 192))
        assertEquals(0 to 0, NowPlayingLogic.scaledSize(100, -5, 192))
        assertEquals(0 to 0, NowPlayingLogic.scaledSize(100, 100, 0))
    }

    @Test
    fun mediaAccessHandlesComponentFormsRemovalDirectSwitchAndAdbCommands() {
        val full = "$own/$own.MediaListenerService"
        val short = "$own/.MediaListenerService"
        assertFalse(MediaAccess.hasListener(null, full))
        assertFalse(MediaAccess.hasListener("", full))
        assertTrue(MediaAccess.hasListener(full, full))
        assertTrue(MediaAccess.hasListener("a/.A:$short", full))
        assertTrue(MediaAccess.hasListener("a/.A:$full", short))

        assertEquals("a/.A:b/.B", MediaAccess.withoutListener("a/.A:$short:b/.B", full))
        assertEquals("", MediaAccess.withoutListener(full, short))

        assertTrue(MediaAccess.canSwitchDirectly(26, hasWriteSecureSettings = true))
        assertTrue(MediaAccess.canSwitchDirectly(27, hasWriteSecureSettings = true))
        assertFalse(MediaAccess.canSwitchDirectly(27, hasWriteSecureSettings = false))
        assertFalse(MediaAccess.canSwitchDirectly(28, hasWriteSecureSettings = true))
        assertFalse(MediaAccess.canSwitchDirectly(30, hasWriteSecureSettings = true))

        assertEquals(
            "adb shell pm grant $own ${Manifest.permission.WRITE_SECURE_SETTINGS}",
            MediaAccess.adbCommand(26, own, full)
        )
        assertEquals(
            "adb shell cmd notification allow_listener $full",
            MediaAccess.adbCommand(28, own, full)
        )
        assertEquals(
            "adb shell cmd notification allow_listener $full",
            MediaAccess.adbCommand(30, own, full)
        )
    }

    @Test
    fun ordersEligibleSessionsAndSwitchesBetweenActivePlayers() {
        val s1 = snapshot(id = "p1", state = MediaState.PAUSED)
        val s2 = snapshot(id = "p2", state = MediaState.PLAYING)
        val s3 = snapshot(id = "p3", state = MediaState.BUFFERING)
        val ordered = NowPlayingLogic.orderedSessions(listOf(s1, s2, s3), own)
        assertEquals(listOf(s2, s3, s1), ordered)

        assertEquals(s2 to 0, NowPlayingLogic.pickSelectedSession(ordered, preferredId = null))
        assertEquals(s1 to 2, NowPlayingLogic.pickSelectedSession(ordered, preferredId = "p1"))
        assertEquals(s2 to 0, NowPlayingLogic.pickSelectedSession(ordered, preferredId = "missing"))

        assertEquals("p3", NowPlayingLogic.nextSessionId(ordered, "p2"))
        assertEquals("p1", NowPlayingLogic.nextSessionId(ordered, "p3"))
        assertEquals("p2", NowPlayingLogic.nextSessionId(ordered, "p1"))
        assertNull(NowPlayingLogic.nextSessionId(emptyList(), "p1"))
    }
}
