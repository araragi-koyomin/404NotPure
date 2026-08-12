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
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'PerformanceScripts.Tests.ps1: 6/6 passed'
