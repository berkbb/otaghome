package com.berkbb.otaghome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiIntentTest {

    private val tvSettings = "com.android.tv.settings"

    @Test
    fun stockTvKeepsTheWifiSettings() {
        // Arçelik and most Android TVs: both intents are handled by the TV settings app
        assertFalse(prefersWifiPicker(settingsApp = tvSettings, pickerApp = tvSettings))
    }

    @Test
    fun vendorWizardIsSkippedForThePicker() {
        // Philips sends the Wi-Fi settings intent to its own connection wizard
        assertTrue(prefersWifiPicker(settingsApp = "org.droidtv.networkwizard", pickerApp = tvSettings))
    }

    @Test
    fun tvWithoutAPickerKeepsTheWifiSettings() {
        assertFalse(prefersWifiPicker(settingsApp = tvSettings, pickerApp = null))
        assertFalse(prefersWifiPicker(settingsApp = "org.droidtv.networkwizard", pickerApp = null))
    }

    @Test
    fun pickerIsUsedWhenNothingHandlesTheWifiSettings() {
        assertTrue(prefersWifiPicker(settingsApp = null, pickerApp = tvSettings))
    }
}
