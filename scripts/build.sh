#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Build Script ==="
gradle :app:assembleDebug :app:assembleRelease
echo "=== Build PASSED ==="
