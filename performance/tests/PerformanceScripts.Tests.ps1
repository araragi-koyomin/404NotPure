$ErrorActionPreference = 'Stop'

function Assert-Throws([scriptblock]$Action, [string]$ExpectedText) {
    try {
        & $Action
    } catch {
        if ($_.Exception.Message -notlike "*$ExpectedText*") {
            throw "Expected error containing '$ExpectedText', received: $($_.Exception.Message)"
        }
        return
    }
    throw "Expected an error containing '$ExpectedText'"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("perf001-script-tests-" + [guid]::NewGuid().ToString('N'))
$results = Join-Path $tempRoot 'results'
$report = Join-Path $tempRoot 'report'
New-Item -ItemType Directory -Force -Path $results | Out-Null

try {
    $resultJson = @'
{
  "metrics": {
    "http_reqs": { "values": { "count": 1, "rate": 1 } },
    "business_requests": { "values": { "count": 1, "rate": 1 } },
    "business_duration": { "values": { "med": 1, "p(90)": 1, "p(95)": 1, "p(99)": 1, "max": 1 } },
    "business_failures": { "values": { "rate": 0 }, "thresholds": { "rate==0": { "ok": true } } },
    "http_req_failed": { "values": { "rate": 0 }, "thresholds": { "rate==0": { "ok": true } } },
    "checks": { "values": { "rate": 1 }, "thresholds": { "rate==1": { "ok": true } } },
    "dropped_iterations": { "values": { "count": 0 }, "thresholds": { "count==0": { "ok": true } } }
  }
}
'@
    [System.IO.File]::WriteAllText((Join-Path $results 'case-run1.json'), $resultJson, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText((Join-Path $results 'case-run2.json'), $resultJson, [System.Text.Encoding]::UTF8)
    Assert-Throws {
        & "$PSScriptRoot/../scripts/Build-PerfReport.ps1" -ResultsDirectory $results `
            -OutputDirectory $report -NamePattern 'case-*' -ExpectedRepeats 3 `
            -ExpectedRepeatedScenarios @('case')
    } 'must contain exactly run1 through run3'

    [System.IO.File]::WriteAllText((Join-Path $results 'case-run3.json'), $resultJson, [System.Text.Encoding]::UTF8)
    & "$PSScriptRoot/../scripts/Build-PerfReport.ps1" -ResultsDirectory $results `
        -OutputDirectory $report -NamePattern 'case-*' -ExpectedRepeats 3 `
        -ExpectedRepeatedScenarios @('case') | Out-Null
    if (-not (Test-Path -LiteralPath (Join-Path $report 'medians.csv'))) {
        throw 'A complete three-run group should generate medians.csv'
    }

    $existingPrefix = 'perf001-prefix-test-' + [guid]::NewGuid().ToString('N')
    $existingOutput = Join-Path (Resolve-Path "$PSScriptRoot/../results").Path "$existingPrefix-placeholder.json"
    [System.IO.File]::WriteAllText($existingOutput, '{}', [System.Text.Encoding]::UTF8)
    try {
        Assert-Throws {
            & "$PSScriptRoot/../scripts/Invoke-FormalPerfSuite.ps1" -Prefix $existingPrefix
        } 'already has result files'
    } finally {
        Remove-Item -LiteralPath $existingOutput -Force -ErrorAction SilentlyContinue
    }

    $partialResultName = 'perf001-partial-test-' + [guid]::NewGuid().ToString('N')
    $partialSnapshot = Join-Path (Resolve-Path "$PSScriptRoot/../results/raw").Path "$partialResultName-before.json"
    [System.IO.File]::WriteAllText($partialSnapshot, '{}', [System.Text.Encoding]::UTF8)
    try {
        Assert-Throws {
            & "$PSScriptRoot/../scripts/Run-PerfScenario.ps1" -Scenario hot -Rate 1 `
                -Duration '1s' -ResultName $partialResultName
        } 'already has output files'
    } finally {
        Remove-Item -LiteralPath $partialSnapshot -Force -ErrorAction SilentlyContinue
    }

    $detailScript = Get-Content -Raw -LiteralPath "$PSScriptRoot/../k6/detail.js"
    if ($detailScript -notmatch 'ALLOW_PROTECTIVE_503' `
            -or $detailScript -notmatch "p\(99\)<=1000" `
            -or $detailScript -notmatch 'max<=2000' `
            -or $detailScript -notmatch 'protective_503_responses') {
        throw 'Redis outage k6 scenario must accept only the explicit 503 contract and enforce latency limits'
    }
    $outageScript = Get-Content -Raw -LiteralPath "$PSScriptRoot/../scripts/Invoke-RedisOutageScenario.ps1"
    if ($outageScript -notmatch 'databaseUpdateObserved' `
            -or $outageScript -notmatch 'nonProductKeyPreserved' `
            -or $outageScript -notmatch 'cache003:recovery-proof') {
        throw 'Redis outage recovery must verify a database update and preserve a non-product key'
    }
    $restoreMarkerPosition = $outageScript.IndexOf('$productRestoreRequired = $true')
    $updateRequestPosition = $outageScript.IndexOf('$updated = Invoke-RestMethod')
    if ($restoreMarkerPosition -lt 0 -or $updateRequestPosition -lt 0 `
            -or $restoreMarkerPosition -gt $updateRequestPosition `
            -or $outageScript -notmatch 'finally\s*\{[\s\S]*finally\s*\{[\s\S]*DEL \$nonProductKey') {
        throw 'Redis outage cleanup must schedule product restoration before the update request and always delete the proof key'
    }
    if ($outageScript -notmatch '\$recoveryProbe\.code -ne ''200''' `
            -or $outageScript -notmatch '\$recoveryProbe\.data\.id -ne \$productId' `
            -or $outageScript -match 'A bounded 503 response is allowed until') {
        throw 'Every Redis recovery polling request must return the correct product; 503, 500 and network errors cannot be ignored'
    }

    $snapshotScript = Get-Content -Raw -LiteralPath "$PSScriptRoot/../scripts/Collect-PerfSnapshot.ps1"
    $requiredSingleFlightMetrics = @(
        'tomatomall.cache.product.singleflight.leader',
        'tomatomall.cache.product.singleflight.follower',
        'tomatomall.cache.product.singleflight.wait.success',
        'tomatomall.cache.product.singleflight.wait.timeout',
        'tomatomall.cache.product.singleflight.wait.interrupted',
        'tomatomall.cache.product.singleflight.leader.failures',
        'tomatomall.cache.product.singleflight.active',
        'tomatomall.cache.product.singleflight.waiters.active',
        'tomatomall.cache.product.singleflight.wait.duration'
        'tomatomall.cache.product.singleflight.enabled'
    )
    foreach ($metric in $requiredSingleFlightMetrics) {
        if ($snapshotScript -notmatch [regex]::Escape($metric)) {
            throw "Performance snapshots must collect CACHE-002 metric $metric"
        }
    }

    $burstScript = Get-Content -Raw -LiteralPath "$PSScriptRoot/../scripts/Invoke-HotspotBurst.ps1"
    foreach ($requiredText in @(
        'singleFlightLeaderDelta',
        'singleFlightTimeoutDelta',
        'singleFlightFailureDelta',
        'singleFlightActiveAfter',
        'singleFlightWaitersAfter',
        'mysqlSelectDelta',
        'mysqlRowLockWaitDelta'
        'singleFlightEffectiveBefore'
        'singleFlightFollowerDelta'
        'maxHikariPending'
    )) {
        if ($burstScript -notmatch $requiredText) {
            throw "Hotspot burst acceptance must validate $requiredText"
        }
    }

    $cache002Acceptance = Get-Content -Raw -LiteralPath `
        "$PSScriptRoot/../scripts/Invoke-Cache002Acceptance.ps1"
    foreach ($requiredText in @(
        "Invoke-Mode 'disabled'",
        "Invoke-Mode 'enabled'",
        'p95MedianMs',
        'p99MedianMs',
        'Invoke-PerfPreflight -Seconds 30'
    )) {
        if ($cache002Acceptance -notmatch [regex]::Escape($requiredText)) {
            throw "CACHE-002 acceptance script must contain $requiredText"
        }
    }
    if ($cache002Acceptance -notmatch '\[ValidateSet\(100\)\]\[int\]\$VirtualUsers = 100') {
        throw 'CACHE-002 formal acceptance must require exactly 100 virtual users'
    }
    $burstK6 = Get-Content -Raw -LiteralPath "$PSScriptRoot/../k6/burst.js"
    if ($burstK6 -notmatch "executor: 'per-vu-iterations'" `
            -or $burstK6 -notmatch 'iterations: 1' `
            -or $burstK6 -notmatch 'participating_vus' `
            -or $burstK6 -notmatch 'startAt') {
        throw 'CACHE-002 burst must run exactly one synchronized request per virtual user'
    }
    $singleFlightModeScript = Get-Content -Raw -LiteralPath `
        "$PSScriptRoot/../scripts/Set-PerfSingleFlightMode.ps1"
    if ($singleFlightModeScript -notmatch '--build --force-recreate backend') {
        throw 'CACHE-002 formal mode switch must rebuild the backend from current source'
    }
    $cacheModeScript = Get-Content -Raw -LiteralPath `
        "$PSScriptRoot/../scripts/Set-PerfCacheMode.ps1"
    foreach ($modeScript in @($cacheModeScript, $singleFlightModeScript)) {
        if ($modeScript -notmatch '--profile tools' -or $modeScript -notmatch 'up -d metrics') {
            throw 'Performance mode switches must start the metrics helper before probing Actuator'
        }
    }
    $runScript = Get-Content -Raw -LiteralPath "$PSScriptRoot/../scripts/Run-PerfScenario.ps1"
    if ($runScript -notmatch 'hikaricp\.connections\.pending' `
            -or $runScript -notmatch 'maxHikariPending' `
            -or $runScript -notmatch 'Start-Sleep -Milliseconds 100') {
        throw 'CACHE-002 hotspot run must sample Hikari pending connections throughout the burst'
    }
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'PerformanceScripts.Tests.ps1: 9/9 passed'
