@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Clean script for dev.perms.test (Windows)
REM Run from repo root (folder containing settings.gradle)
REM
REM Android Studio/Gradle create module .cxx folders for CMake/NDK builds.
REM They are generated native-build intermediates and can be deleted safely;
REM Android Studio recreates them on the next native build.

title dev.perms.test - Clean Menu

:pick_root
set "ROOT=%CD%"
if exist "%ROOT%\settings.gradle" goto menu
if exist "%ROOT%\settings.gradle.kts" goto menu

echo.
echo This folder does not look like the repo root.
echo Current: "%ROOT%"
echo.
echo 1) Use this folder anyway
echo 2) Browse to repo root
echo 3) Exit
echo.
set /p CH=Choose [1-3]: 
if "%CH%"=="1" goto menu
if "%CH%"=="2" goto browse
if "%CH%"=="3" goto :eof
goto pick_root

:browse
echo.
echo Enter the full path to the repo root (the folder with settings.gradle):
set /p ROOT=Path: 
if not exist "%ROOT%" (
  echo Not found.
  goto browse
)
goto menu

:menu
cls
echo ==========================================================
echo   dev.perms.test - Clean Menu
echo   Root: "%ROOT%"
echo ==========================================================
echo.
echo  1) Clean module build/ folders (fast)
echo  2) Clean .gradle in repo (resets Gradle state for this repo)
echo  3) Clean Android Studio caches (.idea, *.iml) (safe)
echo  4) Clean ALL generated project state (recommended)
echo     - build/, .cxx/, repo .gradle, .idea, and *.iml
echo  5) Clean native CMake/NDK intermediates (.cxx)
echo  6) Also stop Gradle daemon(s)
echo  7) Also clear GLOBAL Gradle caches (slow, affects all projects)
echo  8) Run Gradle clean (./gradlew clean)
echo  9) Exit
echo.
set /p CH=Choose [1-9]: 

if "%CH%"=="1" goto clean_builds
if "%CH%"=="2" goto clean_repo_gradle
if "%CH%"=="3" goto clean_idea
if "%CH%"=="4" goto clean_all
if "%CH%"=="5" goto clean_cxx
if "%CH%"=="6" goto stop_daemons
if "%CH%"=="7" goto clean_global_gradle
if "%CH%"=="8" goto gradle_clean
if "%CH%"=="9" goto :eof
goto menu

:clean_builds
echo.
echo Deleting build/ folders under "%ROOT%" ...
for /d /r "%ROOT%" %%D in (build) do (
  echo   - %%D
  rmdir /s /q "%%D" 2>nul
)
echo Done.
pause
goto menu

:clean_cxx
echo.
echo Deleting native CMake/NDK .cxx folders under "%ROOT%" ...
call :clean_cxx_silent
echo Done.
pause
goto menu

:clean_repo_gradle
echo.
echo Deleting "%ROOT%\.gradle" ...
rmdir /s /q "%ROOT%\.gradle" 2>nul
echo Done.
pause
goto menu

:clean_idea
echo.
echo Deleting Android Studio project files in "%ROOT%" ...
if exist "%ROOT%\.idea" (
  echo   - %ROOT%\.idea
  rmdir /s /q "%ROOT%\.idea" 2>nul
)
del /q "%ROOT%\*.iml" 2>nul
echo Done.
pause
goto menu

:clean_all
call :clean_builds_silent
call :clean_cxx_silent
call :clean_repo_gradle_silent
call :clean_idea_silent
echo.
echo Done (all generated project state).
pause
goto menu

:stop_daemons
echo.
echo Stopping Gradle daemons ...
if exist "%ROOT%\gradlew.bat" (
  pushd "%ROOT%"
  call gradlew.bat --stop
  popd
) else (
  echo gradlew.bat not found in repo root.
)
echo Done.
pause
goto menu

:clean_global_gradle
echo.
echo WARNING: This clears GLOBAL Gradle caches in %USERPROFILE%\.gradle
echo It will slow the next build for ALL Gradle projects.
echo.
set /p OK=Type YES to continue: 
if /I not "%OK%"=="YES" goto menu
echo.
echo Deleting "%USERPROFILE%\.gradle\caches" ...
rmdir /s /q "%USERPROFILE%\.gradle\caches" 2>nul
echo Deleting "%USERPROFILE%\.gradle\daemon" ...
rmdir /s /q "%USERPROFILE%\.gradle\daemon" 2>nul
echo Done.
pause
goto menu

:gradle_clean
echo.
if exist "%ROOT%\gradlew.bat" (
  pushd "%ROOT%"
  call gradlew.bat clean
  popd
) else (
  echo gradlew.bat not found in repo root.
)
pause
goto menu

:clean_builds_silent
for /d /r "%ROOT%" %%D in (build) do rmdir /s /q "%%D" 2>nul
exit /b

:clean_cxx_silent
for /d /r "%ROOT%" %%D in (.cxx) do (
  echo   - %%D
  rmdir /s /q "%%D" 2>nul
)
exit /b

:clean_repo_gradle_silent
rmdir /s /q "%ROOT%\.gradle" 2>nul
exit /b

:clean_idea_silent
if exist "%ROOT%\.idea" rmdir /s /q "%ROOT%\.idea" 2>nul
del /q "%ROOT%\*.iml" 2>nul
exit /b
