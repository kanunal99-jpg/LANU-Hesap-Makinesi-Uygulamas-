#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Generating SHA-256 Checksum == "
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$DEBUG_APK" ]; then
  sha256sum "$DEBUG_APK" > "app/build/outputs/apk/debug/app-debug.apk.sha256"
  echo "SHA-256 generated:"
  cat "app/build/outputs/apk/debug/app-debug.apk.sha256"
else
  echo "Debug APK not found for checksum."
  exit 1
fi
echo "=== Checksum PASSED ==="
