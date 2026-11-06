#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
gradle lwjgl3:installDist
APP_DIR="lwjgl3/build/install/JavaFarmer"
MAIN_JAR="java-farmer-desktop-0.2.0.jar"
mkdir -p dist/macos
jpackage \
  --type dmg \
  --name "Java Farmer" \
  --app-version 0.2.0 \
  --input "$APP_DIR/lib" \
  --main-jar "$MAIN_JAR" \
  --main-class com.javafarmer.lwjgl3.Lwjgl3Launcher \
  --dest dist/macos \
  --java-options "-Xms256m" \
  --java-options "-Xmx1g" \
  --java-options "--enable-native-access=ALL-UNNAMED"
echo "DMG criado em dist/macos (build deve ser executado no próprio macOS)."
