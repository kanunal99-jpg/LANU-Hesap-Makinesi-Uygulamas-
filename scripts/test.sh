#!/usr/bin/env bash
set -e
echo "=== LANU HESAP MAKİNESİ: Test Script ==="
gradle :app:testDebugUnitTest
echo "=== Tests PASSED ==="
