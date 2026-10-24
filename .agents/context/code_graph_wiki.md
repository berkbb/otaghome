# Code graph

`MainActivity` → `LauncherScreen`
- reads `LauncherViewModel` (apps, art, favorites, orders, modes, weather)
- draws `LauncherBackground` (+ `BackdropColors` via `LocalBackdrop`)
- `TopBar` (Components.kt): `Clock`, `TabBar`, `PitwallIconButton`s, registers `UpTargets`
- Favorites `FlowRow` / All apps `LazyVerticalGrid` of `AppCard` (Components.kt)
- `FooterBar` (Footer.kt), `AppMenu`, `SettingsPanel`, `WeatherConsentDialog`

`SettingsPanel` pages: main, background → presets grid, colours (`ColorPickerDialog`),
weather (`WeatherPage`), About (`AboutWindow`).

Also: `Display.kt` (tabs, clock format, card size, start tab, `Backup`), `SelfUpdate.kt`
(update check and install, `InstallResultReceiver`, `SelfUpdateRestartReceiver`), `Busy.kt`
(`BusyOverlay`, `LocalBusy`), `HomeOverride.kt` (`HomeOverride` targets, `HomeOverrideService`;
its row and dialog are `HomeOverrideRow` / `HomeOverrideDialog` on the Display page).

Pure, unit-tested logic: `AppCatalog`, `BackgroundSpec`, `Backup`, `ClockFormat`, `CardSize`,
`StartTab`, `ReleaseParser`, `HomeOverride`, `TempUnit`, `ThemeMode`/`Palette`/`ColorRole`,
`BackdropColors`, `ArtColor`, `ColorMath`, `HexColor`, `Weather*`, `NoticeTimer`, `UpTargets`,
`RoomyBringIntoView`, `NetworkStatus.wifiBars`.

Preferences file: `otaghome_prefs` (screen settings in `MainActivity`, app and weather settings
in `LauncherViewModel`).
