@echo off
cd /d "%~dp0"
start "" "..\java\bin\javaw.exe" -cp "lib/*;out" Main
exit /b 0
