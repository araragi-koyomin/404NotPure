param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [int]$Port = 8080,
    [int]$StartupTimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Local backend environment file was not found: $EnvFile"
}

Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        } else {
            $value = ($value -replace '\s+#.*$', '').Trim()
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

$existingListener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($existingListener) {
    throw "Port $Port is already listening; refusing to replace the existing process."
}

$jar = Join-Path $PSScriptRoot '..\target\TomatoMall-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Backend JAR was not found: $jar"
}

$probeDirectory = Join-Path $PSScriptRoot '..\target\probes'
New-Item -ItemType Directory -Path $probeDirectory -Force | Out-Null
$stdout = Join-Path $probeDirectory "backend-$Port.out.log"
$stderr = Join-Path $probeDirectory "backend-$Port.err.log"

$java = (Get-Command java.exe -ErrorAction Stop).Source
$process = Start-Process `
    -FilePath $java `
    -ArgumentList @('-Xmx512m', '-jar', (Resolve-Path -LiteralPath $jar).Path, "--server.port=$Port") `
    -WorkingDirectory (Resolve-Path (Join-Path $PSScriptRoot '..')).Path `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

$ready = $false
for ($attempt = 0; $attempt -lt $StartupTimeoutSeconds; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        break
    }
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -OwningProcess $process.Id -ErrorAction SilentlyContinue) {
        $ready = $true
        break
    }
}

if ($process.HasExited) {
    throw "Backend exited before listening on port $Port (exit code $($process.ExitCode)). Inspect ignored target/probes logs locally."
}
if (-not $ready) {
    Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
    throw "Backend did not listen on port $Port within $StartupTimeoutSeconds seconds."
}

Write-Output "BACKEND_PID=$($process.Id)"
Write-Output "BACKEND_PORT=$Port"
Write-Output 'BACKEND_STATUS=LISTENING'
