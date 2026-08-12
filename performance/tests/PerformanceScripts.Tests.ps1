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
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'PerformanceScripts.Tests.ps1: 4/4 passed'
