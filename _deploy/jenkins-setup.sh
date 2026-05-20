#!/bin/bash
# Jenkins Configuration Setup Script
# This script configures Jenkins with the necessary plugins and job configurations
# Run this after Jenkins is initialized

set -e

JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
JENKINS_USER="${JENKINS_USER:-admin}"
JENKINS_API_TOKEN="${JENKINS_API_TOKEN:-}"  # Get from initial setup
JENKINS_CLI="${JENKINS_CLI:-${JENKINS_URL}/jenkins/cli}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Wait for Jenkins to be ready
wait_for_jenkins() {
    echo_info "Waiting for Jenkins to be ready..."
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${JENKINS_URL}/login" > /dev/null 2>&1; then
            echo_info "Jenkins is ready!"
            return 0
        fi
        echo "  Attempt $attempt/$max_attempts..."
        sleep 5
        attempt=$((attempt + 1))
    done

    echo_error "Jenkins did not become ready in time"
    return 1
}

# Install Jenkins CLI
install_jenkins_cli() {
    echo_info "Installing Jenkins CLI..."
    if [ ! -f jenkins-cli.jar ]; then
        curl -sfL "${JENKINS_URL}/jenkins/jnlpJenkins.jar" -o jenkins-cli.jar
    fi
}

# Install required plugins
install_plugins() {
    echo_info "Installing Jenkins plugins..."

    local plugins=(
        # Git integration
        git:latest
        github:latest
        github-branch-source:latest

        # Pipeline
        workflow-aggregator:latest
        pipeline-stage-view:latest
        blueocean:latest

        # Docker integration
        docker-workflow:latest
        docker-build-step:latest

        # Build tools
        maven-plugin:latest

        # Notifications
        slack:latest

        # Code quality
        warnings-ng:latest
        jacoco:latest
        code-coverage-api:latest

        # Security
        matrix-auth:latest

        # Credentials
        credentials-binding:latest

        # Utilities
        timestamper:latest
        build-timeout:latest
        ws-cleanup:latest
    )

    local plugins_param=$(IFS=,; echo "${plugins[*]}")

    curl -sfL "${JENKINS_URL}/jenkins/pluginManager/installNecessaryPlugins" \
        --data-urlencode "plugin=$plugins_param" \
        -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" \
        || echo_warn "Plugin installation may require manual intervention"
}

# Configure GitHub integration
configure_github_integration() {
    echo_info "Configuring GitHub integration..."

    # This requires GitHub App or Personal Access Token
    # Manual configuration recommended for security
    cat << 'EOF'
To configure GitHub integration:

1. Go to: Manage Jenkins > System > GitHub
2. Add GitHub Server:
   - Name: GitHub
   - API URL: https://api.github.com
   - Credentials: Add GitHub Personal Access Token
     - Required scopes: repo, read:org, repo:status

To create a GitHub Personal Access Token:
1. Go to GitHub > Settings > Developer settings
2. Personal access tokens > Fine-grained tokens
3. Generate new token with:
   - Repository access: All repositories
   - Permissions: repo (full), read:org

For webhooks (alternative to Jenkins GitHub App):
1. Go to GitHub repository > Settings > Webhooks
2. Add webhook:
   - Payload URL: http://YOUR_JENKINS_URL/jenkins/github-webhook/
   - Content type: application/json
   - Events: Push, Pull request
   - Secret: Generate and save securely
3. Add Secret to Jenkins credentials
EOF
}

# Create seed job for Job DSL
create_seed_job() {
    echo_info "Creating Jenkins seed job..."

    local seed_job_xml='<?xml version="1.0" encoding="UTF-8"?>
<project>
    <description>Seed job for generating Jenkins jobs from DSL</description>
    <keepDependencies>false</keepDependencies>
    <properties>
        <com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty>
            <gitLabConnection></gitLabConnection>
        </com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty>
    </properties>
    <scm class="hudson.plugins.git.GitSCM">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
            <hudson.plugins.git.UserRemoteConfig>
                <url>YOUR_REPO_URL</url>
            </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
            <hudson.plugins.git.BranchSpec>
                <name>*/main</name>
            </hudson.plugins.git.BranchSpec>
        </branches>
    </scm>
    <canRoam>true</canRoam>
    <disabled>false</disabled>
    <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
    <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
    <triggers/>
    <concurrentBuild>false</concurrentBuild>
    <builders>
        <org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition>
            <scriptPath>jobs/seed.groovy</scriptPath>
            <lightweight>true</lightweight>
        </org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition>
    </builders>
    <publishers/>
    <buildWrappers/>
</project>'

    echo "$seed_job_xml" > seed-job.xml
    echo_info "Seed job XML created. Import manually via Jenkins UI or CLI."
}

# Create job definitions
create_job_dsl() {
    echo_info "Creating Job DSL definitions..."

    mkdir -p jobs

    cat > jobs/seed.groovy << 'GROOVY'
// Jenkins Job DSL for Leafy Backend Services

// Folder for microservices
folder('leafy-backend') {
    displayName('Leafy Backend Services')
    description('All backend microservices CI/CD jobs')
}

// Multi-branch pipeline for main repository
multibranchPipelineJob('leafy-backend/main-pipeline') {
    displayName('Main Pipeline')
    description('Multi-branch pipeline for main repository')

    branchSources {
        github {
            id('main-repo')
            scanCredentialsId('github-credentials')
            repoOwner('YOUR_GITHUB_ORG')
            repository('YOUR_REPO_NAME')
            includes('*')

            // Build origin branches only
            buildOriginBranch(true)
            buildOriginBranchWithPR(true)
            buildOriginPRMerge(true)
            buildOriginPRHead(true)

            // Configure trigger
            buildContributors()
        }
    }

    factory {
        workflowBranchProjectFactory {
            scriptPath('Jenkinsfile')
        }
    }

    // Configure behaviors
    configure { node ->
        node / 'buildStrategies' {
            'com.腾飞.jenkins.branch.BuildStrategies' {
                'buildBranches' {
                    'spring.io.jenkins.plugins.fully_initialized.DefaultInitialBuildStrategy' {}
                }
            }
        }
    }

    // Discard old builds
    logRotator {
        daysToKeep(30)
        numToKeep(50)
        artifactDaysToKeep(14)
        artifactNumToKeep(10)
    }

    // Enable periodic SCM polling
    triggers {
        periodic(3600) // Every hour
    }
}

// Individual service pipelines
def services = [
    [name: 'auth-service', port: '8081', type: 'spring-boot'],
    [name: 'api-gateway', port: '8060', type: 'spring-boot'],
    [name: 'plant-management-service', port: '8085', type: 'spring-boot'],
    [name: 'search-service', port: '8088', type: 'spring-boot'],
    [name: 'notification-service', port: '8095', type: 'spring-boot'],
    [name: 'socket-service', port: '8093', type: 'spring-boot'],
    [name: 'message-service', port: '8092', type: 'spring-boot'],
    [name: 'community-feed-service', port: '8090', type: 'spring-boot'],
    [name: 'profile-service', port: '8086', type: 'spring-boot'],
    [name: 'file-service', port: '8084', type: 'spring-boot'],
    [name: 'rag-service', port: '8199', type: 'python'],
    [name: 'disease-detection-service', port: '8000', type: 'python']
]

services.each { service ->
    organizationFolder("${service.name}") {
        displayName(service.name)
        description("CI/CD pipeline for ${service.name}")

        authorizedAgents 'development'

        // GitHub source
        projectFactories {
            workflowMultiBranchProjectFactory {
                scriptPath('Jenkinsfile')
            }
        }

        configure { node ->
            node / 'projectRepositoryStats' / 'repositoryStats' {
                'defaultRepositoryStats' {}
            }
        }
    }
}
GROOVY

    echo_info "Job DSL created at jobs/seed.groovy"
}

# Create credentials setup
create_credentials_script() {
    echo_info "Creating credentials setup..."

    cat > setup-credentials.sh << 'CREDENTIALS'
#!/bin/bash
# Setup Jenkins credentials
# Run after Jenkins is configured

JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
JENKINS_USER="${JENKINS_USER:-admin}"
JENKINS_API_TOKEN="YOUR_API_TOKEN"

# Add Docker Registry credentials
curl -X POST "${JENKINS_URL}/jenkins/credentials/store/system/domain/_/createCredentials" \
    -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" \
    --data-urlencode "json={
        \"\": \"0\",
        \"credentials\": {
            \"scope\": \"GLOBAL\",
            \"id\": \"docker-registry\",
            \"usernamePassword\": {
                \"username\": \"YOUR_DOCKER_USERNAME\",
                \"password\": \"YOUR_DOCKER_PASSWORD\",
                \"description\": \"Docker Registry Credentials\"
            },
            \"stapler-class\": \"com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl\"
        }
    }"

# Add GitHub credentials
curl -X POST "${JENKINS_URL}/jenkins/credentials/store/system/domain/_/createCredentials" \
    -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" \
    --data-urlencode "json={
        \"\": \"0\",
        \"credentials\": {
            \"scope\": \"GLOBAL\",
            \"id\": \"github-credentials\",
            \"apiToken\": {
                \"apiToken\": \"YOUR_GITHUB_TOKEN\",
                \"description\": \"GitHub Personal Access Token\"
            },
            \"stapler-class\": \"org.jenkinsci.plugins.github_branch_source.GitHubTokenCredentials\"
        }
    }"
CREDENTIALS

    chmod +x setup-credentials.sh
    echo_info "Credentials setup script created"
}

# Main execution
main() {
    echo "========================================"
    echo "Jenkins Setup for Leafy Backend"
    echo "========================================"
    echo ""

    # Wait for Jenkins
    wait_for_jenkins || exit 1

    # Install Jenkins CLI
    install_jenkins_cli

    # Install plugins
    install_plugins

    # Configure GitHub
    configure_github_integration

    # Create job definitions
    create_job_dsl

    # Create credentials setup
    create_credentials_script

    echo ""
    echo "========================================"
    echo "Setup Complete!"
    echo "========================================"
    echo ""
    echo "Next steps:"
    echo "1. Access Jenkins at: ${JENKINS_URL}/jenkins"
    echo "2. Install required plugins manually if needed"
    echo "3. Configure GitHub credentials in Manage Jenkins > Credentials"
    echo "4. Update 'YOUR_GITHUB_ORG' and 'YOUR_REPO_NAME' in job definitions"
    echo "5. Configure webhook in GitHub repository"
    echo ""
}

main "$@"
