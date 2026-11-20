# Privacy rules

- Network: Open-Meteo for the weather (including its 5-day daily forecast), ipwho.is / ipapi.co
  only after the user's consent, and GitHub for the update check (manual or optional scheduled
  check, off by default). Nothing else.
- The consent can be changed in Settings → Weather; without it the internet address is never sent.
- No analytics, crash reporting or tracking SDKs.
- The Home override's accessibility service stays off until the user turns it on, never reads
  window content (`canRetrieveWindowContent="false"`) and listens only to the TV's own home
  screen packages.
- `WRITE_SECURE_SETTINGS` is only ever granted by the user over adb, and is used only to add
  OtagHome's own service to the enabled accessibility services (or, on Android 8.0–8.1, its own
  `MediaListenerService` to `enabled_notification_listeners`); other entries stay as they are.
- `MediaListenerService` (used only for `MediaSessionManager.getActiveSessions` when Notification
  access is granted for Now Playing controls) reads no notifications and sends nothing anywhere.
- Repository hygiene: no absolute local paths, no personal data, no keystores or passwords.
- Picture backgrounds use a persisted read permission on the chosen document only, released when
  the picture is replaced; on pickerless TVs, optional photo access (`READ_MEDIA_IMAGES`, or
  `READ_EXTERNAL_STORAGE` up to Android 12) is requested only when the user asks to browse TV
  folders in Settings → Background, stays strictly local on the device and sends nothing anywhere.
- Optional 4-digit PIN lock (`PinLock`) stores only a per-device random salt and SHA-256 hash
  locally, excluded from exported JSON backups.
