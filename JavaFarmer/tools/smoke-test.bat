@echo off
setlocal
cd /d "%~dp0\.."
where gradle >nul 2>nul || (echo Gradle nao encontrado. Consulte docs\INSTALL_WINDOWS.md.& exit /b 1)
where javac >nul 2>nul || (echo JDK nao encontrado.& exit /b 1)
gradle core:compileJava || exit /b 1
if not exist tools\build mkdir tools\build
javac -encoding UTF-8 -cp "core\build\classes\java\main" -d tools\build tools\CoreSmokeTest.java || exit /b 1
java -ea -cp "tools\build;core\build\classes\java\main" CoreSmokeTest || exit /b 1
where py >nul 2>nul
if not errorlevel 1 (
  py -3 tools\asset_audit.py || exit /b 1
) else (
  where python >nul 2>nul || (echo Python 3 nao encontrado. Instale Python 3 para auditar os assets.& exit /b 1)
  python tools\asset_audit.py || exit /b 1
)
echo ALL SMOKE TESTS PASSED
