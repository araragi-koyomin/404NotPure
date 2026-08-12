param(
    [int[]]$Rates = @(10,50,100),
    [ValidateSet(3)][int]$Repeats = 3,
    [ValidatePattern('^[1-9][0-9]*s$')][string]$WarmupDuration = '20s',
    [ValidatePattern('^[1-9][0-9]*s$')][string]$MeasureDuration = '60s',
    [ValidateRange(0,300)][int]$CooldownSeconds = 30,
    [string]$Prefix = 'formal'
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/ResourceSafety.ps1"
if ($Prefix -notmatch '^[a-zA-Z0-9._-]+$') { throw 'Prefix contains unsupported characters' }
foreach ($rate in $Rates) {
    if ($rate -lt 1 -or $rate -gt 10000) { throw "Invalid rate: $rate" }
}
$existingPrefixOutputs = @(Get-ChildItem -LiteralPath "$PSScriptRoot/../results" -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.BaseName -like "$Prefix-*" -or $_.Name -like "$Prefix-*" })
if ($existingPrefixOutputs.Count -gt 0) {
    throw "Prefix '$Prefix' already has result files. Choose a new prefix so previous evidence cannot be mixed into this run."
}

$preflight = Invoke-PerfPreflight -Seconds 30
if (-not $preflight.Allowed) { throw "Preflight rejected formal suite: $($preflight.Reasons -join '; ')" }
Write-Output "Preflight passed: CPU=$($preflight.AverageCpuPercent)% freeMemory=$($preflight.FreeMemoryGiB)GiB"

function Invoke-Cooldown {
    if ($CooldownSeconds -gt 0) { Start-Sleep -Seconds $CooldownSeconds }
}

function Invoke-StableGroup([string]$Mode, [string]$Scenario) {
    & "$PSScriptRoot/Set-PerfCacheMode.ps1" -Mode $Mode | Out-Null
    foreach ($rate in $Rates) {
        if ($Mode -eq 'enabled') { & "$PSScriptRoot/Clear-PerfProductCache.ps1" | Out-Null }
        & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario $Scenario -Rate $rate `
            -Duration $WarmupDuration -ResultName "$Prefix-$Mode-$Scenario-r$rate-warmup" `
            -SkipSnapshots | Out-Null
        for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
            & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario $Scenario -Rate $rate `
                -Duration $MeasureDuration -ResultName "$Prefix-$Mode-$Scenario-r$rate-run$repeat"
            if ($repeat -lt $Repeats) { Invoke-Cooldown }
        }
        Invoke-Cooldown
    }
}

Invoke-StableGroup -Mode disabled -Scenario hot
Invoke-StableGroup -Mode enabled -Scenario hot

foreach ($scenario in @('missing-fixed','missing-random')) {
    & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario $scenario -Rate 100 `
        -Duration $WarmupDuration -ResultName "$Prefix-$scenario-warmup" -SkipSnapshots | Out-Null
    for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
        & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario $scenario -Rate 100 `
            -Duration $MeasureDuration -ResultName "$Prefix-$scenario-run$repeat"
        if ($repeat -lt $Repeats) { Invoke-Cooldown }
    }
    Invoke-Cooldown
}

for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
    & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario read-write -Rate 100 `
        -Duration $MeasureDuration -ResultName "$Prefix-read-write-run$repeat"
    if ($repeat -lt $Repeats) { Invoke-Cooldown }
}

& "$PSScriptRoot/Clear-PerfProductCache.ps1" | Out-Null
& "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate 100 -Duration $MeasureDuration `
    -ResultName "$Prefix-concentrated-cold-start"
Invoke-Cooldown
& "$PSScriptRoot/Invoke-HotspotBurst.ps1" -VirtualUsers 100 -ResultName "$Prefix-hotspot-burst"
Invoke-Cooldown
$redisOutageLimitation = $false
try {
    & "$PSScriptRoot/Invoke-RedisOutageScenario.ps1" -Rate 50 -Duration '10s' `
        -ResultName "$Prefix-redis-outage"
} catch {
    $failureMarker = "$PSScriptRoot/../results/raw/$Prefix-redis-outage-expected-failure.txt"
    if (-not (Test-Path -LiteralPath $failureMarker)) { throw }
    $redisOutageLimitation = $true
    Write-Warning 'Redis outage failed the hard thresholds; recovery passed and the measured limitation is retained.'
}

$expectedRepeatedScenarios = @()
foreach ($mode in @('disabled','enabled')) {
    foreach ($rate in $Rates) { $expectedRepeatedScenarios += "$Prefix-$mode-hot-r$rate" }
}
$expectedRepeatedScenarios += @("$Prefix-missing-fixed", "$Prefix-missing-random", "$Prefix-read-write")
& "$PSScriptRoot/Build-PerfReport.ps1" -NamePattern "$Prefix-*" -ExpectedRepeats $Repeats `
    -ExpectedRepeatedScenarios $expectedRepeatedScenarios
if ($redisOutageLimitation) {
    Write-Output 'Formal PERF-001 suite completed with the measured Redis outage limitation.'
} else {
    Write-Output 'Formal PERF-001 suite completed.'
}
