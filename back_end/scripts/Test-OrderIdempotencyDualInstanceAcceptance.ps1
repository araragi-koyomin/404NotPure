[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'Invoke-OrderIdempotencyDualInstanceAcceptance.ps1'
$content = Get-Content -LiteralPath $scriptPath -Raw

if ($content -match "tomatomall-ord002-backend-a" -or $content -match "tomatomall-ord002-backend-b") {
    throw 'Dual-instance acceptance must not use fixed container names.'
}
if ($content -notmatch "--label" -or $content -notmatch "tomatomall.acceptance.run") {
    throw 'Dual-instance acceptance containers must carry a run-specific ownership label.'
}
if ($content -notmatch "createdContainerIds") {
    throw 'Dual-instance acceptance cleanup must track only containers created by this run.'
}

Write-Output 'order_idempotency_dual_instance_script_tests=3 passed=3'
