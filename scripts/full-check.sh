#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Full Check Pipeline == "
./scripts/test.sh
./scripts/check.sh
./scripts/build.sh
./scripts/verify-apk.sh
./scripts/checksum.sh
echo "=== FULL CHECK PIPELINE PASSED SUCCESSFULLY ==="
