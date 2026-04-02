$content = Get-Content -Path "app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java" -Raw
$lines = $content -split "`n"

$logPattern = 'Log\.(d|w|e)\(TAG|android\.util\.Log\.(d|e|w)\(TAG'
$missing = @()

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match $logPattern) {
        $lineNum = $i + 1
        $nextLine = if ($i + 1 -lt $lines.Count) { $lines[$i + 1] } else { "" }
        
        if ($nextLine -notmatch 'FileLogger\.(trace|warn|error)') {
            $missing += [PSCustomObject]@{ Line = $lineNum; Content = $lines[$i].Trim() }
        }
    }
}

if ($missing.Count -eq 0) {
    Write-Host "✅ Все Log вызовы имеют FileLogger"
} else {
    Write-Host "❌ Найдены Log вызовы БЕЗ FileLogger:"
    $missing | ForEach-Object { Write-Host "  Line $($_.Line): $($_.Content)" }
}
