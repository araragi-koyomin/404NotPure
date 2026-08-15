[CmdletBinding()]
param([string]$DatabaseContainer = '404notpure-db-1')

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'Invoke-OrderLifecycleMigrationPreflight.ps1'
$schema = 'tomato_order_preflight_' + [Guid]::NewGuid().ToString('N')

function Invoke-Mysql([string]$Sql, [string]$DatabaseName) {
    $command = 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --batch --raw -uroot'
    if ($DatabaseName) { $command += " -D$DatabaseName" }
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL test command failed without exposing credentials: $($output -join [Environment]::NewLine)" }
}

function Invoke-Preflight {
    param([int]$ExpectedCurrentVersion = 5)
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
        -DatabaseName $schema -DatabaseContainer $DatabaseContainer -PendingTimeoutMinutes 30 `
        -ExpectedCurrentVersion $ExpectedCurrentVersion 2>&1
    [pscustomobject]@{ ExitCode=$LASTEXITCODE; Text=($output -join [Environment]::NewLine) }
}

function Assert-Contains([string]$Text, [string]$Expected) {
    if (-not $Text.Contains($Expected)) { throw "Expected '$Expected' in '$Text'" }
}

try {
    Invoke-Mysql "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" ''
    $baseSchemaSql = @'
CREATE TABLE orders (order_id INT PRIMARY KEY, status VARCHAR(20), create_time TIMESTAMP NULL);
CREATE TABLE products (product_id INT PRIMARY KEY);
CREATE TABLE stockpile (id INT PRIMARY KEY, product_id INT, amount INT, frozen INT);
CREATE TABLE order_item (id INT PRIMARY KEY, order_id INT, product_id INT, quantity INT);
CREATE TABLE flyway_schema_history (installed_rank INT PRIMARY KEY, version VARCHAR(50), description VARCHAR(200), success TINYINT);
INSERT INTO flyway_schema_history VALUES (1,'5','cart integrity',1);
INSERT INTO products VALUES (101);
INSERT INTO stockpile VALUES (201,101,3,2);
INSERT INTO orders VALUES (301,'PENDING',CURRENT_TIMESTAMP - INTERVAL 31 MINUTE);
INSERT INTO order_item VALUES (401,301,101,2);
'@
    Invoke-Mysql $baseSchemaSql $schema

    $ready = Invoke-Preflight
    if ($ready.ExitCode -ne 0) { throw "Expected ready exit code 0, got $($ready.ExitCode)" }
    Assert-Contains $ready.Text 'expired_pending=1'
    Assert-Contains $ready.Text 'failed_migrations=0'
    Assert-Contains $ready.Text 'current_successful_version=5'
    Assert-Contains $ready.Text 'status=READY_WITH_EXPIRED_PENDING'

    Invoke-Mysql "UPDATE flyway_schema_history SET version='4' WHERE installed_rank=1" $schema
    $oldVersion = Invoke-Preflight
    if ($oldVersion.ExitCode -ne 2) { throw "Expected old-version exit code 2, got $($oldVersion.ExitCode)" }
    Assert-Contains $oldVersion.Text 'current_successful_version=4'
    Assert-Contains $oldVersion.Text 'status=BLOCKED'
    Invoke-Mysql "UPDATE flyway_schema_history SET version='5' WHERE installed_rank=1" $schema

    Invoke-Mysql "INSERT INTO flyway_schema_history VALUES (2,'6','failed lifecycle',0)" $schema
    $failedMigration = Invoke-Preflight
    if ($failedMigration.ExitCode -ne 2) { throw "Expected failed-migration exit code 2, got $($failedMigration.ExitCode)" }
    Assert-Contains $failedMigration.Text 'failed_migrations=1'
    Assert-Contains $failedMigration.Text 'status=BLOCKED'
    Invoke-Mysql "DELETE FROM flyway_schema_history WHERE installed_rank=2" $schema

    $invalidDataSql = @'
INSERT INTO orders VALUES
  (302,'UNKNOWN',CURRENT_TIMESTAMP),
  (306,'pending',CURRENT_TIMESTAMP),
  (303,'PENDING',CURRENT_TIMESTAMP),
  (304,'PENDING',CURRENT_TIMESTAMP);
INSERT INTO orders VALUES (305,'PENDING',NULL);
INSERT INTO order_item VALUES (402,304,999,1), (403,301,101,1);
'@
    Invoke-Mysql $invalidDataSql $schema
    $blocked = Invoke-Preflight
    if ($blocked.ExitCode -ne 2) { throw "Expected blocked exit code 2, got $($blocked.ExitCode)" }
    Assert-Contains $blocked.Text 'unknown_statuses=2'
    Assert-Contains $blocked.Text 'pending_without_create_time=1'
    Assert-Contains $blocked.Text 'pending_without_items=2'
    Assert-Contains $blocked.Text 'missing_pending_stock=1'
    Assert-Contains $blocked.Text 'frozen_shortage_products=1'
    Assert-Contains $blocked.Text 'status=BLOCKED'
    Write-Output 'order_lifecycle_preflight_tests=4 passed=4'
} finally {
    if ($schema -notmatch '^tomato_order_preflight_[a-f0-9]{32}$') {
        throw "Refusing to drop unsafe test database name: $schema"
    }
    Invoke-Mysql "DROP DATABASE IF EXISTS ``$schema``" ''
}
