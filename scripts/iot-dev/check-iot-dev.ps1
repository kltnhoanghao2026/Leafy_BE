param(
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "../..")
$ComposeFile = Join-Path $RootDir "docker-compose.iot-dev.yml"
$EnvFile = Join-Path $RootDir ".env.iot-dev"
$ExampleEnvFile = Join-Path $RootDir ".env.iot-dev.example"

if (-not (Test-Path $EnvFile)) {
    Write-Host "Missing .env.iot-dev. Create it with:"
    Write-Host "  Copy-Item `"$ExampleEnvFile`" `"$EnvFile`""
    exit 1
}

function Wait-ForUrl {
    param(
        [string]$Name,
        [string]$Url
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Write-Host -NoNewline "Waiting for $Name at $Url"

    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            Write-Host " ready"
            return
        } catch {
            Write-Host -NoNewline "."
            Start-Sleep -Seconds 3
        }
    }

    Write-Host ""
    throw "$Name did not become reachable within ${TimeoutSeconds}s"
}

Wait-ForUrl "Config Server" "http://localhost:8888/actuator/health"
Wait-ForUrl "Eureka" "http://localhost:8761"
Wait-ForUrl "IoT collector" "http://localhost:8091/iot/alert-events"
Wait-ForUrl "IoT test-data service" "http://localhost:8099/seed/simulation/status"

Write-Host ""
Write-Host "Container status:"
docker compose --env-file $EnvFile -f $ComposeFile ps

Write-Host ""
Write-Host "IoT local/dev stack is reachable."
