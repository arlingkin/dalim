# PLAN-NEXT — release planning after v0.5.2

> Working notes for the next release. Same rules that shipped v0.5.0: feature
> branch → CI (test + lint + assembleRelease) → fixes → merge to **main** →
> release. No new dependencies; all state in `UsagePrefs` / SQLite; every
> screen/feature bilingual (EN / ID).

## 🧪 v0.5.2 — in verification (card-tap crash fix)
- versionCode **11** · versionName **0.5.2** · workflow `APP_VERSION: 0.5.2`.
- **Fix — card-tap crash:** `DataActivity` and `BatteryActivity` read
  `applicationContext` in a field initializer, which runs before
  `attachBaseContext`, so `ContextWrapper.mBase` was null and both screens
  died during construction — tapping the DATA or BATTERY card force-closed
  the app. Both now initialize lazily.
- **Fix — DB/thread churn:** added `ui/Background.kt`, a single serialized
  worker. Every screen used to spawn a raw `Thread` per 5 s tick and per
  keystroke, each opening its own SQLite connection (the dashboard leaked one
  connection every 5 s). Refresh jobs coalesce; mutating jobs
  (`coalesce = false`) are never dropped.
- **Fix — duplicate battery rows:** `TrafficMonitorService.batterySnapshot()`
  ran 3x per poll and appended a row each time, on a fresh store per call. Now
  one reused store, recording only on a real level change.
- Also: `COUNT(*)` moved off the main thread in NOTIFICATIONS, `prefs.apply`
  guarded in SETTINGS config import, `AppControlActivity` capped at 100 rows,
  render guarded on resume in DATA / DATA GATE, and `UsagePrefs` enum parsing
  falls back to a default instead of throwing.
- Docs rolled: README.md + indonesia.md + .github/release-notes.md updated.
- **Not yet verified on the reporter's device** — the original report was that
  tapping a card force-closes the app. The unit tests are pure-logic only
  (`core` / `data` / `monitor`) and do not exercise any Activity, so CI green
  proves it builds, not that the crash is gone. Awaiting on-device confirmation.

## ✅ v0.5.1 — shipped (stability fix)
- versionCode **10** · versionName **0.5.1** · workflow `APP_VERSION: 0.5.1`.
- **Fix — crash-loop:** the monitor poll tick (`TrafficMonitorService`) and the
  dashboard battery render / DATA refresh tick could take the whole process
  down on a per-tick failure; `START_STICKY` then restarted the service and
  re-crashed in an infinite loop ("app closes by itself"). All tick paths are
  now try/catch-guarded; monitor logs failures under the `DataLimitMonitor`
  tag and falls back to the slow poll rate instead of crashing.
- Docs rolled: README.md + indonesia.md + .github/release-notes.md updated;
  public roadmap keeps only the root-radio module item.
- Verified on-device by the user before any further release work.

## ✅ v0.5.0 — shipped ("Planning & Insights")
- versionCode **9** · versionName **0.5.0** · workflow `APP_VERSION: 0.5.0`.
- **M1 — Schedule window:** per-period active window (Off / Weekdays / Weekend /
  Every day / Custom, overnight ranges OK); outside the window the existing
  full-screen gate auto-halts usage; status on dashboard data card + DATA
  screen. (`core/Schedule.kt`, unit-tested.)
- **M2 — Config export/import:** Settings **Export/Import config**; JSON
  snapshot via SAF; validate-before-apply; unknown fields ignored, missing →
  defaults; corrupt/wrong-version rejected atomically; monitor restart +
  firewall tunnel rebuild on import; runtime monitoring never overwritten.
  (`data/ConfigExchange.kt`, dependency-free; test suite added.)
- **M3 — Usage history graphs:** DATA screen **History** tab, Daily / Weekly /
  Monthly bars (last 12 periods), SQLite `usage_history` kept 13 months.
- **M4 — Animated native UI:** `ui/Anim.kt` helpers (fadeSlideIn,
  animateProgress, countTo, growWidth); staggered card entrance, tweened
  values, chart reveals (bars + battery line); **Reduce motion** switch in
  Settings paints final state instantly.
- Docs rolled: README.md + indonesia.md + .github/release-notes.md updated;
  public roadmap keeps only the root-radio module item.

## 0. Release shape (next)
- Name: **v0.6.0** (versionCode **12**, versionName "0.6.0"; bump workflow
  `APP_VERSION`) — versionCode is already 11 on v0.5.2.
- Framework decision unchanged: **not Flutter** — native-only platform APIs
  (NetworkStatsManager, VpnService, SYSTEM_ALERT_WINDOW, NotificationListener,
  foreground services + appops) can't be replaced by a Flutter UI, which would
  only add the SDK + render engine behind platform channels and break the
  zero-dependency rule. Animations stay native (Views + ValueAnimator).
- Each release updates README.md + indonesia.md + .github/release-notes.md and
  adds tests; CI green before merge; delete merged feature branches.

## Candidate M1 — Root/system module to actually disable the radio
(The remaining public roadmap item.)
- Optional root / system-signed companion that truly cuts data (e.g.
  `svc data disable`) instead of only UI-blocking; the app stays the
  scheduler/UI, the module only executes the side effect.
- Open questions: delivery (separate Magisk module vs. embedded with root
  check)? Fallback when the module is absent? Never disable radio when
  tethering/emergency depends on it; require explicit opt-in consent.

## More candidates (unranked)
- Connection log / daily report export (CSV/PDF)
- "Weekend unlimited" preset surfaced as a named budget type
- Quick-glance quota widget / notification
- Staged percentage warnings (e.g. 80% → 100%)

## Risks / watch-outs
- Root paths are device-specific — keep the module optional and self-testing.
- Don't regress the v0.5.0 schedule / history / export / animation surface.
- Reduce-motion correctness: animations must never delay first paint.
- Confirm the v0.5.2 card-tap fix on the reporter's device before tagging;
  a residual failure is no longer a crash-loop, so it needs a real logcat
  (`adb logcat -d AndroidRuntime:E *:S`) rather than another static pass.