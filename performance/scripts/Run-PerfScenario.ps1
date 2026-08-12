param(
    [Parameter(Mandatory)][ValidateSet('hot','hotspot','missing-fixed','missing-random','hotspot-burst','read-write')][string]$Scenario,
    [Parameter(Mandatory)][ValidateRange(1,10000)][int]$Rate,
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*s$')][string]$Duration,
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ResultName,
    [switch]$AllowRedisUnavailable,
    [switch]$SkipBeforeSnapshot,
    [switch]$SkipSnapshots
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/ResourceSafety.ps1"
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$rawDirectory = Join-Path $repoRoot 'performance/results/raw'
New-Item -ItemType Directory -Force -Path $rawDirectory | Out-Null
$resultFile = Join-Path $repoRoot "performance/results/$ResultName.json"
$stdoutPath = Join-Path $rawDirectory "$ResultName.stdout.log"
$stderrPath = Join-Path $rawDirectory "$ResultName.stderr.log"
$runtimePath = Join-Path $rawDirectory "$ResultName-runtime.json"
$beforePath = Join-Path $rawDirectory "$ResultName-before.json"
$afterPath = Join-Path $rawDirectory "$ResultName-after.json"
$recoveredPath = Join-Path $rawDirectory "$ResultName-recovered.json"
$failureMarkerPath = Join-Path $rawDirectory "$ResultName-expected-failure.txt"
$ownedOutputs = @($resultFile, $stdoutPath, $stderrPath, $runtimePath,
    $afterPath, $recoveredPath, $failureMarkerPath)
if (-not $SkipBeforeSnapshot) {
    $ownedOutputs += $beforePath
}
if ($ownedOutputs | Where-Object { Test-Path -LiteralPath $_ }) {
    throw "ResultName '$ResultName' already has output files. Choose a new name so old evidence is not overwritten or mixed with this run."
}

$readWriteOriginalDescription = $null
if ($Scenario -eq 'read-write') {
    $page = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1&sort=id,asc' -TimeoutSec 5
    if ($page.code -ne '200' -or $page.data.items.Count -ne 1) { throw 'Could not select product before read-write run' }
    $product = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/products/$($page.data.items[0].id)" -TimeoutSec 5
    if ($product.code -ne '200') { throw 'Could not read product before read-write run' }
    $readWriteOriginalDescription = [string]$product.data.description
}

if (-not $SkipSnapshots -and -not $SkipBeforeSnapshot) {
    & "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-before" `
        -AllowRedisUnavailable:$AllowRedisUnavailable | Out-Null
}
$scriptName = switch ($Scenario) {
    'hotspot-burst' { 'burst.js' }
    'read-write' { 'read-write.js' }
    default { 'detail.js' }
}
$arguments = @('compose','--env-file','back_end/.env','-f','docker-compose.perf.yml',
    '-p','404notpure-perf','--profile','tools','run','--rm','--no-deps',
    '-e',"SCENARIO=$Scenario",'-e',"RATE=$Rate",'-e',"DURATION=$Duration",
    '-e',"BURST_VUS=$Rate",'-e',"READ_RATE=$Rate",'-e','WRITE_RATE=1',
    '-e',"RESULT_NAME=$ResultName",'k6','run','--include-system-env-vars',"/scripts/$scriptName")
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = 'docker'
$startInfo.Arguments = $arguments -join ' '
$startInfo.WorkingDirectory = $repoRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
if (-not $process.Start()) { throw 'Could not start the k6 Docker process' }
$stdoutTask = $process.StandardOutput.ReadToEndAsync()
$stderrTask = $process.StandardError.ReadToEndAsync()
$completedNormally = $false
try {
    $highCpuSamples = 0
    $lowMemorySamples = 0
    $stopReason = $null
    $peakCpuPercent = 0.0
    $minimumFreeMemoryGiB = [double]::PositiveInfinity
    $minimumCDriveFreeGiB = [double]::PositiveInfinity
    $minimumEDriveFreeGiB = [double]::PositiveInfinity
    $protectedServices = @('404notpure-perf-backend-1','404notpure-perf-db-1')
    if (-not $AllowRedisUnavailable) {
        $protectedServices += '404notpure-perf-redis-1'
    }
    $initialRestarts = @{}
    foreach ($containerName in $protectedServices) {
        $initialRestarts[$containerName] = [int](docker inspect $containerName --format '{{.RestartCount}}')
        if ($LASTEXITCODE -ne 0) { throw "Could not inspect $containerName before k6" }
    }
    while (-not $process.HasExited) {
        Start-Sleep -Seconds 2
        $snapshot = Get-HostResourceSnapshot
        $peakCpuPercent = [math]::Max($peakCpuPercent, $snapshot.CpuPercent)
        $minimumFreeMemoryGiB = [math]::Min($minimumFreeMemoryGiB, $snapshot.FreeMemoryGiB)
        $minimumCDriveFreeGiB = [math]::Min($minimumCDriveFreeGiB, $snapshot.CDriveFreeGiB)
        $minimumEDriveFreeGiB = [math]::Min($minimumEDriveFreeGiB, $snapshot.EDriveFreeGiB)
        $state = Update-PerfWatchdogState -CpuPercent $snapshot.CpuPercent `
            -FreeMemoryGiB $snapshot.FreeMemoryGiB -CDriveFreeGiB $snapshot.CDriveFreeGiB `
            -EDriveFreeGiB $snapshot.EDriveFreeGiB -HighCpuSamples $highCpuSamples `
            -LowMemorySamples $lowMemorySamples
        $highCpuSamples = $state.HighCpuSamples
        $lowMemorySamples = $state.LowMemorySamples
        try {
            docker info --format '{{.ServerVersion}}' | Out-Null
            if ($LASTEXITCODE -ne 0) { $stopReason = 'Docker became unavailable' }
        } catch {
            $stopReason = 'Docker became unavailable'
        }
        if (-not $stopReason) {
            foreach ($containerName in $protectedServices) {
                $currentRestartCount = [int](docker inspect $containerName --format '{{.RestartCount}}' 2>$null)
                if ($LASTEXITCODE -ne 0 -or $currentRestartCount -ne $initialRestarts[$containerName]) {
                    $stopReason = "$containerName restarted or disappeared"
                    break
                }
            }
        }
        if ($state.MustStop) { $stopReason = $state.Reason }
        if ($stopReason) { throw "PERF watchdog stopped k6: $stopReason" }
    }
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $stdoutText = $stdoutTask.GetAwaiter().GetResult()
    $stderrText = $stderrTask.GetAwaiter().GetResult()
    [System.IO.File]::WriteAllText($stdoutPath, $stdoutText, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($stderrPath, $stderrText, [System.Text.Encoding]::UTF8)
    [ordered]@{
        peakHostCpuPercent = $peakCpuPercent
        minimumFreeMemoryGiB = $minimumFreeMemoryGiB
        minimumCDriveFreeGiB = $minimumCDriveFreeGiB
        minimumEDriveFreeGiB = $minimumEDriveFreeGiB
        watchdogStopReason = $stopReason
    } | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $runtimePath
    if (-not $SkipSnapshots) {
        & "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-after" `
            -AllowRedisUnavailable:$AllowRedisUnavailable | Out-Null
    }
    if ($Scenario -eq 'read-write') {
        $pageAfter = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1&sort=id,asc' -TimeoutSec 5
        $productAfter = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/products/$($pageAfter.data.items[0].id)" -TimeoutSec 5
        if ($productAfter.code -ne '200' -or [string]$productAfter.data.description -cne $readWriteOriginalDescription) {
            throw 'The read-write scenario did not restore the tested product description'
        }
    }
    if ($exitCode -ne 0) {
        $tail = Get-Content -LiteralPath $stderrPath -Tail 30 -ErrorAction SilentlyContinue
        throw "k6 failed with exit code $exitCode`: $($tail -join ' ')"
    }
    $completedNormally = $true
    Get-Content -LiteralPath $stdoutPath -Tail 5
} finally {
    if (-not $completedNormally) {
        Stop-PerfK6Containers
        if (-not $process.HasExited) {
            [void]$process.WaitForExit(15000)
        }
        if ($process.HasExited) {
            try {
                [System.IO.File]::WriteAllText($stdoutPath, $stdoutTask.GetAwaiter().GetResult(), [System.Text.Encoding]::UTF8)
                [System.IO.File]::WriteAllText($stderrPath, $stderrTask.GetAwaiter().GetResult(), [System.Text.Encoding]::UTF8)
            } catch {
                # Preserve the original monitoring or k6 failure; partial logs may remain unavailable.
            }
        }
    }
}
