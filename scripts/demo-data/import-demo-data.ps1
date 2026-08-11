[CmdletBinding()]
param(
    [ValidateRange(20, 2000)]
    [int]$Books = 300,

    [ValidateRange(0, 5000)]
    [int]$Users = 500,

    [long]$Seed = 404,

    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot 'back_end\.env'
}
$assetDirectory = Join-Path $repoRoot 'front_end\public\demo-data\generated'
$managedVariables = @(
    'DB_HOST',
    'DB_PORT',
    'DB_NAME',
    'DB_USER',
    'DB_PASSWORD',
    'DEMO_DATA_BOOK_COUNT',
    'DEMO_DATA_USER_COUNT',
    'DEMO_DATA_SEED',
    'TOMATOMALL_DEMO_ASSET_DIR'
)
$originalValues = @{}

function Get-DotEnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw 'Local environment file not found. Create back_end/.env or pass -EnvFile.'
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }

        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }
        if ($trimmed.Substring(0, $separator).Trim() -ne $Name) {
            continue
        }

        $value = $trimmed.Substring($separator + 1).Trim()
        $doubleQuoted = $value.StartsWith('"') -and $value.EndsWith('"')
        $singleQuoted = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($doubleQuoted -or $singleQuoted) {
            return $value.Substring(1, $value.Length - 2)
        }

        # dotenv permits comments after an unquoted value. Keep a literal '#'
        # when it is part of the value, and strip only whitespace-delimited comments.
        return ($value -replace '\s+#.*$', '').Trim()
    }
    return $null
}

function Set-ProcessVariable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [AllowNull()]
        [string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

foreach ($name in $managedVariables) {
    $originalValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    foreach ($name in @('DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD')) {
        $current = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ([string]::IsNullOrWhiteSpace($current)) {
            $fromFile = Get-DotEnvValue -Name $name -Path $EnvFile
            if ([string]::IsNullOrWhiteSpace($fromFile)) {
                throw "Missing required local variable $name. Its value will not be printed."
            }
            Set-ProcessVariable -Name $name -Value $fromFile
        }
    }

    if ([string]::IsNullOrWhiteSpace($env:TOMATOMALL_DEMO_PASSWORD)) {
        throw 'Set TOMATOMALL_DEMO_PASSWORD in the current PowerShell process first. Its value will not be stored or printed.'
    }

    Set-ProcessVariable -Name 'DB_NAME' -Value 'tomatomall_demo'
    Set-ProcessVariable -Name 'DEMO_DATA_BOOK_COUNT' -Value $Books.ToString()
    Set-ProcessVariable -Name 'DEMO_DATA_USER_COUNT' -Value $Users.ToString()
    Set-ProcessVariable -Name 'DEMO_DATA_SEED' -Value $Seed.ToString()
    Set-ProcessVariable -Name 'TOMATOMALL_DEMO_ASSET_DIR' -Value $assetDirectory

    $mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -ne $mavenCommand) {
        $mavenExecutable = $mavenCommand.Source
    }
    else {
        $mavenExecutable = Join-Path $repoRoot '.local-tools\apache-maven-3.9.9\bin\mvn.cmd'
        if (-not (Test-Path -LiteralPath $mavenExecutable)) {
            throw 'Maven not found. Install Maven or prepare .local-tools/apache-maven-3.9.9.'
        }
    }

    Write-Host "Preparing to import $Books books and $Users bulk users into tomatomall_demo."
    Write-Host 'The importer never prints passwords or deletes databases, tables, or existing records.'
    Push-Location (Join-Path $repoRoot 'back_end')
    try {
        & $mavenExecutable '-Pdemo-data' 'compile' 'exec:java'
        if ($LASTEXITCODE -ne 0) {
            throw "DATA-001 Java importer failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($name in $managedVariables) {
        Set-ProcessVariable -Name $name -Value $originalValues[$name]
    }
}
