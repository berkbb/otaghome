# Privacy rules

- Network: Open-Meteo for the weather, ipwho.is / ipapi.co only after the user's consent, and
  GitHub for the update check. Nothing else.
- The consent can be changed in Settings → Weather; without it the internet address is never sent.
- No analytics, crash reporting or tracking SDKs.
- The Home override's accessibility service stays off until the user turns it on, never reads
  window content (`canRetrieveWindowContent="false"`) and listens only to the TV's own home
  screen packages.
- `WRITE_SECURE_SETTINGS` is only ever granted by the user over adb, and is used only to add
  OtagHome's own service to the enabled accessibility services; other entries stay as they are.
- Repository hygiene: no absolute local paths, no personal data, no keystores or passwords.
- Picture backgrounds use a persisted read permission on the chosen document only, released when
  the picture is replaced.
