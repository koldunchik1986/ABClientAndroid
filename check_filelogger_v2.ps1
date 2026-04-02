$content = Get-Content -Path "app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java" -Raw
$lines = $content -split "`n"

$logPattern = 'Log\.(d|w|e)\(TAG|android\.util\.Log\.(d|e|w)\(TAG'
$missing = @()

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match $logPattern) {
        $lineNum = $i + 1
        
        # Check next 5 lines for FileLogger (to account for multi-line Log statements)
        $hasFileLogger = $false
        for ($j = 1; $j -le 5 -and ($i + $j) -lt $lines.Count; $j++) {
            if ($lines[$i + $j] -match 'FileLogger\.(trace|warn|error)') {
                $hasFileLogger = $true
                break
            }
            # Stop checking if we hit closing parenthesis of Log statement
            if ($lines[$i + $j] -match '\);' -and $lines[$i + $j] -notmatch 'FileLogger') {
                break
            }
        }
        
        if (-not $hasFileLogger) {
            $missing += [PSCustomObject]@{ Line = $lineNum; Content = $lines[$i].Trim() }
        }
    }
}

Write-Host "Found missing FileLogger calls: $($missing.Count)"
if ($missing.Count -eq 0) {
    Write-Host "✅ Все Log вызовы имеют FileLogger"
} else {
    Write-Host "`n❌ Найдены Log вызовы БЕЗ FileLogger:"
    $missing | ForEach-Object { Write-Host "  Line $($_.Line): $($_.Content)" }
}
