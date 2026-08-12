param(
    [ValidateRange(1,10000)][int]$Rate = 50,
    [ValidatePattern('^[1-9][0-9]*s$')][string]$Duration = '20s',
    [ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ResultName = 'redis-outage'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$failureMarker = Join-Path $repoRoot "performance/results/raw/$ResultName-expected-failure.txt"
$existingOutageOutputs = @(Get-ChildItem -LiteralPath "$repoRoot/performance/results" -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.BaseName -eq $ResultName -or $_.BaseName -like "$ResultName-*" })
if ($existingOutageOutputs.Count -gt 0) {
    throw "ResultName '$ResultName' already has outage or recovery evidence. Choose a new name."
}
& "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-before" | Out-Null
$redisStopped = $false
$outageFailure = $null
try {
    docker stop --time 10 404notpure-perf-redis-1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the isolated PERF Redis container' }
    $redisStopped = $true
    $redisRunning = docker inspect 404notpure-perf-redis-1 --format '{{.State.Running}}'
    if ($LASTEXITCODE -ne 0 -or $redisRunning.Trim() -ne 'false') {
        throw 'PERF Redis still reports running after stop'
    }
    try {
        & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate $Rate -Duration $Duration `
            -ResultName $ResultName -AllowRedisUnavailable -SkipBeforeSnapshot
    } catch {
        $outageFailure = $_.Exception.Message
    }
} finally {
    if ($redisStopped) {
        docker start 404notpure-perf-redis-1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restart the isolated PERF Redis container' }
        & "$PSScriptRoot/Wait-PerfService.ps1" -Service redis | Out-Null
    }
}
& "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-recovered" | Out-Null
& "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate $Rate -Duration $Duration `
    -ResultName "$ResultName-after-recovery"
if ($outageFailure) {
    'Redis outage did not meet the hard correctness/throughput thresholds; see the k6 JSON and stderr log.' `
        | Set-Content -Encoding ascii -LiteralPath $failureMarker
    throw "Redis outage scenario failed as a measured system limitation; recovery passed. $outageFailure"
}
Write-Output 'Redis outage and recovery scenario completed'
