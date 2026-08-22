@echo off
chcp 65001 >nul 2>&1
title CraftStation - Cook
echo ==========================================
echo   CraftStation - Release Packaging
echo ==========================================
echo.

REM ===== Build first =====
echo [1/5] Building...
set JAVA_HOME=..\java
set PATH=%JAVA_HOME%\bin;%PATH%

if exist out rmdir /s /q out
mkdir out

if exist sources.txt del sources.txt
for /r src\main\java %%f in (*.java) do (
    set "FPATH=%%f"
    setlocal enabledelayedexpansion
    set "FPATH=!FPATH:\=/!"
    echo "!FPATH!" >> sources.txt
    endlocal
)

"%JAVA_HOME%\bin\javac" -cp "lib/*" -d out -sourcepath "src\main\java" --enable-preview --release 25 @sources.txt
set BUILD_RESULT=%ERRORLEVEL%
del sources.txt 2>nul

if %BUILD_RESULT% NEQ 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo       Build OK.

REM ===== Release directory =====
set RELEASE_DIR=..\CraftStation-Release
echo [2/5] Creating release dir: %RELEASE_DIR%
if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%"
mkdir "%RELEASE_DIR%"
mkdir "%RELEASE_DIR%\panel"
mkdir "%RELEASE_DIR%\panel\lib"
mkdir "%RELEASE_DIR%\panel\assets"
mkdir "%RELEASE_DIR%\panel\out"

REM ===== Copy compiled classes =====
echo [3/5] Copying compiled classes...
xcopy /s /e /q /y "out\*" "%RELEASE_DIR%\panel\out\" >nul

REM ===== Runtime libs only (no test JARs) =====
echo [4/5] Copying runtime libraries...
copy /y "lib\flatlaf-3.5.4.jar" "%RELEASE_DIR%\panel\lib\" >nul

REM ===== Assets =====
xcopy /s /e /q /y "assets\*" "%RELEASE_DIR%\panel\assets\" >nul

REM ===== Root files =====
echo [5/5] Copying launcher files...
if exist "..\CraftStation.exe" copy /y "..\CraftStation.exe" "%RELEASE_DIR%\" >nul
if exist "..\CraftStation.ico" copy /y "..\CraftStation.ico" "%RELEASE_DIR%\" >nul
if exist "..\CraftStation-logo.png" copy /y "..\CraftStation-logo.png" "%RELEASE_DIR%\" >nul

REM ===== Bundled JRE =====
if exist "..\java" (
    echo       Copying bundled JRE...
    xcopy /s /e /q /y "..\java" "%RELEASE_DIR%\java\" >nul
)


REM ===== README =====
> "%RELEASE_DIR%\README.txt" (
    echo ===================================
    echo   CraftStation v1.0.0
    echo ===================================
    echo.
    echo SETUP:
    echo   1. Copy this folder next to your Minecraft server files.
    echo   2. Run CraftStation.exe to start.
    echo.
    echo REQUIREMENTS:
    echo   - Windows 10/11 64-bit
    echo   - Bundled JRE included in java/ folder
)

echo.
echo ==========================================
echo   RELEASE PACKAGING DONE!
echo ==========================================
echo.
echo Location: %RELEASE_DIR%
echo.

set /a fcount=0
for /r "%RELEASE_DIR%" %%f in (*.*) do set /a fcount+=1
echo Total files: %fcount%
echo.
echo Structure:
echo   CraftStation-Release\
echo     +-- CraftStation.exe     (Launcher)
echo     +-- CraftStation.ico     (Icon)
echo     +-- README.txt           (Setup guide)
echo     +-- java\                (Bundled JRE)
echo     +-- panel\
echo         +-- out\              (Compiled classes)
echo         +-- lib\              (Runtime JAR only - flatlaf)
echo         +-- assets\           (Graphics and fonts)
echo.
pause
