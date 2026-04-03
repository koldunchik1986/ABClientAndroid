# PowerShell script to install Android Studio

Write-Host "========================================"
Write-Host "Android Studio Installer for Windows"
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Check Windows Package Manager
try {
    $null = winget.exe --version
    Write-Host "Windows Package Manager found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Windows Package Manager (winget) not found" -ForegroundColor Red
    Write-Host "Please install winget or download Android Studio manually"
    Write-Host "From: https://developer.android.com/studio"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Installing Android Studio..."
Write-Host "This may take 5-10 minutes depending on your internet speed"
Write-Host ""

$result = & winget install Google.AndroidStudio

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "SUCCESS: Android Studio installed!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Start Android Studio"
    Write-Host "2. File → Open Project"
    Write-Host "3. Select: c:\Users\User\AbclientAndroid"
    Write-Host "4. Wait for Gradle Sync"
    Write-Host "5. Build → Build APK(s)"
    Write-Host ""
    Read-Host "Press Enter to exit"
} else {
    Write-Host ""
    Write-Host "ERROR: Installation failed" -ForegroundColor Red
    Write-Host "Please download manually from:"
    Write-Host "https://developer.android.com/studio"
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}
