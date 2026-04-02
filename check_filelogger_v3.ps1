$content = Get-Content -Path "app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java" -Raw
$lines = $content -split "`n"

$logPattern = 'Log\.(d|w|e)\(TAG|android\.util\.Log\.(d|e|w)\(TAG'
$missing = @()
$inMultilineLog = $false
$multilineStartIdx = -1

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match $logPattern) {
        $inMultilineLog = $true
        $multilineStartIdx = $i
    }
    
    if ($inMultilineLog) {
        if ($lines[$i] -match '\);$') {
            # End of Log statement found
            $logEndIdx = $i
            
            # Check next 10 lines for FileLogger
            $hasFileLogger = $false
            for ($j = 1; $j -le 10 -and ($logEndIdx + $j) -lt $lines.Count; $j++) {
                if ($lines[$logEndIdx + $j] -match 'FileLogger\.(trace|warn|error)') {
                    $hasFileLogger = $true
                    break
                }
                # Stop if we hit another statement
                if ($lines[$logEndIdx + $j] -match '^\s*[a-zA-Z]' -and $lines[$logEndIdx + $j] -notmatch 'FileLogger') {
                    break
                }
            }
            
            if (-not $hasFileLogger -and $lines[$multilineStartIdx] -match $logPattern) {
                $missing += [PSCustomObject]@{ Line = $multilineStartIdx + 1; Content = $lines[$multilineStartIdx].Trim() }
            }
            
            $inMultilineLog = $false
            $multilineStartIdx = -1
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
