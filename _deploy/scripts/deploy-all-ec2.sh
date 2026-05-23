#!/bin/bash
# ============================================
# Full deployment script for all EC2 machines
# ============================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV=${1:-production}

echo "========================================"
echo "Deploying ALL EC2 machines"
echo "Environment: $ENV"
echo "========================================"

# Deploy in parallel
for i in 1 2 3 4; do
    echo "Starting deployment to machine $i in background..."
    bash "${SCRIPT_DIR}/deploy-ec2.sh" "$i" "$ENV" &
done

# Wait for all deployments
echo "Waiting for all deployments to complete..."
wait

echo "========================================"
echo "All EC2 deployments complete!"
echo "========================================"
