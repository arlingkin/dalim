# Data Limit (dalim)

> 🇮🇩 **Bahasa Indonesia?** Baca → [indonesia.md](indonesia.md)

[![CI Build](https://github.com/arlingkin/dalim/actions/workflows/build.yml/badge.svg)](https://github.com/arlingkin/dalim/actions)
[![Release](https://img.shields.io/badge/release-v0.4.0-blue)](https://github.com/arlingkin/dalim/releases/tag/v0.4.0)

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

---

## Download

Latest build is a **production release**, signed with the release
keystore. Install on Android 8.0+ (API 26).

| Version | Type | Download |
|---------|------|----------|
| `v0.4.0` | Production · signed | [datalimit-0.4.0-signed.apk](https://github.com/arlingkin/dalim/releases/download/v0.4.0/datalimit-0.4.0-signed.apk) |

- Release page (anchor to tag): [github.com/arlingkin/dalim/releases/tag/v0.4.0](https://github.com/arlingkin/dalim/releases/tag/v0.4.0)
- Checksum: [SHA256SUMS.txt](https://github.com/arlingkin/dalim/releases/download/v0.4.0/SHA256SUMS.txt)

Earlier testing builds (`v0.2.x-pre`, unsigned / debug-signed) are still
available on the [releases page](https://github.com/arlingkin/dalim/releases).

## Features

| Area | Details |
|------|---------|
| Budget types | Daily · Weekly · Monthly |
| Window styles | Fixed (midnight reset) / Rolling 24 h |
| Dashboard | Live cards: Data (gauge, RX & TX, last check) · Battery · Vault · App control · Settings |
| Gate | Full-screen blocking overlay over any app + re-popping full-screen alert, "+100 MB" escape hatch |
| Battery panel | Voltage / temperature / drain-rate, time-to-empty estimate, charge alerts, battery-floor halt |
| Notifications vault | SQLite-backed history of alerts with 3 / 7 / 30-day retention, searchable |
| Firewall | Per-app blocking + per-app budgets via local **VPN** (Android 8+, no root), temporary allow-until blocks |
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
- Scheduling (e.g. "weekend unlimited")
- Export/import of configuration
- Connection history / daily report graphs

## Copyright

© 2026 **arlingkin**. — [arlingkin.vercel.app](https://arlingkin.vercel.app)

## License

AGPL-3.0 – see `LICENSE` in the repository root.