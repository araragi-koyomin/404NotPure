[CmdletBinding()]
param(
    [ValidateSet('1g', '1536m', '2g')]
    [string]$MemoryLimit = '2g',

    [ValidateRange(1.0, 8.0)]
    [double]$MinimumHostFreeGiB = 1.5,

    [switch]$SkipClean
)

$ErrorActionPreference = 'Stop'

$backendDirectory = Split-Path -Parent $PSScriptRoot
$repositoryDirectory = Split-Path -Parent $backendDirectory
$environmentFile = Join-Path $backendDirectory '.env'
$containerName = "tomatomall-test002-$($MemoryLimit.Replace('m', 'mb'))"
$mavenCacheVolume = '404notpure-maven-cache'
$networkName = '404notpure_tomato-network'
$imageName = 'maven:3.9.9-eclipse-temurin-17'

function Get-FreePhysicalMemoryGiB {
    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    return [double]$operatingSystem.FreePhysicalMemory / 1MB
}

function Convert-ToMiB([string]$memoryValue) {
    if ($memoryValue -notmatch '^([0-9.]+)(KiB|MiB|GiB)$') {
        return 0.0
    }

    $number = [double]$Matches[1]
    switch ($Matches[2]) {
        'KiB' { return $number / 1024 }
        'MiB' { return $number }
        'GiB' { return $number * 1024 }
    }
}

function Get-SurefireSummary {
    $reportDirectory = Join-Path $backendDirectory 'target/surefire-reports'
    $reportFiles = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $summary = [ordered]@{
        testClasses = $reportFiles.Count
        tests = 0
        failures = 0
        errors = 0
        skipped = 0
    }

    foreach ($reportFile in $reportFiles) {
        [xml]$report = Get-Content -LiteralPath $reportFile.FullName
        $summary.tests += [int]$report.testsuite.tests
        $summary.failures += [int]$report.testsuite.failures
        $summary.errors += [int]$report.testsuite.errors
        $summary.skipped += [int]$report.testsuite.skipped
    }

    return $summary
}

if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    throw 'back_end/.env is required; the script never prints its values'
}

$initialFreeGiB = Get-FreePhysicalMemoryGiB
if ($initialFreeGiB -lt $MinimumHostFreeGiB) {
    throw "Host free memory is below the safety floor: required=$MinimumHostFreeGiB GiB, actual=$([math]::Round($initialFreeGiB, 2)) GiB"
}

$existingContainer = docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}'
if ($existingContainer) {
    throw "Refusing to reuse or remove an existing container named $containerName"
}

$existingReportDirectory = Join-Path $backendDirectory 'target/surefire-reports'
if (Test-Path -LiteralPath $existingReportDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $existingReportDirectory -Filter 'TEST-*.xml' -File |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
}

$mavenGoals = if ($SkipClean) { @('test') } else { @('clean', 'test') }
$dockerArguments = @(
    'create', '--name', $containerName,
    '--memory', $MemoryLimit, '--memory-swap', $MemoryLimit, '--cpus', '2',
    '--network', $networkName,
    '--env-file', $environmentFile,
    '-e', 'DB_HOST=db', '-e', 'DB_PORT=3306',
    '-e', 'REDIS_HOST=redis', '-e', 'REDIS_PORT=6379',
    '-e', 'RUN_REAL_OSS_PROBE=false',
    '-e', 'RUN_REAL_OSS_PERMISSION_PROBE=false',
    '-e', 'RUN_REAL_ALIPAY_PROBE=false',
    '-e', 'ALIYUN_OSS_ENDPOINT=placeholder.invalid',
    '-e', 'ALIYUN_OSS_ACCESS_KEY_ID=placeholder',
    '-e', 'ALIYUN_OSS_ACCESS_KEY_SECRET=placeholder',
    '-e', 'ALIYUN_OSS_BUCKET_NAME=placeholder',
    '-e', 'ALIPAY_APP_ID=placeholder',
    '-e', 'ALIPAY_SELLER_ID=placeholder',
    '-e', 'ALIPAY_APP_PRIVATE_KEY=placeholder',
    '-e', 'ALIPAY_ALIPAY_PUBLIC_KEY=placeholder',
    '-e', 'ALIPAY_NOTIFY_URL=https://placeholder.invalid/notify',
    '-e', 'ALIPAY_SERVER_URL=https://placeholder.invalid/gateway',
    '-e', 'ALIPAY_RETURN_URL=https://placeholder.invalid/return',
    '-e', 'FRONTEND_URL=http://127.0.0.1:5173',
    '-e', 'JWT_SECRET=test002-placeholder-secret-at-least-32-characters',
    '-v', "${mavenCacheVolume}:/root/.m2",
    '-v', "${backendDirectory}:/workspace",
    '-w', '/workspace',
    $imageName,
    'mvn'
) + $mavenGoals

$created = $false
$safetyStop = $false
$sampledPeakMiB = 0.0
$minimumObservedHostFreeGiB = $initialFreeGiB
$maximumObservedJavaProcesses = 0

try {
    $containerId = docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to create the controlled Maven test container'
    }
    $created = $true

    docker start $containerId | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to start the controlled Maven test container'
    }
    while ((docker inspect --format '{{.State.Running}}' $containerId) -eq 'true') {
        $memoryUsage = (docker stats --no-stream --format '{{.MemUsage}}' $containerId).Split('/')[0].Trim()
        $sampledMiB = Convert-ToMiB $memoryUsage
        if ($sampledMiB -gt $sampledPeakMiB) {
            $sampledPeakMiB = $sampledMiB
        }

        $containerProcesses = @(docker top $containerId -eo pid,comm)
        if ($LASTEXITCODE -ne 0) {
            if ((docker inspect --format '{{.State.Running}}' $containerId) -eq 'true') {
                throw 'Unable to observe Java process isolation inside the running test container'
            }
            continue
        }
        $javaProcessCount = @($containerProcesses | Where-Object { $_ -match '^\s*\d+\s+java\s*$' }).Count
        if ($javaProcessCount -gt $maximumObservedJavaProcesses) {
            $maximumObservedJavaProcesses = $javaProcessCount
        }

        $currentFreeGiB = Get-FreePhysicalMemoryGiB
        if ($currentFreeGiB -lt $minimumObservedHostFreeGiB) {
            $minimumObservedHostFreeGiB = $currentFreeGiB
        }
        if ($currentFreeGiB -lt $MinimumHostFreeGiB) {
            $safetyStop = $true
            docker stop --time 10 $containerId | Out-Null
            break
        }

        Start-Sleep -Seconds 2
    }

    $exitCode = [int](docker inspect --format '{{.State.ExitCode}}' $containerId)
    $oomKilled = [bool]::Parse((docker inspect --format '{{.State.OOMKilled}}' $containerId))
    $summary = Get-SurefireSummary

    [pscustomobject]@{
        memoryLimit = $MemoryLimit
        mavenGoals = ($mavenGoals -join ' ')
        exitCode = $exitCode
        oomKilled = $oomKilled
        safetyStop = $safetyStop
        initialHostFreeGiB = [math]::Round($initialFreeGiB, 2)
        minimumObservedHostFreeGiB = [math]::Round($minimumObservedHostFreeGiB, 2)
        sampledContainerPeakMiB = [math]::Round($sampledPeakMiB, 1)
        maximumObservedJavaProcesses = $maximumObservedJavaProcesses
        testClasses = $summary.testClasses
        tests = $summary.tests
        failures = $summary.failures
        errors = $summary.errors
        skipped = $summary.skipped
    } | ConvertTo-Json

    $missingFreshReports = $summary.testClasses -eq 0 -or $summary.tests -eq 0
    $missingForkEvidence = $maximumObservedJavaProcesses -lt 2
    if ($exitCode -ne 0 -or $oomKilled -or $safetyStop -or $missingFreshReports -or $missingForkEvidence) {
        Write-Host 'Last Maven output lines:'
        docker logs --tail 40 $containerId
        exit 1
    }
}
finally {
    if ($created) {
        docker rm --force $containerName | Out-Null
    }
}
