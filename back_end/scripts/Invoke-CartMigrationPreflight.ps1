[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DatabaseContainer = '404notpure-db-1'
)

$ErrorActionPreference = 'Stop'

function Invoke-ContainerMysql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $command = "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql --batch --raw --skip-column-names -uroot -D$DatabaseName"
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $command 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Cart preflight query failed without exposing credentials: $($output -join [Environment]::NewLine)"
    }
    return @($output)
}

$running = docker inspect --format '{{.State.Running}}' $DatabaseContainer 2>&1
if ($LASTEXITCODE -ne 0 -or ($running | Select-Object -First 1) -ne 'true') {
    throw "Database container is not running: $DatabaseContainer"
}

$snapshotSql = @'
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET SESSION group_concat_max_len = 1048576;
START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT
    (SELECT COUNT(*) FROM carts),
    (SELECT COUNT(*) FROM carts WHERE quantity <= 0),
    (SELECT COUNT(*) FROM (
        SELECT user_id, product_id FROM carts GROUP BY user_id, product_id HAVING COUNT(*) > 1
    ) duplicate_keys);
SELECT COALESCE(GROUP_CONCAT(cart_item_id ORDER BY cart_item_id), '')
FROM (SELECT cart_item_id FROM carts WHERE quantity <= 0 ORDER BY cart_item_id LIMIT 100) invalid_rows;
SELECT COALESCE(GROUP_CONCAT(CONCAT(user_id, ':', product_id) ORDER BY user_id, product_id), '')
FROM (
    SELECT user_id, product_id FROM carts
    GROUP BY user_id, product_id HAVING COUNT(*) > 1
    ORDER BY user_id, product_id LIMIT 100
) duplicate_keys;
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(COALESCE(version, 'baseline'), ':', description, ':', success)
    ORDER BY installed_rank SEPARATOR ','
), '') FROM flyway_schema_history;
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(index_name, ':', IF(non_unique = 0, 'unique', 'non_unique'), ':', column_name)
    ORDER BY index_name, seq_in_index SEPARATOR ','
), '')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'carts';
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(constraint_name, ':', check_clause)
    ORDER BY constraint_name SEPARATOR ','
), '')
FROM information_schema.table_constraints
JOIN information_schema.check_constraints USING (constraint_schema, constraint_name)
WHERE constraint_schema = DATABASE() AND table_name = 'carts';
COMMIT;
'@

$lines = @(Invoke-ContainerMysql -Sql $snapshotSql)
if ($lines.Count -ne 6) { throw "Cart preflight returned an invalid snapshot row count: $($lines.Count)" }
$summary = $lines[0] -split "`t"
if ($summary.Count -ne 3) { throw "Cart preflight returned an invalid summary column count: $($summary.Count)" }

$cartRows = [int]$summary[0]
$nonPositiveRows = [int]$summary[1]
$duplicateGroups = [int]$summary[2]

Write-Output "database=$DatabaseName"
Write-Output "cart_rows=$cartRows"
Write-Output "non_positive_quantity_rows=$nonPositiveRows"
Write-Output "non_positive_cart_item_ids=$($lines[1])"
Write-Output "non_positive_details_truncated=$(if ($nonPositiveRows -gt 100) { 'true' } else { 'false' })"
Write-Output "duplicate_user_product_groups=$duplicateGroups"
Write-Output "duplicate_user_product_keys=$($lines[2])"
Write-Output "duplicate_details_truncated=$(if ($duplicateGroups -gt 100) { 'true' } else { 'false' })"
Write-Output "flyway_history=$($lines[3])"
Write-Output "cart_indexes=$($lines[4])"
Write-Output "cart_checks=$($lines[5])"

if ($nonPositiveRows -gt 0 -or $duplicateGroups -gt 0) {
    Write-Output 'status=BLOCKED'
    Write-Output 'action=Correct non-positive quantities or duplicate user/product cart rows manually; no rows were changed.'
    exit 2
}

Write-Output 'status=READY'
exit 0
