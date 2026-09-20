## Data Limit v0.4.0 — DALIM Core

Signed production release of the **DALIM Core** engine: the app was rebuilt
around a new monitoring core (data + battery + notifications vault + per-app
firewall) with a fully redesigned UI, still in English / Bahasa Indonesia.

Check `SHA256SUMS.txt` to verify the file.

### What's new in v0.4.0
- **DALIM Core redesign.** New dashboard with live cards — **Data**, **Battery**,
  **Notifications vault**, **App control**, and **Settings** — replacing the
  single-screen layout. New dedicated screens for each card.
- **Battery monitoring.** Live battery voltage / temperature / drain-rate and
  time-to-empty estimation, an optional **battery floor gate** that halts data
  when charge runs low, charging-session alerts, and a per-day **battery
  budget**.
- **Notifications vault.** All missed alerts are stored locally (SQLite) and
  shown in a searchable list with 3 / 7 / 30-day retention — nothing is lost
  while the notification is dismissed.
- **Per-app firewall (Android 8+).** Uses a **local VPN** connection to block
  selected apps from the internet — no root required — with per-app data
  budgets and temporary allow-until blocks, plus a live traffic view.
- **One fewer dependency set.** Everything runs on the AndroidX / Material
  stack and the existing SQLite engine already in the project — no new
  libraries were added.
- Data budget, gate, autostart, and the in-app language switch keep working
  exactly as before.

### What's new in v0.3.x (for reference)
- **v0.3.1** — in-app language setting (English / Bahasa Indonesia), applied
  to every screen, gate, overlay, and notification.
- **v0.3.0** — signed production build; icon fixed to match the artwork;
  the halt gate re-pops immediately so it cannot be swiped away without
  choosing Allow +100 MB / Reset / Stop.

### Feature set
- Data budget: **Daily / Weekly / Monthly**, fixed (midnight reset) or
  rolling 24 h styles; live dashboard gauge with RX & TX totals
- Foreground-service monitoring, fast polling near the limit, boot-autostart
- Data gate overlay + full-screen alert with "+100 MB" escape hatch
- **Battery panel**: drain estimate, charge alerts, battery-floor halt
- **Notifications vault** with 3/7/30-day retention
- **Per-app firewall & budgets** via local VPN (Android 8+, no root)
- Bilingual interface: Language switch (English / Bahasa Indonesia)

### Known limits (Android)
- The firewall is a **local VPN** (usable without root) — a per-app data
  cap can *not* meter traffic the way the carrier does; it gates the network
  at the system level like the VPN it is.
- A normal app cannot physically cut the mobile radio without root or a
  system signature. The data gate works by blocking the interface until you
  dismiss it; on rooted devices a companion module could disable the radio.

---

© 2026 arlingkin — https://arlingkin.vercel.app