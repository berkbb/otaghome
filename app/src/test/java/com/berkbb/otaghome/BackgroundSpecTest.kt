package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSpecTest {

    @Test
    fun hexCodesInEveryCommonForm() {
        assertEquals(0xFF7C4DFF.toInt(), HexColor.parse("#7C4DFF"))
        assertEquals(0xFF7C4DFF.toInt(), HexColor.parse(" 7c4dff "))
        assertEquals(0xFFFF0000.toInt(), HexColor.parse("#F00"))
        assertEquals(0x807C4DFF.toInt(), HexColor.parse("#807C4DFF"))
    }

    @Test
    fun invalidHexCodesAreRejected() {
        listOf("", "#", "#12", "#12345", "#GGGGGG", "7C4DFF00FF", "red").forEach { assertNull(it, HexColor.parse(it)) }
    }

    @Test
    fun hexFormatDropsOpaqueAlpha() {
        assertEquals("#7C4DFF", HexColor.format(0xFF7C4DFF.toInt()))
        assertEquals("#807C4DFF", HexColor.format(0x807C4DFF.toInt()))
    }

    @Test
    fun everyBackgroundSurvivesARoundTrip() {
        listOf(
            BackgroundSpec.Default,
            BackgroundSpec.Preset(PresetImage.AURORA),
            BackgroundSpec.Solid(0xFF101820.toInt()),
            BackgroundSpec.Gradient(0xFF1A1033.toInt(), 0x807C4DFF.toInt(), GradientDirection.HORIZONTAL),
            BackgroundSpec.Image("content://com.android.providers.media.documents/document/image%3A42")
        ).forEach { assertEquals(it, BackgroundSpec.decode(it.encode())) }
    }

    @Test
    fun unreadableSettingsFallBackToTheDefaultPicture() {
        listOf(null, "", "solid|nope", "gradient|#FFF|#000|SIDEWAYS", "image|", "preset|MARS", "plasma").forEach {
            assertEquals(BackgroundSpec.Default, BackgroundSpec.decode(it))
        }
    }

    @Test
    fun theDefaultPictureKeepsItsOldStoredName() {
        assertEquals("default", BackgroundSpec.Default.encode())
        assertEquals(BackgroundSpec.Default, BackgroundSpec.decode("default"))
        assertEquals("preset|OCEAN", BackgroundSpec.Preset(PresetImage.OCEAN).encode())
    }

    @Test
    fun everyReadyMadePictureIsStoredAndReadBackWithItsSampleColours() {
        PresetImage.entries.forEach { image ->
            assertEquals(BackgroundSpec.Preset(image), BackgroundSpec.decode(BackgroundSpec.Preset(image).encode()))
            assertEquals("${image.name} needs top, middle and bottom samples", 3, image.samples.size)
        }
        assertEquals(30, PresetImage.entries.count { it.group == PresetGroup.TURKEY })
    }

    @Test
    fun pictureOverlayGoesNormalLightOff() {
        assertEquals(VeilLevel.LIGHT, VeilLevel.NORMAL.next())
        assertEquals(VeilLevel.OFF, VeilLevel.LIGHT.next())
        assertEquals(VeilLevel.NORMAL, VeilLevel.OFF.next())
        assertEquals(0f, VeilLevel.OFF.strength)
        assertEquals(VeilLevel.NORMAL, VeilLevel.fromName("THICK"))
    }

    @Test
    fun gradientDirectionCycles() {
        assertEquals(GradientDirection.HORIZONTAL, GradientDirection.VERTICAL.next())
        assertEquals(GradientDirection.VERTICAL, GradientDirection.DIAGONAL.next())
    }

    @Test
    fun presetRotationCyclesAndAdvancesThroughPresets() {
        assertEquals(PresetRotation.DAILY, PresetRotation.OFF.next())
        assertEquals(PresetRotation.ON_LAUNCH, PresetRotation.DAILY.next())
        assertEquals(PresetRotation.OFF, PresetRotation.ON_LAUNCH.next())
        assertEquals(PresetRotation.OFF, PresetRotation.fromName("UNKNOWN"))

        val pool = listOf(PresetImage.OTAG, PresetImage.AURORA, PresetImage.SUNSET)
        assertEquals(PresetImage.AURORA, PresetRotator.next(PresetImage.OTAG, pool))
        assertEquals(PresetImage.OTAG, PresetRotator.next(PresetImage.SUNSET, pool))
        assertEquals(PresetImage.OTAG, PresetRotator.next(PresetImage.FOREST, pool))

        assertTrue(PresetRotator.shouldRotateDaily(lastDay = 20_000L, today = 20_001L))
        assertFalse(PresetRotator.shouldRotateDaily(lastDay = 20_000L, today = 20_000L))
        assertFalse(PresetRotator.shouldRotateDaily(lastDay = -1L, today = 20_000L))
        assertEquals(0L, PresetRotator.localEpochDay(86_399_000L, 0))
        assertEquals(1L, PresetRotator.localEpochDay(86_399_000L, 3_600_000))
    }
}
