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
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql
    )

    $containerCommand = "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql --batch --raw --skip-column-names -uroot -D$DatabaseName"
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $containerCommand 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Inventory preflight query failed without exposing credentials: $($output -join [Environment]::NewLine)"
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
    (SELECT COUNT(*) FROM products),
    (SELECT COUNT(*) FROM stockpile),
    (SELECT COUNT(*) FROM (
        SELECT product_id FROM stockpile GROUP BY product_id HAVING COUNT(*) > 1
    ) duplicate_products),
    (SELECT COUNT(*) FROM stockpile
        LEFT JOIN products ON products.product_id = stockpile.product_id
        WHERE products.product_id IS NULL),
    (SELECT COUNT(*) FROM products
        LEFT JOIN stockpile ON stockpile.product_id = products.product_id
        WHERE stockpile.id IS NULL),
    (SELECT COUNT(*) FROM stockpile WHERE amount < 0 OR frozen < 0);
SELECT COALESCE(GROUP_CONCAT(product_id ORDER BY product_id), '')
FROM (
    SELECT product_id FROM stockpile
    GROUP BY product_id HAVING COUNT(*) > 1
    ORDER BY product_id LIMIT 100
) duplicates;
SELECT COALESCE(GROUP_CONCAT(CONCAT(id, ':', product_id) ORDER BY id), '')
FROM (
    SELECT stockpile.id, stockpile.product_id
    FROM stockpile
    LEFT JOIN products ON products.product_id = stockpile.product_id
    WHERE products.product_id IS NULL
    ORDER BY stockpile.id LIMIT 100
) orphans;
SELECT COALESCE(GROUP_CONCAT(product_id ORDER BY product_id), '')
FROM (
    SELECT products.product_id
    FROM products
    LEFT JOIN stockpile ON stockpile.product_id = products.product_id
    WHERE stockpile.id IS NULL
    ORDER BY products.product_id LIMIT 100
) missing_inventory;
SELECT COALESCE(GROUP_CONCAT(id ORDER BY id), '')
FROM (
    SELECT id FROM stockpile
    WHERE amount < 0 OR frozen < 0
    ORDER BY id LIMIT 100
) negative_inventory;
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(COALESCE(version, 'baseline'), ':', description, ':', success)
    ORDER BY installed_rank SEPARATOR ','
), '') FROM flyway_schema_history;
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(index_name, ':', IF(non_unique = 0, 'unique', 'non_unique'), ':', column_name)
    ORDER BY index_name, seq_in_index SEPARATOR ','
), '')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'stockpile';
SELECT COALESCE(GROUP_CONCAT(
    CONCAT(constraint_name, ':', column_name, '->', referenced_table_name, '.', referenced_column_name)
    ORDER BY constraint_name SEPARATOR ','
), '')
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND table_name = 'stockpile'
    AND referenced_table_name IS NOT NULL;
COMMIT;
'@

$snapshotLines = @(Invoke-ContainerMysql -Sql $snapshotSql)
if ($snapshotLines.Count -ne 8) {
    throw "Inventory preflight returned an invalid snapshot row count: $($snapshotLines.Count)"
}

$values = $snapshotLines[0] -split "`t"
if ($values.Count -ne 6) {
    throw "Inventory preflight returned an invalid summary column count: $($values.Count)"
}

$products = [int]$values[0]
$stockRows = [int]$values[1]
$duplicateGroups = [int]$values[2]
$orphanRows = [int]$values[3]
$missingProducts = [int]$values[4]
$negativeRows = [int]$values[5]
$detailLines = $snapshotLines[1..7]
$duplicateDetailsTruncated = if ($duplicateGroups -gt 100) { 'true' } else { 'false' }
$orphanDetailsTruncated = if ($orphanRows -gt 100) { 'true' } else { 'false' }
$missingDetailsTruncated = if ($missingProducts -gt 100) { 'true' } else { 'false' }
$negativeDetailsTruncated = if ($negativeRows -gt 100) { 'true' } else { 'false' }

Write-Output "database=$DatabaseName"
Write-Output "products=$products"
Write-Output "stock_rows=$stockRows"
Write-Output "duplicate_product_groups=$duplicateGroups"
Write-Output "duplicate_product_ids=$($detailLines[0])"
Write-Output "duplicate_product_ids_truncated=$duplicateDetailsTruncated"
Write-Output "orphan_stock_rows=$orphanRows"
Write-Output "orphan_stock_id_product_id=$($detailLines[1])"
Write-Output "orphan_stock_details_truncated=$orphanDetailsTruncated"
Write-Output "products_without_stock=$missingProducts"
Write-Output "products_without_stock_ids=$($detailLines[2])"
Write-Output "products_without_stock_ids_truncated=$missingDetailsTruncated"
Write-Output "negative_stock_rows=$negativeRows"
Write-Output "negative_stock_ids=$($detailLines[3])"
Write-Output "negative_stock_ids_truncated=$negativeDetailsTruncated"
Write-Output "flyway_history=$($detailLines[4])"
Write-Output "stockpile_indexes=$($detailLines[5])"
Write-Output "stockpile_foreign_keys=$($detailLines[6])"

if ($duplicateGroups -gt 0 -or $orphanRows -gt 0) {
    Write-Output 'status=BLOCKED'
    Write-Output 'action=Resolve duplicate inventory or invalid product references manually; no rows were changed.'
    exit 2
}

if ($missingProducts -gt 0 -and $negativeRows -gt 0) {
    Write-Output 'status=READY_WITH_BACKFILL_AND_WARNING'
} elseif ($missingProducts -gt 0) {
    Write-Output 'status=READY_WITH_BACKFILL'
} elseif ($negativeRows -gt 0) {
    Write-Output 'status=READY_WITH_WARNING'
} else {
    Write-Output 'status=READY'
}

if ($missingProducts -gt 0) {
    Write-Output 'backfill=V4 will create one amount=0,frozen=0 row for each listed product.'
}
if ($negativeRows -gt 0) {
    Write-Output 'warning=Negative inventory is tracked by STOCK-001 and is not modified by DB-001B.'
}

exit 0
