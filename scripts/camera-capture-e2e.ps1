param(
    [string]$CollectorBaseUrl = "http://localhost:8091",
    [string]$TestDataBaseUrl = "http://localhost:8099",
    [string]$DeviceId,
    [string]$DeviceUid = "leafy-prototype-001",
    [string]$MqttHost = "localhost",
    [int]$MqttPort = 1883,
    [string]$MqttEnv = "prod",
    [string]$MqttUser = "",
    [string]$MqttPassword = "",
    [string]$MosquittoPub = "mosquitto_pub",
    [string]$MosquittoSub = "mosquitto_sub",
    [string]$Resolution = "VGA",
    [string]$Quality = "MEDIUM",
    [switch]$IncludeFailure,
    [switch]$SkipTestDataSmoke,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "== $Message" -ForegroundColor Cyan
}

function Invoke-Json([string]$Method, [string]$Url, $Body = $null) {
    $headers = @{
        "Content-Type" = "application/json"
        "X-User-Id" = "iot-e2e-test"
    }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
    }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body ($Body | ConvertTo-Json -Depth 12)
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Resolve-DeviceId() {
    if (-not [string]::IsNullOrWhiteSpace($DeviceId)) {
        return $DeviceId
    }
    $devices = Invoke-Json "GET" "$CollectorBaseUrl/iot/devices/me?size=100"
    $items = @()
    if ($devices.content) {
        $items = @($devices.content)
    } elseif ($devices.items) {
        $items = @($devices.items)
    }
    $match = $items | Where-Object { $_.deviceUid -eq $DeviceUid } | Select-Object -First 1
    Assert-True ($null -ne $match) "Could not resolve deviceId for deviceUid=$DeviceUid. Pass -DeviceId explicitly."
    return $match.id
}

function New-MqttArgs([string]$Topic) {
    $args = @("-h", $MqttHost, "-p", "$MqttPort", "-t", $Topic, "-q", "1")
    if (-not [string]::IsNullOrWhiteSpace($MqttUser)) {
        $args += @("-u", $MqttUser)
    }
    if (-not [string]::IsNullOrWhiteSpace($MqttPassword)) {
        $args += @("-P", $MqttPassword)
    }
    return $args
}

function Start-MqttCapture([string]$Topic) {
    $outputFile = New-TemporaryFile
    $args = New-MqttArgs $Topic
    $args += @("-C", "1", "-W", "$TimeoutSeconds")
    $process = Start-Process -FilePath $MosquittoSub -ArgumentList $args -RedirectStandardOutput $outputFile.FullName -PassThru -WindowStyle Hidden
    Start-Sleep -Milliseconds 500
    return [pscustomobject]@{
        Process = $process
        OutputFile = $outputFile.FullName
        Topic = $Topic
    }
}

function Read-MqttCapture($Capture) {
    $Capture.Process.WaitForExit($TimeoutSeconds * 1000) | Out-Null
    $text = ""
    if (Test-Path $Capture.OutputFile) {
        $text = (Get-Content -Path $Capture.OutputFile -Raw).Trim()
    }
    Remove-Item -Path $Capture.OutputFile -Force -ErrorAction SilentlyContinue
    Assert-True (-not [string]::IsNullOrWhiteSpace($text)) "No MQTT payload captured on topic $($Capture.Topic)."
    return $text | ConvertFrom-Json
}

function Publish-ImageMeta(
    [string]$RequestId,
    [string]$TriggerType,
    [string]$Status = "SUCCESS",
    [string]$ErrorMessage = $null
) {
    $topic = "coffee/$MqttEnv/devices/$DeviceUid/image/meta"
    $success = $Status -eq "SUCCESS"
    $payload = [ordered]@{
        deviceUid = $DeviceUid
        requestId = $RequestId
        triggerType = $TriggerType
        timestamp = (Get-Date).ToUniversalTime().ToString("o")
        ts = (Get-Date).ToUniversalTime().ToString("o")
        status = $Status
        success = $success
        fileId = if ($success) { "mock-file-$([guid]::NewGuid())" } else { $null }
        contentType = "image/jpeg"
        sizeBytes = if ($success) { 43008 } else { 0 }
        width = 640
        height = 480
        errorMessage = $ErrorMessage
        error = $ErrorMessage
    }
    $payloadFile = New-TemporaryFile
    ($payload | ConvertTo-Json -Depth 8) | Set-Content -Path $payloadFile.FullName -Encoding UTF8
    $args = New-MqttArgs $topic
    $args += @("-f", $payloadFile.FullName)
    & $MosquittoPub @args
    $exitCode = $LASTEXITCODE
    Remove-Item -Path $payloadFile.FullName -Force -ErrorAction SilentlyContinue
    Assert-True ($exitCode -eq 0) "mosquitto_pub failed for topic $topic with exitCode=$exitCode."
    return $payload
}

function Wait-MediaStatus([string]$DeviceIdValue, [string]$RequestId, [string]$ExpectedStatus) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $media = @(Invoke-Json "GET" "$CollectorBaseUrl/iot/devices/$DeviceIdValue/media")
        $event = $media | Where-Object { $_.requestId -eq $RequestId } | Select-Object -First 1
        if ($event -and $event.status -eq $ExpectedStatus) {
            return $event
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for media requestId=$RequestId to reach status=$ExpectedStatus."
}

function Assert-CaptureCommand($Command, [string]$ExpectedTriggerType, [string]$ExpectedDeviceUid) {
    Assert-True ($Command.deviceUid -eq $ExpectedDeviceUid) "MQTT command deviceUid mismatch."
    Assert-True ($Command.triggerType -eq $ExpectedTriggerType) "MQTT command triggerType mismatch. expected=$ExpectedTriggerType actual=$($Command.triggerType)"
    Assert-True (-not [string]::IsNullOrWhiteSpace($Command.requestId)) "MQTT command requestId is missing."
    Assert-True ($Command.resolution -eq $Resolution) "MQTT command resolution mismatch."
    Assert-True ($Command.quality -eq $Quality) "MQTT command quality mismatch."
}

function Test-ManualCapture([string]$DeviceIdValue) {
    Write-Step "Manual capture through collector -> MQTT -> image/meta -> media API"
    $commandTopic = "coffee/$MqttEnv/devices/$DeviceUid/camera/capture"
    $capture = Start-MqttCapture $commandTopic
    $response = Invoke-Json "POST" "$CollectorBaseUrl/iot/devices/$DeviceIdValue/camera/capture" @{
        resolution = $Resolution
        quality = $Quality
    }
    $command = Read-MqttCapture $capture
    Assert-CaptureCommand $command "MANUAL" $DeviceUid
    Assert-True ($command.requestId -eq $response.requestId) "Collector response requestId does not match MQTT command requestId."
    $metadata = Publish-ImageMeta $response.requestId "MANUAL"
    $event = Wait-MediaStatus $DeviceIdValue $response.requestId "UPLOADED"
    Assert-True ($event.fileId -eq $metadata.fileId) "Media event fileId did not match published metadata."
    Assert-True ($event.sizeBytes -eq $metadata.sizeBytes) "Media event sizeBytes did not match metadata."
    Write-Host "Manual capture passed: requestId=$($response.requestId), fileId=$($event.fileId)"

    Publish-ImageMeta $response.requestId "MANUAL" | Out-Null
    $media = @(Invoke-Json "GET" "$CollectorBaseUrl/iot/devices/$DeviceIdValue/media")
    $sameRequestEvents = @($media | Where-Object { $_.requestId -eq $response.requestId })
    Assert-True ($sameRequestEvents.Count -eq 1) "Duplicate media event found after repeated image/meta for same requestId."
    Write-Host "Manual idempotency check passed for duplicate image/meta."
}

function Test-ScheduledCapture([string]$DeviceIdValue) {
    Write-Step "Scheduled run-now capture through collector schedule API"
    $schedule = Invoke-Json "POST" "$CollectorBaseUrl/iot/camera-schedules" @{
        deviceUid = $DeviceUid
        enabled = $true
        triggerType = "SCHEDULED"
        timeOfDay = (Get-Date).AddMinutes(10).ToString("HH:mm:ss")
        recurrence = "DAILY"
    }

    $commandTopic = "coffee/$MqttEnv/devices/$DeviceUid/camera/capture"
    $capture = Start-MqttCapture $commandTopic
    Invoke-Json "POST" "$CollectorBaseUrl/iot/camera-schedules/$($schedule.id)/run-now" | Out-Null
    $command = Read-MqttCapture $capture
    Assert-CaptureCommand $command "SCHEDULED" $DeviceUid
    Publish-ImageMeta $command.requestId "SCHEDULED" | Out-Null
    $event = Wait-MediaStatus $DeviceIdValue $command.requestId "UPLOADED"
    Assert-True ($event.triggerType -eq "SCHEDULED") "Scheduled media event triggerType mismatch."

    $schedules = @(Invoke-Json "GET" "$CollectorBaseUrl/iot/camera-schedules")
    $updated = $schedules | Where-Object { $_.id -eq $schedule.id } | Select-Object -First 1
    Assert-True ($null -ne $updated.lastRunAt) "Schedule lastRunAt was not set."
    Assert-True ($null -ne $updated.nextRunAt) "Schedule nextRunAt was not set."
    Write-Host "Scheduled capture passed: scheduleId=$($schedule.id), requestId=$($command.requestId)"
}

function Test-FailureCapture([string]$DeviceIdValue) {
    Write-Step "Failure metadata path"
    $commandTopic = "coffee/$MqttEnv/devices/$DeviceUid/camera/capture"
    $capture = Start-MqttCapture $commandTopic
    $response = Invoke-Json "POST" "$CollectorBaseUrl/iot/devices/$DeviceIdValue/camera/capture" @{
        resolution = $Resolution
        quality = $Quality
    }
    Read-MqttCapture $capture | Out-Null
    Publish-ImageMeta $response.requestId "MANUAL" "FAILURE" "SIMULATED_UPLOAD_FAILED" | Out-Null
    $event = Wait-MediaStatus $DeviceIdValue $response.requestId "FAILED"
    Assert-True ($event.error -eq "SIMULATED_UPLOAD_FAILED") "Failure error did not match simulated upload error."
    Write-Host "Failure path passed: requestId=$($response.requestId), error=$($event.error)"
}

function Test-TestDataSimulationSmoke() {
    if ($SkipTestDataSmoke) {
        return
    }
    Write-Step "IoT Test Data Service camera simulation smoke"
    $manual = Invoke-Json "POST" "$TestDataBaseUrl/seed/scenarios/camera-capture-manual" @{
        deviceUid = $DeviceUid
        resolution = "QVGA"
        quality = "LOW"
        count = 2
    }
    Assert-True ($manual.captures.Count -eq 2) "Test data manual simulation did not return two captures."
    Assert-True ($manual.captures[0].triggerType -eq "MANUAL") "Test data manual triggerType mismatch."

    $scheduled = Invoke-Json "POST" "$TestDataBaseUrl/seed/scenarios/camera-capture-scheduled?run-now=true" @{
        deviceUid = $DeviceUid
        timeOfDay = (Get-Date).AddMinutes(15).ToString("HH:mm:ss")
        recurrence = "DAILY"
        resolution = "VGA"
        quality = "MEDIUM"
    }
    Assert-True ($scheduled.triggerType -eq "SCHEDULED") "Test data scheduled triggerType mismatch."
    Assert-True ($scheduled.captures.Count -eq 1) "Test data scheduled run-now did not publish one capture."
    Write-Host "Test data service smoke passed."
}

Write-Step "Resolving device"
$resolvedDeviceId = Resolve-DeviceId
Write-Host "Using deviceUid=$DeviceUid deviceId=$resolvedDeviceId"

Test-ManualCapture $resolvedDeviceId
Test-ScheduledCapture $resolvedDeviceId
if ($IncludeFailure) {
    Test-FailureCapture $resolvedDeviceId
}
Test-TestDataSimulationSmoke

Write-Step "Camera capture E2E checks completed"
Write-Host "Firmware retry validation still requires ESP32 serial logs because firmware rebuild/hardware execution is out of scope for this simulated harness."
