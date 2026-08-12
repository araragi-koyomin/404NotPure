param([Parameter(Mandatory)][ValidateSet('enabled','disabled')][string]$Mode)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$enabled = if ($Mode -eq 'enabled') { 'true' } else { 'false' }
$cacheModePath = Join-Path $repoRoot 'performance/cache-mode.env'
[System.IO.File]::WriteAllText($cacheModePath,
    "PRODUCT_DETAIL_CACHE_ENABLED=$enabled`n", [System.Text.Encoding]::ASCII)
docker compose --env-file back_end/.env -f docker-compose.perf.yml `
    -p 404notpure-perf up -d --force-recreate backend | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not recreate PERF backend' }

$deadline = (Get-Date).AddMinutes(2)
do {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1' `
            -TimeoutSec 3
        $ready = $response.code -eq '200' -and $response.data.totalElements -eq 300
    } catch { $ready = $false }
} until ($ready -or (Get-Date) -gt $deadline)
if (-not $ready) { throw "PERF backend did not recover with cache $Mode" }
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker exec 404notpure-perf-metrics-1 curl -fsS `
        'http://backend:9090/actuator/health' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $metricsReady = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $metricsReady) { throw "PERF metrics endpoint did not recover with cache $Mode" }
Write-Output "PERF product detail cache: $Mode"
