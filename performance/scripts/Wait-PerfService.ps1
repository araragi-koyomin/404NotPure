param(
    [Parameter(Mandatory)][ValidateSet('backend','redis')][string]$Service,
    [ValidateRange(1,300)][int]$TimeoutSeconds = 120
)
$ErrorActionPreference = 'Stop'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    if ($Service -eq 'redis') {
        docker exec 404notpure-perf-redis-1 redis-cli ping 2>$null | Out-Null
        $ready = $LASTEXITCODE -eq 0
    } else {
        try {
            $response = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/products/page?page=1&size=1' `
                -TimeoutSec 3
            $ready = $response.code -eq '200' -and $response.data.totalElements -eq 300
        } catch { $ready = $false }
    }
} until ($ready -or (Get-Date) -gt $deadline)
if (-not $ready) { throw "PERF $Service did not become ready within $TimeoutSeconds seconds" }
Write-Output "PERF $Service is ready"
