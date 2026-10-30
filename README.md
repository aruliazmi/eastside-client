# Eastside Stories — Android Client Release

Official Android Client Launcher & Game Engine untuk server **Eastside Stories** (SA-MP / open.mp 0.3.DL).

[![Release](https://img.shields.io/github/v/release/aruliazmi/eastside-client?color=D4A548&label=Latest%20Version)](https://github.com/aruliazmi/eastside-client/releases/latest)
[![Android Compatibility](https://img.shields.io/badge/Android-8.0%20%E2%80%94%2016%20(16%20KB)-brightgreen)](https://github.com/aruliazmi/eastside-client/releases)
[![Architecture](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20armeabi--v7a-blue)](https://github.com/aruliazmi/eastside-client/releases)

---

## 📥 Unduh Client & Data Game

| Berkas | Deskripsi | Link Download |
|---|---|:---:|
| **APK Client Terbaru (v2.9.8)** | Installer aplikasi Android (16 KB page-aligned & signed) | [⬇️ Unduh APK](https://github.com/aruliazmi/eastside-client/releases/download/v2.9.8/EastsideStories-2.9.8.apk) |
| **Paket Data Game (v3)** | Aset GTA SA lengkap + audio + texture multi-GPU (±1.95 GB) | [📦 Unduh Data Pack](https://github.com/aruliazmi/eastside-client/releases/download/data-v3/files.zip) |
| **Daftar Semua Rilis** | Riwayat versi, changelog lengkap, dan arsip build | [📋 Lihat Rilis](https://github.com/aruliazmi/eastside-client/releases) |

---

## 🚀 Panduan Pemasangan

1. **Install APK**:
   * Unduh dan pasang berkas `EastsideStories-2.9.8.apk` di HP Android kamu.
   * Berikan izin penyimpanan (*Storage Permission*) dan izin akses notifikasi jika diminta.

2. **Ekstrak Data Game**:
   * Ekstrak file zip `files.zip` menggunakan aplikasi file manager (misal: ZArchiver) ke direktori:
     ```
     /Android/data/com.eastside.samp/files/
     ```
   * Pastikan folder `SAMP`, `data`, `models`, dan `audio` berada tepat di dalam folder `files/`.

3. **Mulai Bermain**:
   * Buka launcher **Eastside Stories**.
   * Masukkan Nickname karakter Roleplay kamu (format: `Nama_Keluarga`).
   * Tekan tombol **MASUK KOTA / LOGIN** untuk terhubung langsung ke server!

---

## ✨ Fitur & Keunggulan Client

* **Kompatibilitas Penuh Android 15 & 16**:
  Seluruh pustaka native (`libsamp.so`, `libGTASA.so`, engine mod loader) telah di-*page-align* ke **16 KB (16384 bytes)** sesuai standar ketat Google Android 15/16.
* **Dukungan Multi-Touch 5 Jari**:
  Mendukung kontrol sentuh hingga 5 jari secara bersamaan untuk gameplay *claw* (kemudi analog + kontrol kamera + sprint + jump + aim/tembak) tanpa sentuhan terputus.
* **SA-MP 0.3.DL Custom Asset Engine**:
  Dukungan penuh skin dan object kustom server dengan *streaming engine* instan, auto-download CDN, dan *color correction* tekstur uncompressed BGRA 32-bit & DXT.
* **Modern HUD & Tactile Buttons**:
  Desain tombol virtual *semi-transparent* modern yang bersih dengan aksen tactile feedback kuningan (`#D4A548`) saat ditekan, serta tata letak HUD ala PC (radar kiri bawah & chat log kiri atas).
* **Quick Action Button Panel**:
  Bilah panel aksi atas modern dengan ikon visual (`ESC`, `TAB Scoreboard`, `ALT Walk`, `SPC Jump`, `Enter Car`, `Klakson`, dll.).
* **Overhead Status Indicators**:
  Ikon status melayang di atas karakter saat pemain sedang *AFK* (badge pause) dan saat mengetik pesan percakapan (*chat bubble*).
* **CEF Web-Based UI & Voice Chat**:
  Integrasi antarmuka HTML/CSS/JS Chromium Embedded Framework (CEF) transparan dan sistem suara spatial 3D Voice Chat BASS.

---

## 🌐 Informasi Server

* **Nama Server**: Eastside Stories Roleplay
* **Server Address**: `play.stateside.id:7777`
* **Port**: `7777` (UDP)
* **Website / Komunitas**: Hubungi pengelola komunitas Eastside Stories untuk pendaftaran whitelist dan Discord role.

---

*Repository ini khusus mempublikasikan rilis biner, pembaruan data, dan dokumentasi client Eastside Stories.*
