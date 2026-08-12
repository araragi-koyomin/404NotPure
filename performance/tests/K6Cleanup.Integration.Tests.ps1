$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../scripts/ResourceSafety.ps1"

$coreContainers = @(
    '404notpure-perf-backend-1',
    '404notpure-perf-db-1',
    '404notpure-perf-redis-1'
)
$restartCounts = @{}
foreach ($container in $coreContainers) {
    $restartCounts[$container] = [int](docker inspect $container --format '{{.RestartCount}}')
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect $container before cleanup test" }
}

$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$started = $false
$probeContainerId = $null
try {
    $probeContainerId = (docker compose --env-file back_end/.env -f docker-compose.perf.yml -p 404notpure-perf `
        --profile tools run -d --rm --no-deps --entrypoint sleep k6 60).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the no-traffic k6 cleanup probe' }
    $started = $true

    $runningBefore = @(docker ps --filter 'label=com.docker.compose.project=404notpure-perf' `
        --filter 'label=com.docker.compose.service=k6' -q)
    if ($runningBefore.Count -ne 1) { throw "Expected one k6 probe before cleanup, found $($runningBefore.Count)" }

    try {
        throw 'simulated watchdog failure'
    } catch {
        if ($_.Exception.Message -ne 'simulated watchdog failure') { throw }
    } finally {
        Stop-PerfK6Containers
    }

    $remainingAfter = @(docker ps -a --filter 'label=com.docker.compose.project=404notpure-perf' `
        --filter 'label=com.docker.compose.service=k6' -q)
    if ($remainingAfter.Count -ne 0) { throw 'The k6 cleanup probe still exists in running or stopped state' }

    foreach ($container in $coreContainers) {
        $running = docker inspect $container --format '{{.State.Running}}'
        $restartCount = [int](docker inspect $container --format '{{.RestartCount}}')
        if ($running.Trim() -ne 'true') { throw "$container was stopped by k6 cleanup" }
        if ($restartCount -ne $restartCounts[$container]) { throw "$container restarted during k6 cleanup" }
    }
} finally {
    if ($started) { Stop-PerfK6Containers }
    if ($probeContainerId) {
        $matchingProbe = docker ps -a --filter "id=$probeContainerId" `
            --filter 'label=com.docker.compose.project=404notpure-perf' `
            --filter 'label=com.docker.compose.service=k6' -q
        if ($matchingProbe) { docker rm -f $probeContainerId | Out-Null }
    }
}

Write-Output 'K6Cleanup.Integration.Tests.ps1: 1/1 passed'
