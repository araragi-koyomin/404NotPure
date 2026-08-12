$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$preflight = & {
    . "$PSScriptRoot/ResourceSafety.ps1"
    Invoke-PerfPreflight -Seconds 30
}
if (-not $preflight.Allowed) { throw "Preflight rejected capacity discovery: $($preflight.Reasons -join '; ')" }

$rates = @(1, 10, 25, 50, 100)
foreach ($rate in $rates) {
    $name = "discovery-hot-r$rate"
    & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate $rate -Duration 15s -ResultName $name
    if ($LASTEXITCODE -ne 0) { throw "Capacity discovery stopped at rate $rate" }
    if ($rate -ne $rates[-1]) { Start-Sleep -Seconds 30 }
}
