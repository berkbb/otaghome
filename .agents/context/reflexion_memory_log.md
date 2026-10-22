# Reflexion log

- **Holes in shapes**: circles drawn counter-clockwise inside a clockwise rect cancel out under the
  non-zero fill rule. `vec.circle()` and `vec.ellipse()` wind clockwise like `rect()`.
- **zsh does not split words**: `f $xy` passes "120 90" as one argument. Use Python or arrays for
  generated assets.
- **Quotes in generated strings**: an apostrophe in Turkish text ("Ayarlar'dan") broke inline
  Python. Write such strings from a file with double-quoted literals.
- **adb key presses**: presses closer than about a second apart are merged; focus lands elsewhere.
- **A moving card keeps focus**: `requestFocus` does nothing, so nothing scrolls. Call
  `BringIntoViewRequester.bringIntoView()` whenever the card lands somewhere new.
- **Two clocks**: the footer's countdown and a separate dismiss timer drifted apart; the
  countdown alone ends the message now.
- **Fades at rest**: edge fades must depend on `canScrollBackward` / `canScrollForward`, or the
  first row looks cut.
- **Timers are not animations**: the footer countdown ran as `Animatable.animateTo`; with the TV's
  animator duration scale at 0 (animations off) it ended at once and a 10-second message closed
  instantly. Anything that measures time uses the clock (`SystemClock.uptimeMillis` + `delay`);
  animations are only for looks.
