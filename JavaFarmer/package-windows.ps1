$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
gradle lwjgl3:installDist
$appDir = "lwjgl3/build/install/JavaFarmer"
$mainJar = "java-farmer-desktop-0.2.0.jar"
New-Item -ItemType Directory -Force -Path "dist/windows" | Out-Null
jpackage `
  --type exe `
  --name "Java Farmer" `
  --app-version 0.2.0 `
  --input "$appDir/lib" `
  --main-jar $mainJar `
  --main-class com.javafarmer.lwjgl3.Lwjgl3Launcher `
  --dest "dist/windows" `
  --java-options "-Xms256m" `
  --java-options "-Xmx1g" `
  --java-options "--enable-native-access=ALL-UNNAMED"
Write-Host "Instalador criado em dist/windows (execute este script no próprio Windows)."
