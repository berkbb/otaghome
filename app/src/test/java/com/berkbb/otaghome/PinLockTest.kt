package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PinLockTest {

    @Test
    fun pinValidationAllowsOnlyFourAsciiDigits() {
        assertTrue(PinLock.isValidPin("0429"))
        assertTrue(PinLock.isValidPin("1234"))
        assertFalse(PinLock.isValidPin(""))
        assertFalse(PinLock.isValidPin("123"))
        assertFalse(PinLock.isValidPin("12345"))
        assertFalse(PinLock.isValidPin("12ab"))
    }

    @Test
    fun saltedHashesVerifyCorrectPinAndRejectWrongInputs() {
        val saltA = "0123456789abcdef0123456789abcdef"
        val saltB = "fedcba9876543210fedcba9876543210"
        val hashA = PinLock.hash("2580", saltA)
        val hashB = PinLock.hash("2580", saltB)

        assertNotEquals(hashA, hashB)
        assertTrue(PinLock.verify("2580", saltA, hashA))
        assertFalse(PinLock.verify("2581", saltA, hashA))
        assertFalse(PinLock.verify("2580", saltB, hashA))
        assertFalse(PinLock.verify("25", saltA, hashA))
        assertFalse(PinLock.verify("2580", "", hashA))
        assertFalse(PinLock.verify("2580", saltA, ""))
    }

    @Test
    fun generateSaltProducesUnique32CharHexStrings() {
        val a = PinLock.generateSalt()
        val b = PinLock.generateSalt()
        assertEquals(32, a.length)
        assertEquals(32, b.length)
        assertNotEquals(a, b)
    }

    @Test
    fun lockSettingsRequirePinBeforeLockingSettingsOrApps() {
        val noPin = LockSettings(lockSettings = true, lockedApps = setOf("com.video"))
        assertFalse(noPin.hasPin)
        assertFalse(noPin.isSettingsLocked())
        assertFalse(noPin.isAppLocked("com.video"))

        val withPin = LockSettings(pinHash = "h", pinSalt = "s", lockSettings = true, lockedApps = setOf("com.video"))
        assertTrue(withPin.hasPin)
        assertTrue(withPin.isSettingsLocked())
        assertTrue(withPin.isAppLocked("com.video"))
        assertFalse(withPin.isAppLocked("com.music"))
    }

    @Test
    fun storageFormatFormatsMegabytesGigabytesAndPercent() {
        assertEquals("0 MB", StorageFormat.formatBytes(-50L, Locale.US))
        assertEquals("450 MB", StorageFormat.formatBytes(450_000_000L, Locale.US))
        assertEquals("4.2 GB", StorageFormat.formatBytes(4_200_000_000L, Locale.US))
        assertEquals(75, StorageFormat.usedPercent(4_000_000_000L, 16_000_000_000L))
        assertEquals(0, StorageFormat.usedPercent(0L, 0L))
    }
}
