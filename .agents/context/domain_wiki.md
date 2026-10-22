# Domain wiki

- **Tabs**: Favorites (centred grid, inset on all sides) and All apps (grid). A tab switches when
  it gets focus. Up from the first row goes to the top-bar control above the card (`UpTargets`).
- **User-facing apps**: launchable apps minus the launcher itself; system-image apps are shown
  per `SystemAppsMode` (TV apps only by default, hidden, or all). Apps updated from a store count
  as the user's.
- **Order**: Favorites keep the user's order; All apps are alphabetical until the first move.
  `SortMode` can override either with name A→Z / Z→A, which hides Move.
- **Move mode**: the card lifts, arrows move it (left/right one place, up/down one row), OK or
  Back drops it. The moved card is scrolled into view with a margin (`RoomyBringIntoView`).
- **Background**: a ready-made picture (`PresetImage`, 9 OtagHome + 30 Türkiye), the user's
  picture, a solid colour or a gradient. Pictures are veiled with the theme ground
  (`VeilLevel`: normal, light, off).
- **Backdrop colours**: anything drawn straight on the background (focus frames, status lines)
  picks white or dark and keeps the accent only where it reaches 3:1 contrast (WCAG).
- **Theme**: Auto (TV night mode), Dark, Light, Match background (from the background's sample
  colours). Users can replace seven theme colours (`ColorRole`); the accent is shared by both
  themes, the rest are per theme.
- **Weather**: Open-Meteo, shown in the clock card as an icon and temperature with the place
  name. The place is a searched city or, only with consent, found from the internet address.
- **Footer**: messages with an icon, close button and countdown; it takes its own room.
- **Hidden apps**: hidden from both tabs; listed in Settings → Apps, OK shows one again.
- **Busy ring**: covers the screen while apps load after the activity is (re)created and for a
  short pulse after changes that redraw it (`LocalBusy`).
- **Backup**: every preference as JSON (`Backup`), minus the weather cache and a picture chosen
  on this TV; restoring recreates the activity.
