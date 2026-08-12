$ErrorActionPreference = 'Stop'
$deleted = docker exec 404notpure-perf-redis-1 sh -c `
    'redis-cli --scan --pattern "product:detail:v1:*" | xargs -r redis-cli DEL'
if ($LASTEXITCODE -ne 0) { throw 'Could not clear isolated PERF product cache keys' }
Write-Output "Deleted isolated PERF product cache keys: $deleted"
