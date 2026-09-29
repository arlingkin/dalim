## Data Limit v0.5.2 — Card-tap crash fix (stability)

**Stability fix release.** v0.5.1 stopped the crash-*loop*, but two screens could
still close instantly the moment you opened them: the **DATA** and **BATTERY**
cards force-closed the app. Both screens read the app context in a field
initializer, which runs before the screen is attached, so the lookup returned
nothing and the screen died during startup. This release fixes that and removes
the background-thread and database churn that could also end the app early.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.5.2
- **DATA and BATTERY cards no longer close the app.** Both screens now
  initialize their data readers after the screen is attached instead of during
  startup, so tapping either card opens it normally.
- **No more runaway database connections.** The dashboard was opening a fresh
  database connection every 5 seconds while it sat open, and the vault search
  opened a new thread for every keystroke. A single shared worker now handles
  this work, so a long session can no longer exhaust the app's file
  descriptors and get killed.
- **A failing screen update can no longer kill the app.** Every screen refresh
  — DATA, BATTERY, HISTORY, NOTIFICATIONS, APP CONTROL, SETTINGS, the data gate
  and the monitor — now survives an individual error, and a screen that is
  already closing is never updated again.
- **Cleaner battery history.** The monitor wrote three duplicate rows per poll
  into the battery chart. It now records a point only when the level really
  changes, which makes the 24-hour chart easier to read.
- **Config import is safer.** Importing a malformed config no longer risks
  closing the app mid-import, and a bad file is reported instead.
- **A corrupt setting can no longer break every screen.** An unreadable
  budget-period or window setting now falls back to its default.
- **App Control lists at most 100 apps**, so a device with a very large app list
  no longer builds an oversized screen.
- All v0.5.1 and v0.5.0 features unchanged: crash-loop guard, schedule
  data-gate, config export/import, usage-history graphs, animated UI (with
  Reduce-motion option).

### Known behavior change
- The battery chart records a point when the level changes (or at most every
  5 minutes) instead of several times per poll. Duplicate rows written by
  earlier versions remain until the normal retention purge clears them.

### What's new in v0.5.1 (for reference)
- **No more crash-loop.** The monitor poll tick is now guarded end-to-end: a
  failure in any tick path (usage-history writes, schedule/battery reads,
  notifications) is caught, logged (`DataLimitMonitor` tag), and the loop
  continues at a safe slow pace instead of killing the app. The foreground
  service can no longer take the whole app down by crashing repeatedly.
- **Dashboard & DATA screen hardened the same way.** The battery render on the
  dashboard and the DATA screen's 5-second refresh tick now survive individual
  render errors (the refresh loop already recovered from data/vault failures).

### What's new in v0.5.0 (for reference)
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