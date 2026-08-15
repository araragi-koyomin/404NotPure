[CmdletBinding()]
param(
    [string]$DatabaseContainer = '404notpure-db-1'
)

$ErrorActionPreference = 'Stop'

$preflightScript = Join-Path $PSScriptRoot 'Invoke-InventoryMigrationPreflight.ps1'
if (-not (Test-Path -LiteralPath $preflightScript -PathType Leaf)) {
    throw "Inventory migration preflight script is missing: $preflightScript"
}
$preflightSource = Get-Content -Raw -LiteralPath $preflightScript

$schema = 'tomato_inventory_preflight_test_' + [Guid]::NewGuid().ToString('N')

function Invoke-ContainerMysql {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql,

        [string]$DatabaseName
    )

    $containerCommand = 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --batch --raw -uroot'
    if ($DatabaseName) {
        if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
            throw "Unsafe test database name: $DatabaseName"
        }
        $containerCommand += " -D$DatabaseName"
    }

    $output = $Sql | docker exec -i $DatabaseContainer sh -c $containerCommand 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL test command failed without exposing credentials: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Invoke-Preflight {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $preflightScript `
            -DatabaseName $schema -DatabaseContainer $DatabaseContainer 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Text = $output -join [Environment]::NewLine
    }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message expected=$Expected actual=$Actual"
    }
}

function Assert-Contains {
    param([string]$Text, [string]$Expected, [string]$Message)
    if (-not $Text.Contains($Expected)) {
        throw "$Message expected fragment='$Expected' actual='$Text'"
    }
}

try {
    Assert-Contains $preflightSource 'START TRANSACTION WITH CONSISTENT SNAPSHOT' `
        'Preflight summary and details must use one consistent database snapshot'

    Invoke-ContainerMysql -Sql "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    Invoke-ContainerMysql -DatabaseName $schema -Sql @'
CREATE TABLE products (
    product_id INT NOT NULL PRIMARY KEY,
    title VARCHAR(255) NULL
) ENGINE=InnoDB;
CREATE TABLE stockpile (
    id INT NOT NULL PRIMARY KEY,
    amount INT NOT NULL,
    frozen INT NOT NULL,
    product_id INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL PRIMARY KEY,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NOT NULL,
    success TINYINT(1) NOT NULL
) ENGINE=InnoDB;
INSERT INTO flyway_schema_history VALUES (1, '1', 'baseline schema', 1), (2, '2', 'payment result', 1);
INSERT INTO products VALUES (101, 'duplicate'), (102, 'negative'), (103, 'missing');
INSERT INTO stockpile VALUES
    (201, 3, 0, 101),
    (202, 4, 1, 101),
    (203, -1, 0, 102),
    (204, 2, 0, 999);
'@

    $blocked = Invoke-Preflight
    Assert-Equal 2 $blocked.ExitCode 'Blocking data must fail preflight'
    Assert-Contains $blocked.Text 'duplicate_product_groups=1' 'Duplicate count must be reported'
    Assert-Contains $blocked.Text 'orphan_stock_rows=1' 'Orphan count must be reported'
    Assert-Contains $blocked.Text 'products_without_stock=1' 'Missing stock count must be reported'
    Assert-Contains $blocked.Text 'negative_stock_rows=1' 'Negative stock warning must be reported'
    Assert-Contains $blocked.Text 'duplicate_product_ids_truncated=false' `
        'Short duplicate details must explicitly report that they are complete'
    Assert-Contains $blocked.Text 'status=BLOCKED' 'Blocking status must be explicit'

    Invoke-ContainerMysql -DatabaseName $schema -Sql @'
DELETE FROM stockpile WHERE id IN (202, 204);
INSERT INTO stockpile VALUES (205, 0, 0, 103);
'@

    $ready = Invoke-Preflight
    Assert-Equal 0 $ready.ExitCode 'Negative stock alone is tracked by STOCK-001 and must not block DB-001B'
    Assert-Contains $ready.Text 'duplicate_product_groups=0' 'Clean duplicate count must be reported'
    Assert-Contains $ready.Text 'orphan_stock_rows=0' 'Clean orphan count must be reported'
    Assert-Contains $ready.Text 'products_without_stock=0' 'Clean missing count must be reported'
    Assert-Contains $ready.Text 'negative_stock_rows=1' 'Negative stock warning must remain visible'
    Assert-Contains $ready.Text 'status=READY_WITH_WARNING' 'Warning-only status must be explicit'

    Invoke-ContainerMysql -DatabaseName $schema -Sql 'DELETE FROM stockpile; DELETE FROM products;'
    $productValues = (1..101 | ForEach-Object { "($($_ + 1000), 'duplicate-$_')" }) -join ','
    $stockValues = (1..101 | ForEach-Object {
        $productId = $_ + 1000
        "($($productId * 10 + 1), 1, 0, $productId),($($productId * 10 + 2), 1, 0, $productId)"
    }) -join ','
    Invoke-ContainerMysql -DatabaseName $schema -Sql `
        "INSERT INTO products VALUES $productValues; INSERT INTO stockpile VALUES $stockValues;"

    $truncated = Invoke-Preflight
    Assert-Equal 2 $truncated.ExitCode 'More than 100 duplicate groups must still block migration'
    Assert-Contains $truncated.Text 'duplicate_product_groups=101' `
        'The full duplicate count must not be truncated'
    Assert-Contains $truncated.Text 'duplicate_product_ids_truncated=true' `
        'A partial diagnostic list must be labelled as truncated'
    $duplicateLine = ($truncated.Text -split "`r?`n") | Where-Object {
        $_.StartsWith('duplicate_product_ids=')
    } | Select-Object -First 1
    $duplicateIds = $duplicateLine.Substring('duplicate_product_ids='.Length) -split ','
    Assert-Equal 100 $duplicateIds.Count 'The diagnostic list must contain exactly the first 100 IDs'
    Assert-Equal '1001' $duplicateIds[0] 'The diagnostic list must keep deterministic ascending order'
    Assert-Equal '1100' $duplicateIds[-1] 'The hundredth duplicate ID must be the final displayed item'
    if ($duplicateIds -contains '1101') {
        throw 'The 101st duplicate ID must be omitted when the truncated flag is true'
    }

    Write-Output 'inventory_preflight_tests=3 passed=3'
} finally {
    if ($schema -notmatch '^tomato_inventory_preflight_test_[a-f0-9]{32}$') {
        throw "Refusing to drop unsafe test database name: $schema"
    }
    Invoke-ContainerMysql -Sql "DROP DATABASE IF EXISTS ``$schema``"
}
