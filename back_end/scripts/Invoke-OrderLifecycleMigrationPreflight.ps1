[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DatabaseContainer = '404notpure-db-1',

    [ValidateRange(1, 10080)]
    [int]$PendingTimeoutMinutes = 30,

    [ValidateSet(5, 6)]
    [int]$ExpectedCurrentVersion = 5
)

$ErrorActionPreference = 'Stop'

$running = docker inspect --format '{{.State.Running}}' $DatabaseContainer 2>&1
if ($LASTEXITCODE -ne 0 -or ($running | Select-Object -First 1) -ne 'true') {
    throw "Database container is not running: $DatabaseContainer"
}

$sql = @"
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET SESSION group_concat_max_len = 1048576;
START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='PENDING'),
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='PAID'),
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='CANCELLED'),
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='CLOSED'),
  (SELECT COUNT(*) FROM orders WHERE status IS NULL
      OR CAST(status AS BINARY) NOT IN ('PENDING','PAID','CANCELLED','CLOSED')),
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='PENDING' AND create_time IS NULL),
  (SELECT COUNT(*) FROM orders WHERE CAST(status AS BINARY)='PENDING'
      AND create_time <= CURRENT_TIMESTAMP - INTERVAL $PendingTimeoutMinutes MINUTE),
  (SELECT COUNT(*) FROM (
      SELECT o.order_id FROM orders o LEFT JOIN order_item i ON i.order_id=o.order_id
      WHERE CAST(o.status AS BINARY)='PENDING' GROUP BY o.order_id HAVING COUNT(i.id)=0
  ) pending_without_items),
  (SELECT COUNT(*) FROM order_item i JOIN orders o ON o.order_id=i.order_id
      WHERE CAST(o.status AS BINARY)='PENDING' AND (i.quantity IS NULL OR i.quantity<=0)),
  (SELECT COUNT(*) FROM order_item i JOIN orders o ON o.order_id=i.order_id
      LEFT JOIN stockpile s ON s.product_id=i.product_id
      WHERE CAST(o.status AS BINARY)='PENDING' AND s.id IS NULL),
  (SELECT COUNT(*) FROM (
      SELECT i.product_id FROM order_item i JOIN orders o ON o.order_id=i.order_id
      JOIN stockpile s ON s.product_id=i.product_id WHERE CAST(o.status AS BINARY)='PENDING'
      GROUP BY i.product_id, s.frozen HAVING SUM(i.quantity)>s.frozen
  ) shortage),
  (SELECT COUNT(*) FROM flyway_schema_history WHERE success=0),
  COALESCE((SELECT version FROM flyway_schema_history WHERE success=1
      ORDER BY installed_rank DESC LIMIT 1), '');
SELECT COALESCE(GROUP_CONCAT(DISTINCT COALESCE(status,'<NULL>') ORDER BY COALESCE(status,'<NULL>')), '')
  FROM (SELECT status FROM orders WHERE status IS NULL
      OR CAST(status AS BINARY) NOT IN ('PENDING','PAID','CANCELLED','CLOSED') LIMIT 100) x;
SELECT COALESCE(GROUP_CONCAT(order_id ORDER BY order_id), '') FROM (
  SELECT o.order_id FROM orders o LEFT JOIN order_item i ON i.order_id=o.order_id
  WHERE CAST(o.status AS BINARY)='PENDING' GROUP BY o.order_id HAVING COUNT(i.id)=0 ORDER BY o.order_id LIMIT 100
) x;
SELECT COALESCE(GROUP_CONCAT(order_id ORDER BY order_id), '') FROM (
  SELECT DISTINCT o.order_id FROM orders o JOIN order_item i ON i.order_id=o.order_id
  LEFT JOIN stockpile s ON s.product_id=i.product_id
  WHERE CAST(o.status AS BINARY)='PENDING' AND (i.quantity IS NULL OR i.quantity<=0 OR s.id IS NULL)
  ORDER BY o.order_id LIMIT 100
) x;
SELECT COALESCE(GROUP_CONCAT(product_id ORDER BY product_id), '') FROM (
  SELECT i.product_id FROM order_item i JOIN orders o ON o.order_id=i.order_id
  JOIN stockpile s ON s.product_id=i.product_id WHERE CAST(o.status AS BINARY)='PENDING'
  GROUP BY i.product_id, s.frozen HAVING SUM(i.quantity)>s.frozen ORDER BY i.product_id LIMIT 100
) x;
SELECT COALESCE(GROUP_CONCAT(CONCAT(COALESCE(version,'baseline'),':',description,':',success)
  ORDER BY installed_rank SEPARATOR ','), '') FROM flyway_schema_history;
COMMIT;
"@

$command = "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql --batch --raw --skip-column-names -uroot -D$DatabaseName"
$lines = @($sql | docker exec -i $DatabaseContainer sh -c $command 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Order lifecycle preflight query failed without exposing credentials: $($lines -join [Environment]::NewLine)"
}
if ($lines.Count -ne 6) {
    throw "Order lifecycle preflight returned an invalid snapshot row count: $($lines.Count)"
}

$values = $lines[0] -split "`t"
if ($values.Count -ne 13) {
    throw "Order lifecycle preflight returned an invalid summary column count: $($values.Count)"
}
$names = @('pending','paid','cancelled','closed','unknown_statuses','pending_without_create_time','expired_pending',
    'pending_without_items','invalid_pending_items','missing_pending_stock','frozen_shortage_products',
    'failed_migrations','current_successful_version')
Write-Output "database=$DatabaseName"
for ($i=0; $i -lt $names.Count; $i++) { Write-Output "$($names[$i])=$($values[$i])" }
Write-Output "unknown_status_values=$($lines[1])"
Write-Output "unknown_status_values_truncated=$(if ([int]$values[4] -gt 100) { 'true' } else { 'false' })"
Write-Output "pending_without_item_ids=$($lines[2])"
Write-Output "pending_without_item_ids_truncated=$(if ([int]$values[7] -gt 100) { 'true' } else { 'false' })"
Write-Output "invalid_or_missing_stock_order_ids=$($lines[3])"
Write-Output "invalid_or_missing_stock_order_ids_truncated=$(if (([int]$values[8] + [int]$values[9]) -gt 100) { 'true' } else { 'false' })"
Write-Output "frozen_shortage_product_ids=$($lines[4])"
Write-Output "frozen_shortage_product_ids_truncated=$(if ([int]$values[10] -gt 100) { 'true' } else { 'false' })"
Write-Output "flyway_history=$($lines[5])"

$dataBlocking = [int]$values[4] + [int]$values[5] + [int]$values[7] + [int]$values[8] + [int]$values[9] + [int]$values[10]
$historyBlocking = [int]$values[11] -gt 0 -or $values[12] -ne [string]$ExpectedCurrentVersion
if ($dataBlocking -gt 0 -or $historyBlocking) {
    Write-Output 'status=BLOCKED'
    Write-Output "action=Repair unknown statuses, inconsistent pending-order items/stock, failed migrations, or unexpected schema version; expected version is $ExpectedCurrentVersion and no rows were changed."
    exit 2
}
if ([int]$values[6] -gt 0) {
    Write-Output 'status=READY_WITH_EXPIRED_PENDING'
    Write-Output 'action=After deployment, run the lifecycle batch processor and verify closed/failed metrics.'
} else {
    Write-Output 'status=READY'
}
exit 0
