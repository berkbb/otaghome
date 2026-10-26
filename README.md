# OtagHome

A simple, light home screen (launcher) for Android TV and Google TV, in the look of
[Pitwall Updater](https://github.com/berkbb/pitwall-updater): dark Pitwall ground, purple accent,
Material 3 shapes.

*Otağ* is the command tent of the old Turkic khans; the logo is one.

- Package: `com.berkbb.otaghome` · Android 8.0+ (API 26) · one APK for every TV (~1.5 MB)
- Languages: English, Turkish, Romanian (the TV's language picks one; others fall back to English)
- License: GNU General Public License v3.0

## Features

**Home**
- **Favorites** tab: a centred grid, inset on all sides, growing downwards.
- **All apps** tab: every app meant for the viewer in a five-column grid.
- Material 3 app cards: the app's TV banner (or its logo on its own colour) and the name.
- Long press OK (or Menu) on an app: Move, App info, Add to / Remove from Favorites, Hide, Uninstall.
- Move mode: the card lifts, the arrows move it, OK or Back drops it; the order is kept.
- Top bar: clock with weekday and date (and the weather), the tabs in the centre, and buttons
  for search (keyboard), voice search, inputs (HDMI, tuner), network and settings.
- Up from the first row goes to the control right above the card.
- Messages slide up from the bottom with an icon, a close button and a countdown.
- A full-screen progress ring covers the screen while it is rebuilt or redrawn.

**Settings** (the gear)
- Theme: Auto (TV), Dark, Light, or Match background.
- Theme colours: accent, cards, text, secondary text, screen ground, focus fill, text on focus;
  each by hex code, spectrum (hue, saturation, brightness) or RGB.
- Background: 39 ready-made vector pictures (9 OtagHome pictures and 30 landscapes of Türkiye),
  your own picture, a solid colour or a gradient; an overlay (Normal, Light, Off) for pictures.
- Weather in the clock card, from [Open-Meteo](https://open-meteo.com); the city is chosen by
  search or, only after you allow it, found from the internet address.
- Display: clock format, card size, app names on cards, start tab, Home button to OtagHome
  (off by default; see *Make it the home screen*), message duration.
- Apps: system apps (TV apps only, hidden, or all), order of Favorites and of All apps (your own
  or by name), and the apps you hid.
- Back up all settings to a file and restore them, for example on another TV.
- Language, network and all TV settings as shortcuts; About with the update check.

Everything shown on the background (focus frames, status lines) picks its colours by contrast
against the background, so it stays readable on any colour, gradient or picture.

## Tested TVs

| TV | Android | Checked |
|---|---|---|
| Arçelik | 11 | OtagHome 1.0.1: the network (Wi-Fi), search (keyboard) and voice search buttons work |
| Philips 49PUS7503 (TPM171E) | 8 | OtagHome 1.0.2: works as the home screen |

## Privacy

No accounts, no analytics, no tracking. Network use is limited to the weather and the update
check (GitHub, only when you press Check for updates):
- Open-Meteo receives the coordinates of the chosen city (or of the found one).
- Only if you allow it, ipwho.is (or ipapi.co when it does not answer) receives the TV's internet
  address to find the city. You are asked first, and can change this in Settings → Weather.

The optional Home override is an accessibility service, off until you turn it on. It only
notices when the TV's own home screen opens; it reads nothing on the screen and sends nothing.

## Install

Download `otaghome-v<version>-universal-release.apk` and install it with a file manager, or:

```sh
adb install -r otaghome-v1.0.3-universal-release.apk
```

### Make it the home screen

An app cannot make itself the home screen; how you choose it depends on the TV.

**TVs that ask.** Press Home after installing; if the TV lists the home screens, choose OtagHome,
then Always. Some TVs offer the same choice under Settings → Apps → Default apps → Home app.

**Google TV and most newer Android TVs** never ask: their own home screen always opens. Either:

- Turn on Settings → Display → Home button to OtagHome. OtagHome explains its accessibility
  service and opens Accessibility, where you turn OtagHome on. From then on OtagHome opens
  whenever the TV's own home screen comes up, which may show for a moment first. The same row
  turns it off. If a newer TV will not let you turn it on, allow restricted settings in
  OtagHome's App info first. Some TVs (Philips) have no Accessibility screen at all; there,
  allow OtagHome once over adb and the same row turns the service on and off by itself:

  ```sh
  adb shell pm grant com.berkbb.otaghome android.permission.WRITE_SECURE_SETTINGS
  ```
- Or turn the TV's own home screen off over adb (debugging on in Developer options). Find its package
  among the home screens:

  ```sh
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  ```

  Turn it off (here Google TV's; on other TVs it is, for example, `com.google.android.tvlauncher`
  or `com.google.android.leanbacklauncher`):

  ```sh
  adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  ```

  Turn it back on to undo this, and always before uninstalling OtagHome, or the TV is left without
  a home screen:

  ```sh
  adb shell pm enable com.google.android.apps.tv.launcherx
  ```

  Some TVs turn their home screen back on after a system update; run `disable-user` again.
  Screens that belong to the TV's own home screen may not open while it is off.
- Or remap the Home button with another app that does it through Accessibility (a "button
  mapper"), so that Home opens OtagHome.

## Build

See [DEVELOPMENT.md](DEVELOPMENT.md). In short: `./package.sh` (or double-click `package.command` in Finder) runs the tests and leaves the APK
and its checksum in `dist/`.
