# UI rules

- Remote first: every control is reachable with the D-pad, shows focus clearly (focus fill or an
  accent frame) and is no smaller than 44 dp.
- Colours come from `LocalPalette` (cards, panels) or `LocalBackdrop` (straight on the
  background); never a fixed colour for text on the background.
- Shapes come from `Corners` (8 / 12 / 16 / 28 dp, Material 3 scale).
- Dialogs and the settings sheet have a soft shadow; dialogs put first focus on the safest choice.
- Messages go through `LocalNotice`, never `Toast`.
- No hard-coded display text; device details come from `Build` at runtime, never brand names.
