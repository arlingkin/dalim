# Data Limit (dalim)

[![CI Build](https://github.com/arlingkin/dalim/actions/workflows/build.yml/badge.svg)](https://github.com/arlingkin/dalim/actions)
[![Release](https://img.shields.io/badge/release-v0.2.2--pre-blue)](https://github.com/arlingkin/dalim/releases/tag/v0.2.2-pre)

Track your data usage on Android and automatically enforce a budget you
configure: **daily**, **weekly**, or **monthly**.

> A normal app cannot kill the mobile radio. When the limit is hit the
> app locks the whole screen with a full-screen **data gate** — over
> whatever app you're in — until you explicitly allow more data.

---

## Download

Latest build is published as a GitHub **pre-release** (unsigned /
debug-signed, no release keystore yet). Install on Android 8.0+ (API 26).

| Version | Type | Download |
|---------|------|----------|
| `v0.2.2-pre` | Pre-release · unsigned | [datalimit-0.2.2-pre-unsigned.apk](https://github.com/arlingkin/dalim/releases/download/v0.2.2-pre/datalimit-0.2.2-pre-unsigned.apk) |

- Release page (anchor to tag): [github.com/arlingkin/dalim/releases/tag/v0.2.2-pre](https://github.com/arlingkin/dalim/releases/tag/v0.2.2-pre)
- Checksum: [SHA256SUMS.txt](https://github.com/arlingkin/dalim/releases/download/v0.2.2-pre/SHA256SUMS.txt)

## Features

| Area | Details |
|------|---------|
| Budget types | Daily · Weekly · Monthly |
| Window styles | Fixed (midnight reset) / Rolling 24 h |
| Dashboard | Used / Limit / Remaining gauge, RX & TX, last check time |
| Gate | Full-screen blocking overlay over any app + re-popping full-screen alert, "+100 MB" escape hatch |
| Autostart | Monitoring resumes after reboot |
| Permissions | One-tap flow for Usage Access, Overlay, Notifications |

## Permissions explained

| Permission | Why |
|------------|-----|
| `PACKAGE_USAGE_STATS` | Read exact mobile/Wi-Fi totals |
| `SYSTEM_ALERT_WINDOW` | Show the blocking data-gate overlay |
| `POST_NOTIFICATIONS` | Display high-priority alerts |
| `FOREGROUND_SERVICE` + `dataSync` | Keep the counter running in background |
| `RECEIVE_BOOT_COMPLETED` | Auto-start monitoring after reboot |

## Building locally

```bash
# Requires Android SDK (Android Studio is the easiest way)
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Or run `gradle wrapper --gradle-version 8.7` first to generate `gradlew`.

## CI / Pre-release

Every push to `main` (or manual **Run**) builds an unsigned / debug-signed
APK via **GitHub Actions** and publishes it as a GitHub pre-release
(`v0.2.2-pre`). No signing key is required for this pre-release workflow.

## Roadmap

- Optional root/system module to actually disable the radio
- Per-app data breakdown
- Alerts at 80 % / 90 %
- Scheduling (e.g. "weekend unlimited")
- Export/import of configuration

## Copyright

© 2026 **arlingkin**. — [arlingkin.vercel.app](https://arlingkin.vercel.app)

## License

AGPL-3.0 – see `LICENSE` in the repository root.