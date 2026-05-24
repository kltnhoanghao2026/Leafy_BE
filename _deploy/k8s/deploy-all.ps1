# =============================================================================
# Leafy Backend Services - Kubernetes Deployment Script (PowerShell)
# =============================================================================
# Usage:
#   .\deploy-all.ps1                # Deploy all services
#   .\deploy-all.ps1 -Service auth-service  # Deploy specific service
#   .\deploy-all.ps1 -DryRun        # Show what would be deployed
#   .\deploy-all.ps1 -Status        # Show deployment status
#   .\deploy-all.ps1 -Restart       # Restart all services
#   .\deploy-all.ps1 -Watch         # Watch rollout status
#   .\deploy-all.ps1 -Logs          # Show logs for a service
#   .\deploy-all.ps1 -Undeploy      # Delete all services
# =============================================================================

param(
    [Parameter(Mandatory=$false)]
    [string]$Service = "",
    
    [Parameter(Mandatory=$false)]
    [switch]$DryRun,
    
    [Parameter(Mandatory=$false)]
    [switch]$Status,
    
    [Parameter(Mandatory=$false)]
    [switch]$Pods,
    
    [Parameter(Mandatory=$false)]
    [switch]$Restart,
    
    [Parameter(Mandatory=$false)]
    [switch]$Watch,
    
    [Parameter(Mandatory=$false)]
    [switch]$Logs,
    
    [Parameter(Mandatory=$false)]
    [switch]$Undeploy
)

# Configuration
$Namespace = "leafy"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Service order (dependencies first)
$Services = @(
    "config-server",
    "discovery-server",
    "api-gateway",
    "auth-service",
    "file-service",
    "notification-service",
    "profile-service",
    "search-service",
    "plant-management-service",
    "community-feed-service",
    "disease-detection-service",
    "rag-service",
    "socket-service",
    "message-service",
    "iot-metrics-collector-service"
)

# Colors for output (PowerShell console)
$colors = @{
    RED     = 'Red'
    GREEN   = 'Green'
    YELLOW  = 'Yellow'
    BLUE    = 'Cyan'
    NC      = 'White'
}

# =============================================================================
# Helper Functions
# =============================================================================

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    
    $color = $colors[$Level]
    Write-Host "[$Level] $Message" -ForegroundColor $color
}

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

# =============================================================================
# Deployment Functions
# =============================================================================

function Deploy-Service {
    param([string]$ServiceName)
    
    $deploymentFile = Join-Path $ScriptDir "$ServiceName\k8s\deployment.yaml"
    
    if (-not (Test-Path $deploymentFile)) {
        Write-Log "Deployment file not found: $deploymentFile" "WARN"
        return $false
    }
    
    Write-Log "Deploying $ServiceName..."
    
    if ($DryRun) {
        Write-Host "  kubectl apply -f $deploymentFile -n $Namespace" -ForegroundColor Gray
        return $true
    }
    
    try {
        kubectl apply -f $deploymentFile -n $Namespace 2>&1 | Out-Null
        Write-Log "Deployed $ServiceName" "GREEN"
        return $true
    }
    catch {
        Write-Log "Failed to deploy $ServiceName : $_" "RED"
        return $false
    }
}

function Deploy-AllServices {
    Write-Header "Deploying All Services to $Namespace"
    
    $failed = 0
    $success = 0
    
    foreach ($svc in $Services) {
        if (Deploy-Service -ServiceName $svc) {
            $success++
        } else {
            $failed++
        }
    }
    
    if ($failed -eq 0) {
        Write-Log "All services deployed successfully!" "GREEN"
    } else {
        Write-Log "$failed service(s) failed to deploy" "RED"
        return 1
    }
}

function Show-Status {
    Write-Header "Service Status in $Namespace"
    Write-Host ""
    
    # Header
    $header = "{0,-40} {1,-15} {2,-15}" -f "SERVICE", "READY", "STATUS"
    Write-Host $header -ForegroundColor Gray
    Write-Host ("-" * 75) -ForegroundColor Gray
    
    foreach ($svc in $Services) {
        $deploymentName = "leafy-$svc"
        
        try {
            $status = kubectl get deployment $deploymentName -n $Namespace -o json 2>$null | ConvertFrom-Json
            
            if ($status) {
                $ready = $status.status.readyReplicas
                $available = $status.status.availableReplicas
                
                if ($ready -eq "1" -or $ready -eq "1/1") {
                    $color = "GREEN"
                } elseif ($ready -eq $null) {
                    $color = "RED"
                } else {
                    $color = "YELLOW"
                }
                
                $line = "{0,-40} {1,-15} {2,-15}" -f $svc, "$ready/1", "$available"
                Write-Host $line -ForegroundColor $color
            }
        }
        catch {
            $line = "{0,-40} {1,-15} {2,-15}" -f $svc, "N/A", "Not Found"
            Write-Host $line -ForegroundColor RED
        }
    }
    
    Write-Host ""
}

function Show-Pods {
    Write-Header "Pods Status in $Namespace"
    kubectl get pods -n $Namespace --sort-by=.metadata.creationTimestamp
    Write-Host ""
}

function Restart-Services {
    Write-Header "Restarting All Services"
    
    foreach ($svc in $Services) {
        $deploymentName = "leafy-$svc"
        Write-Log "Restarting $svc..."
        kubectl rollout restart deployment/$deploymentName -n $Namespace 2>&1 | Out-Null
    }
    
    Write-Log "All services restart initiated" "GREEN"
    Write-Host ""
    Write-Log "Use '.\deploy-all.ps1 -Watch' to monitor rollout status" "INFO"
}

function Watch-Rollout {
    Write-Header "Watching Rollout Status"
    
    foreach ($svc in $Services) {
        $deploymentName = "leafy-$svc"
        Write-Log "Checking rollout for $svc..."
        
        try {
            kubectl rollout status deployment/$deploymentName -n $Namespace --timeout=300s
        }
        catch {
            Write-Log "Rollout timeout or failed for $svc" "RED"
        }
    }
    
    Write-Log "All rollouts complete!" "GREEN"
}

function Show-Logs {
    if ([string]::IsNullOrEmpty($Service)) {
        Write-Log "Service name required. Use: .\deploy-all.ps1 -Logs -Service <service-name>" "RED"
        return
    }
    
    Write-Log "Fetching logs for $Service..."
    kubectl logs -l "app=$Service" -n $Namespace --tail=100 -f
}

function Undeploy-Service {
    param([string]$ServiceName)
    
    $deploymentName = "leafy-$ServiceName"
    
    Write-Log "Deleting $ServiceName..." "WARN"
    kubectl delete deployment $deploymentName -n $Namespace 2>&1 | Out-Null
    Write-Log "Deleted $ServiceName" "GREEN"
}

function Undeploy-All {
    Write-Header "Undeploying All Services"
    
    foreach ($svc in $Services) {
        Undeploy-Service -ServiceName $svc
    }
    
    Write-Log "All services undeployed!" "GREEN"
}

function Show-Help {
    Write-Host @"
Usage: .\deploy-all.ps1 [OPTIONS]

Options:
  -Service <name>   Deploy specific service (e.g., auth-service)
  -DryRun           Show what would be deployed
  -Status           Show deployment status
  -Pods             Show pods status
  -Restart          Restart all services
  -Watch            Watch rollout status
  -Logs             Show logs for a service (use with -Service)
  -Undeploy         Delete all services
  -Help             Show this help message

Examples:
  .\deploy-all.ps1                           # Deploy all services
  .\deploy-all.ps1 -Service auth-service     # Deploy specific service
  .\deploy-all.ps1 -Status                  # Show status
  .\deploy-all.ps1 -Restart                 # Restart all
  .\deploy-all.ps1 -Logs -Service auth-service  # Tail logs

"@
}

# =============================================================================
# Main Script
# =============================================================================

function Main {
    # Handle -Help first
    if ($Help) {
        Show-Help
        return
    }
    
    # If a specific service is provided, deploy just that
    if (-not [string]::IsNullOrEmpty($Service)) {
        Deploy-Service -ServiceName $Service
        return
    }
    
    # Otherwise, execute the requested action
    if ($Status) {
        Show-Status
    }
    elseif ($Pods) {
        Show-Pods
    }
    elseif ($Restart) {
        Restart-Services
    }
    elseif ($Watch) {
        Watch-Rollout
    }
    elseif ($Logs) {
        Show-Logs
    }
    elseif ($Undeploy) {
        Undeploy-All
    }
    else {
        Deploy-AllServices
    }
}

# Run main function
Main
