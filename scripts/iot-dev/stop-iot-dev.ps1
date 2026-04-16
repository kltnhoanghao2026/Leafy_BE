param(
    [switch]$Volumes
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

$DockerArgs = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile, "down")
if ($Volumes) {
    $DockerArgs += "-v"
}

docker @DockerArgs

if ($Volumes) {
    Write-Host "IoT local/dev stack stopped and volumes removed."
} else {
    Write-Host "IoT local/dev stack stopped. Volumes were preserved."
}
