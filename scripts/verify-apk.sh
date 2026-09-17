#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Verify APK Script ==="
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$DEBUG_APK" ]; then
  echo "Debug APK verified: $DEBUG_APK"
else
  echo "Error: Debug APK not found!"
  exit 1
fi
echo "=== APK Verification PASSED ==="
