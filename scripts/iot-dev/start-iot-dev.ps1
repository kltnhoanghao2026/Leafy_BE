$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "../..")
$ComposeFile = Join-Path $RootDir "docker-compose.iot-dev.yml"
$EnvFile = Join-Path $RootDir ".env.iot-dev"
$ExampleEnvFile = Join-Path $RootDir ".env.iot-dev.example"

if (-not (Test-Path $EnvFile)) {
    Copy-Item $ExampleEnvFile $EnvFile
    Write-Host "Created .env.iot-dev from .env.iot-dev.example."
}

Write-Host "Starting IoT local/dev stack..."
docker compose --env-file $EnvFile -f $ComposeFile up -d --build

Write-Host ""
& (Join-Path $ScriptDir "check-iot-dev.ps1")

Write-Host ""
Write-Host "Key URLs:"
Write-Host "  Eureka:            http://localhost:8761"
Write-Host "  Config Server:     http://localhost:8888/actuator/health"
Write-Host "  Collector:         http://localhost:8091"
Write-Host "  Test Data Service: http://localhost:8099"
Write-Host ""
Write-Host "Next commands:"
Write-Host "  .\scripts\iot-dev\demo-minimal.ps1 -WithAnomaly"
Write-Host "  .\scripts\iot-dev\demo-full.ps1 -WithConfigAck"
