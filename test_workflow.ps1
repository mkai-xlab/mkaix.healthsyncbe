$ErrorActionPreference = "Stop"

$dicom1 = "d:\Capstone\source\mkaix.healthsyncbe\9000622_20050613_00831903.dcm"
$dicom2 = "d:\Capstone\source\mkaix.healthsyncbe\9000099_20050531_00839603.dcm"

Write-Host "Resetting Database for clean tests..."
docker exec capstone-mysql mysql -u root -pcapstone_root_password capstone -e "SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE dicom_instances; TRUNCATE TABLE images; TRUNCATE TABLE examinations; TRUNCATE TABLE patients; SET FOREIGN_KEY_CHECKS=1;"

Write-Host "Resetting Redis for clean tests..."
docker exec capstone-redis redis-cli FLUSHALL

# --- TEST CASE 1: Accept 1 patient, Reject 1 ---
Write-Host "`n=== TEST CASE 1: Accept 1, Reject 1 ==="
$uploadUri = "http://localhost:8080/api/v1/dicom/upload/batch"
Write-Host "Uploading files..."
$uploadResultJson = curl.exe -s -X POST $uploadUri -F "files=@$dicom1" -F "files=@$dicom2"
Write-Host "Upload Result: $uploadResultJson"

Write-Host "Waiting 5 seconds for async processing..."
Start-Sleep -Seconds 5

# Get Session ID directly from Redis using docker exec
$sessionKeyOutput = docker exec capstone-redis redis-cli KEYS "uploadSession:*"
$sessionKey = $sessionKeyOutput.Trim()
if ([string]::IsNullOrWhiteSpace($sessionKey)) {
    Write-Host "No session found in Redis!"
    exit 1
}
$sessionId = $sessionKey.Split(":")[1]
Write-Host "Found Session ID: $sessionId"

# Fetch patients list from Redis
$sessionDataOutput = docker exec capstone-redis redis-cli GET $sessionKey
$sessionData = $sessionDataOutput | ConvertFrom-Json
$patients = $sessionData.patients.psobject.properties.name
Write-Host "Patients extracted: $($patients -join ', ')"

$acceptedPatient = $patients[0]
$rejectedPatient = $patients[1]

$rejectedPaths = $sessionData.patients.$rejectedPatient.physicalFilePaths.psobject.properties.value
Write-Host "Physical paths for rejected patient:"
$rejectedPaths | ForEach-Object { Write-Host " - $_" }

Write-Host "Verifying session... accepting only $acceptedPatient"
$verifyUri = "http://localhost:8080/api/v1/dicom/verify"
$verifyBody = @{
    uploadSessionId = $sessionId
    acceptedPatientCodes = @($acceptedPatient)
} | ConvertTo-Json

$verifyResult = Invoke-RestMethod -Uri $verifyUri -Method Post -Body $verifyBody -ContentType "application/json"
Write-Host "Verify Result: $verifyResult"

# Assertions
$remKeyOutput = docker exec capstone-redis redis-cli EXISTS $sessionKey
if ($remKeyOutput.Trim() -eq "0") {
    Write-Host "SUCCESS: Redis cache was cleared."
} else {
    Write-Host "ERROR: Redis cache still exists!"
}

foreach ($path in $rejectedPaths) {
    if (Test-Path $path) {
        Write-Host "ERROR: Rejected physical file was NOT deleted: $path"
    } else {
        Write-Host "SUCCESS: Rejected physical file was deleted: $path"
    }
}


# --- TEST CASE 2: Accept ALL ---
Write-Host "`n=== TEST CASE 2: Accept ALL ==="
Write-Host "Uploading files..."
$uploadResultJson2 = curl.exe -s -X POST $uploadUri -F "files=@$dicom1" -F "files=@$dicom2"
Start-Sleep -Seconds 5

$sessionKeyOutput2 = docker exec capstone-redis redis-cli KEYS "uploadSession:*"
$sessionKey2 = $sessionKeyOutput2.Trim()
$sessionId2 = $sessionKey2.Split(":")[1]
Write-Host "Found Session ID: $sessionId2"

$sessionDataOutput2 = docker exec capstone-redis redis-cli GET $sessionKey2
$sessionData2 = $sessionDataOutput2 | ConvertFrom-Json
$patients2 = $sessionData2.patients.psobject.properties.name

Write-Host "Verifying session... accepting ALL patients"
$verifyBody2 = @{
    uploadSessionId = $sessionId2
    acceptedPatientCodes = @($patients2)
} | ConvertTo-Json

$verifyResult2 = Invoke-RestMethod -Uri $verifyUri -Method Post -Body $verifyBody2 -ContentType "application/json"
Write-Host "Verify Result: $verifyResult2"

$remKeyOutput2 = docker exec capstone-redis redis-cli EXISTS $sessionKey2
if ($remKeyOutput2.Trim() -eq "0") {
    Write-Host "SUCCESS: Redis cache was cleared."
} else {
    Write-Host "ERROR: Redis cache still exists!"
}


# --- TEST CASE 3: Doctor forgets (Cleanup Job) ---
Write-Host "`n=== TEST CASE 3: Cleanup Job removes expired data ==="
Write-Host "Uploading files..."
$uploadResultJson3 = curl.exe -s -X POST $uploadUri -F "files=@$dicom1" -F "files=@$dicom2"
Start-Sleep -Seconds 5

$sessionKeyOutput3 = docker exec capstone-redis redis-cli KEYS "uploadSession:*"
$sessionKey3 = $sessionKeyOutput3.Trim()
$sessionId3 = $sessionKey3.Split(":")[1]
Write-Host "Found Session ID: $sessionId3"

$sessionDataOutput3 = docker exec capstone-redis redis-cli GET $sessionKey3
$sessionData3 = $sessionDataOutput3 | ConvertFrom-Json
$allPaths = @()
foreach ($p in $sessionData3.patients.psobject.properties.name) {
    $allPaths += $sessionData3.patients.$p.physicalFilePaths.psobject.properties.value
}

Write-Host "Simulating time passage by modifying Redis ZSET score (to 15 mins ago)..."
$fifteenMinsAgo = [math]::Floor([decimal](Get-Date (Get-Date).AddMinutes(-15) -UFormat %s) * 1000)
docker exec capstone-redis redis-cli ZADD uploadSessionTimeouts $fifteenMinsAgo $sessionId3 | Out-Null

Write-Host "Waiting 65 seconds for the @Scheduled job to trigger and clean up..."
Start-Sleep -Seconds 65

$remKeyOutput3 = docker exec capstone-redis redis-cli EXISTS $sessionKey3
if ($remKeyOutput3.Trim() -eq "0") {
    Write-Host "SUCCESS: Redis cache was automatically cleared by cleanup job."
} else {
    Write-Host "ERROR: Redis cache STILL EXISTS! Cleanup job did not delete it."
}

foreach ($path in $allPaths) {
    if (Test-Path $path) {
        Write-Host "ERROR: Expired physical file was NOT deleted: $path"
    } else {
        Write-Host "SUCCESS: Expired physical file was deleted: $path"
    }
}

Write-Host "`nAll tests completed!"
