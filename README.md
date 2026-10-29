# Eastside Stories — Android Client

Launcher Android native untuk server **Eastside Stories** (open.mp / SA-MP), dengan dukungan
**SA-MP 0.3.DL**, **CEF UI via WebView**, mod loader **AML**, dan packaging yang lolos syarat
**Android 15 & 16** (16 KB page size).

| | |
|---|---|
| Nama aplikasi | Eastside Stories |
| Package (`applicationId`) | `com.eastside.samp` |
| Namespace Java | `com.sampmobile.xyvern` |
| Versi saat ini | **2.2.0** (versionCode 20200) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| ABI | `arm64-v8a`, `armeabi-v7a` |
| Direktori data game | `/Android/data/com.eastside.samp/files/` |
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
  FPS limit, jumlah baris chat, voice chat, **Mod Loader (AML)**, dan **Data termodifikasi**.
- **Cek update** — menarik rilis terbaru dari GitHub Releases, menampilkan catatan rilis,
  mengunduh APK dengan progres, lalu membuka pemasang Android.

### Game data
- Unduh paket aset `data-v3` (**1.957.396.608 byte / ±1.95 GB**) dengan resume HTTP Range
  (`files.zip.part`).
- Ekstraksi ZIP langsung ke `getExternalFilesDir()`, tanpa helper eksternal.
- Verifikasi file inti + triplets tekstur sebelum bermain (`GameDataVerifier`).

Dua switch di Pengaturan mengubah perilaku unduhan data:

| Switch | Efek |
|---|---|
| **Data termodifikasi** ON | Pemeriksaan `data_version` dilewati sepenuhnya; launcher langsung boot memakai isi `files/` apa adanya. Dipakai kalau kamu mengedit `.img` / `.txd` / script sendiri, supaya ekstraktor tidak menimpa hasil editanmu. |
| **Data termodifikasi** OFF | Perilaku normal: versi dibandingkan dengan `config.json`, dan unduhan ditawarkan bila versi berbeda. |

### Pemilihan tekstur sesuai GPU
Paket data mengirim database tekstur dalam beberapa varian (`.dxt`, `.etc`, `.pvr`, `.unc`) dan
engine `libGTASA.so` memilih salah satunya dari daftar `GL_EXTENSIONS`. `GpuTextureFormat`
memeriksa GPU sekali saat launcher start lalu menyimpan hasilnya:

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
> `memcpy` dalam `RwPluginRegistry::ReadDataChunks` (`SEGV_ACCERR`). **`data-v2`** melengkapinya,
> **`data-v3`** (aktif sekarang) memuat basis GTA v1.08 penuh beserta semua varian tekstur
> termasuk database `cutscene`.

### 0.3.DL (custom model)
- Unduhan model `.dff` / `.txd` dari CDN server (`/models/` di Nginx VPS) saat connect.
- Model dipak menjadi `SAMP/custom_dl.img` dan di-mount ke streaming engine GTA SA.
- Registrasi slot model langsung ke `CStreamingInfo` (tanpa `LoadCdDirectory`, yang
  pointer `pExtraDirectory`-nya `NULL` di runtime Android).
- Model kustom mewarisi collision, draw distance, dan data animasi dari model dasar,
  supaya `CObject` / `CPed` tidak pernah dibuat dengan `m_pColModel == nullptr`.

### Mod loader (AML / CLEO / MonetLoader)
- `libAML.so` dimuat lewat `System.loadLibrary("AML")` bila switch **Mod Loader** aktif.
- **Urutan muat penting:** `libAML.so` dipanggil **sebelum** `super.onCreate()`. AML menjalankan
  `StartAMLRightNow()` langsung dari `JNI_OnLoad`, dan mod-modnya perlu mendaratkan hook ke
  engine sebelum `NvEventQueueActivity.onCreate()` memanggil `init(false)`. Memuat AML setelah
  engine boot membuat semua hook AML gagal terpasang dan `files/mods/` tidak berefek apa pun.
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

### Stabilitas engine (patch native)
Perbaikan yang sudah mendarat di `libsamp.so` / `hooks.cpp` / `util.cpp`:

| Gejala | Akar masalah | Perbaikan |
|---|---|---|
| `SIGSEGV 0x110` di `TextureDatabaseRuntime::GetTexture` | slot `NULL` tertinggal di array `registered` setelah database engine di-`Unregister` | `CleanRegisteredDatabases()` + hook `GetTexture` |
| `SIGSEGV 0x5` saat `SetMaterial` | offset hardcoded ARMv7 `0x5D3E60+1`; di ARM64 itu alamat `IKChainManager_c::IsFacingTarget` | helper TXD dimigrasi ke `CTxdStore` native 64-bit |
| `SIGSEGV 0x40` di `CustomPipeRenderCB` | `texture->raster == NULL` dibaca tanpa guard di offset `+0x40` | sanitasi mesh sebelum render pass |
| Crash spawn di ImGui voicechat | widget textdraw `samp` mendereferensi raster kosong | null-check `curTex && curTex->raster` |
| Void / jalan hilang di chunk map default | pointer `ms_rwObjectInstances` ditimpa memory tak terinisialisasi, `MakeSpaceFor` dimatikan | dua-duanya dikembalikan ke native engine |
| IPL/IDE gagal dibaca (mis. `LaWn.ipl` vs `LAwn.IPL`) | filesystem Android case-sensitive | `CFileMgr::OpenFile` fallback case-insensitive |

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

`local.properties` harus menunjuk SDK/NDK:

```properties
sdk.dir=/opt/android-sdk
ndk.dir=/opt/android-sdk/ndk/26.2.11394342
```

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
./release.sh 2.2.1 "Ringkasan perubahan"
```

Script akan menaikkan `versionCode` dari semver (`2.2.1 → 20201`), build, sign, commit, push,
lalu membuat GitHub Release `v2.2.1` dan menandainya sebagai *latest*.

Tombol **Cek Update** di aplikasi membaca endpoint
`api.github.com/repos/aruliazmi/eastside-client/releases/latest`, jadi penomoran tag harus
mengikuti semver (`v2.2.1`, bukan `v2.2`).

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

AML hanya tersedia untuk **arm64-v8a**. Switch AML default `false`; pemuatannya dibungkus
`catch (Throwable)` supaya tidak pernah menghentikan proses game. CLEO tidak punya switch
sendiri karena dimuat oleh AML.

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
