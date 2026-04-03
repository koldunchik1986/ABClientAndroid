@echo off
REM Script to download and install Android Studio on Windows

echo ========================================
echo Android Studio Installer for Windows
echo ========================================

REM Check if winget is available
where winget >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows Package Manager (winget) not found
    echo Please install winget or download Android Studio manually
    echo From: https://developer.android.com/studio
    pause
    exit /b 1
)

echo.
echo Installing Android Studio...
echo This may take 5-10 minutes depending on your internet speed
echo.

winget install Google.AndroidStudio

if errorlevel 0 (
    echo.
    echo ========================================
    echo SUCCESS: Android Studio installed!
    echo ========================================
    echo.
    echo Next steps:
    echo 1. Start Android Studio
    echo 2. File -^> Open Project
    echo 3. Select: c:\Users\User\AbclientAndroid
    echo 4. Wait for Gradle Sync
    echo 5. Build -^> Build APK^(s^)
    echo.
    pause
) else (
    echo.
    echo ERROR: Installation failed
    echo Please download manually from:
    echo https://developer.android.com/studio
    echo.
    pause
    exit /b 1
)
