@echo off
cd /d "%~dp0"
start "" "..\java\bin\javaw.exe" --enable-preview -cp "lib/*;out" Main
exit /b 0
