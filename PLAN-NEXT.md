# PLAN-NEXT — v0.5.0 "Planning & Insights"

> Candidate roadmap items consolidated into one release. Same rules as
> v0.4.0: feature branch → CI (test+lint+assembleRelease) → fixes → main →
> release. No new dependencies; all state in `UsagePrefs` / SQLite.

## 0. Release shape
- Name: **v0.5.0** (versionCode 9, versionName "0.5.0"; APP_VERSION bump in workflow)
  — note versionCode is already 8 on v0.4.25, so the next release must be 9.
- Scope: 4 features, 1 theme — plan *when* data is used (Scheduling), move it
  between devices (Export/Import), see it over time (History), and a
  **polished animated native UI** (M4).
- Framework decision: **not Flutter.** The app's core rides on native-only
  platform APIs (NetworkStatsManager, VpnService, SYSTEM_ALERT_WINDOW,
  NotificationListenerService, foreground services + appops) that a Flutter UI
  cannot replace — it would only add the Flutter SDK + render engine behind
  platform channels, breaking the zero-dependency rule. Animated layouts stay
  native (Views + ValueAnimator), aligned with existing code.
- Docs: update README.md + indonesia.md + .github/release-notes.md.
- New tests each M; CI must stay green before each merge.

---

## M1 — Scheduling ("weekend unlimited" + quiet windows)

### Behavior
- Per-budget **active window**: optional daily/weekly time range when data
  may be used; outside it → auto-halt via existing gate (overlay + full-screen).
- Presets: `Every day 00:00–23:59`, `Weekdays`, `Weekend`, `Custom range`.

### State (UsagePrefs)
- `scheduleWindowStyle` (`off|weekdays|weekend|everyday|custom`), default `off`
- `scheduleStartMin`, `scheduleEndMin` (minutes-of-day), default 0..1439
- Derived helper: `fun isOutsideSchedule() = windowStyle != off && !nowInRange()`

### Cut
- `core/Schedule.kt` (pure): `nowInRange(now, start, end)` incl. overnight
  wrap (e.g. 22:00→06:00) — **unit-tested** like M1
- Halt poly: `TrafficMonitorService` poll ticks → if outside schedule, reuse
  the existing gate-halt path (skip when monitoring off)
- Dashboard data card shows schedule status line; Data screen gains a
  "Schedule" section; wording mirrors gate strings (EN/ID)

### Edge cases
- Overnight ranges wrap midnight; DST ignored (device-local clock)
- Existing budget gate unaffected when schedule=off
- Re-halt re-pops on fast poll (same behavior as limit hit)

---

## M2 — Config export / import (JSON, share sheet)

### Behavior
- Settings gains **Export config** / **Import config**
- Export: JSON snapshot → `ACTION_CREATE_DOCUMENT` (user picks location);
  Import: `ACTION_OPEN_DOCUMENT` → parse → apply
- Import never applies empty/replaced prefs on failure (atomic: write to
  temp prefs file, validate, swap)

### Format (no schema lib — hand-rolled serializer)
```json
{
  "format": "dalim-config", "version": 1,
  "limitMb": 5120, "windowStyle": "monthly", "periodType": "fixed",
  "monitoringEnabled": true,
  "firewall": { "enabled": false, "budgetsMb": { "pkg": 256 } },
  "battery": { "floor": 10, "alerts": true },
  "schedule": { "style": "off", "start": 0, "end": 1439 },
  "language": "en"
}
```

### Cut
- `data/ConfigExchange.kt`: `toJson(prefs)`, `fromJson(raw) -> Result<UsagePrefsPatch>`
- `core/UsagePrefs.save/load` handle patch object
- Settings: two rows; validation snackbars (invalid file / old version)

### Edge cases
- Unknown/future fields ignored; missing fields fall back to defaults
- Import can overwrite while monitoring is running → restart monitor +
  rebuild firewall tunnel (existing `requestRebuild`)
- No file reads of arbitrary paths (scoped storage, SAF only)

---

## M3 — Usage history graphs + connection log

### Behavior
- Data screen: **History** tab — Daily/Weekly/Monthly bar series (12 buckets),
  built from a new SQLite table (`usage_history`: bucket, rx, tx)
- One row appended per window-rollover and on processor settle
- Simple Canvas/View-drawn bars (no chart library; matches no-dep rule)

### Cut
- `data/UsageHistoryStore` (SQLite): `insert(bucket, rx, tx)`,
  `series(windowLengthMonths, granularity) -> List<Bucket>`
  Backfill: on first open after upgrade, derive from current meter snapshot.
- `ui/HistoryActivity` (+ `activity_history.xml`): segmented toggle
  Daily/Weekly/Monthly; bars + totals + per-bucket labels
- Dashboard data card routes to History (replace "last check" detail)

### Edge cases
- Granularity switch keeps axis readable (auto bucket count)
- Rotation preserves selected tab (remember via `onSaveInstanceState`)
- Store writes debounced; purge older than 13 months

---

## M4 — Beautiful animated native UI (theme, zero new deps)

### Behavior
- Dashboard cards (MainActivity) animate in on first appearance (translateY +
  fade, slight stagger), like the existing battery repaint tick — reuse the
  single self-scheduling pattern, do not replace it.
- Values tween when refreshed (used/percent/level) using `ValueAnimator`
  (no dependency); progress bar fills smoothly.
- Chart views (BatteryLevelChartView + DataActivity network bars) animate on
  first draw: bars grow / line reveals. Keep the Canvas, no chart library.
- Align with primary context: same color resources, same `dp/sp` helpers,
  units are `LayoutDirection`-safe; no third-party libs added.

### Cut
- `ui/Anim.kt`: tiny helpers (`fadeSlideIn(view)`, `animateProgress(view)`,
  `countTo(textView, from, to)`), plus reusable interpolators
- Wire into `MainActivity.renderTick` / `DataActivity.refreshAppChart` /
  `BatteryActivity.recordAndRefreshChart` — animations only on value change,
  skipped while an identical value is re-rendered (already the pattern)
- Respect "reduce motion": gate animations off via `Settings > reduceMotion`
  (falls back to instant paint); store in `UsagePrefs` (`reduceMotion`)

### Edge cases
- Animations must not delay first-frame correctness: paint final value first,
  then tween (render crash-proof rule stays: never throw from tick path)
- Stagger must not depend on layout order assumptions across screens
- Reduced motion also disables chart reveal (jump straight to final state)

---

## Release steps (all M after green)
1. Feature-branch commits `feat(M*)`, CI green each push
2. Bump `versionCode`/`versionName` + workflow `APP_VERSION` to 0.5.0
3. Update release notes + README + indonesia.md (with new download row)
4. Single push: `git push <url> HEAD:main`
5. Verify release `v0.5.0` + SHA256SUMS (re-download & compare)
6. Roll roadmap: remove scheduling/export/history; keep root-radio module

---

## Risks / watch-outs
- Overnight schedule + weekly variety → covered by pure-unit tests
- JSON hand-parsing: test with unknown/extra keys & corrupt strings
- History backfill on upgrade is one-time; don't block monitor start
- Canvas bars: keep it simple (vertical bars + totals), no gestures yet