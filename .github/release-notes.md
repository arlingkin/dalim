## Data Limit v0.5.0 — Scheduling, export/import, history & animated UI

"Planning & Insights": on top of the **DALIM Core (v0.4.0)** engine, this
release makes data gates follow a schedule you set, lets you move your config
between devices, adds usage-history graphs, and polishes the whole UI with
light-native animations. Still **zero new dependencies**.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.5.0
- **Schedule window (auto data gate).** Choose when data may be used:
  **Off · Weekdays · Weekend · Every day · Custom** time range (overnight
  ranges work too, e.g. 22:00–06:00). Outside the window, usage is halted by
  the existing full-screen gate; the dashboard data card and DATA screen show
  the schedule status.
- **Config export / import.** Settings gains **Export config / Import config**.
  Export writes a **JSON snapshot** (`format: dalim-config`, versioned) via the
  system save/share sheet; import reads it back through the file picker,
  **validates before applying** (unknown/future fields ignored, missing fields
  fall back to defaults, corrupt or wrong-version files are rejected with no
  change), and restarts the monitor / rebuilds the firewall tunnel when needed.
  Runtime monitoring state is never overwritten by an import.
- **Usage history graphs.** The DATA screen gains a **History** tab with
  **Daily / Weekly / Monthly** bar series (last 12 periods; one row per
  window-rollover), stored in a new SQLite table kept for 13 months.
- **Animated native UI.** Dashboard cards enter with a light staggered
  fade/slide, usage / percent / battery values tween smoothly, and both charts
  (per-app bars + battery line) animate on first draw — no new dependencies.
  A **Reduce motion** switch in Settings disables the animations and paints the
  final state instantly.

### What's new in v0.4.25 (for reference)
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
- Schedule window (Off / Weekdays / Weekend / Every day / Custom hours) with
  auto data-gate outside the window
- Full-screen data gate + overlay with "+100 MB" escape hatch
- Battery panel: drain estimate, charge alerts, battery-floor halt, 24 h chart
- Notifications vault (SQLite, 3/7/30-day retention) + per-app firewall &
  budgets via local VPN (no root)
- Usage history graphs (Daily / Weekly / Monthly) on the DATA screen
- Config export/import (validated JSON snapshot, SAF, atomic apply)
- Animated native UI with a Reduce-motion accessibility option
- Bilingual interface (English / Bahasa Indonesia)

### Known limits (Android)
- The firewall is a **local VPN** (usable without root); per-app caps gate the
  network at the system level.
- A normal app cannot cut the mobile radio without root/system signature; the
  gate works by blocking the interface until you act.

---

© 2026 arlingkin — https://arlingkin.vercel.app