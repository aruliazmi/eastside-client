# Eastside Stories — Android Client

Launcher Android native untuk server **Eastside Stories** (open.mp / SA-MP), dengan dukungan
**SA-MP 0.3.DL**, **CEF UI via WebView**, dan packaging yang lolos syarat **Android 15 & 16**.

Server tujuan sudah di-hardcode: `163.52.135.25:7777`.

| | |
|---|---|
| Nama aplikasi | Eastside Stories |
| Package | `com.sampmobile.xyvern` |
| Versi saat ini | **1.6.5** (versionCode 166) |
| minSdk / targetSdk | 26 / 34 |
| ABI | `arm64-v8a`, `armeabi-v7a` |
| Rilis | [github.com/aruliazmi/eastside-client/releases](https://github.com/aruliazmi/eastside-client/releases) |

---

## Fitur

### Launcher
- **Beranda** — status server live (query UDP langsung ke `163.52.135.25:7777`), jumlah pemain,
  latency, input nama karakter, dan tombol masuk kota.
- **Files** — file explorer bawaan untuk direktori game: navigasi folder, baca/edit file teks
  (`.cfg`, `.ini`, `.txt`, `.log`, `.json`), hapus file, bersihkan cache.
- **Pengaturan** — nama karakter, versi game (0.3.DL-R1 / 0.3.7 / 0.3.7-R5), CEF on/off,
  keyboard, FPS limit, jumlah baris chat, voice chat, mod loader.
- **Cek update** — menarik rilis terbaru dari GitHub Releases, menampilkan catatan rilis,
  mengunduh APK dengan progres, lalu membuka pemasang Android.

### Game data
- Verifikasi file inti GTA SA + SA-MP secara lokal sebelum bermain.
- Unduh paket aset `~635 MB` dengan resume HTTP Range (`files.zip.part`).
- Ekstraksi ZIP langsung ke `getExternalFilesDir()`, tanpa helper eksternal.

### 0.3.DL (custom model)
- Unduhan model `.dff` / `.txd` dari CDN server saat connect.
- Model dipak menjadi `SAMP/custom_dl.img` dan di-mount ke streaming engine GTA SA.
- Registrasi slot model langsung ke `CStreamingInfo` (tanpa `LoadCdDirectory`, yang
  pointer `pExtraDirectory`-nya `NULL` di runtime Android).
- Model kustom mewarisi collision, draw distance, dan data animasi dari model dasar,
  supaya `CObject` / `CPed` tidak pernah dibuat dengan `m_pColModel == nullptr`.

### CEF (WebView)
- UI di-render oleh Android System WebView transparan di atas surface game.
- Komunikasi client ⇄ server lewat **RakNet custom packet id 252**.
- HTML/CSS/JS diambil dari web server (`http://163.52.135.25/`), jadi perubahan UI
  tidak perlu build ulang APK. Salinan di `assets/cef/` hanya fallback offline.
- Proteksi stabilitas: `onRenderProcessGone → true`, `startActionMode → null`,
  long-click dimatikan, dan `onWindowFocusChanged(false)` diabaikan saat CEF aktif.

---

## Struktur repositori

```
app/
  src/main/java/com/sampmobile/xyvern/
    launcher/            # Activity, fragment (Beranda/Files/Pengaturan), util
    game/                # SAMP activity, GTASA loader, UI in-game
    cef/                 # CefJavaManager, CefClientManager (WebView bridge)
  src/main/cpp/
    samp/                # libsamp.so: net, game hooks, dl/, cef/, voice
    opus/                # codec voice chat
  src/main/jniLibs/      # libGTASA.so, libOpenAL, libSCAnd, libshadowhook, dst.
  src/main/assets/       # data game, fonts, CEF fallback
server/                  # MobileCEF.inc + contoh UI untuk sisi server (Pawn)
server-side/             # pipeline model 0.3.DL + generator manifest
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

---

## Rilis

```bash
./release.sh 1.6.6 "Ringkasan perubahan"
```

Script akan menaikkan `versionCode` dari semver (`1.6.6 → 10606`), build, sign,
commit, push, lalu membuat GitHub Release `v1.6.6` dan menandainya sebagai *latest*.

Tombol **Cek Update** di aplikasi membaca endpoint
`api.github.com/repos/aruliazmi/eastside-client/releases/latest`, jadi penomoran tag
harus mengikuti semver (`v1.6.6`, bukan `v1.0.0`).

---

## Catatan kompatibilitas Android 15 & 16

Kernel Android 15/16 memakai page size 16 KB, sehingga ada tiga syarat yang harus dipenuhi:

| Syarat | Penerapan |
|---|---|
| `p_align` tiap `PT_LOAD` ≥ 16384, dan `p_vaddr % 16384 == p_offset % 16384` | `-Wl,-z,max-page-size=16384` di `app/src/main/cpp/samp/CMakeLists.txt` untuk `libsamp.so`; `.so` pihak ketiga diganti dengan build yang sudah 16 KB |
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

Modul `libAML.so`, `libluajit-5.1.so`, dan `libmonetloader.so` **tidak lagi dibundel**
karena tidak 16 KB-compatible. Switch AML/CLEO di Pengaturan default `false` dan
pemuatannya dibungkus `catch (Throwable)` supaya tidak pernah menghentikan proses game.

---

## Komponen pihak ketiga

- `libGTASA.so`, `libOpenAL`, `libSCAnd` — binary game GTA:SA Android.
- [ShadowHook](https://github.com/bytedance/android-inline-hook) — inline hooking.
- [GlossHook](https://github.com/HoShiMin/GlossHook) — PLT hooking.
- [BASS](https://www.un4seen.com/) — audio, versi 16 KB-compatible.
- [Pawn.RakNet](https://github.com/katursis/Pawn.RakNet) — prasyarat `server/MobileCEF.inc`.
