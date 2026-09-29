# Data Limit (dalim)

> 🇮🇩 **Bahasa Indonesia?** Baca → [indonesia.md](indonesia.md)

[![CI Build](https://github.com/arlingkin/dalim/actions/workflows/build.yml/badge.svg)](https://github.com/arlingkin/dalim/actions)
[![Release](https://img.shields.io/badge/release-v0.5.2-blue)](https://github.com/arlingkin/dalim/releases/tag/v0.5.2)

Track your data usage on Android and automatically enforce a budget you
configure: **daily**, **weekly**, or **monthly**. The interface is
**bilingual** — switch between **English** and **Bahasa Indonesia** in the
app's LANGUAGE setting.

**DALIM Core (v0.4.0)** rebuilt the app around a monitoring engine that
tracks **data**, **battery**, a **notifications vault**, and a **per-app
firewall** — all from a redesigned card-based dashboard, with no new
dependencies.

> A normal app cannot kill the mobile radio. When the limit is hit the
> app locks the whole screen with a full-screen **data gate** — over
> whatever app you're in — until you explicitly allow more data.

**v0.5.0 — "Planning & Insights"** adds a per-period **schedule window**
(data is auto-gated outside the hours you choose), **configuration
export/import** (a validated JSON snapshot via the share sheet), **usage
history graphs** on the DATA screen (daily / weekly / monthly), and an
**animated native UI** with a "Reduce motion" setting — still zero new
dependencies.

**v0.5.2 — card-tap crash fix.** The **DATA** and **BATTERY** cards could close
the app the moment you tapped them: both screens read the app context before
they were attached, so opening either one failed. v0.5.2 fixes that and also
stops the app from opening a new database connection every few seconds, which
could end a long session early.

**v0.5.1 — stability fix.** On some devices the app could close by itself in
a loop (the monitor's poll tick crashed the whole app and got restarted over
and over). v0.5.1 guards every monitoring and render tick so a failing tick
can never take the app down again.

---

## Download

Latest build is a **production release**, signed with the release
keystore. Install on Android 8.0+ (API 26).

| Version | Type | Download |
|---------|------|----------|
| `v0.5.2` | Production · signed | [datalimit-0.5.2-signed.apk](https://github.com/arlingkin/dalim/releases/download/v0.5.2/datalimit-0.5.2-signed.apk) |

- Release page (anchor to tag): [github.com/arlingkin/dalim/releases/tag/v0.5.2](https://github.com/arlingkin/dalim/releases/tag/v0.5.2)
- Checksum: [SHA256SUMS.txt](https://github.com/arlingkin/dalim/releases/download/v0.5.2/SHA256SUMS.txt)

Earlier testing builds (`v0.2.x-pre`, unsigned / debug-signed) are still
available on the [releases page](https://github.com/arlingkin/dalim/releases).

## Features

| Area | Details |
|------|---------|
| Budget types | Daily · Weekly · Monthly |
| Window styles | Fixed (midnight reset) / Rolling 24 h |
| Schedule window | Off · Weekdays · Weekend · Every day · Custom hours — data auto-gated outside the active window |
| Dashboard | Live cards: Data (gauge, RX & TX, last check) · Battery · Vault · App control · Settings |
| Gate | Full-screen blocking overlay over any app + re-popping full-screen alert, "+100 MB" escape hatch |
| Battery panel | Voltage / temperature / drain-rate, time-to-empty estimate, charge alerts, **battery-floor halt**, 24 h history chart |
| Network chart | Per-app usage bars for the current window on the DATA screen |
| Usage history | DATA screen **History** tab: daily / weekly / monthly bar series (12 periods, 13-month retention) |
| Config transfer | **Export / import** a validated JSON snapshot of settings via the share/save sheet (Settings) |
| Notifications vault | SQLite-backed history of alerts with 3 / 7 / 30-day retention, searchable |
| Firewall | Per-app blocking + per-app budgets via local **VPN** (Android 8+, no root), temporary allow-until blocks |
| Animated UI | Staggered card entrance, tweened values, animated charts — disable via **Reduce motion** |
| Autostart | Monitoring resumes after reboot |
| Permissions | One-tap flow for Usage Access, Overlay, Notifications |
| Language | English · Bahasa Indonesia (in-app switch) |

## Permissions explained

| Permission | Why |
|------------|-----|
| `PACKAGE_USAGE_STATS` | Read exact mobile/Wi-Fi totals |
| `SYSTEM_ALERT_WINDOW` | Show the blocking data-gate / battery-floor overlays |
| `POST_NOTIFICATIONS` | Display high-priority alerts |
| `FOREGROUND_SERVICE` + `dataSync` | Keep the counter running in background |
| `RECEIVE_BOOT_COMPLETED` | Auto-start monitoring after reboot |
| (none – VPN) | The per-app firewall uses a **local `VpnService`** (no permission needed) |

## Building locally

```bash
# Requires Android SDK (Android Studio is the easiest way)
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Or run `gradle wrapper --gradle-version 8.7` first to generate `gradlew`.

To build a **signed release** you need the release keystore and its
credentials (they are injected as `DALIM_KEYSTORE_*` environment variables —
never committed):

```bash
export DALIM_KEYSTORE_FILE=/path/to/dalim-release.jks
export DALIM_KEYSTORE_PASSWORD=...
export DALIM_KEY_ALIAS=dalim
export DALIM_KEY_PASSWORD=...
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

## CI / Release

Every push to `main` (or manual **Run**) builds a **signed** release APK via
**GitHub Actions**, using the release keystore restored from repository
secrets (`DALIM_KEYSTORE_RELEASES` plus `DALIM_KEYSTORE_PASSWORD`,
`DALIM_KEY_ALIAS`, `DALIM_KEY_PASSWORD`), and publishes it as a full GitHub
release (`v0.4.0` and later) with a `SHA256SUMS.txt` checksum.

## Roadmap

- Optional root/system module to actually disable the radio
- (Scheduling, config export/import and history graphs shipped in **v0.5.0**)
- (Crash-loop hardening shipped in **v0.5.1**)
- (Card-tap crash fix and database-connection fix shipped in **v0.5.2**)

## Copyright

© 2026 **arlingkin**. — [arlingkin.vercel.app](https://arlingkin.vercel.app)

## License

AGPL-3.0 – see `LICENSE` in the repository root.