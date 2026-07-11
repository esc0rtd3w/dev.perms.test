@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PKG=moe.shizuku.privileged.api"
set "START_SH=/storage/emulated/0/Android/data/%PKG%/start.sh"
set "REMOTE_BIN=/data/local/tmp/shizuku_starter"
set "TMPDIR=%TEMP%\shizuku_start_%RANDOM%%RANDOM%"
set "TMPBIN=%TMPDIR%\libshizuku.so"

echo Starting Shizuku.
echo Trying start.sh, extracted native lib, then APK-embedded native lib...
echo.

echo [1/3] Trying start.sh...
adb shell "if [ -f %START_SH% ]; then sh %START_SH%; else exit 127; fi"
if not errorlevel 1 goto done

echo.
echo [2/3] Trying extracted native lib path reported by package manager...
adb shell "PKG=%PKG%; for D in $(dumpsys package $PKG | sed -n 's/.*legacyNativeLibraryDir=//p;s/.*nativeLibraryDir=//p' | tr -d '\r'); do for L in $D/libshizuku.so $D/*/libshizuku.so; do if [ -x $L ]; then echo Starting $L; exec $L; fi; done; done; exit 1"
if not errorlevel 1 goto done

echo.
echo [3/3] Extracting APK-embedded libshizuku.so and running from /data/local/tmp...

where tar.exe >nul 2>nul
if errorlevel 1 (
    echo tar.exe not found. Windows 10/11 normally includes tar.exe.
    goto fail
)

mkdir "%TMPDIR%" >nul 2>nul

adb shell pm path %PKG% > "%TMPDIR%\paths.txt"
adb shell getprop ro.product.cpu.abilist > "%TMPDIR%\abis.txt"

set /p ABI_CSV=<"%TMPDIR%\abis.txt"
set "ABI_CSV=%ABI_CSV: =%"
set "ABIS=%ABI_CSV:,= %"

if not defined ABIS (
    echo Could not read ABI list from device.
    goto fail
)

set /a APK_COUNT=0

for /f "usebackq tokens=1,* delims=:" %%A in ("%TMPDIR%\paths.txt") do (
    if /i "%%A"=="package" (
        set /a APK_COUNT+=1
        echo Pulling APK !APK_COUNT!: %%B
        adb pull "%%B" "%TMPDIR%\pkg!APK_COUNT!.apk" >nul
    )
)

if "%APK_COUNT%"=="0" (
    echo Failed to find installed APK path for %PKG%.
    goto fail
)

set "FOUND_LIB="

for %%P in ("%TMPDIR%\pkg*.apk") do (
    for %%A in (!ABIS!) do (
        if not defined FOUND_LIB (
            tar -tf "%%~fP" "lib/%%A/libshizuku.so" >nul 2>nul
            if not errorlevel 1 (
                echo Found lib/%%A/libshizuku.so in %%~nxP
                tar -xOf "%%~fP" "lib/%%A/libshizuku.so" > "%TMPBIN%"
                for %%S in ("%TMPBIN%") do (
                    if %%~zS GTR 0 set "FOUND_LIB=1"
                )
            )
        )
    )
)

if not defined FOUND_LIB (
    echo Failed to find libshizuku.so inside installed Shizuku APK files.
    goto fail
)

echo Pushing extracted starter to %REMOTE_BIN%...
adb push "%TMPBIN%" "%REMOTE_BIN%" >nul
adb shell chmod 700 "%REMOTE_BIN%"

echo Starting %REMOTE_BIN%...
adb shell "%REMOTE_BIN%"
if errorlevel 1 goto fail

goto done

:done
echo.
echo Shizuku start command completed.
rmdir /s /q "%TMPDIR%" >nul 2>nul
pause
endlocal
exit /b 0

:fail
echo.
echo Shizuku failed to start.
rmdir /s /q "%TMPDIR%" >nul 2>nul
pause
endlocal
exit /b 1