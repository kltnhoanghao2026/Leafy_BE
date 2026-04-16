param(
    [switch]$SkipStart,
    [switch]$WithAnomaly,
    [string]$TestDataBaseUrl = "http://localhost:8099"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-SeedPost {
    param(
        [string]$Label,
        [string]$Path,
        [object]$Body = $null
    )

    Write-Host ""
    Write-Host "==> $Label"

    $Uri = "$TestDataBaseUrl$Path"
    if ($null -eq $Body) {
        $Response = Invoke-RestMethod -Method Post -Uri $Uri
    } else {
        $Json = $Body | ConvertTo-Json -Depth 8
        $Response = Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Body $Json
    }

    $Response | ConvertTo-Json -Depth 8
}

if (-not $SkipStart) {
    & (Join-Path $ScriptDir "start-iot-dev.ps1")
} else {
    & (Join-Path $ScriptDir "check-iot-dev.ps1")
}

Invoke-SeedPost "Bootstrap minimal demo data" "/seed/bootstrap/minimal"
Invoke-SeedPost "Seed last 7 days of history" "/seed/history/last-7d" @{
    readingsPerHour = 2
    includeAnomalies = $true
}
Invoke-SeedPost "Start live simulation" "/seed/simulation/start" @{
    telemetryIntervalSeconds = 60
    statusIntervalSeconds = 30
    anomaliesEnabled = $true
}

if ($WithAnomaly) {
    Invoke-SeedPost "Trigger high-temperature anomaly" "/seed/scenarios/high-temperature" @{
        deviceUid = "prod-minimal-device-1"
        count = 5
        targetValue = 44.0
    }
}

Write-Host ""
Write-Host "Minimal demo workflow complete."
Write-Host ""
Write-Host "Verification URLs:"
Write-Host "  Collector alert events:    http://localhost:8091/iot/alert-events"
Write-Host "  Collector alert rules:     http://localhost:8091/iot/alert-rules"
Write-Host "  Test-data simulation:      http://localhost:8099/seed/simulation/status"
Write-Host "  Eureka dashboard:          http://localhost:8761"
Write-Host ""
Write-Host "Stop simulation:"
Write-Host "  Invoke-RestMethod -Method Post http://localhost:8099/seed/simulation/stop"
