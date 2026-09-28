## Data Limit v0.4.25 — Charts & battery gate

Signed production release on top of the **DALIM Core (v0.4.0)** engine with a
pair of new charts and a fully working battery-floor gate.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.4.25
- **Network usage chart.** The **DATA** screen now lists per-app usage for the
  current window (daily / weekly / monthly, fixed or rolling) as horizontal
  bar rows — top 6 apps by bytes, live-refreshed.
- **Battery history chart.** The **BATTERY** screen now draws a 24-hour level
  chart (samples recorded about every minute while monitoring runs) using a
  lightweight Canvas chart view — no chart library added.
- **Battery-floor data gate (now actually enforced).** When the device is
  discharging at/below the battery floor, a dedicated **BATTERY GATE** overlay
  blocks usage until you plug in. You can snooze it until the next charge, or
  disable the behavior from the battery screen.
- **Monitor hardening.** The dashboard refresh loop no longer double-schedules,
  survives individual render errors, and the battery drain estimate can now
  actually compute (sampling is throttled to honest ≥5-minute intervals); the
  first page screens data + battery + notification vault immediately.

### What's new in v0.4.0 (for reference)
- DALIM Core redesign: card-based dashboard, battery/notification-vault/per-app
  modules, local-VPN firewall, no new dependencies.

### Feature set
- Data budget (Daily / Weekly / Monthly, fixed or rolling 24 h), live gauge with
  RX & TX totals, foreground-service monitoring, boot autostart
- Full-screen data gate + overlay with "+100 MB" escape hatch
- Battery panel: drain estimate, charge alerts, battery-floor halt, 24 h chart
- Notifications vault (SQLite, 3/7/30-day retention) + per-app firewall &
  budgets via local VPN (no root)
- Bilingual interface (English / Bahasa Indonesia)

### Known limits (Android)
- The firewall is a **local VPN** (usable without root); per-app caps gate the
  network at the system level.
- A normal app cannot cut the mobile radio without root/system signature; the
  gate works by blocking the interface until you act.

---

© 2026 arlingkin — https://arlingkin.vercel.app