## Data Limit v0.3.1

Signed production release — **Bahasa Indonesia is now built into the app**.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.3.1
- **In-app language setting (English / Bahasa Indonesia).** A new
  **LANGUAGE** card in the dashboard lets you switch the whole interface —
  dashboard, data gate, overlay, and notifications — between **English** and
  **Bahasa Indonesia**, no system-locale change required.
- Settings remember your choice and it survives restarts/reboots.
- Updated `indonesia.md` (Indonesian README) to match.

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

### Feature set
- Configurable data budget with **Daily / Weekly / Monthly** windows
  and **rolling 24h** or **midnight-reset** styles
- Live dashboard showing used / limit / remaining, RX & TX totals
- Foreground-service monitoring with fast polling near the limit
- Automatic **data gate** (blocking overlay + full-screen alert) when
  the budget is exhausted, plus "allow +100 MB" escape hatch
- Boot-autostart of monitoring
- Two-tap permission flow (usage access, overlays, notifications)
- Bilingual interface: Language switch (English / Bahasa Indonesia)

### Known limits (Android)
A normal app cannot physically cut the mobile radio without root or a
system signature. The gate works by blocking the interface until you
dismiss it; on rooted devices a companion module could disable the radio.

---

© 2026 arlingkin — https://arlingkin.vercel.app