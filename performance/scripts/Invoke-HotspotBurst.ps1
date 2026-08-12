param(
    [ValidateRange(1,1000)][int]$VirtualUsers = 100,
    [ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ResultName = 'hotspot-burst'
)
$ErrorActionPreference = 'Stop'
$page = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1&sort=id,asc' `
    -TimeoutSec 5
if ($page.code -ne '200' -or $page.data.items.Count -ne 1) { throw 'Could not select hotspot product' }
$productId = [int]$page.data.items[0].id
docker exec 404notpure-perf-redis-1 redis-cli DEL "product:detail:v1:$productId" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not expire the isolated hotspot key' }
& "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hotspot-burst -Rate $VirtualUsers `
    -Duration '1s' -ResultName $ResultName
$resultPath = Join-Path (Resolve-Path "$PSScriptRoot/../results").Path "$ResultName.json"
$result = [System.IO.File]::ReadAllText($resultPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$requestCount = [long]$result.metrics.business_requests.values.count
$iterationCount = [long]$result.metrics.iterations.values.count
if ($requestCount -ne $VirtualUsers -or $iterationCount -ne $VirtualUsers) {
    throw "Hotspot burst completed $requestCount business requests and $iterationCount iterations; expected exactly $VirtualUsers of each"
}
