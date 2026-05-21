#!/bin/bash

# Build script for Project-KLTN backend
# This script builds all JARs with Maven and then builds Docker images using docker compose

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR"
DEPLOY_DIR="$BACKEND_DIR/_deploy"
ENV_FILE="${1:-.env.docker-compose}"

# Java services that need JAR copying
JAVA_SERVICES=(
    "api-gateway"
    "discovery-server"
    "config-server"
    "auth-service"
    "file-service"
    "profile-service"
    "search-service"
    "notification-service"
    "plant-management-service"
    "iot-metrics-collector-service"
    "iot-test-data-service"
    "community-feed-service"
    "socket-service"
    "message-service"
)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Project-KLTN Backend Build Script${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# Step 1: Maven Build
if [ -z "${SKIP_MAVEN:-}" ]; then
    echo -e "${YELLOW}[1/4] Running Maven build (clean install -DskipTests)...${NC}"
    echo ""

    cd "$BACKEND_DIR"

    mvn_start=$(date +%s)
    mvn clean install -DskipTests

    if [ $? -ne 0 ]; then
        echo -e "${RED}Maven build failed!${NC}"
        exit 1
    fi

    mvn_end=$(date +%s)
    mvn_duration=$((mvn_end - mvn_start))
    mvn_minutes=$((mvn_duration / 60))
    mvn_seconds=$((mvn_duration % 60))

    echo ""
    echo -e "${GREEN}Maven build completed in ${mvn_minutes}m ${mvn_seconds}s${NC}"
    echo ""
else
    echo -e "${YELLOW}[1/4] Skipping Maven build...${NC}"
fi

# Step 2: Copy JARs to service directories
echo -e "${YELLOW}[2/4] Copying JARs to service directories...${NC}"
echo ""

for service in "${JAVA_SERVICES[@]}"; do
    src_dir="$BACKEND_DIR/$service/target"
    dest_dir="$BACKEND_DIR/$service"

    if [ -d "$src_dir" ]; then
        # Find the main JAR (excluding .original files)
        jar_file=$(ls "$src_dir"/*.jar 2>/dev/null | grep -v '.original$' | head -1)
        if [ -n "$jar_file" ]; then
            # Ensure target directory exists
            mkdir -p "$dest_dir/target"
            cp "$jar_file" "$dest_dir/target/"
            echo -e "  Copied $service JAR" >&2
        fi
    else
        echo -e "  Warning: No target directory for $service" >&2
    fi
done

echo ""
echo -e "${GREEN}JARs copied to service directories${NC}"
echo ""

# Step 3: Copy env file
echo -e "${YELLOW}[3/4] Setting up environment file...${NC}"
echo ""

cd "$DEPLOY_DIR"

env_file_path="$DEPLOY_DIR/$ENV_FILE"
if [ -f "$env_file_path" ]; then
    echo -e "Using environment file: $env_file_path"
    echo ""

    # Copy env file to .env (docker compose auto-loads .env)
    cp "$env_file_path" "$DEPLOY_DIR/.env"
    echo -e "Copied env file to .env for docker compose"
else
    echo -e "${YELLOW}Warning: Environment file '$env_file_path' not found!${NC}"
fi

echo ""

# Step 4: Docker Compose Build
echo -e "${YELLOW}[4/4] Building Docker images with docker compose...${NC}"
echo ""

docker_start=$(date +%s)

# Build with docker compose (will auto-load .env)
docker compose build

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker compose build failed!${NC}"
    exit 1
fi

docker_end=$(date +%s)
docker_duration=$((docker_end - docker_start))
docker_minutes=$((docker_duration / 60))
docker_seconds=$((docker_duration % 60))

echo ""
echo -e "${GREEN}Docker build completed in ${docker_minutes}m ${docker_seconds}s${NC}"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
