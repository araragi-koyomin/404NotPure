[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DatabaseContainer = '404notpure-db-1',

    [ValidateSet(6, 7)]
    [int]$ExpectedCurrentVersion = 6
)

$ErrorActionPreference = 'Stop'

$running = docker inspect --format '{{.State.Running}}' $DatabaseContainer 2>&1
if ($LASTEXITCODE -ne 0 -or ($running | Select-Object -First 1) -ne 'true') {
    throw "Database container is not running: $DatabaseContainer"
}

$sql = @"
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE success=0),
  COALESCE((SELECT version FROM flyway_schema_history WHERE success=1
      ORDER BY installed_rank DESC LIMIT 1), ''),
  (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='orders'
        AND column_name IN ('idempotency_key','request_fingerprint')),
  (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='orders'
        AND column_name='idempotency_key' AND data_type='char'
        AND character_maximum_length=36 AND character_set_name='ascii'
        AND collation_name='ascii_bin' AND is_nullable='YES'),
  (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='orders'
        AND column_name='request_fingerprint' AND data_type='char'
        AND character_maximum_length=64 AND character_set_name='ascii'
        AND collation_name='ascii_bin' AND is_nullable='YES'),
  (SELECT COUNT(*) FROM (
      SELECT index_name
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='orders' AND non_unique=0
        AND index_name='uk_orders_user_idempotency_key'
      GROUP BY index_name
      HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='user_id,idempotency_key'
  ) exact_unique_index),
  (SELECT COUNT(*)
      FROM information_schema.table_constraints tc
      JOIN information_schema.check_constraints cc
        ON cc.constraint_schema=tc.constraint_schema
       AND cc.constraint_name=tc.constraint_name
      WHERE tc.constraint_schema=DATABASE() AND tc.table_name='orders'
        AND tc.constraint_name='chk_orders_idempotency_pair'
        AND tc.constraint_type='CHECK' AND tc.enforced='YES'
        AND REPLACE(REPLACE(LOWER(cc.check_clause),CHAR(96),''),' ','')=
          '(((idempotency_keyisnull)and(request_fingerprintisnull))or((idempotency_keyisnotnull)and(request_fingerprintisnotnull)))');
SET @idempotency_columns = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='orders'
      AND column_name IN ('idempotency_key','request_fingerprint'));
SET @pair_query = IF(@idempotency_columns=2,
    'SELECT COUNT(*) FROM orders WHERE (idempotency_key IS NULL) <> (request_fingerprint IS NULL)',
    'SELECT -1');
PREPARE pair_statement FROM @pair_query;
EXECUTE pair_statement;
DEALLOCATE PREPARE pair_statement;
SELECT COALESCE(GROUP_CONCAT(CONCAT(COALESCE(version,'baseline'),':',description,':',success)
  ORDER BY installed_rank SEPARATOR ','), '') FROM flyway_schema_history;
COMMIT;
"@

$command = "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql --batch --raw --skip-column-names -uroot -D$DatabaseName"
$lines = @($sql | docker exec -i $DatabaseContainer sh -c $command 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Order idempotency preflight query failed without exposing credentials: $($lines -join [Environment]::NewLine)"
}
if ($lines.Count -ne 3) {
    throw "Order idempotency preflight returned an invalid snapshot row count: $($lines.Count)"
}

$values = $lines[0] -split "`t"
if ($values.Count -ne 7) {
    throw "Order idempotency preflight returned an invalid summary column count: $($values.Count)"
}
$names = @('failed_migrations','current_successful_version','idempotency_columns',
    'exact_idempotency_key_column','exact_request_fingerprint_column',
    'exact_user_key_unique_index','idempotency_pair_check')
Write-Output "database=$DatabaseName"
for ($i=0; $i -lt $names.Count; $i++) { Write-Output "$($names[$i])=$($values[$i])" }
Write-Output "mismatched_idempotency_pairs=$($lines[1])"
Write-Output "flyway_history=$($lines[2])"

$historyReady = [int]$values[0] -eq 0 -and $values[1] -eq [string]$ExpectedCurrentVersion
if ($ExpectedCurrentVersion -eq 6) {
    $structureReady = [int]$values[2] -eq 0 -and [int]$values[3] -eq 0 `
        -and [int]$values[4] -eq 0 -and [int]$values[5] -eq 0 `
        -and [int]$values[6] -eq 0 -and [int]$lines[1] -eq -1
} else {
    $structureReady = [int]$values[2] -eq 2 -and [int]$values[3] -eq 1 `
        -and [int]$values[4] -eq 1 -and [int]$values[5] -eq 1 `
        -and [int]$values[6] -eq 1 -and [int]$lines[1] -eq 0
}

if (-not $historyReady -or -not $structureReady) {
    Write-Output 'status=BLOCKED'
    Write-Output "action=Repair failed migrations, unexpected schema version, partial V7 structure, or mismatched idempotency fields; expected version is $ExpectedCurrentVersion and no rows were changed."
    exit 2
}

Write-Output 'status=READY'
exit 0
