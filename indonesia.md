# 🌟 Data Limit (dalim) — Bahasa Indonesia 🇮🇩

[![CI Build](https://github.com/arlingkin/dalim/actions/workflows/build.yml/badge.svg)](https://github.com/arlingkin/dalim/actions)
[![Rilis](https://img.shields.io/badge/rilis-v0.4.0-blue)](https://github.com/arlingkin/dalim/releases/tag/v0.4.0)

> 🔎 Butuh versi **English**? Lihat → [README.md](README.md)

---

## ✨ Tentang Aplikasi

Lacak pemakaian **data internet** Anda di Android dan terapkan **batas
pemakaian** otomatis sesuai yang Anda atur: **harian**, **mingguan**, atau
**bulanan** 📊

> ⚠️ **Penting:** aplikasi biasa TIDAK bisa memutus sinyal radio seluler.
> Saat batas tercapai, aplikasi mengunci seluruh layar dengan **gerbang
> data** (data gate) — di atas aplikasi apa pun yang sedang Anda buka 🚧 —
> sampai Anda secara eksplisit mengizinkan pemakaian data tambahan.

💬 Aplikasi kini **bilingual** — bisa ganti bahasa antarmuka antara
**English** dan **Bahasa Indonesia** lewat pengaturan **LANGUAGE** di dalam
aplikasi, tanpa harus mengubah bahasa sistem.

🧠 **DALIM Core (v0.4.0)** — aplikasi dibangun ulang di atas mesin pemantauan
yang melacak **data**, **baterai**, **kubah notifikasi**, dan **firewall per
aplikasi**, semuanya dari dasbor kartu yang didesain ulang — tanpa menambah
dependensi baru.

---

## 📥 Download

Versi terbaru adalah **rilis produksi**, ditandatangani dengan **keystore
rilis** 🔐. Dapat dipasang di **Android 8.0+** (API 26).

| Versi | Tipe | Download |
|-------|------|----------|
| `v0.4.0` | Produksi · Ditandatangani | [datalimit-0.4.0-signed.apk](https://github.com/arlingkin/dalim/releases/download/v0.4.0/datalimit-0.4.0-signed.apk) |

- Halaman rilis (pranala ke tag): [github.com/arlingkin/dalim/releases/tag/v0.4.0](https://github.com/arlingkin/dalim/releases/tag/v0.4.0)
- Cek jumlah: [SHA256SUMS.txt](https://github.com/arlingkin/dalim/releases/download/v0.4.0/SHA256SUMS.txt)

Versi uji sebelumnya (`v0.2.x-pre`, tanpa tanda tangan / debug-signed) masih
tersedia di [halaman rilis](https://github.com/arlingkin/dalim/releases).

---

## 🚀 Fitur

| Area | Detail |
|------|--------|
| Jenis anggaran | Harian · Mingguan · Bulanan |
| Gaya periode | Tetap (reset tengah malam) / Bergulir 24 jam |
| Dasbor | Kartu langsung: Data (gauge, RX & TX, waktu cek) · Baterai · Kubah · Kontrol aplikasi · Pengaturan |
| Gerbang data | Overlay pemblokiran layar penuh di atas aplikasi apa pun + peringatan layar penuh yang muncul lagi, pintu darurat "+100 MB" |
| Panel baterai | Tegangan / suhu / laju pengurasan, perkiraan waktu habis, peringatan pengisian, penghentian saat baterai nyaris habis |
| Kubah notifikasi | Riwayat peringatan berbasis SQLite dengan retensi 3 / 7 / 30 hari, bisa dicari |
| Firewall | Blokir per aplikasi + anggaran per aplikasi lewat **VPN** lokal (Android 8+, tanpa root), izin sementara hingga waktu tertentu |
| Autostart | Pemantauan dilanjutkan otomatis setelah perangkat reboot |
| Izin | Alur sekali sentuh untuk Akses Penggunaan, Overlay, Notifikasi |
| Bahasa | English · Bahasa Indonesia (ganti lewat aplikasi) |

---

## 🔐 Penjelasan Izin

| Izin | Alasan |
|------|--------|
| `PACKAGE_USAGE_STATS` | Membaca total pemakaian seluler/Wi-Fi secara akurat |
| `SYSTEM_ALERT_WINDOW` | Menampilkan overlay gerbang data / penghentian baterai |
| `POST_NOTIFICATIONS` | Menampilkan peringatan prioritas tinggi |
| `FOREGROUND_SERVICE` + `dataSync` | Menjaga penghitung tetap berjalan di latar belakang |
| `RECEIVE_BOOT_COMPLETED` | Memulai pemantauan otomatis setelah reboot |
| (tidak ada – VPN) | Firewall per aplikasi memakai **`VpnService`** lokal (tanpa izin khusus) |

---

## 🛠️ Membangun Secara Lokal

```bash
# Membutuhkan Android SDK (Android Studio cara paling mudah)
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Atau jalankan `gradle wrapper --gradle-version 8.7` terlebih dahulu untuk
membuat `gradlew`.

Untuk membuat **rilis bertanda tangan** Anda memerlukan keystore rilis dan
kredensialnya (diberikan lewat variabel lingkungan `DALIM_KEYSTORE_*` —
tidak pernah di-commit):

```bash
export DALIM_KEYSTORE_FILE=/path/ke/dalim-release.jks
export DALIM_KEYSTORE_PASSWORD=...
export DALIM_KEY_ALIAS=dalim
export DALIM_KEY_PASSWORD=...
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

---

## 🔄 CI / Rilis

Setiap push ke `main` (atau **Run** manual) membuat APK rilis yang
**ditandatangani** melalui **GitHub Actions**, menggunakan keystore rilis
yang dipulihkan dari secret repositori (`DALIM_KEYSTORE_RELEASES` plus
`DALIM_KEYSTORE_PASSWORD`, `DALIM_KEY_ALIAS`, `DALIM_KEY_PASSWORD`), lalu
mempublikasikannya sebagai rilis GitHub penuh (`v0.4.0` dan seterusnya)
dengan checksum `SHA256SUMS.txt`.

---

## 🗺️ Peta Jalan

- 📡 Modul root/sistem opsional untuk benar-benar mematikan radio
- 📅 Penjadwalan (mis. "akhir pekan tanpa batas")
- 💾 Ekspor/impor konfigurasi
- 📈 Riwayat koneksi / grafik laporan harian

---

## ©️ Hak Cipta

© 2026 **arlingkin**. — [arlingkin.vercel.app](https://arlingkin.vercel.app)

## ⚖️ Lisensi

AGPL-3.0 — lihat `LICENSE` di akar repositori.