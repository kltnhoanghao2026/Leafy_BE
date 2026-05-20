#!/bin/bash
# ===========================================
# Deploy Script for EC2 Cluster
# Usage: ./deploy.sh <cluster-number|all|status|logs|stop>
# ===========================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${SCRIPT_DIR}"
NETWORK_NAME="leafy-network"

# EC2 Host mappings (update with your EC2 private IPs)
declare -A CLUSTER_HOSTS=(
    ["cluster-1"]="ec2-gateway"
    ["cluster-2"]="ec2-data"
    ["cluster-3"]="ec2-services"
    ["cluster-4"]="ec2-ml"
)

print_usage() {
    echo "Usage: ./deploy.sh <command> [cluster]"
    echo ""
    echo "Commands:"
    echo "  network     - Create Docker network"
    echo "  deploy      - Deploy cluster(s)"
    echo "  status      - Show status of services"
    echo "  logs        - Show logs"
    echo "  stop        - Stop services"
    echo "  restart     - Restart services"
    echo "  update      - Pull latest images and redeploy"
    echo "  health      - Run health checks"
    echo ""
    echo "Clusters:"
    echo "  cluster-1   - Gateway & Core (Discovery, Config, API Gateway, Auth)"
    echo "  cluster-2   - Data (MongoDB, PostgreSQL, Redis)"
    echo "  cluster-3   - Services (Plant, Search, Notification, Socket, etc.)"
    echo "  cluster-4   - ML (Kafka, Elasticsearch, Qdrant, Jenkins, ML Services)"
    echo "  all         - All clusters"
    echo ""
    echo "Examples:"
    echo "  ./deploy.sh network"
    echo "  ./deploy.sh deploy cluster-2"
    echo "  ./deploy.sh deploy all"
    echo "  ./deploy.sh logs cluster-1"
    echo "  ./deploy.sh health"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create Docker network
create_network() {
    log_info "Creating Docker network: ${NETWORK_NAME}"
    if docker network inspect ${NETWORK_NAME} > /dev/null 2>&1; then
        log_warn "Network ${NETWORK_NAME} already exists"
    else
        docker network create --driver bridge ${NETWORK_NAME}
        log_info "Network created successfully"
    fi
}

# Deploy a specific cluster
deploy_cluster() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    log_info "Deploying ${cluster}..."
    log_info "Compose file: ${compose_file}"

    # Check if .env exists
    if [ ! -f "${COMPOSE_DIR}/.env" ]; then
        log_warn ".env file not found, using defaults"
    fi

    # Pull latest images
    log_info "Pulling latest images..."
    docker-compose -f "${compose_file}" pull || true

    # Deploy
    docker-compose -f "${compose_file}" up -d

    log_info "${cluster} deployed successfully"
}

# Show status of services
show_status() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    echo ""
    echo "=========================================="
    echo " Status: ${cluster}"
    echo "=========================================="
    docker-compose -f "${compose_file}" ps
}

# Show logs
show_logs() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    docker-compose -f "${compose_file}" logs -f --tail=100
}

# Stop services
stop_services() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    log_info "Stopping ${cluster}..."
    docker-compose -f "${compose_file}" down
    log_info "${cluster} stopped"
}

# Restart services
restart_services() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    log_info "Restarting ${cluster}..."
    docker-compose -f "${compose_file}" restart
    log_info "${cluster} restarted"
}

# Update and redeploy
update_cluster() {
    local cluster=$1
    local compose_file="docker-compose.${cluster}.yml"

    if [ ! -f "${COMPOSE_DIR}/${compose_file}" ]; then
        log_error "Compose file not found: ${compose_file}"
        return 1
    fi

    log_info "Updating ${cluster}..."
    docker-compose -f "${compose_file}" pull
    docker-compose -f "${compose_file}" up -d
    log_info "${cluster} updated"
}

# Health check
health_check() {
    echo ""
    echo "=========================================="
    echo " Health Check - All Clusters"
    echo "=========================================="
    echo ""

    local failed=0

    for cluster in cluster-1 cluster-2 cluster-3 cluster-4; do
        local compose_file="docker-compose.${cluster}.yml"
        if [ -f "${COMPOSE_DIR}/${compose_file}" ]; then
            echo -e "${GREEN}Checking ${cluster}...${NC}"
            if docker-compose -f "${compose_file}" ps --format json 2>/dev/null | jq -r 'select(.Service != null) | "\(.Service): \(.State)"' 2>/dev/null; then
                echo ""
            else
                echo "No running services in ${cluster}"
            fi
        fi
    done

    if [ $failed -eq 0 ]; then
        log_info "All health checks passed!"
    else
        log_error "Some health checks failed"
    fi
}

# Deploy order matters!
deploy_order() {
    log_info "Deploying in correct order..."

    log_info "Step 1: Deploying Cluster 2 (Data)..."
    deploy_cluster "cluster-2"
    log_info "Waiting 60 seconds for data services..."
    sleep 60

    log_info "Step 2: Deploying Cluster 1 (Gateway)..."
    deploy_cluster "cluster-1"
    log_info "Waiting 60 seconds for core services..."
    sleep 60

    log_info "Step 3: Deploying Cluster 3 (Services)..."
    deploy_cluster "cluster-3"
    log_info "Waiting 30 seconds for business services..."
    sleep 30

    log_info "Step 4: Deploying Cluster 4 (ML)..."
    deploy_cluster "cluster-4"

    log_info "All clusters deployed!"
}

# Main
COMMAND=${1:-}
CLUSTER=${2:-}

case "$COMMAND" in
    network)
        create_network
        ;;
    deploy)
        if [ -z "$CLUSTER" ]; then
            log_error "Cluster is required"
            print_usage
            exit 1
        fi
        if [ "$CLUSTER" == "all" ]; then
            create_network
            deploy_order
        else
            deploy_cluster "$CLUSTER"
        fi
        ;;
    status)
        if [ -z "$CLUSTER" ]; then
            for c in cluster-1 cluster-2 cluster-3 cluster-4; do
                show_status "$c"
                echo ""
            done
        else
            show_status "$CLUSTER"
        fi
        ;;
    logs)
        if [ -z "$CLUSTER" ]; then
            log_error "Cluster is required"
            print_usage
            exit 1
        fi
        show_logs "$CLUSTER"
        ;;
    stop)
        if [ -z "$CLUSTER" ]; then
            log_error "Cluster is required"
            print_usage
            exit 1
        fi
        if [ "$CLUSTER" == "all" ]; then
            for c in cluster-4 cluster-3 cluster-1 cluster-2; do
                stop_services "$c"
            done
        else
            stop_services "$CLUSTER"
        fi
        ;;
    restart)
        if [ -z "$CLUSTER" ]; then
            log_error "Cluster is required"
            print_usage
            exit 1
        fi
        restart_services "$CLUSTER"
        ;;
    update)
        if [ -z "$CLUSTER" ]; then
            log_error "Cluster is required"
            print_usage
            exit 1
        fi
        if [ "$CLUSTER" == "all" ]; then
            for c in cluster-1 cluster-2 cluster-3 cluster-4; do
                update_cluster "$c"
            done
        else
            update_cluster "$CLUSTER"
        fi
        ;;
    health)
        health_check
        ;;
    *)
        print_usage
        ;;
esac
