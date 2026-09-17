## Data Limit v0.3.0

First **public production release** — signed with the release keystore.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.3.0
- **Signed production build.** APK is now signed with a release keystore
  (no more unsigned / debug-signed pre-release).
- **Icon fixed to match the artwork.** The launcher now uses the teal gate
  glyph from `icons/828*.jpg` on the adaptive-icon foreground instead of a
  blank dark tile; legacy round/square launcher icons included.
- **Halt opens the red gate immediately.** When the data limit is hit, the
  full-screen red gate re-pops on every fast poll tick — no 5-minute
  throttle — so it cannot be swiped away without picking an action
  (Allow +100 MB / Reset / Stop monitoring).

### What's new in v0.2.2-pre
- **Harder data-gate halt.**
  - The monitor now launches the blocking screen directly (granting the
    overlay permission is what allows this on modern Android), instead of
    relying only on the full-screen notification.
  - Added `USE_FULL_SCREEN_INTENT` for Android 13+ full-screen alerts.

### What's new in v0.2.1-pre
- **Copyright footer**: the dashboard footer and README now show
  "© 2026 arlingkin", clickable to https://arlingkin.vercel.app

### What's new in v0.2.0-pre
- **Stronger data gate**: the block now covers the whole screen and
  intercepts touches, halting whatever app is in the foreground until you
  tap an action (Allow +100 MB / Reset / Stop).
- **Persistent popup**: the full-screen alert re-pops over any app even
  after being dismissed, as long as the limit is still exceeded.

### Feature set
- Configurable data budget with **Daily / Weekly / Monthly** windows
  and **rolling 24h** or **midnight-reset** styles
- Live dashboard showing used / limit / remaining, RX & TX totals
- Foreground-service monitoring with fast polling near the limit
- Automatic **data gate** (blocking overlay + full-screen alert) when
  the budget is exhausted, plus "allow +100 MB" escape hatch
- Boot-autostart of monitoring
- Two-tap permission flow (usage access, overlays, notifications)

### Known limits (Android)
A normal app cannot physically cut the mobile radio without root or a
system signature. The gate works by blocking the interface until you
dismiss it; on rooted devices a companion module could disable the radio.

---

© 2026 arlingkin — https://arlingkin.vercel.app