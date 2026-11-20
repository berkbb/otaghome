# Changelog

All notable changes to OtagHome are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0] - 2026-11-20

### Added
- **Media controls on the home screen:** a Pitwall-styled docked Now Playing bar appears at the
  bottom whenever another app plays audio in the background (such as Radyo İLEF or music players),
  monitored only while OtagHome is in the foreground. With Notification access granted (`Settings →
  Display → Media controls`), it displays the track title (with a bounded 2-pass marquee for long
  titles), artist and app name, scaled artwork (or the app icon), buffering progress, a clickable
  header to open the playing app, a player switcher when multiple sessions exist, a Mute toggle, and
  supported playback buttons (Previous, Play/Pause, Next, Stop/Close). Without Notification access,
  basic Play/Pause, Mute and Stop/Close controls still work via `AudioPlaybackCallback` and media
  key events, with a setup dialog offering TV settings, direct enabling on Android 8.0–8.1
  (`WRITE_SECURE_SETTINGS`), or the `adb` grant command on TVs without a Notification access screen.
- **Recently opened sort (`SortMode.RECENT`):** orders apps by when you last opened them from
  OtagHome (purely local timestamps, no system permissions).
- **Auto-changing preset backgrounds:** `Settings → Background → Auto-change picture` cycles
  ready-made pictures (`Off`, `Daily`, or `Every time OtagHome opens`).
- **App menu & remote shortcuts:** Rename any app locally (or restore its original name), Uninstall
  directly from Favorites too, switch tabs with `CH+`/`CH-`, and page through app grids with
  `PageUp`/`PageDown`.
- **Backup fallback on pickerless TVs:** TVs without a document picker save and restore backups
  directly in OtagHome's app storage (`otaghome-backup.json`) instead of opening a system stub.
- **2D spectrum box in the colour picker:** added an interactive Saturation × Brightness spectrum
  box to `ColorPickerDialog` alongside the Hue/Saturation/Brightness sliders, RGB sliders and
  Hex code field (touch/drag and TV remote D-pad via OK + arrow keys).
- **In-app device picture browser fallback:** on TVs without a real SAF document picker,
  `Settings → Background → Choose image…` (and a dedicated **Device pictures** row when a system
  picker is also present) opens a built-in browser that lists pictures from OtagHome's own pictures
  folder (zero permissions) and, with optional photo permission, TV storage (`MediaStore` +
  `Pictures`/`Downloads`), plus any installed `GET_CONTENT` picker. Auto-change picture also rotates
  user images stored in OtagHome's pictures folder.
- **Remote & media shortcuts:** press number keys `1`–`9` to launch Favorites directly
  (`Settings → Display`), press hardware media Play/Pause/Next/Previous keys anywhere on the home
  screen, press `Menu` outside an app card to open Settings, or click the top-left clock/weather
  card to open an icon-only 5-day forecast dialog (`WeatherForecastDialog`).
- **Custom categories, card opacity & custom app covers:** create up to 6 custom category tabs in
  `Settings → Apps`, assign/reorder apps per category, set custom 16:9 card covers from the app
  menu (`AppCoverPickerDialog`), adjust unfocused `Card opacity` (`Solid`, `Translucent`, `Light
  glass`), and show/hide top-bar buttons in `Settings → Display → Top bar buttons`.
- **PIN lock, auto-update checks, storage info & reset:** protect Settings and individual apps with
  a 4-digit remote/keypad PIN (`Settings → Display → PIN lock`), enable optional daily/weekly
  GitHub update notifications, inspect free/total TV storage in `About`, and reset all settings
  with confirmation in `Settings → Backup & restore`.

### Changed
- **Live event-driven monitors:** replaced polling loops with `LauncherApps.Callback` for instant
  app install/uninstall updates, `ConnectivityManager.NetworkCallback` (reading `NetworkCapabilities.signalStrength`
  on API 29+) for network state, lifecycle-aware `ACTION_TIME_TICK` / time-zone receivers for the
  clock, and lifecycle-scoped weather refreshes.
- **Theme-aware errors, locale & update cache cleanup:** moved error text colour into `Palette.error`
  for light-theme readability, fixed uppercase/capitalization locale calls in `AboutWindow`, and
  automatically clean up cached `otaghome-*.apk` update files after updates or on startup.

### Fixed
- **Update download during a GitHub regional outage:** on 2026-11-08 one GitHub region answered 504 for every
  `github.com/…/releases/download/…` address for hours while `api.github.com` kept working, so the in-app update
  could not download the APK. When the github.com download fails with an I/O error or a 5xx answer, OtagHome now
  fetches the same asset once more from its `api.github.com/repos/berkbb/otaghome/releases/assets/<id>` address
  (`Accept: application/octet-stream`); the SHA-256 (or size) check runs on whichever download succeeded.

### Security
- **Release signing off the debug keystore:** without a configured keystore, releases fell back to `~/.android/debug.keystore`, whose password is public. Releases now use the TV apps' shared keystore `~/.signing/babadogan-tv-release.p12` (same certificate, so installed TVs keep updating) with a strong password kept in the macOS Keychain, or `keystore.properties` / `OTAGHOME_*` variables; the debug key is only a fallback with a warning.

## [1.0.3] - 2026-10-24

### Changed
- The release APK is now named `otaghome-v<version>-universal-release.apk`, like the other TV
  apps; the in-app update still finds it (it takes the first `*-release.apk` asset).
- Back no longer closes OtagHome once Home has brought it up, even if it was first opened from
  the apps list.

### Added
- `package.command`: double-click launcher for `package.sh` in macOS Finder.
- Settings → Display → Home button to OtagHome, off by default: for TVs that never offer a choice
  of home screen (Google TV and most newer Android TVs). OtagHome's accessibility service opens
  OtagHome whenever the TV's own home screen comes up; it notices only that screen opening and
  reads nothing on the screen. A dialog explains it, with focus on the button that turns it on.
- TVs without an Accessibility screen (Philips, where opening it only says no app can do it):
  allowed once over adb (`pm grant … WRITE_SECURE_SETTINGS`), OtagHome turns its service on and
  off itself; until then the dialog shows that adb line instead of opening a screen that is not
  there.

## [1.0.2] - 2026-10-23

### Changed
- Less memory: apps with a TV banner no longer keep their logo in memory as well; the app menu
  loads the logo when it opens. Banners and logos are drawn at the same size and quality as before.

### Fixed
- Memory grew by about 12 MB after the first return to the home screen and stayed there: every
  return rendered all banners again. Now only new or updated apps get their art drawn again.

## [1.0.1] - 2026-10-23

### Added
- Search button in the top bar: opens Google search with the keyboard, for remotes without a
  microphone. The voice search button stays next to it.

### Changed
- The top bar measures the screen: the tabs stay centred while they fit, otherwise they sit
  between the clock and the buttons and shrink just enough, so nothing overlaps at any
  resolution, font size or language.

### Fixed
- The network button opens the Wi-Fi network list on TVs that send the Wi-Fi settings to their
  own connection wizard (Philips).

## [1.0.0] - 2026-10-22

First release. Started as "Paddock" (`com.berkbb.paddock`), renamed to OtagHome
(`com.berkbb.otaghome`) before any release.

### Added
- Home screen for Android TV / Google TV (HOME, LAUNCHER and LEANBACK_LAUNCHER entries).
- Favorites tab (centred grid) and All apps tab (five columns), Material 3 app cards with the
  TV banner or the logo on its own colour, names centred in the system typeface.
- Long-press / Menu app menu: Move, App info, Add to / Remove from Favorites, Uninstall.
- Move mode with animated placement; the moved card always scrolls fully into view.
- Top bar: clock card with weekday, date and accent bar; tabs on one card with a sliding
  selection pill and accent bar; Pitwall-style buttons for voice search, inputs, network and
  settings. Up from the first row goes to the control above the card.
- Footer messages with a Material icon, close button, countdown ring and adjustable duration;
  they take their own room so they never cover the apps.
- Settings side sheet: theme (Auto, Dark, Light, Match background), theme colours with a colour
  picker (hex, spectrum, RGB), background (ready-made pictures, own picture, solid colour,
  gradient, picture overlay), weather, system apps, sort orders, message duration, system
  shortcuts and About.
- 39 vector backgrounds: 9 OtagHome pictures and 30 landscapes of Türkiye.
- Weather from Open-Meteo in the clock card; city search in the TV's language; the place from
  the internet address only after asking; refresh every 15 to 180 minutes; last reading kept.
- Contrast-aware colours for everything drawn straight on the background.
- About window with the GNU GPL v3 text, and the OtagHome logo, icon and TV banner.
- English, Turkish and Romanian; the UI follows the TV's language.
- `package.sh` release packager with a signing-key check, and the landscape generator under
  `tools/landscapes/`.
- Settings grouped into short pages: Display, Apps, and Back up and restore.
- Display: clock format (as on the TV, 24-hour, 12-hour), card size (small, normal, large: six,
  five or four columns), app names on cards on or off, start tab (automatic, Favorites, All apps,
  last used), message duration
  (picked from a list).
- About: the result of Check for updates also slides up as a message inside the About window.
- Apps: hide an app from its long-press menu; hidden apps are listed in Settings → Apps and shown
  again with OK.
- Back up every setting to a JSON file and restore it on another TV (the weather reading and a
  picture chosen on this TV are left out).
- Weather: temperature in °C or °F.
- About: check for updates on GitHub (`berkbb/otaghome`), download the release APK, verify its
  SHA-256 digest and install it over the app; OtagHome reopens afterwards.
- Full-screen progress ring with the logo, as in AynaCast, while the screen is rebuilt (language or
  system change) and briefly after changes that redraw it (theme, colours, background, overlay,
  card size, restored backup).
