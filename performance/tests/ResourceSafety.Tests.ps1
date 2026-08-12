$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../scripts/ResourceSafety.ps1"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$safe = Get-PerfSafetyDecision -AverageCpuPercent 20 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 10 -DockerHealthy $true -FrontendStopped $true
Assert-True $safe.Allowed 'A safe snapshot should allow execution'

$highCpu = Get-PerfSafetyDecision -AverageCpuPercent 35 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 10 -DockerHealthy $true -FrontendStopped $true
Assert-True (-not $highCpu.Allowed) 'CPU at the boundary must reject execution'

$lowMemory = Get-PerfSafetyDecision -AverageCpuPercent 20 -FreeMemoryGiB 3.99 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 10 -DockerHealthy $true -FrontendStopped $true
Assert-True (-not $lowMemory.Allowed) 'Low memory must reject execution'

$frontendRunning = Get-PerfSafetyDecision -AverageCpuPercent 20 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 10 -DockerHealthy $true -FrontendStopped $false
Assert-True (-not $frontendRunning.Allowed) 'A running regular frontend must reject execution'

$dockerDown = Get-PerfSafetyDecision -AverageCpuPercent 20 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 10 -DockerHealthy $false -FrontendStopped $true
Assert-True (-not $dockerDown.Allowed) 'Unavailable Docker must reject execution'

$watchdog = [pscustomobject]@{ HighCpuSamples = 0; LowMemorySamples = 0 }
for ($index = 0; $index -lt 14; $index++) {
    $watchdog = Update-PerfWatchdogState -CpuPercent 90 -FreeMemoryGiB 10 `
        -HighCpuSamples $watchdog.HighCpuSamples -LowMemorySamples $watchdog.LowMemorySamples
}
Assert-True (-not $watchdog.MustStop) 'High CPU must not stop before 30 seconds'
$watchdog = Update-PerfWatchdogState -CpuPercent 90 -FreeMemoryGiB 10 `
    -HighCpuSamples $watchdog.HighCpuSamples -LowMemorySamples $watchdog.LowMemorySamples
Assert-True $watchdog.MustStop 'High CPU must stop at 30 seconds'

$watchdog = [pscustomobject]@{ HighCpuSamples = 0; LowMemorySamples = 0 }
for ($index = 0; $index -lt 5; $index++) {
    $watchdog = Update-PerfWatchdogState -CpuPercent 20 -FreeMemoryGiB 3.5 `
        -HighCpuSamples $watchdog.HighCpuSamples -LowMemorySamples $watchdog.LowMemorySamples
}
Assert-True $watchdog.MustStop 'Low memory must stop at 10 seconds'

$watchdog = Update-PerfWatchdogState -CpuPercent 20 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 14.99 -EDriveFreeGiB 10 -HighCpuSamples 0 -LowMemorySamples 0
Assert-True $watchdog.MustStop 'C drive below the safety line must stop immediately'

$watchdog = Update-PerfWatchdogState -CpuPercent 20 -FreeMemoryGiB 10 `
    -CDriveFreeGiB 20 -EDriveFreeGiB 4.99 -HighCpuSamples 0 -LowMemorySamples 0
Assert-True $watchdog.MustStop 'E drive below the safety line must stop immediately'

Write-Output 'ResourceSafety.Tests.ps1: 9/9 passed'
