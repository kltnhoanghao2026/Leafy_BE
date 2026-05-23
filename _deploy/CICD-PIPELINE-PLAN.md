# Leafy CI/CD Pipeline Deployment Guide

This document is a step-by-step guide for deploying the Leafy CI/CD pipeline. It assumes you are a beginner setting up on AWS with Jenkins as the CI/CD orchestrator. You will handle the infrastructure (VPC, ECR, EKS) manually; the pipeline handles building, testing, and deploying your application.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Step 1: Set Up AWS Infrastructure Manually](#2-step-1-set-up-aws-infrastructure-manually)
3. [Step 2: Set Up Jenkins](#3-step-2-set-up-jenkins)
4. [Step 3: Configure Jenkins Credentials](#4-step-3-configure-jenkins-credentials)
5. [Step 4: Create Jenkins Pipeline Job](#5-step-4-create-jenkins-pipeline-job)
6. [Step 5: Understand the Pipeline Stages](#6-step-5-understand-the-pipeline-stages)
7. [Step 6: Set Up GitHub Webhook (Optional)](#7-step-6-set-up-github-webhook-optional)
8. [Step 7: Run Your First Pipeline](#8-step-7-run-your-first-pipeline)
9. [Step 8: Verify Deployment](#9-step-8-verify-deployment)
10. [Appendix: Troubleshooting](#10-appendix-troubleshooting)

---

## 1. Prerequisites

Before starting, ensure you have the following:

### Tools You Need Installed Locally (for testing)

| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | Interact with AWS from your machine |
| kubectl | 1.30.0 | Interact with Kubernetes cluster |
| Docker | Latest | Build and run containers locally |
| Terraform | 1.6.0 | (Optional) Provision infrastructure |

### AWS Requirements

- An **AWS Account** with appropriate permissions
- AWS Access Key ID and Secret Access Key
- IAM user with permissions for: ECR, EKS, S3 (for state), DynamoDB

### Jenkins Server Requirements

- A server (VM or container) with at least 4GB RAM and 2 CPUs
- Ubuntu 20.04+ or similar Linux distribution
- Docker installed on the Jenkins server
- Internet access from the Jenkins server to AWS and GitHub

---

## 2. Step 1: Set Up AWS Infrastructure Manually

You will set up the following AWS resources. Do this through the AWS Console, AWS CLI, or Terraform. Only create the resources you need -- the pipeline does NOT manage these.

### 2.1 Create a VPC

Create a VPC that your EKS cluster and EC2 machines will live in.

**Your existing VPC configuration:**
- Name: `project-vpc`
- VPC ID: `vpc-05b31b1731dfc6cdb`
- CIDR Block: `10.0.0.0/16`
- Region: `ap-southeast-1` (Singapore region)
- Availability Zones: `ap-southeast-1a`, `ap-southeast-1b` (2 AZs)
- Public subnets:
  - `project-subnet-public1-ap-southeast-1a`
  - `project-subnet-public2-ap-southeast-1b`
- Private subnets:
  - `project-subnet-private1-ap-southeast-1a`
  - `project-subnet-private2-ap-southeast-1b`
- NAT Gateway: `project-nat-public1-ap-southeast-1a` (in AZ-a)
- Internet Gateway: `project-igw`
- Route Tables: Already configured with public/private routing

Your VPC is already set up correctly. Move on to the next step.

### 2.2 Create an EKS Cluster

Create an Amazon EKS cluster that will run your microservices.

**Cluster configuration:**
- Name: `leafy-eks`
- Kubernetes Version: `1.30` or newer
- VPC: Select `project-vpc` (`vpc-05b31b1731dfc6cdb`)
- Private subnets: Select **both** private subnets
  - `project-subnet-private1-ap-southeast-1a`
  - `project-subnet-private2-ap-southeast-1b`
- Control plane endpoint: Public endpoint enabled (recommended for beginners)
- **Enable EKS Auto Mode** (compute automatically managed by AWS)
  - In the AWS Console: Cluster creation wizard -> Compute tab -> Enable **EKS Auto Mode**
  - This will automatically provision and manage nodes for you

> **Note on EKS Auto Mode:** When you enable EKS Auto Mode, you do NOT need to create a separate managed node group. AWS will automatically provision nodes based on your pod requirements. It also automatically installs the EBS CSI Driver, so your PersistentVolumeClaims for MongoDB, PostgreSQL, Kafka, Elasticsearch, and Qdrant will work out of the box. However, you still need to manually install the AWS Load Balancer Controller for Ingress to work.

> **Single AZ limitation:** With only one AZ, your workloads cannot fail over to another AZ. This is fine for development and testing. For production, use at least 2-3 AZs for high availability.

### 2.2.1 Install AWS Load Balancer Controller (Required for Ingress)

EKS Auto Mode does not include the Load Balancer Controller by default. You need this for the Ingress resources to work.

```powershell
# Set your cluster name and region
$env:CLUSTER_NAME = "leafy-eks"
$env:AWS_REGION = "ap-southeast-1"
$env:AWS_ACCOUNT_ID = aws sts get-caller-identity --query Account --output text

# 1. Download the IAM policy
curl -o "$env:TEMP\lb-controller-policy.json" https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.2/docs/install/policy.json

# 2. Create the IAM policy
aws iam create-policy `
    --policy-name AWSLoadBalancerControllerPolicy `
    --policy-document "file:///$env:TEMP/lb-controller-policy.json"

# 3. Create a Kubernetes service account with IAM role
eksctl create iamserviceaccount `
    --cluster=$env:CLUSTER_NAME `
    --namespace=kube-system `
    --name=aws-load-balancer-controller `
    --role-name AmazonEKSLoadBalancerControllerRole `
    --attach-policy-arn=arn:aws:iam::$env:AWS_ACCOUNT_ID:policy/AWSLoadBalancerControllerPolicy `
    --approve

# 4. Download and apply the controller manifests
curl -Lo "$env:TEMP\lb-controller.yaml" https://github.com/kubernetes-sigs/aws-load-balancer-controller/releases/download/v2.7.2/v2.7.2.yaml

# 5. Edit the file to set your cluster name
(Get-Content "$env:TEMP\lb-controller.yaml") -replace "your-cluster-name", $env:CLUSTER_NAME | Set-Content "$env:TEMP\lb-controller.yaml"

# 6. Apply the manifests
kubectl apply -f "$env:TEMP\lb-controller.yaml"

# 7. Verify the controller is running
kubectl get deployment -n kube-system aws-load-balancer-controller
```

### 2.3 Create ECR Repositories

Create one Amazon ECR repository for each service. The pipeline will push images to these.

Create these 15 repositories (naming convention: `leafy-{service-name}`):

**Spring Boot services (13):**
```
leafy-discovery-server
leafy-config-server
leafy-api-gateway
leafy-auth-service
leafy-profile-service
leafy-file-service
leafy-search-service
leafy-notification-service
leafy-plant-management-service
leafy-iot-metrics-collector-service
leafy-community-feed-service
leafy-socket-service
leafy-message-service
```

**Python services (2):**
```
leafy-rag-service
leafy-disease-detection-service
```

**How to create via AWS CLI:**
```powershell
# Set your AWS account ID and region
$env:AWS_ACCOUNT_ID = (aws sts get-caller-identity --query Account --output text)
$env:AWS_REGION = "ap-southeast-1"

# Create all repositories
$repos = @(
    "discovery-server", "config-server", "api-gateway", "auth-service",
    "profile-service", "file-service", "search-service", "notification-service",
    "plant-management-service", "iot-metrics-collector-service",
    "community-feed-service", "socket-service", "message-service",
    "rag-service", "disease-detection-service"
)
foreach ($repo in $repos) {
    aws ecr create-repository `
        --repository-name "leafy-$repo" `
        --region $env:AWS_REGION `
        --image-scanning-configuration scanOnPush=true `
        --encryption-configuration encryptionType=AES256
}
```

### 2.4 Create EC2 Machines (Optional - for hybrid deployment)

If you want to run some services on EC2 machines instead of EKS, create 4 EC2 instances all in the same availability zone as your VPC:

| Machine | Purpose | Instance Type | Services |
|---------|---------|---------------|----------|
| Machine 1 | Data Tier | t3.micro | MongoDB, PostgreSQL |
| Machine 2 | Messaging Tier | t3.micro | Kafka, MQTT Broker, Redis, Kafka-UI |
| Machine 3 | Search/AI Tier | t3.medium | Elasticsearch, Kibana, Qdrant |
| Machine 4 | App Tier | t3.medium | Spring Boot services |

> **All EC2 machines should be in the same AZ as your VPC subnets** (e.g., `ap-southeast-1a`) so they can communicate with each other and with the EKS cluster.

**For each EC2 machine:**
1. Launch in the private subnets of your VPC
2. Use Ubuntu 22.04 LTS AMI
3. Assign an IAM role with permissions to pull from ECR
4. Install Docker and Docker Compose on each machine
5. Create an SSH key pair for Jenkins to connect

**Install Docker on each EC2 machine:**
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose
sudo usermod -aG docker ubuntu
sudo systemctl enable docker
```

### 2.5 Create AWS Secrets Manager Secrets (Optional - for External Secrets)

If you want to use the External Secrets Operator, store your sensitive data in AWS Secrets Manager:

```
leafy/production/mongodb         -> {"password": "your-mongodb-password"}
leafy/production/postgresql      -> {"password": "your-postgres-password"}
leafy/production/redis          -> {"password": "your-redis-password"}
leafy/production/jwt            -> {"secret": "your-jwt-secret"}
leafy/production/aws            -> {"access_key_id": "...", "secret_access_key": "..."}
leafy/production/openai         -> {"api_key": "your-openai-api-key"}
```

---

## 3. Step 2: Set Up Jenkins

### 3.1 Install Jenkins

On your Jenkins server, install Jenkins:

```bash
# Install Java (required by Jenkins)
sudo apt update
sudo apt install -y openjdk-17-jdk

# Add Jenkins repository
curl -fsSL https://pkg.jenkins.io/debian/jenkins.io-2023.key | sudo tee \
    /usr/share/keyrings/jenkins-keyring.asc > /dev/null
echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
    https://pkg.jenkins.io/debian binary/ | sudo tee \
    /etc/apt/sources.list.d/jenkins.list > /dev/null

# Install Jenkins
sudo apt update
sudo apt install -y jenkins

# Start Jenkins
sudo systemctl start jenkins
sudo systemctl enable jenkins

# Get initial admin password
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

### 3.2 Install Required Tools on Jenkins Server

Jenkins needs these tools installed on its server:

```bash
# AWS CLI
sudo apt install -y awscli

# Docker
sudo apt install -y docker.io
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins

# kubectl
curl -LO "https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# jq (for parsing JSON)
sudo apt install -y jq
```

### 3.3 Install Jenkins Plugins

1. Open Jenkins at `http://your-jenkins-server:8080`
2. Enter the initial admin password
3. Install suggested plugins
4. Install these additional plugins:
   - **AWS Credentials** - for AWS authentication
   - **Pipeline** - for pipeline jobs
   - **CloudBees AWS Credentials** - for AWS credentials management
   - **Git** - for Git integration
   - **Docker** - for Docker builds

**To install plugins:**
1. Go to **Manage Jenkins** -> **Manage Plugins**
2. Go to the **Available** tab
3. Search for and install each plugin above
4. Restart Jenkins after installing

---

## 4. Step 3: Configure Jenkins Credentials

Jenkins needs credentials to access AWS, GitHub, and other services.

### 4.1 AWS Credentials

1. Go to **Manage Jenkins** -> **Manage Credentials**
2. Click on **(global)** domain
3. Click **Add Credentials**
4. Fill in:
   - **Kind**: AWS Credentials
   - **ID**: `aws-creds`
   - **Description**: AWS Production Credentials
   - **Access Key ID**: Your AWS access key
   - **Secret Access Key**: Your AWS secret key
5. Click **OK**

### 4.2 AWS Account ID

1. Go to **Manage Jenkins** -> **Manage Credentials**
2. Click **Add Credentials**
3. Fill in:
   - **Kind**: Secret text
   - **ID**: `aws-account-id`
   - **Description**: AWS Account ID
   - **Secret**: Your 12-digit AWS account ID (e.g., `123456789012`)
4. Click **OK**

### 4.3 API URL (for smoke tests)

1. Go to **Manage Jenkins** -> **Manage Credentials**
2. Click **Add Credentials**
3. Fill in:
   - **Kind**: Secret text
   - **ID**: `api-url`
   - **Description**: API Gateway URL
   - **Secret**: Your API Gateway URL (e.g., `https://api.leafy.example.com`)
4. Click **OK**

### 4.4 SSH Key for EC2 (if deploying to EC2)

1. Create an SSH key pair on your Jenkins server:
```bash
ssh-keygen -t ed25519 -f ~/.ssh/leafy-ec2-key.pem -N ""
```

2. Go to **Manage Jenkins** -> **Manage Credentials**
3. Click **Add Credentials**
4. Fill in:
   - **Kind**: SSH Username with private key
   - **ID**: `ec2-ssh-key`
   - **Username**: `ubuntu`
   - **Private Key**: Enter directly
   - Paste the private key content from `~/.ssh/leafy-ec2-key.pem`
5. Click **OK**

6. Add the public key `~/.ssh/leafy-ec2-key.pem.pub` to each EC2 machine's `~/.ssh/authorized_keys`

### 4.5 EC2 Machine IPs (if deploying to EC2)

For each EC2 machine, add its IP as a credential:

1. Go to **Manage Jenkins** -> **Manage Credentials**
2. Click **Add Credentials** for each machine:
   - Machine 1: ID `machine1-ip`, Secret: `10.0.1.100` (example IP)
   - Machine 2: ID `machine2-ip`, Secret: `10.0.1.101` (example IP)
   - Machine 3: ID `machine3-ip`, Secret: `10.0.1.102` (example IP)
   - Machine 4: ID `machine4-ip`, Secret: `10.0.1.103` (example IP)

### 4.6 SonarQube Token (Optional - for code analysis)

If you use SonarQube for code quality analysis:

1. Go to your SonarQube server, generate a token
2. Add as credential: ID `sonar-token`, Kind: Secret text

---

## 5. Step 4: Create Jenkins Pipeline Job

### 5.1 Create a New Pipeline Job

1. Click **New Item** on the Jenkins home page
2. Enter item name: `leafy-deploy`
3. Select **Pipeline**
4. Click **OK**

### 5.2 Configure the Pipeline

1. **General** section:
   - Check **Do not allow concurrent builds** (optional)
   - Check **This project is parameterized** (the pipeline parameters are defined in the Jenkinsfile, but you can set defaults here)

2. **Pipeline** section:
   - **Definition**: Select **Pipeline script from SCM**
   - **SCM**: Select **Git**
   - **Repository URL**: Your GitHub repository URL
   - **Credentials**: Add your GitHub credentials if needed
   - **Branch Specifier**: `*/main` (or `*/develop` for dev)
   - **Script Path**: `backend/_deploy/jenkins/Jenkinsfile`
   - **Lightweight checkout**: Check this

3. Click **Save**

### 5.3 Configure the EKS Cluster Name

Before running the pipeline, update the `EKS_CLUSTER_NAME` environment variable in the Jenkinsfile to match your cluster name. Edit line 13 of the Jenkinsfile:

```groovy
// Line 13 - Update this to match your EKS cluster name
EKS_CLUSTER_NAME = 'your-eks-cluster-name'
```

---

## 6. Step 5: Understand the Pipeline Stages

The simplified pipeline has the following stages. They run in order:

### Stage 0: Setup
- Generates a unique `IMAGE_TAG` based on environment and timestamp
- Sets build configuration variables
- Example: `production-20240521-153000` or `dev-20240521-153000`

### Stage 1: Install Tools
- Installs `kubectl` if not already present
- Configures AWS CLI with the correct region

### Stage 2: Login to ECR
- Authenticates Docker to Amazon ECR
- Creates ECR repositories if they don't exist yet

### Stage 3: Build Spring Boot Services
- Builds all 13 Spring Boot microservices in sequence
- Uses Docker BuildKit for faster builds
- Caches previous image layers to speed up rebuilds
- Pushes images with two tags:
  - `${IMAGE_TAG}` - unique timestamp tag
  - `latest` - always points to latest build

### Stage 4: Build Python Services
- Builds the 2 Python AI services:
  - `rag-service` - RAG/AI service
  - `disease-detection-service` - ML disease detection

### Stage 5: Run Tests
- Runs Maven tests for Spring Boot services
- Runs pytest for Python services
- Optional: Runs SonarQube analysis

### Stage 6: Configure kubectl for EKS
- Updates kubeconfig to connect to your EKS cluster
- Verifies cluster connection by running `kubectl cluster-info`

### Stage 7: Deploy to Kubernetes
- Deploys resources in strict dependency order:
  1. **Namespaces** - creates `leafy`, `kube-system`, `ingress-nginx`, `monitoring`, `external-secrets` namespaces
  2. **Storage Classes** - creates gp3, gp3-mongo, gp3-kafka, gp3-elasticsearch storage classes
  3. **Secrets** - creates `leafy-app-secrets` with database connection strings and credentials
  4. **Databases** - deploys MongoDB, PostgreSQL, Redis StatefulSets/Deployments
  5. **Messaging** - deploys Kafka, MQTT broker
  6. **Search & AI** - deploys Elasticsearch, Kibana, Qdrant, Fluent-bit
  7. **Infrastructure Services** - deploys Eureka (Discovery Server), Config Server
  8. **Gateway & Auth** - deploys API Gateway, Auth Service
  9. **Business Services** - deploys Profile, File, Plant Management, Search, Notification services
  10. **IoT & Community** - deploys IoT Metrics, Community Feed, Socket, Message services
  11. **AI Services** - deploys RAG, Disease Detection services

- Waits for infrastructure pods (MongoDB, PostgreSQL, Redis, Kafka, Elasticsearch, Qdrant) to be ready before deploying services

### Stage 8: Health Check
- Runs `kubectl rollout status` for critical services
- Lists all pods and services in the `leafy` namespace
- Reports any pods that failed to start

### Stage 9: Smoke Tests
- Tests the API Gateway health endpoint
- If load balancer is not ready, falls back to port-forward testing
- Checks Eureka dashboard availability

### Stage 10: Deploy to EC2 Machines (Optional)
- Only runs if `DEPLOY_EC2` parameter is set to `true`
- Deploys to 4 EC2 machines in parallel:
  - Machine 1 (Data): MongoDB, PostgreSQL
  - Machine 2 (Messaging): Kafka, MQTT, Redis
  - Machine 3 (Search/AI): Elasticsearch, Qdrant
  - Machine 4 (App): Spring Boot services
- Uses SSH and Docker Compose on each machine

### Post-Build Actions
- Archives kubeconfig
- Sends notifications on success/failure
- Cleans up Docker resources to save disk space

---

## 7. Step 6: Set Up GitHub Webhook (Optional)

To trigger the pipeline automatically on code changes:

1. Go to your GitHub repository
2. Click **Settings** -> **Webhooks** -> **Add webhook**
3. Fill in:
   - **Payload URL**: `http://your-jenkins-server:8080/github-webhook/`
   - **Content type**: `application/json`
   - **Events**: Select **Just the push event** (or choose what you need)
4. Click **Add webhook**

5. In Jenkins:
   - Go to your pipeline job
   - Check **GitHub hook trigger for GITScm polling** under **Build Triggers**

---

## 8. Step 7: Run Your First Pipeline

### 8.1 Build Parameters

When you click **Build with Parameters** in Jenkins, you can select:

| Parameter | Options | Description |
|-----------|---------|-------------|
| SERVICE | all, or any single service | Which service(s) to build and deploy |
| ENVIRONMENT | dev, staging, production | Target environment |
| SKIP_TESTS | true/false | Whether to skip unit tests |
| DEPLOY_EKS | true/false | Whether to deploy to EKS |
| DEPLOY_EC2 | true/false | Whether to deploy to EC2 machines |

**For your first run:**
- SERVICE: `all`
- ENVIRONMENT: `dev`
- SKIP_TESTS: `false`
- DEPLOY_EKS: `true`
- DEPLOY_EC2: `false`

### 8.2 Monitor the Build

1. Click on the build number in Jenkins
2. Click **Console Output** to see live logs
3. Watch for errors in each stage

**Expected build time for first run:** 20-40 minutes
- Most time is spent building Docker images
- Subsequent runs with cached layers: 5-15 minutes

---

## 9. Step 8: Verify Deployment

### 9.1 Check Pod Status

```powershell
kubectl get pods -n leafy
```

All pods should show `Running` status. Some may show `ContainerCreating` or `Pending` while starting.

### 9.2 Check Services

```powershell
kubectl get svc -n leafy
```

Important services and their ports:

| Service | Port | Description |
|---------|------|-------------|
| discovery-server | 8761 | Eureka Dashboard |
| config-server | 8888 | Spring Config Server |
| api-gateway | 8060 | Main API Gateway |
| auth-service | 8081 | Authentication |
| mongodb | 27017 | MongoDB |
| postgresql | 5432 | PostgreSQL |
| redis | 6379 | Redis Cache |
| kafka | 9092 | Kafka Broker |
| elasticsearch | 9200 | Elasticsearch |
| kibana | 5601 | Kibana Dashboard |
| qdrant | 6333 | Vector Database |

### 9.3 Check Pod Logs

```powershell
# Get logs from API Gateway
kubectl logs -n leafy -l app=api-gateway --tail=100

# Get logs from Auth Service
kubectl logs -n leafy -l app=auth-service --tail=100

# Follow logs in real-time
kubectl logs -n leafy -l app=api-gateway -f
```

### 9.4 Access Eureka Dashboard

1. Get the Eureka LoadBalancer URL:
```powershell
kubectl get svc discovery-server -n leafy
```

2. Access the URL in your browser

### 9.5 Test the API Gateway

```powershell
# If using LoadBalancer
curl "http://<api-gateway-lb-url>:8060/actuator/health"

# If using port-forward
Start-Job -ScriptBlock { kubectl port-forward svc/api-gateway 8060:8060 -n leafy }
Start-Sleep -Seconds 5
curl "http://localhost:8060/actuator/health"
```

### 9.6 Check Resource Usage

```powershell
# Node resource usage
kubectl top nodes

# Pod resource usage
kubectl top pods -n leafy
```

---

## 10. Appendix: Troubleshooting

### Pipeline Errors

#### "Unable to locate credentials"
**Cause:** AWS credentials not configured in Jenkins.
**Fix:** Ensure `aws-creds` credential is set up correctly in Jenkins credentials.

#### "docker: command not found"
**Cause:** Docker not installed on Jenkins server.
**Fix:** Install Docker on the Jenkins server:
```bash
sudo apt install -y docker.io
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

#### "kubectl: command not found"
**Cause:** kubectl not installed.
**Fix:** Install kubectl on the Jenkins server:
```bash
curl -LO "https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/
```

#### ECR Login Failed
**Cause:** IAM user lacks ECR permissions.
**Fix:** Ensure the IAM user has `AmazonEC2ContainerRegistryFullAccess` or specific ECR permissions.

### Kubernetes Errors

#### ImagePullBackOff
**Cause:** Kubernetes cannot pull the Docker image from ECR.
**Fix:**
1. Verify the image exists in ECR
2. Check the image tag is correct
3. Ensure the node has ECR permissions (nodes should have an IAM role with ECR permissions)
4. Check if image URL is correct in the deployment YAML

#### CrashLoopBackOff
**Cause:** Application crashes on startup.
**Fix:**
1. Check pod logs: `kubectl logs -n leafy <pod-name>`
2. Check events: `kubectl describe pod -n leafy <pod-name>`
3. Common causes: missing environment variables, wrong database hostnames, incorrect secrets

#### Pending Pods (no nodes available)
**Cause:** No nodes in the cluster or nodes don't have enough resources.
**Fix:**
1. Check nodes: `kubectl get nodes`
2. Check if nodes are Ready: `kubectl describe nodes`
3. Ensure the node group was created successfully in EKS

#### PVC Pending
**Cause:** Storage class not found or EBS volume provisioning issue.
**Fix:**
1. Check storage classes: `kubectl get sc`
2. Check PVC status: `kubectl get pvc -n leafy`
3. Verify EBS CSI driver is installed in the cluster
4. Check if nodes are in the correct AZ for the EBS volume

#### MongoDB/PostgreSQL Pods Won't Start
**Cause:** Often related to storage or initialization issues.
**Fix:**
```powershell
# Check PVC status
kubectl get pvc -n leafy

# Check pod events
kubectl describe pod mongodb-0 -n leafy

# Check logs
kubectl logs mongodb-0 -n leafy -c mongodb
```

### EC2 Deployment Errors

#### SSH Connection Refused
**Cause:** Cannot connect to EC2 instance via SSH.
**Fix:**
1. Verify the EC2 machine IP is correct in Jenkins credentials
2. Check the EC2 security group allows SSH (port 22) from Jenkins server IP
3. Verify the SSH key is correct
4. Check if the EC2 instance is running

#### Docker Compose Failed
**Cause:** Docker Compose execution failed on EC2.
**Fix:**
1. SSH into the EC2 machine manually
2. Check Docker is running: `sudo systemctl status docker`
3. Check Docker Compose is installed: `docker compose version`
4. Check disk space: `df -h`
5. Check Docker logs: `sudo journalctl -u docker`

### General Debugging Commands

```powershell
# List all resources in the leafy namespace
kubectl get all -n leafy

# Describe a specific resource
kubectl describe deployment api-gateway -n leafy

# Get logs from all pods of a service
kubectl logs -n leafy -l app=api-gateway --tail=200

# Execute into a container
kubectl exec -it <pod-name> -n leafy -- sh

# Check resource events
kubectl get events -n leafy --sort-by='.lastTimestamp'

# Check cluster-wide events
kubectl get events --sort-by='.lastTimestamp'

# Check node status
kubectl describe nodes
```

To view kubelet logs on EKS nodes, use:
```bash
# SSH into the node (requires SSM or bastion)
ssh -i leafy-ec2-key.pem ubuntu@<node-ip>
journalctl -u kubelet
```

---

## Pipeline Parameters Reference

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| SERVICE | choice | all | Service to build/deploy. `all` builds and deploys everything |
| ENVIRONMENT | choice | dev | Target environment (dev, staging, production) |
| SKIP_TESTS | boolean | false | Skip unit tests during build |
| DEPLOY_EKS | boolean | true | Deploy application containers to EKS |
| DEPLOY_EC2 | boolean | false | Deploy services to EC2 machines |

**Image Tag Format:**
- Production: `{timestamp}` e.g., `20240521-153000`
- Non-production: `{environment}-{timestamp}` e.g., `dev-20240521-153000`

---

## Quick Reference: Services and Ports

### Spring Boot Services (EKS)

| Service | Port | Replicas |
|---------|------|----------|
| discovery-server | 8761 | 1 |
| config-server | 8888 | 1 |
| api-gateway | 8060 | 2 |
| auth-service | 8081 | 2 |
| profile-service | 8083 | 2 |
| file-service | 8084 | 2 |
| plant-management-service | 8085 | 2 |
| search-service | 8088 | 2 |
| notification-service | 8095 | 2 |
| iot-metrics-collector-service | 8087 | 2 |
| community-feed-service | 8090 | 2 |
| socket-service | 8093 | 2 |
| message-service | 8092 | 2 |

### Python Services (EKS)

| Service | Port | Replicas |
|---------|------|----------|
| rag-service | 8199 | 1 |
| disease-detection-service | 8096 | 1 |

### Infrastructure (EKS)

| Service | Port | Type |
|---------|------|------|
| mongodb | 27017 | StatefulSet (1 replica) |
| postgresql | 5432 | StatefulSet (1 replica) |
| redis | 6379 | Deployment |
| kafka | 9092 | StatefulSet (1 replica) |
| mqtt-broker | 1883 | Deployment |
| elasticsearch | 9200 | StatefulSet (1 replica) |
| kibana | 5601 | Deployment |
| qdrant | 6333 | StatefulSet (1 replica) |
| fluent-bit | 2020 | DaemonSet |

### EC2 Machine Tiers

| Machine | Tier | Services |
|---------|------|----------|
| Machine 1 | Data | MongoDB, PostgreSQL |
| Machine 2 | Messaging | Kafka, MQTT Broker, Redis, Kafka-UI |
| Machine 3 | Search/AI | Elasticsearch, Kibana, Qdrant, Fluent-bit |
| Machine 4 | App | All Spring Boot microservices |

---

*Last updated: May 2026*
