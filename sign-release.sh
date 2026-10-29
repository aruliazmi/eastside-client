#!/usr/bin/env bash
# Canonical Android 15/16 release pipeline:
#   1. Gradle produces app-release.apk (useLegacyPackaging=false)
#   2. zipalign -P 16 page-aligns every uncompressed .so to 16 KB
#   3. apksigner (build-tools 35) signs v1+v2+v3
# Never sign with the Debian /usr/bin/apksigner (0.9) - it corrupts v1.
set -euo pipefail
BT="${BT:-/opt/android-sdk/build-tools/35.0.0}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
IN="$ROOT/app/build/outputs/apk/release/app-release.apk"
OUT="${1:-$ROOT/../EastsideStories.apk}"
TMP="$(mktemp -d)"

"$BT/zipalign" -f -P 16 4 "$IN" "$TMP/aligned.apk"
"$BT/apksigner" sign \
  --ks "$ROOT/app-release-key.jks" \
  --ks-pass pass:password123456 --key-pass pass:password123456 \
  --ks-key-alias sampkey \
  --min-sdk-version 26 --max-sdk-version 36 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$TMP/aligned.apk"

"$BT/apksigner" verify --min-sdk-version 26 --max-sdk-version 36 "$OUT"
"$BT/zipalign" -c -P 16 4 "$OUT"
rm -rf "$TMP"
echo "signed -> $OUT"
