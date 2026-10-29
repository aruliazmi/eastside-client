# Eastside Stories — Android Client

Launcher Android native untuk server **Eastside Stories** (open.mp / SA-MP), dengan dukungan
**SA-MP 0.3.DL**, **CEF UI via WebView**, dan packaging yang lolos syarat **Android 15 & 16**.

| | |
|---|---|
| Nama aplikasi | Eastside Stories |
| Package | `com.sampmobile.xyvern` |
| Versi saat ini | **1.8.0** (versionCode 10800) |
| minSdk / targetSdk | 26 / 34 |
| ABI | `arm64-v8a`, `armeabi-v7a` |
| Rilis | [github.com/aruliazmi/eastside-client/releases](https://github.com/aruliazmi/eastside-client/releases) |

Alamat server tujuan sudah tertanam di dalam build, bukan konfigurasi runtime. Kalau server
dipindah, ganti di keempat titik ini lalu build ulang:

| Berkas | Kegunaan |
|---|---|
| `launcher/data/FavoriteServerData.java` | entri server bawaan |
| `launcher/fragments/HomeFragment.java` | query status + tombol masuk kota |
| `game/SAMP.java` | koneksi game saat masuk |
| `cef/CefJavaManager.java` | origin web CEF (`cef://` dan `http://cef/`) |

`launcher/util/ConfigValidator.java` juga menulis entri server cadangan ke konfigurasi klien.

---

## Fitur

### Launcher
- **Beranda** — status server live (query UDP langsung), jumlah pemain, latency, input nama
  karakter, dan tombol masuk kota.
- **Files** — file explorer bawaan untuk direktori game: navigasi folder, baca/edit file teks
  (`.cfg`, `.ini`, `.txt`, `.log`, `.json`), hapus file, bersihkan cache.
- **Pengaturan** — nama karakter, versi game (0.3.DL-R1 / 0.3.7 / 0.3.7-R5), CEF, keyboard,
  FPS limit, jumlah baris chat, voice chat, mod loader.
- **Cek update** — menarik rilis terbaru dari GitHub Releases, menampilkan catatan rilis,
  mengunduh APK dengan progres, lalu membuka pemasang Android.

### Game data
- Unduh paket aset `~634 MB` dengan resume HTTP Range (`files.zip.part`).
- Ekstraksi ZIP langsung ke `getExternalFilesDir()`, tanpa helper eksternal.
- Verifikasi file inti + triplets tekstur sebelum bermain (`GameDataVerifier`).

### Pemilihan tekstur sesuai GPU
Paket data mengirim database tekstur dalam tiga varian (`.dxt`, `.etc`, `.pvr`) dan engine
`libGTASA.so` memilih salah satunya dari daftar `GL_EXTENSIONS`. `GpuTextureFormat` memeriksa
GPU sekali saat launcher start lalu menyimpan hasilnya:

| Deteksi | Varian |
|---|---|
| `GL_IMG_texture_compression_pvrtc` (PowerVR) | PVR |
| `GL_EXT_texture_compression_s3tc` / `DXT1` / `ATC` (Adreno) | DXT |
| `ETC1` / `ETC2`, atau renderer Mali | ETC |
| tidak dikenali | DXT (varian terlengkap) |

Ekstraksi hanya mematerialisasi varian yang cocok, sehingga engine tidak pernah menunjuk ke
database yang tidak ada. `player` dan `playerhi` selalu PVR — engine membacanya sebagai PVR di
semua GPU, jadi varian lain memang tidak pernah dikirim untuk database itu.

> Riwayat: paket data v1 tidak menyertakan `samp.pvr.dat` dan tidak punya `mobile.pvr.*` sama
> sekali, sehingga device Mali/PVR membaca database kosong dan jatuh di
> `memcpy` dalam `RwPluginRegistry::ReadDataChunks` (`SEGV_ACCERR`). Paket **data-v2**
> melengkapinya; lihat release `data-v2`.

### 0.3.DL (custom model)
- Unduhan model `.dff` / `.txd` dari CDN server saat connect.
- Model dipak menjadi `SAMP/custom_dl.img` dan di-mount ke streaming engine GTA SA.
- Registrasi slot model langsung ke `CStreamingInfo` (tanpa `LoadCdDirectory`, yang
  pointer `pExtraDirectory`-nya `NULL` di runtime Android).
- Model kustom mewarisi collision, draw distance, dan data animasi dari model dasar,
  supaya `CObject` / `CPed` tidak pernah dibuat dengan `m_pColModel == nullptr`.

### Mod loader (AML / CLEO / MonetLoader)
- `libAML.so` dimuat lewat `System.loadLibrary("AML")` bila switch **Mod Loader** aktif.
- CLEO dan MonetLoader adalah **mod AML** (mengekspor `__GetModInfo`), bukan library mandiri.
  `AmlModInstaller` menyalinnya ke `files/mods/` — direktori yang dipindai `LoadMods()` milik
  AML — dan memperbaruinya setiap `versionName` berubah.
- MonetLoader di-link **statis** dengan LuaJIT, jadi tidak butuh `libluajit-5.1.so` terpisah.
- Script CLEO diletakkan di `files/cleo/`, script Lua di direktori MonetLoader.

### CEF (WebView)
- UI di-render oleh Android System WebView transparan di atas surface game.
- Komunikasi client ⇄ server lewat **RakNet custom packet id 252**.
- HTML/CSS/JS diambil dari web server CEF, jadi perubahan UI tidak perlu build ulang APK.
  Salinan di `assets/cef/` hanya fallback offline.
- WebView dikeraskan: akses file/`content://` dimatikan, `allowUniversalAccessFromFileURLs`
  `false`, `MIXED_CONTENT_COMPATIBILITY_MODE`.
- Proteksi stabilitas: `onRenderProcessGone → true`, `startActionMode → null`, long-click
  dimatikan, dan `onWindowFocusChanged(false)` diabaikan saat CEF aktif.

---

## Struktur repositori

```
app/
  src/main/java/com/sampmobile/xyvern/
    launcher/            # Activity, fragment (Beranda/Files/Pengaturan), util
    game/                # SAMP activity, GTASA loader, UI in-game, data & GPU
    cef/                 # CefJavaManager, CefClientManager (WebView bridge)
  src/main/cpp/
    samp/                # libsamp.so: net, game hooks, dl/, cef/, voice
    opus/                # codec voice chat
  src/main/jniLibs/      # libGTASA.so, libOpenAL, libSCAnd, libshadowhook, libAML
  src/main/assets/       # data game, fonts, CEF fallback, amlmods/
server/                  # MobileCEF.inc + contoh UI untuk sisi server (Pawn)
server-side/             # pipeline model 0.3.DL + generator manifest
config/app.json          # dibaca lewat branch `config` (URL data, versi, status)
release.sh               # bump versi → build → sign → publish GitHub release
sign-release.sh          # zipalign -P 16 + apksigner (satu-satunya jalur signing)
patch_16kb.py            # naikkan p_align PT_LOAD ke 16 KB (lihat catatan di bawah)
```

---

## Build

Kebutuhan:

- JDK 17
- Android SDK, `compileSdk 34`
- **Build-tools 35.0.0** (wajib — hanya versi ini punya `zipalign -P 16`)
- NDK `26.2.11394342`

```bash
export ANDROID_HOME=/opt/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.2.11394342

./gradlew assembleRelease
./sign-release.sh out/EastsideStories.apk
```

`sign-release.sh` melakukan dua hal yang **tidak** boleh dilewati:

1. `zipalign -P 16 4` — setiap `.so` yang tidak dikompres di-*page align* ke 16384 byte.
2. `apksigner` build-tools 35 — menandatangani v1+v2+v3 dalam satu jalur.

> Jangan memakai `/usr/bin/apksigner` (paket Debian, versi 0.9). Menandatangani APK yang
> sudah ditandatangani Gradle dengan versi itu merusak blok v1 dan menghasilkan
> `INSTALL_FAILED_INVALID_APK` di Android 15/16.

Membangun ulang mod loader:

```bash
# AML (ndk-build)
NDK=$ANDROID_NDK_HOME ndk-build NDK_PROJECT_PATH=$PWD \
  APP_BUILD_SCRIPT=$PWD/Android.mk NDK_APPLICATION_MK=$PWD/Application.mk \
  APP_ABI=arm64-v8a

# MonetLoader (CMake)
cmake -S . -B build16 -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
cmake --build build16 -j4
```

---

## Rilis

```bash
./release.sh 1.8.1 "Ringkasan perubahan"
```

Script akan menaikkan `versionCode` dari semver (`1.8.1 → 10801`), build, sign, commit, push,
lalu membuat GitHub Release `v1.8.1` dan menandainya sebagai *latest*.

Tombol **Cek Update** di aplikasi membaca endpoint
`api.github.com/repos/aruliazmi/eastside-client/releases/latest`, jadi penomoran tag harus
mengikuti semver (`v1.8.1`, bukan `v1.0.0`).

---

## Catatan kompatibilitas Android 15 & 16

Kernel Android 15/16 memakai page size 16 KB, sehingga ada tiga syarat yang harus dipenuhi:

| Syarat | Penerapan |
|---|---|
| `p_align` tiap `PT_LOAD` ≥ 16384, dan `p_vaddr % 16384 == p_offset % 16384` | `-Wl,-z,max-page-size=16384` untuk `libsamp.so`; `.so` pihak ketiga diganti build yang sudah 16 KB |
| `.so` di dalam APK tidak dikompres dan offset datanya kelipatan 16384 | `useLegacyPackaging = false` + `zipalign -P 16` |
| `extractNativeLibs=false` | library di-*mmap* langsung dari APK, tidak diekstrak saat instalasi |

> `patch_16kb.py` **tidak cukup** untuk memperbaiki library lama: script itu hanya menaikkan
> nilai `p_align`, sedangkan kernel juga menuntut `p_vaddr % 16384 == p_offset % 16384`.
> Memenuhi `p_align` saja membuat linker menolak dengan `-EINVAL`. Library yang tidak
> memenuhi kedua syarat harus diganti dengan build ulang, bukan ditambal.

Verifikasi cepat:

```bash
BT=/opt/android-sdk/build-tools/35.0.0
$BT/apksigner verify --min-sdk-version 26 --max-sdk-version 36 app.apk
$BT/zipalign -c -P 16 4 app.apk
```

### Mod loader 16 KB

`libAML.so`, `libCLEOMod.so`, dan `libmonetloader.so` dibangun dari source dengan
`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`. Binary resmi yang beredar
semuanya 4 KB (`p_align 0x1000`), jadi tidak bisa dipakai di Android 15/16.

| Modul | Ukuran | Lokasi | Dimuat oleh |
|---|---|---|---|
| `libAML.so` | 1.32 MB | `jniLibs/arm64-v8a` | `System.loadLibrary("AML")` |
| `libCLEOMod.so` | 1.33 MB | `assets/amlmods` | AML (`__GetModInfo`) |
| `libmonetloader.so` | 7.96 MB | `assets/amlmods` | AML (`__GetModInfo`) |

Switch AML default `false`; pemuatannya dibungkus `catch (Throwable)` supaya tidak pernah
menghentikan proses game. CLEO tidak punya switch sendiri karena dimuat oleh AML.

Catatan arm64: CLEO butuh perbaikan source (cast pointer ke `int` menjadi `uintptr_t`).
Trampoline asm ARM32 untuk opcode `0DD2` diganti stub, jadi opcode itu tidak tersedia di
64-bit — opcode lain normal.

---

## Komponen pihak ketiga

- `libGTASA.so`, `libOpenAL`, `libSCAnd` — binary game GTA:SA Android.
- [ShadowHook](https://github.com/bytedance/android-inline-hook) — inline hooking.
- [GlossHook](https://github.com/HoShiMin/GlossHook) — PLT hooking.
- [BASS](https://www.un4seen.com/) — audio, versi 16 KB-compatible.
- [AndroidModLoader](https://github.com/RusJJ/AndroidModLoader) — mod loader.
- [GTA_CLEOMod](https://github.com/AndroidModLoader/GTA_CLEOMod) — CLEO untuk AML.
- [MonetLoaderOSS](https://github.com/xefinity/MonetLoaderOSS) — Lua script loader.
- [Pawn.RakNet](https://github.com/katursis/Pawn.RakNet) — prasyarat `server/MobileCEF.inc`.
