// Jenkins Job Definitions using Job DSL
// Place in: jenkins/jobs/seed.groovy
// Run via Jenkins seed job

// Folder for all backend services
folder('leafy-backend') {
    displayName('Leafy Backend')
    description('CI/CD pipelines for Leafy Backend microservices')
}

// ============================================
// SERVICE CONFIGURATIONS
// ============================================
def services = [
    [
        name: 'auth-service',
        displayName: 'Auth Service',
        port: '8081',
        type: 'spring-boot',
        description: 'Authentication and authorization service',
        paths: ['backend/auth-service/**']
    ],
    [
        name: 'api-gateway',
        displayName: 'API Gateway',
        port: '8060',
        type: 'spring-boot',
        description: 'API Gateway and routing service',
        paths: ['backend/api-gateway/**']
    ],
    [
        name: 'plant-management-service',
        displayName: 'Plant Management Service',
        port: '8085',
        type: 'spring-boot',
        description: 'Plant management and tracking',
        paths: ['backend/plant-management-service/**']
    ],
    [
        name: 'search-service',
        displayName: 'Search Service',
        port: '8088',
        type: 'spring-boot',
        description: 'Search functionality with Elasticsearch',
        paths: ['backend/search-service/**']
    ],
    [
        name: 'notification-service',
        displayName: 'Notification Service',
        port: '8095',
        type: 'spring-boot',
        description: 'Multi-channel notifications (email, SMS, push)',
        paths: ['backend/notification-service/**']
    ],
    [
        name: 'socket-service',
        displayName: 'Socket Service',
        port: '8093',
        type: 'spring-boot',
        description: 'WebSocket and real-time communication',
        paths: ['backend/socket-service/**']
    ],
    [
        name: 'message-service',
        displayName: 'Message Service',
        port: '8092',
        type: 'spring-boot',
        description: 'Messaging functionality',
        paths: ['backend/message-service/**']
    ],
    [
        name: 'community-feed-service',
        displayName: 'Community Feed Service',
        port: '8090',
        type: 'spring-boot',
        description: 'Community social features',
        paths: ['backend/community-feed-service/**']
    ],
    [
        name: 'profile-service',
        displayName: 'Profile Service',
        port: '8086',
        type: 'spring-boot',
        description: 'User profile management',
        paths: ['backend/profile-service/**']
    ],
    [
        name: 'file-service',
        displayName: 'File Service',
        port: '8084',
        type: 'spring-boot',
        description: 'File storage and management',
        paths: ['backend/file-service/**']
    ],
    [
        name: 'rag-service',
        displayName: 'RAG Service',
        port: '8199',
        type: 'python',
        description: 'Retrieval Augmented Generation service',
        paths: ['backend/rag-service/**'],
        pythonVersion: '3.11'
    ],
    [
        name: 'disease-detection-service',
        displayName: 'Disease Detection Service',
        port: '8000',
        type: 'python',
        description: 'Plant disease detection using ML',
        paths: ['backend/disease-detection-service/**'],
        pythonVersion: '3.11',
        gpuRequired: true
    ],
    [
        name: 'discovery-server',
        displayName: 'Discovery Server',
        port: '8761',
        type: 'spring-boot',
        description: 'Eureka Service Discovery',
        paths: ['backend/discovery-server/**']
    ],
    [
        name: 'config-server',
        displayName: 'Config Server',
        port: '8888',
        type: 'spring-boot',
        description: 'Spring Cloud Config Server',
        paths: ['backend/config-server/**']
    ]
]

// ============================================
// MULTI-BRANCH PIPELINE
// ============================================
multibranchPipelineJob('leafy-backend/main-pipeline') {
    displayName('Main Pipeline - All Services')
    description('Monitors all branches and builds services based on changes')

    branchSources {
        github {
            id('github-main')
            scanCredentialsId('github-credentials')
            repoOwner('YOUR_GITHUB_ORG')
            repository('YOUR_REPO_NAME')

            buildOriginBranch(true)
            buildOriginBranchWithPR(true)
            buildOriginPRMerge(true)
            buildOriginPRHead(true)

            traits {
                gitHubBranchDiscovery {
                    strategyId(1) // All branches
                }
                gitHubPullRequestDiscovery {
                    strategyId(1) // Merge current PR branch with target branch
                }
                headWildcardFilter {
                    includes('*')
                    excludes('')
                }
            }
        }
    }

    factory {
        workflowBranchProjectFactory {
            scriptPath('Jenkinsfile')
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            daysToKeep(30)
            numToKeep(50)
        }
    }

    configure { node ->
        node / 'buildStrategies' {
            'jenkins.branch.buildstrategies.basic.SkippipableDefaultBranchPropertyStrategy' {}
        }
    }
}

// ============================================
// INDIVIDUAL SERVICE PIPELINES
// ============================================
services.each { service ->
    pipelineJob("leafy-backend/${service.name}") {
        displayName(service.displayName)
        description(service.description ?: "CI/CD pipeline for ${service.name}")

        parameters {
            stringParam('BUILD_VERSION', '', 'Build version/tag')
            booleanParam('RUN_TESTS', true, 'Run unit tests')
            booleanParam('DEPLOY_TO_STAGING', false, 'Deploy to staging after build')
            booleanParam('DEPLOY_TO_PROD', false, 'Deploy to production after build')
        }

        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url('https://github.com/YOUR_ORG/YOUR_REPO.git')
                            credentials('github-credentials')
                        }
                        branch('*/main')
                    }
                }
                scriptPath(service.type == 'python' ? 'Jenkinsfile.python' : 'Jenkinsfile.spring-boot')
                lightweight(true)
            }
        }

        triggers {
            githubPush()
        }

        disabled(false)
    }
}

// ============================================
// INFRASTRUCTURE PIPELINES
// ============================================
pipelineJob('leafy-backend/infrastructure') {
    displayName('Infrastructure Pipeline')
    description('Deploy and manage infrastructure services')

    definition {
        cps {
            script('''
pipeline {
    agent any

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'restart', 'stop', 'logs'],
            description: 'Action to perform'
        )
        choice(
            name: 'SERVICE',
            choices: ['all', 'mongodb', 'postgresql', 'kafka', 'redis', 'elasticsearch', 'jenkins'],
            description: 'Service to target'
        )
    }

    environment {
        DOCKER_REGISTRY = 'localhost:5000'
    }

    stages {
        stage('Prepare') {
            steps {
                sh 'cd backend && docker-compose version'
            }
        }

        stage('Execute') {
            steps {
                script {
                    switch (params.ACTION) {
                        case 'deploy':
                            sh "cd backend && docker-compose up -d ${params.SERVICE}"
                            break
                        case 'restart':
                            sh "cd backend && docker-compose restart ${params.SERVICE}"
                            break
                        case 'stop':
                            sh "cd backend && docker-compose stop ${params.SERVICE}"
                            break
                        case 'logs':
                            sh "cd backend && docker-compose logs -f --tail=100 ${params.SERVICE}"
                            break
                    }
                }
            }
        }

        stage('Verify') {
            steps {
                sh "cd backend && docker-compose ps"
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
            ''')
        }
    }
}

// ============================================
// FULL DEPLOYMENT PIPELINE
// ============================================
pipelineJob('leafy-backend/full-deploy') {
    displayName('Full Deployment Pipeline')
    description('Deploy entire stack to staging or production')

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['staging', 'production'],
            description: 'Target environment'
        )
        booleanParam('SKIP_TESTS', false, 'Skip all tests')
        booleanParam('DRY_RUN', false, 'Perform dry run without actual deployment')
        stringParam('VERSION', '', 'Version to deploy (defaults to latest)')
    }

    definition {
        cps {
            script('''
pipeline {
    agent { label 'docker-build' }

    environment {
        DOCKER_REGISTRY = 'localhost:5000'
        ENVIRONMENT = "${params.ENVIRONMENT}"
    }

    stages {
        stage('Build All Services') {
            parallel {
                stage('Build Spring Boot Services') {
                    steps {
                        sh \'\'\'
                            for service in auth-service api-gateway plant-management-service \\
                                search-service notification-service socket-service \\
                                message-service community-feed-service profile-service \\
                                file-service discovery-server config-server; do
                                echo "Building $service..."
                                docker build -f backend/$service/Dockerfile \\
                                    -t localhost:5000/$service:${params.VERSION ?: env.BUILD_NUMBER} \\
                                    backend/
                            done
                        \'\'\'
                    }
                }
                stage('Build Python Services') {
                    steps {
                        sh \'\'\'
                            for service in rag-service disease-detection-service; do
                                echo "Building $service..."
                                docker build -f backend/$service/Dockerfile \\
                                    -t localhost:5000/$service:${params.VERSION ?: env.BUILD_NUMBER} \\
                                    backend/
                            done
                        \'\'\'
                    }
                }
            }
        }

        stage('Push Images') {
            steps {
                sh \'\'\'
                    for service in auth-service api-gateway plant-management-service \\
                        search-service notification-service socket-service \\
                        message-service community-feed-service profile-service \\
                        file-service rag-service disease-detection-service \\
                        discovery-server config-server; do
                        docker push localhost:5000/$service:${params.VERSION ?: env.BUILD_NUMBER}
                    done
                \'\'\'
            }
        }

        stage('Deploy to ${ENVIRONMENT}') {
            when {
                expression { !params.DRY_RUN }
            }
            steps {
                sh \'\'\'
                    cd backend

                    # Update image tags in compose file
                    sed -i \'s|image: .*|image: localhost:5000/&:${params.VERSION ?: env.BUILD_NUMBER}|g\' docker-compose.yml

                    # Deploy
                    docker-compose up -d

                    # Wait for health
                    echo "Waiting for services to be healthy..."
                    sleep 60

                    # Check health
                    docker-compose ps
                \'\'\'
            }
        }

        stage('Smoke Tests') {
            steps {
                sh \'\'\'
                    echo "Running smoke tests..."

                    # Test API Gateway
                    curl -sf http://localhost:8060/actuator/health && echo "API Gateway: OK"
                    curl -sf http://localhost:8081/actuator/health && echo "Auth Service: OK"
                    curl -sf http://localhost:8085/actuator/health && echo "Plant Service: OK"

                    echo "Smoke tests completed"
                \'\'\'
            }
        }
    }

    post {
        success {
            slackSend(
                color: 'good',
                message: "✅ Deployment to ${ENVIRONMENT} succeeded: ${env.BUILD_URL}"
            )
        }
        failure {
            slackSend(
                color: 'danger',
                message: "❌ Deployment to ${ENVIRONMENT} failed: ${env.BUILD_URL}"
            )
        }
    }
}
            ''')
        }
    }
}

// ============================================
// PERMISSIONS
// ============================================
// Configure which users/groups can access which jobs
securityJobProperty {
    - authorizationMatrix {
        grantedAuthorities(['hudson.model.Item.Build', 'hudson.model.Item.Read', 'hudson.model.Item.Workspace'])
        users('admin', 'jenkins-deploy')
    }
}
