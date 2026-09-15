## Data Limit v0.1.0-pre

Unsigned / debug-signed build for testing. **Not** a production release —
there is no release keystore yet.

Check `SHA256SUMS.txt` to verify the file.

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