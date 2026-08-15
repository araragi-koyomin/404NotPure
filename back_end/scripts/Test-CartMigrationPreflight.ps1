[CmdletBinding()]
param(
    [string]$DatabaseContainer = '404notpure-db-1'
)

$ErrorActionPreference = 'Stop'
$preflightScript = Join-Path $PSScriptRoot 'Invoke-CartMigrationPreflight.ps1'
if (-not (Test-Path -LiteralPath $preflightScript -PathType Leaf)) {
    throw "Cart migration preflight script is missing: $preflightScript"
}

$schema = 'tomato_cart_preflight_test_' + [Guid]::NewGuid().ToString('N')

function Invoke-ContainerMysql {
    param([Parameter(Mandatory = $true)][string]$Sql, [string]$DatabaseName)
    $command = 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --batch --raw -uroot'
    if ($DatabaseName) {
        if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') { throw "Unsafe test database name: $DatabaseName" }
        $command += " -D$DatabaseName"
    }
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL test command failed without exposing credentials: $($output -join [Environment]::NewLine)" }
    return $output
}

function Invoke-Preflight {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $preflightScript `
        -DatabaseName $schema -DatabaseContainer $DatabaseContainer 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Text = $output -join [Environment]::NewLine }
}

function Assert-Equal { param($Expected, $Actual, [string]$Message) if ($Expected -ne $Actual) { throw "$Message expected=$Expected actual=$Actual" } }
function Assert-Contains { param([string]$Text, [string]$Expected, [string]$Message) if (-not $Text.Contains($Expected)) { throw "$Message expected='$Expected' actual='$Text'" } }

try {
    Invoke-ContainerMysql -Sql "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    Invoke-ContainerMysql -DatabaseName $schema -Sql @'
CREATE TABLE carts (
    cart_item_id INT NOT NULL PRIMARY KEY,
    quantity INT NOT NULL,
    user_id INT NOT NULL,
    product_id INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL PRIMARY KEY,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NOT NULL,
    success TINYINT(1) NOT NULL
) ENGINE=InnoDB;
INSERT INTO flyway_schema_history VALUES (4, '4', 'backfill missing inventory', 1);
INSERT INTO carts VALUES (1, 0, 10, 100), (2, -1, 11, 101), (3, 2, 12, 102), (4, 3, 12, 102);
'@

    $blocked = Invoke-Preflight
    Assert-Equal 2 $blocked.ExitCode 'Invalid historical cart data must block migration'
    Assert-Contains $blocked.Text 'non_positive_quantity_rows=2' 'Non-positive count must be complete'
    Assert-Contains $blocked.Text 'duplicate_user_product_groups=1' 'Duplicate count must be complete'
    Assert-Contains $blocked.Text 'non_positive_cart_item_ids=1,2' 'Invalid row identifiers must be shown'
    Assert-Contains $blocked.Text 'duplicate_user_product_keys=12:102' 'Duplicate business key must be shown'
    Assert-Contains $blocked.Text 'status=BLOCKED' 'Blocking status must be explicit'

    Invoke-ContainerMysql -DatabaseName $schema -Sql 'DELETE FROM carts; INSERT INTO carts VALUES (5, 1, 13, 103);'
    $ready = Invoke-Preflight
    Assert-Equal 0 $ready.ExitCode 'Valid carts must be ready for V5'
    Assert-Contains $ready.Text 'non_positive_quantity_rows=0' 'Clean quantity count must be reported'
    Assert-Contains $ready.Text 'duplicate_user_product_groups=0' 'Clean duplicate count must be reported'
    Assert-Contains $ready.Text 'status=READY' 'Ready status must be explicit'

    Invoke-ContainerMysql -DatabaseName $schema -Sql 'DELETE FROM carts;'
    $invalidRows = (1..101 | ForEach-Object {
        "($($_ + 1000), 0, $($_ + 2000), $($_ + 3000))"
    }) -join ','
    $duplicateRows = (1..101 | ForEach-Object {
        $userId = $_ + 4000
        $productId = $_ + 5000
        "($($_ * 2 + 10000), 1, $userId, $productId),($($_ * 2 + 10001), 2, $userId, $productId)"
    }) -join ','
    Invoke-ContainerMysql -DatabaseName $schema -Sql `
        "INSERT INTO carts VALUES $invalidRows; INSERT INTO carts VALUES $duplicateRows;"

    $truncated = Invoke-Preflight
    Assert-Equal 2 $truncated.ExitCode 'More than 100 invalid rows and duplicate groups must block migration'
    Assert-Contains $truncated.Text 'non_positive_quantity_rows=101' 'Full invalid count must not be truncated'
    Assert-Contains $truncated.Text 'duplicate_user_product_groups=101' 'Full duplicate count must not be truncated'
    Assert-Contains $truncated.Text 'non_positive_details_truncated=true' 'Invalid detail truncation must be explicit'
    Assert-Contains $truncated.Text 'duplicate_details_truncated=true' 'Duplicate detail truncation must be explicit'

    $invalidLine = ($truncated.Text -split "`r?`n") | Where-Object {
        $_.StartsWith('non_positive_cart_item_ids=')
    } | Select-Object -First 1
    $invalidIds = $invalidLine.Substring('non_positive_cart_item_ids='.Length) -split ','
    Assert-Equal 100 $invalidIds.Count 'Invalid detail must contain exactly the first 100 IDs'
    Assert-Equal '1001' $invalidIds[0] 'Invalid detail must be sorted'
    Assert-Equal '1100' $invalidIds[-1] 'Invalid detail must stop at the hundredth row'
    if ($invalidIds -contains '1101') { throw 'The 101st invalid row must be omitted' }

    $duplicateLine = ($truncated.Text -split "`r?`n") | Where-Object {
        $_.StartsWith('duplicate_user_product_keys=')
    } | Select-Object -First 1
    $duplicateKeys = $duplicateLine.Substring('duplicate_user_product_keys='.Length) -split ','
    Assert-Equal 100 $duplicateKeys.Count 'Duplicate detail must contain exactly the first 100 keys'
    Assert-Equal '4001:5001' $duplicateKeys[0] 'Duplicate detail must be sorted'
    Assert-Equal '4100:5100' $duplicateKeys[-1] 'Duplicate detail must stop at the hundredth key'
    if ($duplicateKeys -contains '4101:5101') { throw 'The 101st duplicate key must be omitted' }

    Write-Output 'cart_preflight_tests=3 passed=3'
} finally {
    if ($schema -notmatch '^tomato_cart_preflight_test_[a-f0-9]{32}$') { throw "Refusing to drop unsafe test database name: $schema" }
    Invoke-ContainerMysql -Sql "DROP DATABASE IF EXISTS ``$schema``"
}
