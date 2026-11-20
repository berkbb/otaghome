# Domain wiki

- **Tabs**: Favorites (centred grid, inset on all sides), All apps (grid) and up to 6 optional
  user-defined Category tabs (`AppCategory`, managed in Settings → Apps). A tab switches when it
  gets focus. `CH+`/`CH-` cycles tabs; digits `1`–`9` launch favorites directly (configurable).
  Up from the first row goes to the top-bar control above the card (`UpTargets`).
- **User-facing apps**: launchable apps minus the launcher itself; system-image apps are shown
  per `SystemAppsMode` (TV apps only by default, hidden, or all). Apps updated from a store count
  as the user's.
- **Order & customisation**: Favorites and categories keep the user's order; All apps are
  alphabetical until the first move. `SortMode` can override Favorites/All apps with name A→Z,
  Z→A or Recently opened (local launch timestamps, no permissions), all of which hide Move. Users
  can also rename any app locally (`custom_labels`), assign a custom 16:9 card cover (`custom_art`),
  or cycle `CardOpacity` (`Solid`, `Translucent`, `Light glass`).
- **Move mode**: the card lifts, arrows move it (left/right one place, up/down one row), OK or
  Back drops it. The moved card is scrolled into view with a margin (`RoomyBringIntoView`).
- **Background**: a ready-made picture (`PresetImage`, 9 OtagHome + 30 Türkiye), the user's
  picture (chosen through SAF when available, and always reachable via `DeviceImagesPage` from
  OtagHome's folder, `MediaStore`/device folders, or `ACTION_GET_CONTENT`), a solid colour or a
  gradient. Colours open `ColorPickerDialog`, offering a 2D saturation × brightness spectrum box,
  hue/saturation/brightness and RGB sliders, plus a hex code field. Pictures are veiled with the
  theme ground (`VeilLevel`: normal, light, off); preset pictures and pictures in OtagHome's own
  folder can cycle automatically (`PresetRotation`: off, daily, on launch).
- **Backdrop colours**: anything drawn straight on the background (focus frames, status lines)
  picks white or dark and keeps the accent only where it reaches 3:1 contrast (WCAG).
- **Theme**: Auto (TV night mode), Dark, Light, Match background (from the background's sample
  colours). Users can replace seven theme colours (`ColorRole`); the accent is shared by both
  themes, the rest are per theme.
- **Weather**: Open-Meteo, shown in the clickable clock card as an icon and temperature with the
  place name; clicking opens an icon-only 5-day forecast dialog (`WeatherForecastDialog`) with
  refresh and settings shortcuts. The place is a searched city or, only with consent, found from
  the internet address.
- **Top-bar buttons**: search (assistant with the keyboard), voice search (assistant intent,
  falling back to global search), inputs, network and settings; the four optional buttons can each
  be hidden in Settings → Display → Top bar buttons. `Menu` outside an app card opens Settings.
- **PIN lock & reset**: optional 4-digit salted-SHA256 PIN lock (`PinLock`) can protect Settings
  (plus Hide/App Info/Uninstall) and individual apps. Storage capacity (`StatFs`) appears in About;
  Settings → Backup offers a confirmed "Reset all settings" option.
- **Updates & auto-check**: manual check and verified install in About, plus optional scheduled
  background check (`UpdateCheckInterval`: Off by default, Daily, Weekly) posting one footer
  notice per new version.
- **Footer**: messages with an icon, close button and countdown; it takes its own room.
- **Hidden apps**: hidden from tabs; listed in Settings → Apps, OK shows one again.
- **Busy ring**: covers the screen while apps load after the activity is (re)created and for a
  short pulse after changes that redraw it (`LocalBusy`).
- **Backup**: every preference as JSON (`Backup`), minus the weather cache, chosen TV images,
  custom covers, PIN secrets, update-check timestamps and device-local timestamps; restoring
  recreates the activity. Falls back to app storage when the TV has no document picker.
- **Home override**: for TVs whose own home screen always wins the Home button. An optional
  accessibility service (Settings → Display, off by default; its state is the service's own, not
  a preference, so it is not backed up) opens OtagHome when the TV's own home activity comes up.
  Targets are system Home apps with a priority of 0 or more, matched by activity class, so other
  screens of the same app (Google's input panel) still open. OtagHome opened this way, or by Home
  later, counts as the Home app, so Back does not close it.
- **Media controls**: docked bar above the footer when background audio plays (`Settings → Display →
  Media controls`, on by default), active only while the home screen is in the foreground. Uses
  `MediaSessionManager` via `MediaListenerService` when Notification access is granted (song
  title with bounded marquee, artist, artwork, open app, Previous/Play-Pause/Next/Switch player/Mute/Stop)
  and falls back to `AudioPlaybackCallback` + media key events (Play/Pause/Mute/Stop + setup dialog)
  without permissions.
