# Build script for Project-KLTN backend
# This script builds all JARs with Maven and then builds Docker images using docker compose

param(
    [switch]$SkipDocker,
    [switch]$SkipMaven,
    [string]$EnvFile = ".env.docker-compose"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = $ScriptDir
$DeployDir = Join-Path $BackendDir "_deploy"

# Java services that need JAR copying
$JavaServices = @(
    "api-gateway",
    "discovery-server",
    "config-server",
    "auth-service",
    "file-service",
    "profile-service",
    "search-service",
    "notification-service",
    "plant-management-service",
    "iot-metrics-collector-service",
    "iot-test-data-service",
    "community-feed-service",
    "socket-service",
    "message-service"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Project-KLTN Backend Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Maven Build
if (-not $SkipMaven) {
    Write-Host "[1/3] Running Maven build (clean install -DskipTests)..." -ForegroundColor Yellow
    Write-Host ""

    Set-Location $BackendDir

    $mvnStart = Get-Date
    & mvn clean install -DskipTests

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Maven build failed!" -ForegroundColor Red
        exit 1
    }

    $mvnDuration = (Get-Date) - $mvnStart
    Write-Host ""
    Write-Host "Maven build completed in $($mvnDuration.ToString('mm\:ss'))" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "[1/3] Skipping Maven build..." -ForegroundColor Gray
}

# Step 2: Copy JARs to service directories (if not already present)
Write-Host "[2/3] Checking JARs in service directories..." -ForegroundColor Yellow
Write-Host ""

$copiedAny = $false
foreach ($service in $JavaServices) {
    $srcDir = Join-Path $BackendDir "$service\target"
    $destDir = Join-Path $BackendDir $service

    if (Test-Path $srcDir) {
        # Find the main JAR (excluding .original files)
        $jarFiles = Get-ChildItem -Path $srcDir -Filter "*.jar" | Where-Object { $_.Name -notmatch '\.original$' }
        if ($jarFiles) {
            $destTarget = Join-Path $destDir "target"
            if (-not (Test-Path $destTarget)) {
                New-Item -ItemType Directory -Path $destTarget -Force | Out-Null
            }

            $destJar = Join-Path $destTarget $jarFiles[0].Name
            if (-not (Test-Path $destJar)) {
                Copy-Item -Path $jarFiles[0].FullName -Destination $destTarget -Force
                Write-Host "  Copied $service JAR" -ForegroundColor Gray
                $copiedAny = $true
            } else {
                Write-Host "  $service JAR already present" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "  Warning: No target directory for $service" -ForegroundColor Yellow
    }
}

if (-not $copiedAny) {
    Write-Host "All JARs are already in place" -ForegroundColor Gray
}

Write-Host ""
Write-Host "JARs checked" -ForegroundColor Green
Write-Host ""

# Step 3: Docker Compose Build
if (-not $SkipDocker) {
    Write-Host "[3/3] Building Docker images with docker compose..." -ForegroundColor Yellow
    Write-Host ""

    Set-Location $DeployDir

    # Resolve full path to env file
    $envFilePath = Join-Path $DeployDir $EnvFile
    $targetEnvPath = Join-Path $DeployDir ".env"

    if (Test-Path $envFilePath) {
        Write-Host "Using environment file: $envFilePath" -ForegroundColor Gray
        Write-Host ""

        # Copy env file to .env (docker compose auto-loads .env)
        Copy-Item -Path $envFilePath -Destination $targetEnvPath -Force
        Write-Host "Copied env file to .env for docker compose" -ForegroundColor Gray
    } else {
        Write-Host "Warning: Environment file '$envFilePath' not found!" -ForegroundColor Yellow
    }

    Write-Host ""

    $dockerStart = Get-Date

    # Build with docker compose (will auto-load .env)
    & docker compose build

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker compose build failed!" -ForegroundColor Red
        exit 1
    }

    $dockerDuration = (Get-Date) - $dockerStart
    Write-Host ""
    Write-Host "Docker build completed in $($dockerDuration.ToString('mm\:ss'))" -ForegroundColor Green
} else {
    Write-Host "[3/3] Skipping Docker build..." -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
