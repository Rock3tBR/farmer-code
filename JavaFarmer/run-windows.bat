@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul || (echo Instale o JDK 25. Consulte docs\INSTALL_WINDOWS.md.& pause& exit /b 1)
where gradle >nul 2>nul || (echo Gradle 9.8.2 nao encontrado. Consulte docs\INSTALL_WINDOWS.md.& pause& exit /b 1)
gradle lwjgl3:run
if errorlevel 1 pause
