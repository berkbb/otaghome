# Changelog

All notable changes to OtagHome are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
