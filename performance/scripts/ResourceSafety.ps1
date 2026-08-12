Set-StrictMode -Version Latest

function Stop-PerfK6Containers {
    $runningK6Containers = @(docker ps --filter 'label=com.docker.compose.project=404notpure-perf' `
        --filter 'label=com.docker.compose.service=k6' -q 2>$null)
    foreach ($container in $runningK6Containers) {
        docker stop --timeout 5 $container 2>$null | Out-Null
    }
    $allK6Containers = @(docker ps -a --filter 'label=com.docker.compose.project=404notpure-perf' `
        --filter 'label=com.docker.compose.service=k6' -q 2>$null)
    foreach ($container in $allK6Containers) {
        docker rm -f $container 2>$null | Out-Null
    }
}

function Get-PerfSafetyDecision {
    param(
        [Parameter(Mandatory)][double]$AverageCpuPercent,
        [Parameter(Mandatory)][double]$FreeMemoryGiB,
        [Parameter(Mandatory)][double]$CDriveFreeGiB,
        [Parameter(Mandatory)][double]$EDriveFreeGiB,
        [Parameter(Mandatory)][bool]$DockerHealthy,
        [Parameter(Mandatory)][bool]$FrontendStopped
    )

    $reasons = [System.Collections.Generic.List[string]]::new()
    if ($AverageCpuPercent -ge 35) { $reasons.Add("30-second average host CPU must be below 35%; actual=$AverageCpuPercent%") }
    if ($FreeMemoryGiB -lt 8) { $reasons.Add("Free physical memory must be at least 8 GiB; actual=$FreeMemoryGiB GiB") }
    if ($CDriveFreeGiB -lt 15) { $reasons.Add("C drive free space must be at least 15 GiB; actual=$CDriveFreeGiB GiB") }
    if ($EDriveFreeGiB -lt 5) { $reasons.Add("E drive free space must be at least 5 GiB; actual=$EDriveFreeGiB GiB") }
    if (-not $DockerHealthy) { $reasons.Add('Docker is unavailable') }
    if (-not $FrontendStopped) { $reasons.Add('The regular frontend container is still running') }

    [pscustomobject]@{
        Allowed = $reasons.Count -eq 0
        Reasons = @($reasons)
    }
}

function Get-HostResourceSnapshot {
    $processor = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'"
    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    $cDrive = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
    $eDrive = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='E:'"
    [pscustomobject]@{
        CpuPercent = [double]$processor.PercentProcessorTime
        FreeMemoryGiB = [math]::Round($operatingSystem.FreePhysicalMemory / 1MB, 2)
        CDriveFreeGiB = [math]::Round($cDrive.FreeSpace / 1GB, 2)
        EDriveFreeGiB = [math]::Round($eDrive.FreeSpace / 1GB, 2)
        SampledAt = (Get-Date).ToString('o')
    }
}

function Invoke-PerfPreflight {
    param([int]$Seconds = 30)
    if ($Seconds -lt 1) { throw 'Seconds must be positive' }

    $samples = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $Seconds; $index += 2) {
        $samples.Add((Get-HostResourceSnapshot))
        if ($index + 2 -lt $Seconds) { Start-Sleep -Seconds 2 }
    }
    $latest = $samples[$samples.Count - 1]
    $averageCpu = [math]::Round(($samples | Measure-Object CpuPercent -Average).Average, 2)

    $dockerHealthy = $false
    try {
        docker info --format '{{.ServerVersion}}' | Out-Null
        $dockerHealthy = $LASTEXITCODE -eq 0
    } catch {
        $dockerHealthy = $false
    }
    $frontendNames = @(docker ps --filter 'name=frontend' --format '{{.Names}}' 2>$null)
    $frontendStopped = $frontendNames.Count -eq 0
    $decision = Get-PerfSafetyDecision -AverageCpuPercent $averageCpu `
        -FreeMemoryGiB $latest.FreeMemoryGiB -CDriveFreeGiB $latest.CDriveFreeGiB `
        -EDriveFreeGiB $latest.EDriveFreeGiB -DockerHealthy $dockerHealthy `
        -FrontendStopped $frontendStopped

    [pscustomobject]@{
        Allowed = $decision.Allowed
        Reasons = $decision.Reasons
        AverageCpuPercent = $averageCpu
        FreeMemoryGiB = $latest.FreeMemoryGiB
        CDriveFreeGiB = $latest.CDriveFreeGiB
        EDriveFreeGiB = $latest.EDriveFreeGiB
        Samples = @($samples)
    }
}

function Update-PerfWatchdogState {
    param(
        [Parameter(Mandatory)][double]$CpuPercent,
        [Parameter(Mandatory)][double]$FreeMemoryGiB,
        [double]$CDriveFreeGiB = [double]::PositiveInfinity,
        [double]$EDriveFreeGiB = [double]::PositiveInfinity,
        [Parameter(Mandatory)][int]$HighCpuSamples,
        [Parameter(Mandatory)][int]$LowMemorySamples
    )
    $nextHighCpu = if ($CpuPercent -gt 85) { $HighCpuSamples + 1 } else { 0 }
    $nextLowMemory = if ($FreeMemoryGiB -lt 4) { $LowMemorySamples + 1 } else { 0 }
    $reason = $null
    if ($nextHighCpu -ge 15) { $reason = 'Host CPU exceeded 85% for 30 seconds' }
    if ($nextLowMemory -ge 5) { $reason = 'Free memory stayed below 4 GiB for 10 seconds' }
    if ($CDriveFreeGiB -lt 15) { $reason = "C drive free space fell below 15 GiB; actual=$CDriveFreeGiB GiB" }
    if ($EDriveFreeGiB -lt 5) { $reason = "E drive free space fell below 5 GiB; actual=$EDriveFreeGiB GiB" }
    [pscustomobject]@{
        MustStop = $null -ne $reason
        Reason = $reason
        HighCpuSamples = $nextHighCpu
        LowMemorySamples = $nextLowMemory
    }
}
