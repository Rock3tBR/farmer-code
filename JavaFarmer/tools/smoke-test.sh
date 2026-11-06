#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v gradle >/dev/null 2>&1 || { echo "Gradle 9.8.2 não encontrado. Consulte docs/INSTALL_MACOS.md ou docs/INSTALL_WINDOWS.md."; exit 1; }
command -v javac >/dev/null 2>&1 || { echo "JDK não encontrado."; exit 1; }
gradle core:compileJava
mkdir -p tools/build
javac -encoding UTF-8 -cp "core/build/classes/java/main" -d tools/build tools/CoreSmokeTest.java
java -ea -cp "tools/build:core/build/classes/java/main" CoreSmokeTest
python3 tools/asset_audit.py
