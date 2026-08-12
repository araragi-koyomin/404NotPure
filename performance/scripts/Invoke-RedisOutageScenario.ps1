param(
    [ValidateRange(1,10000)][int]$Rate = 50,
    [ValidatePattern('^[1-9][0-9]*s$')][string]$Duration = '20s',
    [ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$ResultName = 'redis-outage'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$adminPasswordPath = Join-Path $repoRoot 'performance/admin-password.txt'
$existingOutageOutputs = @(Get-ChildItem -LiteralPath "$repoRoot/performance/results" -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.BaseName -eq $ResultName -or $_.BaseName -like "$ResultName-*" })
if ($existingOutageOutputs.Count -gt 0) {
    throw "ResultName '$ResultName' already has outage or recovery evidence. Choose a new name."
}
& "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-before" | Out-Null
$pageBefore = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1&sort=id,asc' -TimeoutSec 5
if ($pageBefore.code -ne '200' -or $pageBefore.data.items.Count -ne 1) {
    throw 'Could not select a product before Redis outage verification'
}
$productId = $pageBefore.data.items[0].id
$productBefore = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/products/$productId" -TimeoutSec 5
$originalRate = [double]$productBefore.data.rate
$outageRate = if ($originalRate -lt 4.9) { $originalRate + 0.1 } else { $originalRate - 0.1 }
if (-not (Test-Path -LiteralPath $adminPasswordPath)) {
    throw 'The ignored performance admin password file is missing; initialize the PERF runtime first'
}
$adminPassword = (Get-Content -Raw -LiteralPath $adminPasswordPath).Trim()
$loginBody = @{ username = 'demo_admin'; password = $adminPassword } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -ContentType 'application/json' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($loginBody)) `
    -Uri 'http://127.0.0.1:18080/api/accounts/login' -TimeoutSec 5
if ($login.code -ne '200' -or [string]::IsNullOrWhiteSpace([string]$login.data)) {
    throw 'Could not authenticate the performance admin for outage consistency verification'
}
$adminToken = [string]$login.data
$nonProductKey = "cache003:recovery-proof:$ResultName"
docker exec 404notpure-perf-redis-1 redis-cli SET $nonProductKey preserve-me | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not create the non-product recovery proof key' }
$redisStopped = $false
$productRestoreRequired = $false
$outageCompleted = $false
$primaryFailure = $null
try {
    try {
        docker stop --time 10 404notpure-perf-redis-1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the isolated PERF Redis container' }
        $redisStopped = $true
        $redisRunning = docker inspect 404notpure-perf-redis-1 --format '{{.State.Running}}'
        if ($LASTEXITCODE -ne 0 -or $redisRunning.Trim() -ne 'false') {
            throw 'PERF Redis still reports running after stop'
        }
        & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate $Rate -Duration $Duration `
            -ResultName $ResultName -AllowRedisUnavailable -AllowProtective503 -SkipBeforeSnapshot

        $updateBody = @{ id = $productId; rate = $outageRate } | ConvertTo-Json -Compress
        $productRestoreRequired = $true
        $updated = Invoke-RestMethod -Method Put -ContentType 'application/json; charset=utf-8' `
            -Headers @{ token = $adminToken } -Body ([System.Text.Encoding]::UTF8.GetBytes($updateBody)) `
            -Uri 'http://127.0.0.1:18080/api/products' -TimeoutSec 5
        if ($updated.code -ne '200') { throw 'Could not update a product while Redis was unavailable' }
        $outageCompleted = $true
    } finally {
        if ($redisStopped) {
            docker start 404notpure-perf-redis-1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Could not restart the isolated PERF Redis container' }
            & "$PSScriptRoot/Wait-PerfService.ps1" -Service redis | Out-Null
        }
    }
    if (-not $outageCompleted) { throw 'Redis outage verification did not complete' }
    $recoveryTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $cacheState = 1
    do {
        $recoveryProbe = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/products/$productId" `
            -TimeoutSec 2
        if ($recoveryProbe.code -ne '200' -or $recoveryProbe.data.id -ne $productId) {
            throw 'Redis recovery polling returned an incorrect product response'
        }
        $metricRaw = docker exec 404notpure-perf-metrics-1 curl -fsS `
            'http://backend:9090/actuator/metrics/tomatomall.cache.product.redis.circuit.state' 2>$null
        if ($LASTEXITCODE -eq 0) {
            $metric = $metricRaw | ConvertFrom-Json
            $cacheState = [double]$metric.measurements[0].value
        }
        if ($cacheState -ne 0) { Start-Sleep -Milliseconds 250 }
    } while ($cacheState -ne 0 -and $recoveryTimer.Elapsed.TotalSeconds -lt 10)
    $recoveryTimer.Stop()
    if ($cacheState -ne 0) {
        throw 'Product cache did not leave Redis bypass mode within 10 seconds after Redis restarted'
    }
    $recoveredProduct = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/products/$productId" -TimeoutSec 5
    if ($recoveredProduct.code -ne '200' `
            -or [math]::Abs([double]$recoveredProduct.data.rate - $outageRate) -gt 0.001) {
        throw 'Redis recovery returned the stale product value instead of the database update'
    }
    $proofValue = docker exec 404notpure-perf-redis-1 redis-cli GET $nonProductKey
    if ($LASTEXITCODE -ne 0 -or $proofValue.Trim() -ne 'preserve-me') {
        throw 'Redis recovery removed a non-product key; cleanup must remain limited to product details'
    }
    [ordered]@{
        recovered = $true
        recoverySeconds = [math]::Round($recoveryTimer.Elapsed.TotalSeconds, 3)
        databaseUpdateObserved = $true
        nonProductKeyPreserved = $true
    } | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath `
        (Join-Path $repoRoot "performance/results/raw/$ResultName-recovery-latency.json")
    & "$PSScriptRoot/Collect-PerfSnapshot.ps1" -Name "$ResultName-recovered" | Out-Null
    & "$PSScriptRoot/Run-PerfScenario.ps1" -Scenario hot -Rate $Rate -Duration $Duration `
        -ResultName "$ResultName-after-recovery"
} catch {
    $primaryFailure = $_
    throw
} finally {
    $cleanupFailures = [System.Collections.Generic.List[string]]::new()
    try {
        if ($productRestoreRequired) {
            $restoreBody = @{ id = $productId; rate = $originalRate } | ConvertTo-Json -Compress
            $restored = Invoke-RestMethod -Method Put -ContentType 'application/json; charset=utf-8' `
                -Headers @{ token = $adminToken } -Body ([System.Text.Encoding]::UTF8.GetBytes($restoreBody)) `
                -Uri 'http://127.0.0.1:18080/api/products' -TimeoutSec 5
            if ($restored.code -ne '200') { throw 'Could not restore the product after Redis outage verification' }
        }
    } catch {
        $cleanupFailures.Add("product restore: $($_.Exception.Message)")
    } finally {
        try {
            docker exec 404notpure-perf-redis-1 redis-cli DEL $nonProductKey | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'redis-cli could not delete the recovery proof key' }
        } catch {
            $cleanupFailures.Add("proof key cleanup: $($_.Exception.Message)")
        }
    }
    if ($cleanupFailures.Count -gt 0) {
        $cleanupMessage = $cleanupFailures -join '; '
        if ($null -ne $primaryFailure) {
            throw "Redis outage scenario failed: $($primaryFailure.Exception.Message); cleanup also failed: $cleanupMessage"
        }
        throw "Redis outage cleanup failed: $cleanupMessage"
    }
}
Write-Output 'Redis outage and recovery scenario completed'
