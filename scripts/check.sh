#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Lint & Check == "
gradle lintDebug
echo "=== Check PASSED ==="
