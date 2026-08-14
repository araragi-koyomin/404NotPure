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

function Read-Snapshot([string]$Suffix) {
    $path = Join-Path (Resolve-Path "$PSScriptRoot/../results/raw").Path "$ResultName-$Suffix.json"
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing hotspot snapshot: $path" }
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
}

function Read-ActuatorValue($Snapshot, [string]$MetricName, [string]$Statistic) {
    $metric = @($Snapshot.actuator | Where-Object { $_.name -eq $MetricName })
    if ($metric.Count -ne 1) { throw "Snapshot must contain exactly one $MetricName metric" }
    $measurement = @($metric[0].measurements | Where-Object { $_.statistic -eq $Statistic })
    if ($measurement.Count -ne 1) {
        throw "Metric $MetricName must contain exactly one $Statistic measurement"
    }
    return [double]$measurement[0].value
}

function Read-Mode([string]$Name, [string]$Default) {
    $line = Get-Content -LiteralPath "$PSScriptRoot/../cache-mode.env" -ErrorAction SilentlyContinue |
        Where-Object { $_ -match "^$Name=" } | Select-Object -Last 1
    if (-not $line) { return $Default }
    return ($line -split '=', 2)[1].Trim().ToLowerInvariant()
}

$before = Read-Snapshot 'before'
$after = Read-Snapshot 'after'
$singleFlightLeaderDelta = (Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.leader' 'COUNT') - (Read-ActuatorValue $before `
    'tomatomall.cache.product.singleflight.leader' 'COUNT')
$singleFlightTimeoutDelta = (Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.wait.timeout' 'COUNT') - (Read-ActuatorValue $before `
    'tomatomall.cache.product.singleflight.wait.timeout' 'COUNT')
$singleFlightFailureDelta = (Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.leader.failures' 'COUNT') - (Read-ActuatorValue $before `
    'tomatomall.cache.product.singleflight.leader.failures' 'COUNT')
$singleFlightActiveAfter = Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.active' 'VALUE'
$singleFlightWaitersAfter = Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.waiters.active' 'VALUE'
$singleFlightEffectiveBefore = Read-ActuatorValue $before `
    'tomatomall.cache.product.singleflight.enabled' 'VALUE'
$singleFlightEffectiveAfter = Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.enabled' 'VALUE'
$singleFlightFollowerDelta = (Read-ActuatorValue $after `
    'tomatomall.cache.product.singleflight.follower' 'COUNT') - (Read-ActuatorValue $before `
    'tomatomall.cache.product.singleflight.follower' 'COUNT')
$mysqlSelectDelta = [long]$after.mysql.Com_select - [long]$before.mysql.Com_select
$mysqlRowLockWaitDelta = [long]$after.mysql.Innodb_row_lock_waits - `
    [long]$before.mysql.Innodb_row_lock_waits
$hikariTimeoutDelta = (Read-ActuatorValue $after 'hikaricp.connections.timeout' 'COUNT') - `
    (Read-ActuatorValue $before 'hikaricp.connections.timeout' 'COUNT')
$hikariPendingAfter = Read-ActuatorValue $after 'hikaricp.connections.pending' 'VALUE'
$singleFlightEnabled = (Read-Mode 'PRODUCT_CACHE_SINGLE_FLIGHT_ENABLED' 'true') -eq 'true'
$expectedEffective = if ($singleFlightEnabled) { 1.0 } else { 0.0 }
$runtimePath = Join-Path (Resolve-Path "$PSScriptRoot/../results/raw").Path "$ResultName-runtime.json"
$runtime = [System.IO.File]::ReadAllText($runtimePath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json

if ($singleFlightEffectiveBefore -ne $expectedEffective -or $singleFlightEffectiveAfter -ne $expectedEffective) {
    throw "CACHE-002 requested mode does not match backend effective mode: requested=$singleFlightEnabled before=$singleFlightEffectiveBefore after=$singleFlightEffectiveAfter"
}

if ($singleFlightEnabled -and $singleFlightLeaderDelta -ne 1) {
    throw "CACHE-002 hotspot burst expected one leader, observed $singleFlightLeaderDelta"
}
if (-not $singleFlightEnabled -and ($singleFlightLeaderDelta -ne 0 -or $singleFlightFollowerDelta -ne 0)) {
    throw "CACHE-002 disabled run still used coordination: leader=$singleFlightLeaderDelta follower=$singleFlightFollowerDelta"
}
if ($singleFlightTimeoutDelta -ne 0 -or $singleFlightFailureDelta -ne 0) {
    throw "CACHE-002 hotspot burst observed timeout=$singleFlightTimeoutDelta failure=$singleFlightFailureDelta"
}
if ($singleFlightActiveAfter -ne 0 -or $singleFlightWaitersAfter -ne 0) {
    throw "CACHE-002 state did not return to zero: active=$singleFlightActiveAfter waiters=$singleFlightWaitersAfter"
}
if ($singleFlightEnabled -and $mysqlSelectDelta -gt 18) {
    throw "CACHE-002 hotspot burst used $mysqlSelectDelta MySQL SELECT statements; maximum is 18"
}
if ($singleFlightEnabled -and $mysqlRowLockWaitDelta -ne 0) {
    throw "CACHE-002 hotspot burst produced $mysqlRowLockWaitDelta MySQL row-lock waits"
}
if ($hikariTimeoutDelta -ne 0 -or $hikariPendingAfter -ne 0 -or $runtime.maxHikariPending -ne 0) {
    throw "CACHE-002 hotspot burst observed Hikari timeout=$hikariTimeoutDelta pendingAfter=$hikariPendingAfter maxPending=$($runtime.maxHikariPending)"
}

[pscustomobject]@{
    singleFlightEnabled = $singleFlightEnabled
    singleFlightLeaderDelta = $singleFlightLeaderDelta
    singleFlightTimeoutDelta = $singleFlightTimeoutDelta
    singleFlightFailureDelta = $singleFlightFailureDelta
    singleFlightActiveAfter = $singleFlightActiveAfter
    singleFlightWaitersAfter = $singleFlightWaitersAfter
    singleFlightEffective = $singleFlightEffectiveAfter
    singleFlightFollowerDelta = $singleFlightFollowerDelta
    mysqlSelectDelta = $mysqlSelectDelta
    mysqlRowLockWaitDelta = $mysqlRowLockWaitDelta
    hikariTimeoutDelta = $hikariTimeoutDelta
    hikariPendingAfter = $hikariPendingAfter
    maxHikariPending = $runtime.maxHikariPending
} | ConvertTo-Json -Compress | Write-Output
