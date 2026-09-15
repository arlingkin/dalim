## Data Limit v0.2.0-pre

Unsigned / debug-signed build for testing. **Not** a production release —
there is no release keystore yet.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.2.0-pre
- **Stronger data gate**: the block now covers the whole screen and
  intercepts touches, halting whatever app is in the foreground until you
  tap an action (Allow +100 MB / Reset / Stop).
- **Persistent popup**: the full-screen alert re-pops over any app even
  after being dismissed, as long as the limit is still exceeded.
- **New launcher icon** built from `icons/828*.jpg` (center-cropped to the
  adaptive icon shape).

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