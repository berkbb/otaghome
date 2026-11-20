# Code graph

`MainActivity` → `LauncherScreen`
- reads `LauncherViewModel` (apps, art, favorites, orders, modes, weather)
- draws `LauncherBackground` (+ `BackdropColors` via `LocalBackdrop`)
- `TopBar` (Components.kt): `Clock`, `TabBar`, `PitwallIconButton`s, registers `UpTargets`
- Favorites `FlowRow` / All apps & Categories `LazyVerticalGrid` of `AppCard` (Components.kt)
- `FooterBar` (Footer.kt), `NowPlayingBar` (NowPlayingUi.kt), `AppMenu`, `RenameAppDialog`,
  `CategoryNameDialog`, `AppCategoriesDialog`, `AppCoverPickerDialog`, `SettingsPanel`,
  `WeatherConsentDialog`, `WeatherForecastDialog`, `MediaControlsSetupDialog` (NowPlayingUi.kt),
  `PinDialog` & `ConfirmDialog` (PinLock.kt)

`SettingsPanel` pages: main, background → presets grid or device pictures grid (`DeviceImagesPage`),
colours (`ColorPickerDialog` with 2D spectrum box, HSV/RGB sliders and hex field), weather
(`WeatherPage`), display → top-bar buttons / PIN lock, apps → custom categories, backup & reset,
About (`AboutWindow` with TV storage summary).

Also: `Display.kt` (`HomeTab`, `CardOpacity`, clock format, card size, start tab, `RemoteNavigation`,
`Backup`), `PinLock.kt` (`LockSettings`, `PinLock`, `PinDialog`, `ConfirmDialog`), `DeviceImages.kt`
(`DeviceImageCatalog`, storage/app-folder scanning, scaled bitmap decode and picker/cover import),
`SelfUpdate.kt` (update check/schedule and install, `InstallResultReceiver`,
`SelfUpdateRestartReceiver`), `Busy.kt` (`BusyOverlay`, `LocalBusy`), `HomeOverride.kt`
(`HomeOverride` targets, `HomeOverrideService`; its row and dialog are `HomeOverrideRow` /
`HomeOverrideDialog` on the Display page), `NowPlaying.kt` (`NowPlayingMonitor`,
`MediaListenerService`) and `NowPlayingUi.kt` (`NowPlayingBar`, `MediaControlsRow`,
`MediaControlsSetupDialog`).

Pure, unit-tested logic: `AppCatalog`, `BackgroundSpec`, `PresetRotator`, `DeviceImageCatalog`,
`Backup`, `ClockFormat`, `CardSize`, `CardOpacity`, `StartTab`, `RemoteNavigation`,
`ReleaseParser`, `UpdateCheckInterval`, `UpdateSchedule`, `HomeOverride`, `NowPlayingLogic`,
`MediaAccess`, `PinLock`, `LockSettings`, `StorageFormat`, `TempUnit`, `ThemeMode`/`Palette`/
`ColorRole`, `BackdropColors`, `ArtColor`, `ColorMath`, `HexColor`, `Weather*`, `NoticeTimer`,
`UpTargets`, `RoomyBringIntoView`, `NetworkStatus`.

Preferences file: `otaghome_prefs` (screen settings in `MainActivity`, app and weather settings
in `LauncherViewModel`).
