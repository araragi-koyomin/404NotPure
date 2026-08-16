[CmdletBinding()]
param([string]$DatabaseContainer = '404notpure-db-1')

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'Invoke-OrderIdempotencyMigrationPreflight.ps1'
$schema = 'tomato_ord2_preflight_' + [Guid]::NewGuid().ToString('N')

function Invoke-Mysql([string]$Sql, [string]$DatabaseName) {
    $command = 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --batch --raw -uroot'
    if ($DatabaseName) { $command += " -D$DatabaseName" }
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $command 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL test command failed without exposing credentials: $($output -join [Environment]::NewLine)"
    }
}

function Invoke-Preflight([int]$ExpectedCurrentVersion) {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
        -DatabaseName $schema -DatabaseContainer $DatabaseContainer `
        -ExpectedCurrentVersion $ExpectedCurrentVersion 2>&1
    [pscustomobject]@{ ExitCode=$LASTEXITCODE; Text=($output -join [Environment]::NewLine) }
}

function Assert-Contains([string]$Text, [string]$Expected) {
    if (-not $Text.Contains($Expected)) { throw "Expected '$Expected' in '$Text'" }
}

try {
    Invoke-Mysql "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" ''
    $baseSchemaSql = @'
CREATE TABLE orders (order_id INT PRIMARY KEY, user_id INT NOT NULL);
CREATE TABLE flyway_schema_history (
    installed_rank INT PRIMARY KEY, version VARCHAR(50), description VARCHAR(200), success TINYINT
);
INSERT INTO flyway_schema_history VALUES (1,'6','order lifecycle',1);
'@
    Invoke-Mysql $baseSchemaSql $schema

    $versionSixReady = Invoke-Preflight 6
    if ($versionSixReady.ExitCode -ne 0) {
        throw "Expected V6 ready exit code 0, got $($versionSixReady.ExitCode)"
    }
    Assert-Contains $versionSixReady.Text 'idempotency_columns=0'
    Assert-Contains $versionSixReady.Text 'mismatched_idempotency_pairs=-1'
    Assert-Contains $versionSixReady.Text 'status=READY'

    Invoke-Mysql "ALTER TABLE orders ADD COLUMN idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL" $schema
    $partialStructure = Invoke-Preflight 6
    if ($partialStructure.ExitCode -ne 2) {
        throw "Expected partial-structure exit code 2, got $($partialStructure.ExitCode)"
    }
    Assert-Contains $partialStructure.Text 'idempotency_columns=1'
    Assert-Contains $partialStructure.Text 'status=BLOCKED'
    Invoke-Mysql "ALTER TABLE orders DROP COLUMN idempotency_key" $schema

    Invoke-Mysql "INSERT INTO flyway_schema_history VALUES (2,'7','failed idempotency',0)" $schema
    $failedMigration = Invoke-Preflight 6
    if ($failedMigration.ExitCode -ne 2) {
        throw "Expected failed-migration exit code 2, got $($failedMigration.ExitCode)"
    }
    Assert-Contains $failedMigration.Text 'failed_migrations=1'
    Assert-Contains $failedMigration.Text 'status=BLOCKED'
    Invoke-Mysql "DELETE FROM flyway_schema_history WHERE installed_rank=2" $schema

    $versionSevenWithoutCheckSql = @'
ALTER TABLE orders
  ADD COLUMN idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD CONSTRAINT uk_orders_user_idempotency_key UNIQUE (user_id,idempotency_key);
UPDATE flyway_schema_history SET version='7', description='order checkout idempotency';
INSERT INTO orders VALUES (1,101,'00000000-0000-0000-0000-000000000001',NULL);
'@
    Invoke-Mysql $versionSevenWithoutCheckSql $schema
    $invalidVersionSeven = Invoke-Preflight 7
    if ($invalidVersionSeven.ExitCode -ne 2) {
        throw "Expected invalid V7 exit code 2, got $($invalidVersionSeven.ExitCode)"
    }
    Assert-Contains $invalidVersionSeven.Text 'idempotency_pair_check=0'
    Assert-Contains $invalidVersionSeven.Text 'mismatched_idempotency_pairs=1'
    Assert-Contains $invalidVersionSeven.Text 'status=BLOCKED'

    Invoke-Mysql "DELETE FROM orders; ALTER TABLE orders RENAME INDEX uk_orders_user_idempotency_key TO wrong_idempotency_index" $schema
    $wrongIndexName = Invoke-Preflight 7
    if ($wrongIndexName.ExitCode -ne 2) {
        throw "Expected wrong-index-name exit code 2, got $($wrongIndexName.ExitCode)"
    }
    Assert-Contains $wrongIndexName.Text 'exact_user_key_unique_index=0'
    Assert-Contains $wrongIndexName.Text 'status=BLOCKED'
    Invoke-Mysql "ALTER TABLE orders RENAME INDEX wrong_idempotency_index TO uk_orders_user_idempotency_key" $schema

    Invoke-Mysql "ALTER TABLE orders ADD CONSTRAINT chk_orders_idempotency_pair CHECK (idempotency_key IS NULL OR request_fingerprint IS NOT NULL)" $schema
    $wrongCheckExpression = Invoke-Preflight 7
    if ($wrongCheckExpression.ExitCode -ne 2) {
        throw "Expected wrong-check-expression exit code 2, got $($wrongCheckExpression.ExitCode)"
    }
    Assert-Contains $wrongCheckExpression.Text 'idempotency_pair_check=0'
    Assert-Contains $wrongCheckExpression.Text 'status=BLOCKED'
    Invoke-Mysql "ALTER TABLE orders DROP CHECK chk_orders_idempotency_pair; ALTER TABLE orders ADD CONSTRAINT chk_orders_idempotency_pair CHECK ((idempotency_key IS NULL AND request_fingerprint IS NULL) OR (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL))" $schema
    $versionSevenReady = Invoke-Preflight 7
    if ($versionSevenReady.ExitCode -ne 0) {
        throw "Expected V7 ready exit code 0, got $($versionSevenReady.ExitCode)"
    }
    Assert-Contains $versionSevenReady.Text 'exact_idempotency_key_column=1'
    Assert-Contains $versionSevenReady.Text 'exact_request_fingerprint_column=1'
    Assert-Contains $versionSevenReady.Text 'exact_user_key_unique_index=1'
    Assert-Contains $versionSevenReady.Text 'idempotency_pair_check=1'
    Assert-Contains $versionSevenReady.Text 'mismatched_idempotency_pairs=0'
    Assert-Contains $versionSevenReady.Text 'status=READY'

    Write-Output 'order_idempotency_preflight_tests=6 passed=6'
} finally {
    if ($schema -notmatch '^tomato_ord2_preflight_[a-f0-9]{32}$') {
        throw "Refusing to drop unsafe test database name: $schema"
    }
    Invoke-Mysql "DROP DATABASE IF EXISTS ``$schema``" ''
}
