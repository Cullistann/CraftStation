@echo off
chcp 65001 >nul 2>&1
title CraftStation Kontrol Paneli - Derleme
echo ========================================
echo   CraftStation Kontrol Paneli - Build
echo ========================================
echo.

set JAVA_HOME=..\java
set PATH=%JAVA_HOME%\bin;%PATH%

echo [1/3] Temizleniyor...
if exist out rmdir /s /q out
mkdir out

echo [2/3] Derleniyor...

REM javac @argfile ters slash'i escape karakteri olarak yorumlar.
REM Bu yuzden tum yollari forward slash'e ceviriyoruz.
if exist sources.txt del sources.txt
for /r src\main\java %%f in (*.java) do (
    set "FPATH=%%f"
    setlocal enabledelayedexpansion
    set "FPATH=!FPATH:\=/!"
    echo "!FPATH!" >> sources.txt
    endlocal
)

"%JAVA_HOME%\bin\javac" -cp "lib\*" -d out -sourcepath "src\main\java" --enable-preview --release 25 @sources.txt

set BUILD_RESULT=%ERRORLEVEL%
del sources.txt 2>nul

if %BUILD_RESULT% NEQ 0 (
    echo.
    echo [HATA] Derleme basarisiz!
    pause
    exit /b 1
)

echo [3/3] Derleme basarili!
echo.

REM Derlenen sinif sayisini goster
set /a count=0
for /r out %%f in (*.class) do set /a count+=1
echo Derlenen sinif dosyalari: %count%
echo Calistirmak icin: ..\CraftStation.exe veya run_panel.bat
echo.
pause
