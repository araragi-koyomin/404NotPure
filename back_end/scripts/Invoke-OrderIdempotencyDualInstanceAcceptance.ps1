[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName = 'Tomato',

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DatabaseContainer = '404notpure-db-1',

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$DockerNetwork = '404notpure_tomato-network',

    [ValidateRange(1024, 65534)]
    [int]$FirstPort = 18081,

    [ValidateRange(1024, 65534)]
    [int]$SecondPort = 18082,

    [ValidateRange(3.0, 64.0)]
    [double]$MinimumHostFreeGiB = 4.0
)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $backendRoot '.env'
$marker = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$firstContainer = "tomatomall-ord002-$marker-a"
$secondContainer = "tomatomall-ord002-$marker-b"
$runLabel = "tomatomall.acceptance.run=$marker"
$createdContainerIds = @()
$username = "ord2-$marker"
$telephone = '18' + (Get-Random -Minimum 100000000 -Maximum 999999999)
$productTitle = "ord2-product-$marker"
$password = 'Ord2-test-only-password!'
$productId = $null

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed without exposing credentials: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Invoke-Mysql([string]$Sql) {
    $command = "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql --batch --raw --skip-column-names -uroot -D$DatabaseName"
    $output = $Sql | docker exec -i $DatabaseContainer sh -c $command 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL acceptance command failed without exposing credentials: $($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Wait-Backend([int]$Port) {
    $lastError = $null
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/products/-1" -TimeoutSec 2
            if ($response.code) { return }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Backend on port $Port did not become ready: $lastError"
}

function Remove-TestData {
    $cleanupSql = @"
DELETE FROM order_item WHERE order_id IN (
  SELECT order_id FROM orders WHERE user_id IN (SELECT id FROM account WHERE username='$username')
);
DELETE FROM orders WHERE user_id IN (SELECT id FROM account WHERE username='$username');
DELETE FROM stockpile WHERE product_id IN (SELECT product_id FROM products WHERE title='$productTitle');
DELETE FROM products WHERE title='$productTitle';
DELETE FROM account WHERE username='$username';
"@
    Invoke-Mysql $cleanupSql | Out-Null
}

if (-not (Test-Path $envFile)) {
    throw 'back_end/.env is required but was not found.'
}
if ($FirstPort -eq $SecondPort) {
    throw 'The two backend ports must be different.'
}
$usedPorts = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort)
if ($usedPorts -contains $FirstPort -or $usedPorts -contains $SecondPort) {
    throw "Acceptance port is already in use: $FirstPort or $SecondPort"
}
$operatingSystem = Get-CimInstance Win32_OperatingSystem
$freeGiB = [math]::Round($operatingSystem.FreePhysicalMemory / 1MB, 2)
if ($freeGiB -lt $MinimumHostFreeGiB) {
    throw "Host free memory $freeGiB GiB is below the required $MinimumHostFreeGiB GiB safety threshold."
}

try {
    Invoke-Docker @(
        'run','--rm','--memory','1g',
        '-v',"${backendRoot}:/workspace",
        '-v','tomatomall_maven_cache:/root/.m2',
        '-w','/workspace',
        'maven:3.9.9-eclipse-temurin-17',
        'mvn','-DskipTests','package'
    ) | Out-Null

    $jar = Get-ChildItem (Join-Path $backendRoot 'target') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) { throw 'Packaged backend jar was not found.' }

    $common = @(
        '--memory','512m','--network',$DockerNetwork,'--label',$runLabel,'--env-file',$envFile,
        '-e','DB_HOST=db','-e','DB_PORT=3306','-e',"DB_NAME=$DatabaseName",
        '-e','REDIS_HOST=redis','-e','REDIS_PORT=6379',
        '-e','RUN_REAL_OSS_PROBE=false','-e','RUN_REAL_OSS_PERMISSION_PROBE=false',
        '-e','RUN_REAL_ALIPAY_PROBE=false','-e','JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m',
        '-v',"$($jar.FullName):/app/app.jar:ro"
    )
    $firstDockerArguments = @('run','-d','--name',$firstContainer,
        '-p',"127.0.0.1:${FirstPort}:8080") + $common `
        + @('eclipse-temurin:17-jre','java','-jar','/app/app.jar')
    $secondDockerArguments = @('run','-d','--name',$secondContainer,
        '-p',"127.0.0.1:${SecondPort}:8080") + $common `
        + @('eclipse-temurin:17-jre','java','-jar','/app/app.jar')
    $createdContainerIds += [string](Invoke-Docker $firstDockerArguments | Select-Object -Last 1)
    $createdContainerIds += [string](Invoke-Docker $secondDockerArguments | Select-Object -Last 1)

    Wait-Backend $FirstPort
    Wait-Backend $SecondPort

    $registration = @{
        username=$username; password=$password; name='ORD-002 dual instance test'
        telephone=$telephone; email="$username@example.invalid"; location='test'
    } | ConvertTo-Json -Compress
    $registered = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$FirstPort/api/accounts" `
        -ContentType 'application/json' -Body $registration -TimeoutSec 10
    if ($registered.code -ne '200') { throw 'Test account registration failed.' }

    $login = @{username=$username; password=$password} | ConvertTo-Json -Compress
    $authenticated = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$FirstPort/api/accounts/login" `
        -ContentType 'application/json' -Body $login -TimeoutSec 10
    if ($authenticated.code -ne '200' -or -not $authenticated.data) {
        throw 'Test account login failed.'
    }
    $token = [string]$authenticated.data

    $productId = [int](Invoke-Mysql @"
INSERT INTO products (title,price,rate,description,detail,cover,category)
VALUES ('$productTitle',19.99,5.0,'test','test','test','literature');
SET @product_id=LAST_INSERT_ID();
INSERT INTO stockpile (amount,frozen,product_id) VALUES (5,0,@product_id);
SELECT @product_id;
"@ | Select-Object -Last 1)

    $key = [Guid]::NewGuid().ToString()
    $checkoutBody = @{
        paymentMethod='Alipay'; items=@(@{productId=$productId; amount=2})
    } | ConvertTo-Json -Depth 4 -Compress
    Add-Type -AssemblyName System.Net.Http
    $firstClient = [System.Net.Http.HttpClient]::new()
    $secondClient = [System.Net.Http.HttpClient]::new()
    try {
        $firstClient.DefaultRequestHeaders.Add('token', $token)
        $secondClient.DefaultRequestHeaders.Add('token', $token)
        $firstClient.DefaultRequestHeaders.Add('Idempotency-Key', $key)
        $secondClient.DefaultRequestHeaders.Add('Idempotency-Key', $key)
        $firstContent = [System.Net.Http.StringContent]::new(
            $checkoutBody, [System.Text.Encoding]::UTF8, 'application/json')
        $secondContent = [System.Net.Http.StringContent]::new(
            $checkoutBody, [System.Text.Encoding]::UTF8, 'application/json')
        $firstTask = $firstClient.PostAsync("http://127.0.0.1:$FirstPort/api/cart/checkout", $firstContent)
        $secondTask = $secondClient.PostAsync("http://127.0.0.1:$SecondPort/api/cart/checkout", $secondContent)
        $firstResponse = $firstTask.GetAwaiter().GetResult()
        $secondResponse = $secondTask.GetAwaiter().GetResult()
        $firstBody = ($firstResponse.Content.ReadAsStringAsync()).GetAwaiter().GetResult() | ConvertFrom-Json
        $secondBody = ($secondResponse.Content.ReadAsStringAsync()).GetAwaiter().GetResult() | ConvertFrom-Json
    } finally {
        $firstClient.Dispose()
        $secondClient.Dispose()
    }

    if (-not $firstResponse.IsSuccessStatusCode -or -not $secondResponse.IsSuccessStatusCode) {
        throw 'One of the two checkout requests did not return HTTP 200.'
    }
    if ($firstBody.code -ne '200' -or $secondBody.code -ne '200') {
        throw 'One of the two checkout requests did not return business code 200.'
    }
    if ([string]$firstBody.data.orderId -ne [string]$secondBody.data.orderId) {
        throw 'The two backend instances returned different order IDs.'
    }
    $replayHeaders = 0
    if ($firstResponse.Headers.Contains('Idempotent-Replay')) { $replayHeaders++ }
    if ($secondResponse.Headers.Contains('Idempotent-Replay')) { $replayHeaders++ }
    if ($replayHeaders -ne 1) {
        throw "Expected exactly one replay response header, got $replayHeaders."
    }

    $databaseEvidence = Invoke-Mysql @"
SELECT COUNT(*) FROM orders WHERE user_id=(SELECT id FROM account WHERE username='$username')
  AND idempotency_key='$key';
SELECT COUNT(*),COALESCE(SUM(quantity),0) FROM order_item
  WHERE order_id=(SELECT order_id FROM orders
    WHERE user_id=(SELECT id FROM account WHERE username='$username') AND idempotency_key='$key');
SELECT amount,frozen FROM stockpile WHERE product_id=$productId;
"@
    if ($databaseEvidence.Count -ne 3 -or $databaseEvidence[0] -ne '1' `
        -or $databaseEvidence[1] -ne "1`t2" -or $databaseEvidence[2] -ne "3`t2") {
        throw "Unexpected database result after dual-instance checkout: $($databaseEvidence -join ';')"
    }

    Write-Output "dual_instance_requests=2"
    Write-Output "distinct_orders=1"
    Write-Output "order_items=1"
    Write-Output "frozen_quantity=2"
    Write-Output "replay_responses=1"
    Write-Output "status=PASSED"
} finally {
    try { Remove-TestData } catch { Write-Warning 'Test-data cleanup failed; inspect the ORD-002 marker rows.' }
    foreach ($containerId in $createdContainerIds) {
        if ($containerId) {
            & docker rm -f $containerId 2>$null | Out-Null
        }
    }
}
