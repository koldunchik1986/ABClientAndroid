param($filePath)

$bytes = [System.IO.File]::ReadAllBytes($filePath)
$hasBOM = $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF

if ($hasBOM) {
    Write-Warning "$filePath имеет BOM! Рекомендуется удалить."
} else {
    Write-Host "$filePath: OK (UTF-8 без BOM)" -ForegroundColor Green
}