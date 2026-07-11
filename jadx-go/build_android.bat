@echo off
setlocal EnableExtensions

if not exist dist\arm64-v8a mkdir dist\arm64-v8a
if not exist dist\armeabi-v7a mkdir dist\armeabi-v7a
if not exist dist\x86 mkdir dist\x86
if not exist dist\x86_64 mkdir dist\x86_64

rem arm64 can be built as a proper Android PIE binary with Go's internal linker.
set CGO_ENABLED=0
set GOOS=android
set GOARCH=arm64
set GOARM=
go build -trimpath -o dist\arm64-v8a\jadx
if errorlevel 1 exit /b 1

call :build_external armeabi-v7a arm 7 armv7a-linux-androideabi23-clang
if errorlevel 1 exit /b 1
call :build_external x86 386 "" i686-linux-android23-clang
if errorlevel 1 exit /b 1
call :build_external x86_64 amd64 "" x86_64-linux-android23-clang
if errorlevel 1 exit /b 1

endlocal
exit /b 0

:build_external
set ABI=%~1
set BGOARCH=%~2
set BGOARM=%~3
set CCNAME=%~4
set NDKBIN=
if defined ANDROID_NDK_HOME if exist "%ANDROID_NDK_HOME%\toolchains\llvm\prebuilt\windows-x86_64\bin\%CCNAME%" set NDKBIN=%ANDROID_NDK_HOME%\toolchains\llvm\prebuilt\windows-x86_64\bin
if not defined NDKBIN if defined ANDROID_NDK_ROOT if exist "%ANDROID_NDK_ROOT%\toolchains\llvm\prebuilt\windows-x86_64\bin\%CCNAME%" set NDKBIN=%ANDROID_NDK_ROOT%\toolchains\llvm\prebuilt\windows-x86_64\bin
if defined NDKBIN (
    set CGO_ENABLED=1
    set GOOS=android
    set GOARCH=%BGOARCH%
    set GOARM=%BGOARM%
    set CC=%NDKBIN%\%CCNAME%
    go build -trimpath -o dist\%ABI%\jadx
    exit /b %ERRORLEVEL%
)

echo W: Android NDK clang not found for %ABI%; building linux static fallback. Install/set ANDROID_NDK_HOME for Android PIE output. 1>&2
set CGO_ENABLED=0
set GOOS=linux
set GOARCH=%BGOARCH%
set GOARM=%BGOARM%
go build -trimpath -o dist\%ABI%\jadx
exit /b %ERRORLEVEL%
