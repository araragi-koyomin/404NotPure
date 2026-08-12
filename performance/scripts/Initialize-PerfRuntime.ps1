$ErrorActionPreference = 'Stop'
$runtimePath = Join-Path (Resolve-Path "$PSScriptRoot/..").Path 'runtime.env'
$passwordPath = Join-Path (Resolve-Path "$PSScriptRoot/..").Path 'admin-password.txt'
$cacheModePath = Join-Path (Resolve-Path "$PSScriptRoot/..").Path 'cache-mode.env'
$sourceEnvPath = Join-Path (Resolve-Path "$PSScriptRoot/../..").Path 'back_end/.env'
$bytes = New-Object byte[] 48
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $generator.GetBytes($bytes)
} finally {
    $generator.Dispose()
}
$secret = [Convert]::ToBase64String($bytes)
$databasePasswordLine = Get-Content -LiteralPath $sourceEnvPath | Where-Object {
    $_ -match '^\s*DB_PASSWORD\s*='
} | Select-Object -Last 1
if (-not $databasePasswordLine) { throw 'DB_PASSWORD is missing from back_end/.env' }
$databasePassword = ($databasePasswordLine -split '=', 2)[1].Trim()
if ([string]::IsNullOrWhiteSpace($databasePassword)) { throw 'DB_PASSWORD is empty' }
$runtimeVariables = @(
    "JWT_SECRET=$secret"
    "DB_PASSWORD=$databasePassword"
    'ALIYUN_OSS_ENDPOINT=perf-disabled.invalid'
    'ALIYUN_OSS_ACCESS_KEY_ID=perf-disabled'
    'ALIYUN_OSS_ACCESS_KEY_SECRET=perf-disabled'
    'ALIYUN_OSS_BUCKET_NAME=perf-disabled'
    'ALIPAY_APP_ID=perf-disabled'
    'ALIPAY_SELLER_ID=perf-disabled'
    'ALIPAY_APP_PRIVATE_KEY=perf-disabled'
    'ALIPAY_ALIPAY_PUBLIC_KEY=perf-disabled'
    'ALIPAY_NOTIFY_URL=http://127.0.0.1/perf-disabled'
    'ALIPAY_SERVER_URL=https://perf-disabled.invalid'
    'ALIPAY_RETURN_URL=http://127.0.0.1/perf-disabled'
    'FRONTEND_URL=http://127.0.0.1:5173'
)
[System.IO.File]::WriteAllText(
    $runtimePath,
    ($runtimeVariables -join "`n") + "`n",
    [System.Text.Encoding]::ASCII
)
$demoPasswordLine = Get-Content -LiteralPath $sourceEnvPath | Where-Object {
    $_ -match '^\s*TOMATOMALL_DEMO_PASSWORD\s*='
} | Select-Object -Last 1
if (-not $demoPasswordLine) { throw 'TOMATOMALL_DEMO_PASSWORD is missing from back_end/.env' }
$demoPassword = ($demoPasswordLine -split '=', 2)[1]
if ([string]::IsNullOrWhiteSpace($demoPassword)) { throw 'TOMATOMALL_DEMO_PASSWORD is empty' }
[System.IO.File]::WriteAllText($passwordPath, $demoPassword.Trim(), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($cacheModePath, "PRODUCT_DETAIL_CACHE_ENABLED=true`n", [System.Text.Encoding]::ASCII)
Write-Output 'Generated ignored, performance-only runtime files without copying external-service credentials.'
