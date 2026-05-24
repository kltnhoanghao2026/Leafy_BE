#!/bin/bash
# =============================================================================
# Leafy Backend Services - Kubernetes Deployment Script
# =============================================================================
# Usage:
#   ./deploy-all.sh              # Deploy all services
#   ./deploy-all.sh auth-service # Deploy specific service
#   ./deploy-all.sh --dry-run    # Show what would be deployed
#   ./deploy-all.sh --status     # Show deployment status
#   ./deploy-all.sh --restart    # Restart all services
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="leafy"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="${SCRIPT_DIR}"

# Service order (dependencies first)
SERVICES=(
    "config-server"
    "discovery-server"
    "api-gateway"
    "auth-service"
    "file-service"
    "notification-service"
    "profile-service"
    "search-service"
    "plant-management-service"
    "community-feed-service"
    "disease-detection-service"
    "rag-service"
    "socket-service"
    "message-service"
    "iot-metrics-collector-service"
)

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo ""
    echo "========================================"
    echo " $1"
    echo "========================================"
}

# =============================================================================
# Deployment Functions
# =============================================================================

deploy_service() {
    local service=$1
    local deployment_file="${K8S_DIR}/${service}/k8s/deployment.yaml"

    if [[ ! -f "$deployment_file" ]]; then
        log_warn "Deployment file not found for ${service}: ${deployment_file}"
        return 1
    fi

    log_info "Deploying ${service}..."
    
    if [[ "$DRY_RUN" == "true" ]]; then
        echo "  kubectl apply -f ${deployment_file} -n ${NAMESPACE}"
        return 0
    fi

    kubectl apply -f "$deployment_file" -n "$NAMESPACE"
    
    if [[ $? -eq 0 ]]; then
        log_success "Deployed ${service}"
    else
        log_error "Failed to deploy ${service}"
        return 1
    fi
}

deploy_all_services() {
    print_header "Deploying All Services to ${NAMESPACE}"
    
    local failed=0
    
    for service in "${SERVICES[@]}"; do
        if ! deploy_service "$service"; then
            ((failed++))
        fi
    done
    
    if [[ $failed -eq 0 ]]; then
        log_success "All services deployed successfully!"
    else
        log_error "$failed service(s) failed to deploy"
        return 1
    fi
}

show_status() {
    print_header "Service Status in ${NAMESPACE}"
    
    echo ""
    printf "%-40s %-15s %-15s\n" "SERVICE" "READY" "STATUS"
    echo "------------------------------------------------------------------------"
    
    for service in "${SERVICES[@]}"; do
        local deployment_name="leafy-${service}"
        
        # Get deployment status
        local ready=$(kubectl get deployment "$deployment_name" -n "$NAMESPACE" 2>/dev/null | awk 'NR==2 {print $2}' || echo "N/A")
        local available=$(kubectl get deployment "$deployment_name" -n "$NAMESPACE" 2>/dev/null | awk 'NR==2 {print $5}' || echo "Unknown")
        
        # Color code based on status
        local color="$NC"
        if [[ "$ready" == "1/1" ]] || [[ "$ready" == "1/1" ]]; then
            color="$GREEN"
        elif [[ "$ready" == "N/A" ]]; then
            color="$RED"
        else
            color="$YELLOW"
        fi
        
        printf "${color}%-40s %-15s %-15s${NC}\n" "$service" "$ready" "$available"
    done
    
    echo ""
}

show_pods() {
    print_header "Pods Status in ${NAMESPACE}"
    kubectl get pods -n "$NAMESPACE" --sort-by=.metadata.creationTimestamp
    echo ""
}

restart_services() {
    print_header "Restarting All Services"
    
    for service in "${SERVICES[@]}"; do
        local deployment_name="leafy-${service}"
        log_info "Restarting ${service}..."
        kubectl rollout restart deployment/"$deployment_name" -n "$NAMESPACE"
    done
    
    log_success "All services restart initiated"
    echo ""
    log_info "Use './deploy-all.sh --watch' to monitor rollout status"
}

watch_rollout() {
    print_header "Watching Rollout Status"
    
    for service in "${SERVICES[@]}"; do
        local deployment_name="leafy-${service}"
        log_info "Checking rollout for ${service}..."
        kubectl rollout status deployment/"$deployment_name" -n "$NAMESPACE" --timeout=300s
    done
    
    log_success "All rollouts complete!"
}

logs_service() {
    local service=$1
    local deployment_name="leafy-${service}"
    
    log_info "Fetching logs for ${service}..."
    kubectl logs -l "app=${service}" -n "$NAMESPACE" --tail=100 -f
}

undeploy_service() {
    local service=$1
    local deployment_name="leafy-${service}"
    
    log_warn "Deleting ${service}..."
    kubectl delete deployment "$deployment_name" -n "$NAMESPACE"
    log_success "Deleted ${service}"
}

undeploy_all() {
    print_header "Undeploying All Services"
    
    for service in "${SERVICES[@]}"; do
        undeploy_service "$service"
    done
    
    log_success "All services undeployed!"
}

# =============================================================================
# Main Script
# =============================================================================

usage() {
    echo "Usage: $0 [OPTIONS] [SERVICE_NAME]"
    echo ""
    echo "Options:"
    echo "  --all           Deploy all services (default)"
    echo "  --dry-run        Show what would be deployed"
    echo "  --status        Show deployment status"
    echo "  --pods          Show pods status"
    echo "  --restart       Restart all services"
    echo "  --watch         Watch rollout status"
    echo "  --logs          Show logs for a service"
    echo "  --undeploy      Delete all services"
    echo "  --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                          # Deploy all services"
    echo "  $0 auth-service             # Deploy specific service"
    echo "  $0 --status                 # Show status"
    echo "  $0 --restart                # Restart all"
    echo "  $0 --logs auth-service      # Tail logs for auth-service"
}

# Parse arguments
ACTION=""
SERVICE=""
DRY_RUN="false"

if [[ $# -eq 0 ]]; then
    ACTION="deploy-all"
else
    while [[ $# -gt 0 ]]; do
        case $1 in
            --all)
                ACTION="deploy-all"
                shift
                ;;
            --dry-run)
                DRY_RUN="true"
                ACTION="deploy-all"
                shift
                ;;
            --status)
                ACTION="status"
                shift
                ;;
            --pods)
                ACTION="pods"
                shift
                ;;
            --restart)
                ACTION="restart"
                shift
                ;;
            --watch)
                ACTION="watch"
                shift
                ;;
            --logs)
                ACTION="logs"
                shift
                ;;
            --undeploy)
                ACTION="undeploy"
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            -*)
                log_error "Unknown option: $1"
                usage
                exit 1
                ;;
            *)
                SERVICE="$1"
                shift
                ;;
        esac
    done
fi

# Execute action
case $ACTION in
    deploy-all)
        if [[ -n "$SERVICE" ]]; then
            deploy_service "$SERVICE"
        else
            deploy_all_services
        fi
        ;;
    status)
        show_status
        ;;
    pods)
        show_pods
        ;;
    restart)
        restart_services
        ;;
    watch)
        watch_rollout
        ;;
    logs)
        if [[ -z "$SERVICE" ]]; then
            log_error "Service name required for --logs"
            usage
            exit 1
        fi
        logs_service "$SERVICE"
        ;;
    undeploy)
        undeploy_all
        ;;
    *)
        usage
        ;;
esac
