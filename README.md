# Data Limit (dalim)

Track your data usage on Android and automatically enforce a budget you
configure: **daily**, **weekly**, or **monthly**.

> A normal app cannot kill the mobile radio. When the limit is hit the
> app blocks the screen with a full-screen **data gate** until you
> explicitly allow more data, effectively stopping continued use.

---

## Features

| Area | Details |
|------|---------|
| Budget types | Daily · Weekly · Monthly |
| Window styles | Fixed (midnight reset) / Rolling 24 h |
| Dashboard | Used / Limit / Remaining gauge, RX & TX, last check time |
| Gate | Blocking overlay + full-screen alert, "+100 MB" escape hatch |
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
(`v0.1.0-pre`).

No signing key is required for this pre-release workflow.

## Roadmap

- Optional root/system module to actually disable the radio
- Per-app data breakdown
- Alerts at 80 % / 90 %
- Scheduling (e.g. "weekend unlimited")
- Export/import of configuration

## License

AGPL-3.0 – see `LICENSE` in the repository root.