#!/usr/bin/env bash
set -euo pipefail
VER="${1:?usage: ./release.sh <version> [notes]}"
NOTES="${2:-Rilis Eastside Stories Client v$VER}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="aruliazmi/eastside-client"

IFS='.' read -r MAJ MIN PAT <<< "$VER"
CODE=$(( MAJ * 10000 + MIN * 100 + PAT ))

if [ -f "$ROOT/app/build.gradle.kts" ]; then
  sed -i -E "s/versionCode = [0-9]+/versionCode = $CODE/; s/versionName = \"[^\"]*\"/versionName = \"$VER\"/" "$ROOT/app/build.gradle.kts"
else
  sed -i -E "s/versionCode [0-9]+/versionCode $CODE/; s/versionName \"[^\"]*\"/versionName \"$VER\"/" "$ROOT/app/build.gradle"
fi
echo "bumped -> $VER ($CODE)"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/26.2.11394342}"
"$ROOT/gradlew" -p "$ROOT" assembleRelease

TMP="$(mktemp -d)"
APK="$TMP/EastsideStories-$VER.apk"
"$ROOT/sign-release.sh" "$APK"

git -C "$ROOT" add -A
git -C "$ROOT" commit -q -m "release: $VER ($CODE)" || true
git -C "$ROOT" push -q origin main || true

gh release create "v$VER" --repo "$REPO" --latest \
  --title "Eastside Stories Client v$VER" \
  --notes "**Versi aplikasi: $VER (versionCode $CODE)**

$NOTES

### Pemasangan
Native library dikirim uncompressed + 16 KB page-aligned; ditandatangani apksigner build-tools 35 (v1+v2+v3). Uninstall versi lama jika tanda tangannya berbeda." \
  "$APK#EastsideStories.apk"

rm -rf "$TMP"
echo "released v$VER -> https://github.com/$REPO/releases/tag/v$VER"
