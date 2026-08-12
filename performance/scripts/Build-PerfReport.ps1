param(
    [string]$ResultsDirectory = "$PSScriptRoot/../results",
    [string]$OutputDirectory = "$PSScriptRoot/../results/report",
    [string]$NamePattern = '*',
    [ValidateRange(1,10)][int]$ExpectedRepeats = 3,
    [string[]]$ExpectedRepeatedScenarios = @()
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function MetricValue($result, [string]$metric, [string]$field) {
    $metricProperty = $result.metrics.PSObject.Properties[$metric]
    if (-not $metricProperty) { return 0 }
    $fieldProperty = $metricProperty.Value.values.PSObject.Properties[$field]
    if (-not $fieldProperty) { return 0 }
    return [double]$fieldProperty.Value
}

function Read-JsonUtf8([string]$Path) {
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    return $text | ConvertFrom-Json
}

function Median([double[]]$Values) {
    if (-not $Values -or $Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $middle = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2
}

function Test-K6ThresholdsPassed($Result) {
    foreach ($metric in $Result.metrics.PSObject.Properties.Value) {
        $thresholdsProperty = $metric.PSObject.Properties['thresholds']
        if (-not $thresholdsProperty) { continue }
        foreach ($threshold in $thresholdsProperty.Value.PSObject.Properties.Value) {
            if (-not $threshold.ok) { return $false }
        }
    }
    return $true
}

$rows = @()
$files = Get-ChildItem -LiteralPath $ResultsDirectory -Filter '*.json' -File | Sort-Object Name
foreach ($file in $files) {
    $result = Read-JsonUtf8 $file.FullName
    $name = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    if ($name -notlike $NamePattern) { continue }
    $metricsProperty = $result.PSObject.Properties['metrics']
    if (-not $metricsProperty -or -not $metricsProperty.Value.PSObject.Properties['http_reqs']) {
        continue
    }
    $beforePath = Join-Path $ResultsDirectory "raw/$name-before.json"
    $afterPath = Join-Path $ResultsDirectory "raw/$name-after.json"
    $runtimePath = Join-Path $ResultsDirectory "raw/$name-runtime.json"
    $before = if (Test-Path $beforePath) { Read-JsonUtf8 $beforePath } else { $null }
    $after = if (Test-Path $afterPath) { Read-JsonUtf8 $afterPath } else { $null }
    $runtime = if (Test-Path $runtimePath) { Read-JsonUtf8 $runtimePath } else { $null }
    $rows += [pscustomobject]@{
        name = $name
        requests = [long](MetricValue $result 'business_requests' 'count')
        completedQps = [math]::Round((MetricValue $result 'business_requests' 'rate'), 3)
        p50Ms = [math]::Round((MetricValue $result 'business_duration' 'med'), 3)
        p90Ms = [math]::Round((MetricValue $result 'business_duration' 'p(90)'), 3)
        p95Ms = [math]::Round((MetricValue $result 'business_duration' 'p(95)'), 3)
        p99Ms = [math]::Round((MetricValue $result 'business_duration' 'p(99)'), 3)
        maxMs = [math]::Round((MetricValue $result 'business_duration' 'max'), 3)
        businessFailureRate = MetricValue $result 'business_failures' 'rate'
        transportFailureRate = MetricValue $result 'http_req_failed' 'rate'
        checkSuccessRate = MetricValue $result 'checks' 'rate'
        droppedIterations = [long](MetricValue $result 'dropped_iterations' 'count')
        redisHitDelta = if ($before -and $after -and $before.redis.available -and $after.redis.available -and $after.redis.keyspaceHits -ge $before.redis.keyspaceHits) { $after.redis.keyspaceHits - $before.redis.keyspaceHits } else { $null }
        redisMissDelta = if ($before -and $after -and $before.redis.available -and $after.redis.available -and $after.redis.keyspaceMisses -ge $before.redis.keyspaceMisses) { $after.redis.keyspaceMisses - $before.redis.keyspaceMisses } else { $null }
        mysqlQueryDelta = if ($before -and $after) { [long]$after.mysql.Queries - [long]$before.mysql.Queries } else { $null }
        mysqlSelectDelta = if ($before -and $after) { [long]$after.mysql.Com_select - [long]$before.mysql.Com_select } else { $null }
        mysqlRowsReadDelta = if ($before -and $after) { [long]$after.mysql.Innodb_rows_read - [long]$before.mysql.Innodb_rows_read } else { $null }
        peakHostCpuPercent = if ($runtime) { $runtime.peakHostCpuPercent } else { $null }
        minimumFreeMemoryGiB = if ($runtime) { $runtime.minimumFreeMemoryGiB } else { $null }
        hardThresholdsPassed = Test-K6ThresholdsPassed $result
    }
}
$csvPath = Join-Path $OutputDirectory 'summary.csv'
$rows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $csvPath

$repeatRows = @($rows | Where-Object { $_.name -match '^(.*)-run[1-9][0-9]*$' })
$medianRows = @()
$repeatGroups = @($repeatRows | Group-Object { [regex]::Match($_.name, '^(.*)-run[1-9][0-9]*$').Groups[1].Value })
if ($ExpectedRepeatedScenarios.Count -gt 0) {
    $actualGroupNames = @($repeatGroups.Name | Sort-Object)
    $expectedGroupNames = @($ExpectedRepeatedScenarios | Sort-Object)
    $groupDifference = @(Compare-Object -ReferenceObject $expectedGroupNames -DifferenceObject $actualGroupNames)
    if ($groupDifference.Count -gt 0) {
        throw "Repeated scenario groups do not match the expected list: $($groupDifference.InputObject -join ', ')"
    }
}
foreach ($group in $repeatGroups) {
    $actualRunNumbers = @($group.Group | ForEach-Object {
        [int][regex]::Match($_.name, '-run([1-9][0-9]*)$').Groups[1].Value
    } | Sort-Object)
    $expectedRunNumbers = @(1..$ExpectedRepeats)
    if ($group.Count -ne $ExpectedRepeats -or
        (Compare-Object -ReferenceObject $expectedRunNumbers -DifferenceObject $actualRunNumbers)) {
        throw "Scenario '$($group.Name)' must contain exactly run1 through run$ExpectedRepeats; found: $($actualRunNumbers -join ', ')"
    }
    $failedRuns = @($group.Group | Where-Object { -not $_.hardThresholdsPassed })
    if ($failedRuns.Count -gt 0) {
        throw "Scenario '$($group.Name)' contains failed hard thresholds: $($failedRuns.name -join ', ')"
    }
    $medianRows += [pscustomobject]@{
        scenario = $group.Name
        repeats = $group.Count
        completedQpsMedian = [math]::Round((Median @($group.Group.completedQps)), 3)
        p50MsMedian = [math]::Round((Median @($group.Group.p50Ms)), 3)
        p95MsMedian = [math]::Round((Median @($group.Group.p95Ms)), 3)
        p99MsMedian = [math]::Round((Median @($group.Group.p99Ms)), 3)
        maxMsMedian = [math]::Round((Median @($group.Group.maxMs)), 3)
        failureRateMedian = Median @($group.Group.businessFailureRate)
        redisHitDeltaMedian = Median @($group.Group | Where-Object { $null -ne $_.redisHitDelta } | ForEach-Object { [double]$_.redisHitDelta })
        mysqlQueryDeltaMedian = Median @($group.Group | Where-Object { $null -ne $_.mysqlQueryDelta } | ForEach-Object { [double]$_.mysqlQueryDelta })
        mysqlSelectDeltaMedian = Median @($group.Group | Where-Object { $null -ne $_.mysqlSelectDelta } | ForEach-Object { [double]$_.mysqlSelectDelta })
        peakHostCpuPercentMaximum = [math]::Round(($group.Group | Measure-Object peakHostCpuPercent -Maximum).Maximum, 2)
        minimumFreeMemoryGiB = [math]::Round(($group.Group | Measure-Object minimumFreeMemoryGiB -Minimum).Minimum, 2)
    }
}
$medianRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'medians.csv')

$width = 1000; $height = 420; $margin = 60
$maxP99 = [math]::Max(1, ($rows | Measure-Object p99Ms -Maximum).Maximum)
$barWidth = if ($rows.Count) { [math]::Max(8, [math]::Floor(($width - 2*$margin) / ($rows.Count*2))) } else { 8 }
$svg = [System.Text.StringBuilder]::new()
[void]$svg.AppendLine("<svg xmlns='http://www.w3.org/2000/svg' width='$width' height='$height' viewBox='0 0 $width $height'>")
[void]$svg.AppendLine("<rect width='100%' height='100%' fill='#ffffff'/><text x='20' y='30' font-family='sans-serif' font-size='20'>PERF-001 P99 latency (ms)</text>")
for ($index=0; $index -lt $rows.Count; $index++) {
    $x = $margin + $index * (($width - 2*$margin) / [math]::Max(1,$rows.Count))
    $barHeight = ($rows[$index].p99Ms / $maxP99) * ($height - 2*$margin)
    $y = $height - $margin - $barHeight
    [void]$svg.AppendLine("<rect x='$x' y='$y' width='$barWidth' height='$barHeight' fill='#2563eb'/>")
    [void]$svg.AppendLine("<text x='$x' y='$($height-35)' font-family='sans-serif' font-size='10' transform='rotate(30 $x $($height-35))'>$([System.Security.SecurityElement]::Escape($rows[$index].name))</text>")
}
[void]$svg.AppendLine('</svg>')
$svg.ToString() | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'p99-latency.svg')

$markdown = @('# PERF-001 generated summary','',"Generated: $(Get-Date -Format o)",'',
    '| Scenario | QPS | P50 ms | P95 ms | P99 ms | Business errors | Dropped | Hard thresholds | Redis hit delta | MySQL SELECT delta |',
    '|---|---:|---:|---:|---:|---:|---:|---|---:|---:|')
foreach ($row in $rows) {
    $markdown += "| $($row.name) | $($row.completedQps) | $($row.p50Ms) | $($row.p95Ms) | $($row.p99Ms) | $($row.businessFailureRate) | $($row.droppedIterations) | $($row.hardThresholdsPassed) | $($row.redisHitDelta) | $($row.mysqlSelectDelta) |"
}
$markdown += @('', "## $ExpectedRepeats-run medians", '', '| Scenario | QPS median | P50 ms | P95 ms | P99 ms | Error rate | MySQL SELECT delta |', '|---|---:|---:|---:|---:|---:|---:|')
foreach ($row in $medianRows) {
    $markdown += "| $($row.scenario) | $($row.completedQpsMedian) | $($row.p50MsMedian) | $($row.p95MsMedian) | $($row.p99MsMedian) | $($row.failureRateMedian) | $($row.mysqlSelectDeltaMedian) |"
}
$markdown | Set-Content -Encoding utf8 -LiteralPath (Join-Path $OutputDirectory 'summary.md')
Write-Output "Generated $($rows.Count) scenario rows in $OutputDirectory"
