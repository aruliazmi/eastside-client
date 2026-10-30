# Eastside Stories Android client

Launcher dan game engine Android buat server Eastside Stories (SA-MP / open.mp 0.3.DL).

[![Release](https://img.shields.io/github/v/release/aruliazmi/eastside-client?color=D4A548&label=Latest%20Version)](https://github.com/aruliazmi/eastside-client/releases/latest)
[![Android Compatibility](https://img.shields.io/badge/Android-8.0%20%E2%80%94%2016%20(16%20KB)-brightgreen)](https://github.com/aruliazmi/eastside-client/releases)
[![Architecture](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20armeabi--v7a-blue)](https://github.com/aruliazmi/eastside-client/releases)

## Unduh client dan data game

| Berkas | Deskripsi | Link |
|---|---|:---:|
| APK client terbaru | Installer Android, sudah ditandatangani dan siap dipasang di Android 15/16 | [Unduh APK](https://github.com/aruliazmi/eastside-client/releases/download/v1.0.1b/EastsideStories.apk) |
| Paket data game (v3) | Aset GTA SA lengkap, audio, tekstur multi-GPU (±1.95 GB) | [Unduh data pack](https://github.com/aruliazmi/eastside-client/releases/download/data-v3/files.zip) |
| Daftar semua rilis | Riwayat versi, changelog, arsip build | [Lihat rilis](https://github.com/aruliazmi/eastside-client/releases) |

## Cara pasang

1. Pasang APK. Unduh dan install `EastsideStories.apk` (v1.0.1b) di HP Android. Kasih izin
   penyimpanan dan notifikasi kalau diminta.

2. Ekstrak data game. Unduh `files.zip` dari rilis
   [data-v3](https://github.com/aruliazmi/eastside-client/releases/tag/data-v3), lalu ekstrak
   pakai aplikasi pengelola berkas (ZArchiver, misalnya) ke:

   ```
   /Android/data/com.eastside.samp/files/
   ```

   Folder `SAMP`, `data`, `models`, dan `audio` harus berada langsung di dalam `files/`.

3. Buka launcher Eastside Stories. Isi nickname karakter roleplay kamu dengan format
   `Nama_Keluarga`, lalu tekan tombol MASUK KOTA / LOGIN.

## Fitur

Client ini jalan di Android 15 dan 16. Semua library bawaannya sudah disiapkan buat page size
16 KB yang dipakai Android 15/16, jadi nggak kena blokir pas instal.

Kontrol sentuh sanggup 5 jari bersamaan, jadi main claw (kemudi analog, kamera, sprint, jump,
aim) nggak bikin sentuhan lain keputus.

Skin dan object kustom dari server ke-load langsung dan diunduh otomatis, warnanya juga nggak
pecah.

Tampilan HUD-nya ala PC, radar di kiri bawah dan chat log di kiri atas. Tombol virtualnya
semi-transparan dengan feedback warna kuningan (`#D4A548`) waktu ditekan. Ada panel aksi atas
berisi ikon `ESC`, `TAB Scoreboard`, `ALT Walk`, `SPC Jump`, `Enter Car`, `Klakson`, dan
sejenisnya. Di atas karakter pemain muncul badge pause waktu AFK dan bubble waktu sedang
mengetik.

UI-nya HTML/CSS/JS lewat Chromium Embedded Framework transparan, dan voice chat-nya spatial 3D
pakai BASS.
