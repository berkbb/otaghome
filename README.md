# OtagHome

A simple, light home screen (launcher) for Android TV and Google TV, in the look of
[Pitwall Updater](https://github.com/berkbb/pitwall-updater): dark Pitwall ground, purple accent,
Material 3 shapes.

*Otağ* is the command tent of the old Turkic khans; the logo is one.

- Package: `com.berkbb.otaghome` · Android 8.0+ (API 26) · one APK for every TV (~1.7 MB)
- Languages: English, Turkish, Romanian (the TV's language picks one; others fall back to English)
- License: GNU General Public License v3.0

## Features

**Home**
- **Favorites** tab: a centred grid, inset on all sides, growing downwards.
- **All apps** tab: every app meant for the viewer in a five-column grid.
- **Custom categories** (up to 6 optional tabs): organise apps into your own tabs and reorder them freely.
- Material 3 app cards: the app's TV banner (or its logo on its own colour), or a custom 16:9 cover, with adjustable card opacity (`Solid`, `Translucent`, `Light glass`).
- Long press OK (or Menu) on an app: Move, Rename, Custom cover, Categories, Lock with PIN, App info, Add to / Remove from Favorites, Hide, Uninstall.
- Remote shortcuts: `CH+`/`CH-` cycle tabs, `PageUp`/`PageDown` jump rows, `1`–`9` open Favorites directly, hardware media keys (`Play/Pause`/`Next`/`Previous`) control background audio, and `Menu` outside a card opens Settings.
- Move mode: the card lifts, the arrows move it, OK or Back drops it; the order is kept.
- Top bar: clickable clock with weekday, date and weather (opening an icon-only 5-day forecast dialog), the tabs in the centre, and configurable buttons for search (keyboard), voice search, inputs (HDMI, tuner), network and settings.
- Up from the first row goes to the control right above the card.
- Media controls: a docked bar at the bottom while another app plays audio in the background
  (song info with bounded marquee, artwork, open app, switch player, mute and playback buttons
  with Notification access; basic Play/Pause/Mute/Stop controls without it).
- Messages slide up from the bottom with an icon, a close button and a countdown.
- A full-screen progress ring covers the screen while it is rebuilt or redrawn.

**Settings** (the gear)
- Theme: Auto (TV), Dark, Light, or Match background.
- Theme colours: accent, cards, text, secondary text, screen ground, focus fill, text on focus;
  each by hex code, 2D saturation–brightness spectrum box, HSV sliders or RGB sliders.
- Background: 39 ready-made vector pictures (9 OtagHome pictures and 30 landscapes of Türkiye) or
  your own pictures (from system SAF picker or built-in device picture browser) with optional daily
  or on-launch auto-rotation, a solid colour or a gradient; an overlay (Normal, Light, Off) for pictures.
- Weather in the clock card, from [Open-Meteo](https://open-meteo.com) (with 5-day forecast);
  the city is chosen by search or, only after you allow it, found from the internet address.
- Display: clock format, card size, card opacity, app names on cards, start tab, top-bar buttons,
  1–9 Favorites shortcuts, Home button to OtagHome (off by default; see *Make it the home screen*),
  media controls, optional 4-digit PIN lock (for Settings and locked apps), auto-check updates, message duration.
- Apps: system apps (TV apps only, hidden, or all), order of Favorites and of All apps (your own,
  by name, recently opened), custom categories, custom names/covers, and the apps you hid.
- Back up all settings to a file (with app-storage fallback on TVs without a document picker) and
  restore them on another TV, or reset all settings with confirmation.
- Language, network and all TV settings as shortcuts; About with TV storage info and the update check.

Everything shown on the background (focus frames, status lines) picks its colours by contrast
against the background, so it stays readable on any colour, gradient or picture.

## Tested TVs

| TV | Android | Checked |
|---|---|---|
| Arçelik | 11 | OtagHome 1.0.1: the network (Wi-Fi), search (keyboard) and voice search buttons work |
| Philips 49PUS7503 (TPM171E) | 8 | OtagHome 1.0.2: works as the home screen |

## Privacy

No accounts, no analytics, no tracking. Network use is limited to the weather (including 5-day forecast) and the update
check (GitHub, manual or when scheduled in Display settings):
- Open-Meteo receives the coordinates of the chosen city (or of the found one).
- Only if you allow it, ipwho.is (or ipapi.co when it does not answer) receives the TV's internet
  address to find the city. You are asked first, and can change this in Settings → Weather.

The optional Home override is an accessibility service, off until you turn it on. It only
notices when the TV's own home screen opens; it reads nothing on the screen and sends nothing.
Full Now Playing info uses an optional notification listener (`MediaListenerService`) only to read
active media sessions; it reads no notifications and sends nothing. Optional photo permission
(`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` on older Android) is requested only when browsing
device pictures in Settings and stays strictly local. The optional PIN lock stores only a salted
SHA-256 hash locally and is excluded from backups.

## Install

Download `otaghome-v<version>-universal-release.apk` and install it with a file manager, or:

```sh
adb install -r otaghome-v1.1.0-universal-release.apk
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
