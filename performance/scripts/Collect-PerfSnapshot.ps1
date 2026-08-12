param(
    [Parameter(Mandatory)][string]$Name,
    [string]$OutputDirectory = "$PSScriptRoot/../results/raw",
    [switch]$AllowRedisUnavailable
)
$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^[a-zA-Z0-9._-]+$') { throw 'Name contains unsupported characters' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Read-ActuatorMetric([string]$MetricName) {
    $raw = $null
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $raw = docker exec 404notpure-perf-metrics-1 curl -fsS `
            "http://backend:9090/actuator/metrics/$MetricName" 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($LASTEXITCODE -ne 0) { throw "Could not read Actuator metric $MetricName after 10 attempts" }
    $metric = $raw | ConvertFrom-Json
    [pscustomobject]@{
        name = $MetricName
        measurements = @($metric.measurements)
        availableTags = @($metric.availableTags)
    }
}

function Read-LongOrZero([hashtable]$Values, [string]$Key) {
    if (-not $Values.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace([string]$Values[$Key])) {
        return [long]0
    }
    return [long]$Values[$Key]
}

$redis = @{}
$redisAvailable = $false
$redisRunning = docker inspect 404notpure-perf-redis-1 --format '{{.State.Running}}' 2>$null
if ($LASTEXITCODE -eq 0 -and $redisRunning.Trim() -eq 'true') {
    $redisInfo = docker exec 404notpure-perf-redis-1 redis-cli INFO all 2>$null
} else {
    $redisInfo = $null
}
if ($redisInfo -and $LASTEXITCODE -eq 0) {
    $redisAvailable = $true
    foreach ($line in $redisInfo) {
        if ($line -match '^([^#][^:]+):(.+)$') { $redis[$matches[1]] = $matches[2].Trim() }
    }
} elseif (-not $AllowRedisUnavailable) {
    throw 'Could not read Redis INFO'
}

$mysqlRaw = docker exec 404notpure-perf-db-1 sh -c `
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -uroot extended-status'
if ($LASTEXITCODE -ne 0) { throw 'Could not read MySQL extended status' }
$mysqlWanted = @('Queries','Questions','Threads_connected','Threads_running',
    'Slow_queries','Innodb_row_lock_waits','Innodb_row_lock_time','Connections',
    'Com_select','Innodb_rows_read')
$mysql = @{}
foreach ($line in $mysqlRaw) {
    if ($line -match '^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$') {
        $key = $matches[1].Trim()
        if ($mysqlWanted -contains $key) { $mysql[$key] = $matches[2].Trim() }
    }
}

$containerStats = @()
$statsRaw = docker stats --no-stream --format '{{json .}}' `
    404notpure-perf-backend-1 404notpure-perf-db-1 404notpure-perf-redis-1
foreach ($line in $statsRaw) { if ($line) { $containerStats += ($line | ConvertFrom-Json) } }

$operatingSystem = Get-CimInstance Win32_OperatingSystem
$snapshot = [ordered]@{
    name = $Name
    capturedAt = (Get-Date).ToString('o')
    host = [ordered]@{
        freeMemoryGiB = [math]::Round($operatingSystem.FreePhysicalMemory / 1MB, 3)
    }
    redis = [ordered]@{
        available = $redisAvailable
        keyspaceHits = Read-LongOrZero $redis 'keyspace_hits'
        keyspaceMisses = Read-LongOrZero $redis 'keyspace_misses'
        expiredKeys = Read-LongOrZero $redis 'expired_keys'
        evictedKeys = Read-LongOrZero $redis 'evicted_keys'
        connectedClients = Read-LongOrZero $redis 'connected_clients'
        usedMemory = Read-LongOrZero $redis 'used_memory'
        totalCommandsProcessed = Read-LongOrZero $redis 'total_commands_processed'
    }
    mysql = $mysql
    actuator = @(
        Read-ActuatorMetric 'http.server.requests'
        Read-ActuatorMetric 'hikaricp.connections.active'
        Read-ActuatorMetric 'hikaricp.connections.pending'
        Read-ActuatorMetric 'hikaricp.connections.timeout'
        Read-ActuatorMetric 'jvm.memory.used'
        Read-ActuatorMetric 'jvm.gc.pause'
        Read-ActuatorMetric 'process.cpu.usage'
    )
    containers = $containerStats
}
$path = Join-Path $OutputDirectory "$Name.json"
$snapshot | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -LiteralPath $path
Write-Output $path
