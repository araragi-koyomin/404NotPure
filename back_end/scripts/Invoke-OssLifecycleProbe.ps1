param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$MavenCommand = '',
    [switch]$NoFork
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "OSS probe environment file was not found: $EnvFile"
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

$required = @(
    'ALIYUN_OSS_ENDPOINT',
    'ALIYUN_OSS_ACCESS_KEY_ID',
    'ALIYUN_OSS_ACCESS_KEY_SECRET',
    'ALIYUN_OSS_BUCKET_NAME'
)
$missing = $required | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process'))
}
if ($missing.Count -gt 0) {
    throw "OSS probe is missing environment variable names: $($missing -join ', ')"
}

if ([string]::IsNullOrWhiteSpace($MavenCommand)) {
    $installedMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $installedMaven) {
        $installedMaven = Get-Command mvn -ErrorAction SilentlyContinue
    }
    if ($installedMaven) {
        $MavenCommand = $installedMaven.Source
    } else {
        $repositoryMaven = Join-Path $PSScriptRoot '..\..\.local-tools\apache-maven-3.9.9\bin\mvn.cmd'
        if (Test-Path -LiteralPath $repositoryMaven) {
            $MavenCommand = $repositoryMaven
        } else {
            throw 'Maven was not found. Install Maven or pass -MavenCommand.'
        }
    }
}

$env:RUN_REAL_OSS_PROBE = 'true'

Push-Location (Join-Path $PSScriptRoot '..')
try {
    $mavenArguments = @('-Dtest=OssLifecycleProbeIT')
    if ($NoFork) {
        $mavenArguments += '-DforkCount=0'
    }
    $mavenArguments += 'test'
    & $MavenCommand @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
