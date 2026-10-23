# Development

OtagHome is kept **simple and light**: one activity, Jetpack Compose, no third-party libraries
beyond AndroidX, vector artwork only, and network use limited to the weather. Read
[`.agents/AGENTS.md`](.agents/AGENTS.md) before changing anything.

## Requirements

- JDK 17–21 (`JAVA_HOME`; `package.sh` finds one on macOS)
- Android SDK (`ANDROID_HOME`), compile SDK 37, AGP 9, Kotlin 2.2 with the Compose compiler plugin
- Python 3 with Pillow, only for `tools/landscapes/`

## Build and test

```sh
./gradlew test                 # unit tests (must pass before any change is done)
./gradlew assembleRelease      # app/build/outputs/apk/release/otaghome-v<version>-universal-release.apk
./package.sh                   # clean + test + release APK and SHA256SUMS in dist/ (Finder: package.command)
```

The update check reads `https://api.github.com/repos/berkbb/otaghome/releases/latest` and installs
the first `*-release.apk` asset; publish `dist/otaghome-v<version>-universal-release.apk` there with a `v<version>`
tag. Only the release variant exists. Without `keystore.properties` or `OTAGHOME_KEYSTORE_FILE` the
release is signed with the machine's debug key; `release-cert.sha256` records the expected
certificate and `package.sh` warns on a mismatch. A different key means installed TVs cannot
update without uninstalling first.

## Try it on a TV

```sh
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/release/otaghome-v1.0.2-universal-release.apk
adb shell am start -n com.berkbb.otaghome/.MainActivity
adb exec-out screencap -p > screen.png
```

After checking a TV, add it to *Tested TVs* in the README with its Android version and what was
checked.

Keep key presses at least a second apart when driving the UI over adb; faster presses are
merged and focus ends up somewhere unexpected.

## Layout of the code

| File | Role |
|---|---|
| `MainActivity.kt` | Activity, preferences of the screen (theme, background, colours, veil, message time), `LauncherScreen`, background drawing |
| `LauncherViewModel.kt` | Installed apps and their art, favorites, orders, sort modes, system-app mode, weather |
| `Components.kt` | Top bar, clock, tabs, buttons, app cards, remote keys, placement animation |
| `SettingsPanel.kt` | Settings side sheet and its pages (main, background, ready-made pictures, colours, weather) |
| `ColorPicker.kt` | Colour picker dialog (hex, spectrum, RGB) and colour rows |
| `AppMenu.kt`, `About.kt`, `Footer.kt`, `WeatherUi.kt` | Long-press menu, About and licence, footer messages, weather consent and settings |
| `AppCatalog.kt` | Pure rules: user-facing apps, merging, ordering, moving, sort and system-app modes |
| `BackgroundSpec.kt` | Backgrounds, ready-made pictures, gradient direction, picture overlay |
| `ThemeMode.kt` | Theme modes, palettes, colour roles, Material 3 corner scale |
| `BackdropColors.kt`, `ArtColor.kt`, `ColorMath.kt` | Contrast, colours read from pictures, HSV ↔ RGB |
| `Weather.kt`, `UpTargets.kt`, `RoomyBringIntoView.kt`, `NetworkStatus.kt` | Weather model and client, Up navigation, scroll margins, network icon |
| `Display.kt` | Tabs, clock format, card size, start tab, settings backup format |
| `SelfUpdate.kt` | Update check against GitHub releases, download with digest check, install, restart |
| `Busy.kt` | Full-screen progress ring while the screen is rebuilt or redrawn |

Pure logic stays out of Compose so it can be unit-tested; every such file has a test in
`app/src/test`.

## Strings

Every key exists in `values`, `values-tr` and `values-ro` with the same placeholders;
`LocalizationParityTest` enforces it. Text from the TV (dates, app names, language names, city
names) needs no translation.

## Landscapes

The 30 landscapes are generated, not drawn by hand:

```sh
cd tools/landscapes
python3 build.py ../../app/src/main/res/drawable ./preview scenes1 scenes2 scenes3
```

`vec.py` holds the drawing helpers, `scenes*.py` the scenes and `build.py` writes
`bg_tr_<name>.xml` and a 16:9 contact sheet in the preview folder (Quick Look renders it on
macOS). After adding a scene, add its `PresetImage` entry with three sample colours (top,
middle, bottom of the picture) and its name in all three languages. Keep each file under about
20 KB.
