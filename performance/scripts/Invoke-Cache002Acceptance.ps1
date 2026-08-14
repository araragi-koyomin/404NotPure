param(
    [ValidateSet(3)][int]$Repeats = 3,
    [ValidateSet(100)][int]$VirtualUsers = 100,
    [ValidateRange(0,300)][int]$CooldownSeconds = 30,
    [ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$Prefix = 'cache002-acceptance'
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/ResourceSafety.ps1"
$resultsDirectory = (Resolve-Path "$PSScriptRoot/../results").Path
$existing = @(Get-ChildItem -LiteralPath $resultsDirectory -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.BaseName -like "$Prefix-*" -or $_.Name -like "$Prefix-*" })
if ($existing.Count -gt 0) {
    throw "Prefix '$Prefix' already has result files. Choose a new prefix so evidence is not overwritten or mixed."
}

$preflight = Invoke-PerfPreflight -Seconds 30
if (-not $preflight.Allowed) {
    throw "Preflight rejected CACHE-002 acceptance: $($preflight.Reasons -join '; ')"
}
Write-Output "Preflight passed: CPU=$($preflight.AverageCpuPercent)% freeMemory=$($preflight.FreeMemoryGiB)GiB"

function Invoke-Cooldown {
    if ($CooldownSeconds -gt 0) { Start-Sleep -Seconds $CooldownSeconds }
}

function Invoke-Mode([string]$Mode) {
    & "$PSScriptRoot/Set-PerfCacheMode.ps1" -Mode enabled | Out-Null
    & "$PSScriptRoot/Set-PerfSingleFlightMode.ps1" -Mode $Mode | Out-Null
    for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
        & "$PSScriptRoot/Invoke-HotspotBurst.ps1" -VirtualUsers $VirtualUsers `
            -ResultName "$Prefix-$Mode-run$repeat"
        if ($repeat -lt $Repeats) { Invoke-Cooldown }
    }
}

function MetricValue($result, [string]$metric, [string]$field) {
    $metricProperty = $result.metrics.PSObject.Properties[$metric]
    if (-not $metricProperty) { throw "Result is missing metric $metric" }
    $fieldProperty = $metricProperty.Value.values.PSObject.Properties[$field]
    if (-not $fieldProperty) { throw "Metric $metric is missing $field" }
    return [double]$fieldProperty.Value
}

function Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    return [double]$sorted[[math]::Floor($sorted.Count / 2)]
}

Invoke-Mode 'disabled'
Invoke-Cooldown
Invoke-Mode 'enabled'

$summary = [ordered]@{}
foreach ($mode in @('disabled','enabled')) {
    $p95Values = @()
    $p99Values = @()
    for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
        $path = Join-Path $resultsDirectory "$Prefix-$mode-run$repeat.json"
        $result = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
        $p95Values += MetricValue $result 'business_duration' 'p(95)'
        $p99Values += MetricValue $result 'business_duration' 'p(99)'
    }
    $summary[$mode] = [ordered]@{
        p95MedianMs = [math]::Round((Median $p95Values), 3)
        p99MedianMs = [math]::Round((Median $p99Values), 3)
    }
}

if ($summary.enabled.p95MedianMs -ge $summary.disabled.p95MedianMs) {
    throw "CACHE-002 P95 did not improve: disabled=$($summary.disabled.p95MedianMs)ms enabled=$($summary.enabled.p95MedianMs)ms"
}
$allowedP99 = [math]::Max($summary.disabled.p99MedianMs * 1.10, $summary.disabled.p99MedianMs + 2.0)
if ($summary.enabled.p99MedianMs -gt $allowedP99) {
    throw "CACHE-002 P99 regressed: disabled=$($summary.disabled.p99MedianMs)ms enabled=$($summary.enabled.p99MedianMs)ms allowed=$([math]::Round($allowedP99,3))ms"
}

$summaryPath = Join-Path $resultsDirectory "$Prefix-comparison.json"
$summary | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 -LiteralPath $summaryPath
$summary | ConvertTo-Json -Depth 4
Write-Output "CACHE-002 acceptance passed; comparison saved to $summaryPath"
