param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\..\docker-compose.yml'),
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    throw "Compose file was not found: $ComposeFile"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Compose environment file was not found: $EnvFile"
}

$rendered = & docker compose `
    --env-file $EnvFile `
    -f $ComposeFile `
    config `
    --format json 2>&1
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose configuration rendering failed; output suppressed to avoid exposing environment values.'
}

try {
    $configuration = $rendered | ConvertFrom-Json
} catch {
    throw 'Docker Compose returned invalid JSON; raw output suppressed.'
}

$backendEnvironment = $configuration.services.backend.environment
if ($backendEnvironment.DB_HOST -ne 'db') {
    throw 'Compose backend DB_HOST must resolve to the database service name.'
}
if ([string]$backendEnvironment.DB_PORT -ne '3306') {
    throw 'Compose backend DB_PORT must resolve to the container port 3306.'
}

$databasePort = $configuration.services.db.ports | Select-Object -First 1
if ([string]$databasePort.target -ne '3306' -or [string]$databasePort.published -ne '3307') {
    throw 'Compose database port mapping must publish host 3307 to container 3306.'
}

$redisPorts = @($configuration.services.redis.ports)
if ($redisPorts.Count -ne 1) {
    throw 'Compose Redis must have exactly one host port mapping.'
}
$redisPort = $redisPorts[0]
if ([string]$redisPort.target -ne '6379' -or [string]$redisPort.published -ne '6379') {
    throw 'Compose Redis port mapping must publish host 6379 to container 6379.'
}
if ([string]$redisPort.host_ip -ne '127.0.0.1') {
    throw 'Compose Redis port must bind only to 127.0.0.1.'
}

Write-Output 'COMPOSE_CONFIG=PASS'
Write-Output 'BACKEND_DB_ENDPOINT=db:3306'
Write-Output 'HOST_DB_ENDPOINT=127.0.0.1:3307'
Write-Output 'HOST_REDIS_ENDPOINT=127.0.0.1:6379'
