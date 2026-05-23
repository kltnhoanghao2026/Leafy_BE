#!/bin/bash
# ============================================
# Deployment Script for EC2 Machines
# ============================================
# Usage: ./deploy-ec2.sh <machine-number> [environment]
# Example: ./deploy-ec2.sh 1 production

set -e

MACHINE_NUM=${1:-1}
ENV=${2:-production}
AWS_REGION=${AWS_REGION:-us-east-1}

# Machine configurations
declare -A MACHINE_CONFIG
MACHINE_CONFIG[1]="data|mongodb,postgresql,redis|machine-1-data"
MACHINE_CONFIG[2]="messaging|kafka,mqtt-broker,mqtt-ui|machine-2-messaging"
MACHINE_CONFIG[3]="search-ai|elasticsearch,kibana,qdrant,fluent-bit|machine-3-search-ai"
MACHINE_CONFIG[4]="app|all-services|machine-4-app"

IFS='|' read -r TIER COMPONENTS CLUSTER_DIR <<< "${MACHINE_CONFIG[$MACHINE_NUM]}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${SCRIPT_DIR}/clusters/${CLUSTER_DIR}"
ENV_FILE="${DEPLOY_DIR}/.env"

if [ ! -d "$DEPLOY_DIR" ]; then
    echo "ERROR: Deployment directory not found: $DEPLOY_DIR"
    exit 1
fi

echo "========================================"
echo "Deploying to Machine ${MACHINE_NUM} (${TIER} Tier)"
echo "Environment: $ENV"
echo "========================================"

# Check if .env file exists
if [ ! -f "$ENV_FILE" ]; then
    echo "WARNING: .env file not found at $ENV_FILE"
    echo "Creating from template..."
    if [ -f "${SCRIPT_DIR}/clusters/${CLUSTER_DIR}/.env.template" ]; then
        cp "${SCRIPT_DIR}/clusters/${CLUSTER_DIR}/.env.template" "$ENV_FILE"
        echo "Please edit $ENV_FILE with actual values before deploying"
        exit 1
    fi
fi

# Get EC2 instance IP from AWS
echo "Fetching EC2 instance IP..."
INSTANCE_IP=$(aws ec2 describe-instances \
    --region "$AWS_REGION" \
    --filters "Name=tag:Role,Values=${TIER}-tier" "Name=instance-state-name,Values=running" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' \
    --output text)

if [ -z "$INSTANCE_IP" ]; then
    echo "ERROR: Could not find EC2 instance for ${TIER} tier"
    exit 1
fi

echo "Target EC2 IP: $INSTANCE_IP"

# SSH key check
SSH_KEY="${HOME}/.ssh/leafy-ec2-key.pem"
if [ ! -f "$SSH_KEY" ]; then
    echo "WARNING: SSH key not found at $SSH_KEY"
    echo "Will try to connect anyway..."
fi

# Deploy via SSH
echo "Deploying containers..."
ssh -o StrictHostKeyChecking=no \
    -o ConnectTimeout=30 \
    ${SSH_KEY:+-i "$SSH_KEY"} \
    "ubuntu@${INSTANCE_IP}" << 'ENDSSH'
    set -e

    echo "=== Updating system packages ==="
    sudo apt-get update -y

    echo "=== Installing Docker (if needed) ==="
    if ! command -v docker &> /dev/null; then
        curl -fsSL https://get.docker.com | sh
        sudo usermod -aG docker ubuntu
    fi

    echo "=== Pulling latest images ==="
    docker compose pull

    echo "=== Stopping existing containers ==="
    docker compose down

    echo "=== Starting containers ==="
    docker compose up -d

    echo "=== Checking container status ==="
    docker compose ps

    echo "=== Container logs (last 20 lines) ==="
    docker compose logs --tail=20

    echo "=== Deployment complete ==="
ENDSSH

echo "========================================"
echo "Deployment to Machine ${MACHINE_NUM} complete!"
echo "========================================"

# Health check
sleep 5
echo "Running health check..."
ssh ${SSH_KEY:+-i "$SSH_KEY"} "ubuntu@${INSTANCE_IP}" "docker compose ps"
