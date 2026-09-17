#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Lint Script ==="
gradle lintDebug
echo "=== Lint PASSED ==="
