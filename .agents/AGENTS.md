# AGENTS.md - OtagHome Constitution

## Purpose and scope
OtagHome is a home screen (launcher) for Android TV and Google TV, Android 8.0+ (API 26), in the
Pitwall look modernised with Material 3 shapes. Every agent working here follows this file, the
rules in `.agents/rules/`, the procedures in `.agents/skills/` and the knowledge in
`.agents/context/`.

## Core invariants

### 1. Simplicity and lightness come first
- One activity, Jetpack Compose and AndroidX only; no new third-party runtime library without the
  user's explicit consent.
- Artwork is vector only; a new picture stays under about 20 KB of XML. The APK stays near 1.5 MB.
- A new setting is one row that cycles through a few choices, or a short page. Prefer extending
  an existing page to adding a new one.
- Network use is limited to the weather and the self-update check. No analytics, no tracking.
- Read `.agents/context/user_rejected_spec.md` before proposing anything.

### 2. Trilingual 1:1 strings
English (`values`), Turkish (`values-tr`) and Romanian (`values-ro`) hold exactly the same keys
and placeholders. The UI follows the TV's language; no in-app language picker. No hard-coded
display text in Compose. See `rules/localization_rules.md`.

### 3. Tests are mandatory
`./gradlew test assembleRelease` must pass before a task is reported done. Pure logic lives
outside Compose and gets a unit test. See `rules/testing_rules.md`.

### 4. Privacy
- The internet address is sent to an IP-location service only after the user allows it.
- No absolute local paths, personal data or secrets in the repository; keystores stay in the
  git-ignored `keystore.properties` or `OTAGHOME_*` environment variables.
See `rules/privacy_rules.md`.

### 5. The user's TV
Never connect to, install on, or drive the user's TV over adb unless the user says so in the
current request. When asked, keep key presses at least a second apart.

### 6. English documentation
All Markdown files, including everything under `.agents/`, are written in English.

## Navigation
- `context/domain_wiki.md` - concepts and how the screen works
- `context/code_graph_wiki.md` - which file does what
- `context/roadmap.md` - agreed next steps
- `context/user_rejected_spec.md` - ideas the user turned down
- `context/reflexion_memory_log.md` - mistakes made here and how to avoid them
- `rules/` - UI, localisation, privacy and testing rules
- `skills/` - running the checks, adding a landscape
