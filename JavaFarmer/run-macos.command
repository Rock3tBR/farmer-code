#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "Instale o JDK 25 antes de iniciar. Consulte docs/INSTALL_MACOS.md."
  read -r -p "Pressione Enter para fechar..."
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle não foi encontrado. Instale o Gradle 9.8.2. Consulte docs/INSTALL_MACOS.md."
  read -r -p "Pressione Enter para fechar..."
  exit 1
fi
gradle lwjgl3:run
