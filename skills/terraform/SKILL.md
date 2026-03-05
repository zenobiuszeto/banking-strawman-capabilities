---
name: terraform
description: |
  **Terraform IaC Skill**: Production-grade AWS infrastructure as code for the banking platform using Terraform 1.8+ with community modules. Covers VPC, EKS, RDS PostgreSQL, ElastiCache Redis, DocumentDB, S3, security groups, Helm releases (Vault, Consul, nginx, cert-manager), S3 remote state, DynamoDB locking, workspaces, variable validation, sensitive outputs, and CI/CD integration via GitHub Actions.

  MANDATORY TRIGGERS: Terraform, terraform, .tf, HCL, terraform init, terraform plan, terraform apply, terraform destroy, terraform workspace, terraform state, tfstate, terraform import, terraform output, provider, resource, module, variable, locals, data source, backend, S3 backend, DynamoDB lock, remote state, terraform fmt, terraform validate, tflint, tfsec, checkov, infracost, terraform-aws-modules, vpc module, eks module, rds module, helm_release, kubernetes provider, helm provider, aws provider, VPC, EKS, RDS, ElastiCache, DocumentDB, S3, security group, IAM, IRSA, outputs.tf, variables.tf, main.tf, IaC, infrastructure as code, GitOps.
---

# Terraform IaC Skill — AWS Banking Platform Infrastructure

You are writing and maintaining Terraform code for the **banking platform** in `infra/terraform/`. The codebase uses:
- **Terraform 1.8+** with `terraform-aws-modules` community modules
- **S3 backend** (`banking-platform-terraform-state`) + **DynamoDB locking** (`banking-platform-tf-lock`)
- **AWS Provider 5.x**, Kubernetes Provider 2.x, Helm Provider 2.x
- **Environments**: `staging` and `production` via workspace + `tfvars` files

---

## Repository Layout

```
infra/terraform/
├── main.tf           ← VPC, EKS, RDS, Redis, DocumentDB, S3, Security Groups, Helm
├── variables.tf      ← All input variables with types, defaults, and validation
├── outputs.tf        ← Sensitive and non-sensitive outputs
├── terraform.tfvars  ← Non-secret defaults (committed)
├── staging.tfvars    ← Staging overrides
├── production.tfvars ← Production overrides
└── modules/          ← Optional local modules for reusable components
    └── banking-namespace/
```

---

## Backend & Provider Setup

```hcl
# main.tf
terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.13"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "~> 4.0"
    }
  }

  # Remote state — must be bootstrapped manually the first time
  backend "s3" {
    bucket         = "banking-platform-terraform-state"
    key            = "infra/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "banking-platform-tf-lock"
  }
}

provider "aws" {
  region = var.aws_region

  # Apply common tags to EVERY resource automatically
  default_tags {
    tags = merge(var.tags, {
      Environment = var.environment
      ManagedBy   = "terraform"
      Project     = var.project_name
    })
  }
}
```

---

## Bootstrap Remote State (one-time)

```bash
# Run before first terraform init — creates the S3 bucket + DynamoDB table
aws s3api create-bucket \
  --bucket banking-platform-terraform-state \
  --region us-east-1

aws s3api put-bucket-versioning \
  --bucket banking-platform-terraform-state \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket banking-platform-terraform-state \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws dynamodb create-table \
  --table-name banking-platform-tf-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

---

## VPC Module

```hcl
# main.tf — VPC
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.project_name}-${var.environment}-vpc"
  cidr = var.vpc_cidr

  azs             = var.availability_zones
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  database_subnets = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]

  enable_nat_gateway     = true
  single_nat_gateway     = var.environment == "staging"  # Save cost in staging
  one_nat_gateway_per_az = var.environment == "production"  # HA in prod
  enable_dns_hostnames   = true
  enable_dns_support     = true

  # Required for EKS load balancer controller
  public_subnet_tags = {
    "kubernetes.io/role/elb"                          = "1"
    "kubernetes.io/cluster/${local.cluster_name}"     = "shared"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"                 = "1"
    "kubernetes.io/cluster/${local.cluster_name}"     = "shared"
  }

  create_database_subnet_group = true
}

locals {
  cluster_name = "${var.project_name}-${var.environment}"
}
```

---

## EKS Module

```hcl
# main.tf — EKS
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.cluster_name
  cluster_version = var.eks_cluster_version

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true

  # EKS Add-ons (managed, auto-updated)
  cluster_addons = {
    coredns            = { most_recent = true }
    kube-proxy         = { most_recent = true }
    vpc-cni            = { most_recent = true }
    aws-ebs-csi-driver = { most_recent = true }
  }

  # IRSA — enables pod-level IAM via service account annotations
  enable_irsa = true

  eks_managed_node_groups = {
    # Application pods
    app = {
      name           = "app-nodes"
      instance_types = var.eks_node_instance_types  # ["m6i.xlarge"]
      min_size       = var.eks_min_nodes
      max_size       = var.eks_max_nodes
      desired_size   = var.eks_desired_nodes

      labels = { role = "app" }

      iam_role_additional_policies = {
        AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
      }

      update_config = {
        max_unavailable_percentage = 33  # Rolling node updates
      }
    }

    # Batch / long-running jobs on dedicated nodes
    batch = {
      name           = "batch-nodes"
      instance_types = ["m6i.2xlarge"]
      min_size       = 0
      max_size       = 5
      desired_size   = 1

      labels = { role = "batch" }
      taints = [{
        key    = "dedicated"
        value  = "batch"
        effect = "NO_SCHEDULE"
      }]
    }

    # Prometheus + Grafana — needs extra disk
    monitoring = {
      name           = "monitoring-nodes"
      instance_types = ["m6i.large"]
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      disk_size      = 200

      labels = { role = "monitoring" }
    }
  }
}
```

---

## RDS PostgreSQL

```hcl
module "rds" {
  source  = "terraform-aws-modules/rds/aws"
  version = "~> 6.0"

  identifier        = "${local.cluster_name}-postgres"
  engine            = "postgres"
  engine_version    = "16.2"
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_storage_gb
  max_allocated_storage = var.rds_storage_gb * 2  # Auto-scale storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "banking_db"
  username = var.db_username
  manage_master_user_password = true  # Rotate via Secrets Manager automatically

  port     = 5432
  multi_az = var.environment == "production"

  db_subnet_group_name   = module.vpc.database_subnet_group
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  # Backups
  maintenance_window      = "Mon:00:00-Mon:03:00"
  backup_window           = "03:00-06:00"
  backup_retention_period = var.environment == "production" ? 30 : 7
  copy_tags_to_snapshot   = true

  # Protection
  deletion_protection = var.environment == "production"
  skip_final_snapshot = var.environment != "production"

  # Performance Insights + Enhanced Monitoring
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  monitoring_interval                   = 60
  monitoring_role_arn                   = aws_iam_role.rds_monitoring.arn
  create_monitoring_role                = true

  # PostgreSQL parameters
  parameters = [
    { name = "log_statement",                    value = "ddl" },
    { name = "log_min_duration_statement",       value = "1000" },
    { name = "max_connections",                  value = "500" },
    { name = "shared_preload_libraries",         value = "pg_stat_statements" },
    { name = "log_lock_waits",                   value = "1" },
  ]

  db_option_group_tags = { "Sensitive" = "low" }
  db_parameter_group_tags = { "Sensitive" = "low" }
}
```

---

## ElastiCache Redis

```hcl
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${local.cluster_name}-redis"
  description                = "Redis cache — ${var.environment}"
  node_type                  = var.elasticache_node_type
  num_cache_clusters         = var.environment == "production" ? 3 : 1
  parameter_group_name       = "default.redis7"
  engine_version             = "7.0"
  port                       = 6379

  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis_sg.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result  # TLS auth token

  automatic_failover_enabled = var.environment == "production"
  multi_az_enabled           = var.environment == "production"

  # Maintenance
  maintenance_window         = "tue:03:00-tue:04:00"
  snapshot_window            = "02:00-03:00"
  snapshot_retention_limit   = var.environment == "production" ? 7 : 1

  lifecycle {
    prevent_destroy = var.environment == "production"
  }
}

resource "random_password" "redis_auth" {
  length  = 32
  special = false   # Redis auth token can't contain some special chars
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${local.cluster_name}-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}
```

---

## Helm Releases (Platform Services)

```hcl
# Kubernetes + Helm provider auth via EKS token
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
  }
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
    }
  }
}

# NGINX Ingress
resource "helm_release" "nginx_ingress" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  version          = "4.10.0"
  wait             = true
  timeout          = 300

  values = [yamlencode({
    controller = {
      replicaCount = var.environment == "production" ? 3 : 1
      service = { type = "LoadBalancer" }
      metrics = { enabled = true }
    }
  })]
}

# cert-manager
resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  namespace        = "cert-manager"
  create_namespace = true
  version          = "v1.14.5"

  set { name = "installCRDs"; value = "true" }
}

# HashiCorp Vault
resource "helm_release" "vault" {
  name             = "vault"
  repository       = "https://helm.releases.hashicorp.com"
  chart            = "vault"
  namespace        = "infra"
  create_namespace = true
  version          = "0.28.0"

  values = [yamlencode({
    server = {
      ha = {
        enabled  = var.environment == "production"
        replicas = var.environment == "production" ? 3 : 1
      }
      dataStorage = { size = "10Gi" }
    }
    injector = { enabled = true }
  })]
}

# HashiCorp Consul (service discovery + config KV)
resource "helm_release" "consul" {
  name             = "consul"
  repository       = "https://helm.releases.hashicorp.com"
  chart            = "consul"
  namespace        = "infra"
  create_namespace = true
  version          = "1.4.0"

  values = [yamlencode({
    global = {
      datacenter = "${var.environment}-dc1"
    }
    server = {
      replicas    = var.environment == "production" ? 3 : 1
      storageClass = "gp3"
    }
    ui = { enabled = true }
  })]
}
```

---

## Variables (`variables.tf`)

```hcl
variable "environment" {
  description = "Deployment environment"
  type        = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Must be staging or production."
  }
}

variable "aws_region"   { type = string; default = "us-east-1" }
variable "project_name" { type = string; default = "banking-platform" }
variable "vpc_cidr"     { type = string; default = "10.0.0.0/16" }

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_cluster_version"    { type = string; default = "1.30" }
variable "eks_node_instance_types" { type = list(string); default = ["m6i.xlarge"] }
variable "eks_min_nodes"           { type = number; default = 3 }
variable "eks_max_nodes"           { type = number; default = 10 }
variable "eks_desired_nodes"       { type = number; default = 3 }

variable "rds_instance_class"  { type = string; default = "db.t3.medium" }
variable "rds_storage_gb"      { type = number; default = 100 }
variable "db_username"         { type = string; default = "banking_admin"; sensitive = true }

variable "elasticache_node_type" { type = string; default = "cache.t3.medium" }

variable "tags" {
  type = map(string)
  default = {
    Project   = "banking-platform"
    ManagedBy = "terraform"
    Owner     = "platform-engineering"
  }
}
```

---

## Outputs (`outputs.tf`)

```hcl
output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value     = module.eks.cluster_endpoint
  sensitive = true    # Never logs/prints this
}

output "rds_endpoint" {
  value     = module.rds.db_instance_endpoint
  sensitive = true
}

output "redis_primary_endpoint" {
  value     = aws_elasticache_replication_group.redis.primary_endpoint_address
  sensitive = true
}

output "kubeconfig_command" {
  value = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.aws_region}"
}

output "reports_bucket" {
  value = aws_s3_bucket.reports.bucket
}
```

---

## GitHub Actions CI/CD for Terraform

```yaml
# .github/workflows/terraform.yml
name: Terraform Plan & Apply

on:
  push:
    branches: [ "main" ]
    paths: [ "infra/terraform/**" ]
  pull_request:
    paths: [ "infra/terraform/**" ]

jobs:
  terraform:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: infra/terraform
    steps:
      - uses: actions/checkout@v4

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: "1.8.0"

      - run: terraform fmt -check -recursive
        name: Format check

      - run: terraform init
        name: Init

      - run: terraform validate
        name: Validate

      - name: tfsec (security scan)
        uses: aquasecurity/tfsec-action@v1
        with:
          working_directory: infra/terraform

      - run: terraform plan -out=tfplan -var-file=staging.tfvars
        name: Plan (staging)
        if: github.event_name == 'pull_request'

      - name: Show plan summary
        if: github.event_name == 'pull_request'
        run: terraform show -no-color tfplan | head -100

      - run: terraform apply -auto-approve -var-file=staging.tfvars
        name: Apply (staging — main branch only)
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
```

---

## Useful Terraform Commands

```bash
# Initialise with backend config
terraform init

# Plan for specific environment
terraform plan -var-file=production.tfvars -out=production.tfplan

# Apply
terraform apply production.tfplan

# Inspect state
terraform state list
terraform state show module.eks

# Import existing resource (e.g., manually created S3 bucket)
terraform import aws_s3_bucket.reports banking-platform-production-reports

# Targeted apply (apply only one resource)
terraform apply -target=helm_release.vault -var-file=staging.tfvars

# Destroy (staging only — never use on prod without DRY RUN)
terraform destroy -var-file=staging.tfvars

# Show outputs
terraform output -json | jq .

# Move resource in state (after refactoring)
terraform state mv \
  module.eks.aws_eks_cluster.this \
  module.eks.aws_eks_cluster.main
```

---

## Critical Rules

1. **Never commit secrets** — use `sensitive = true` on variables and never put secrets in `.tfvars` files in Git.
2. **Always use remote state with locking** — S3 + DynamoDB prevents concurrent applies that corrupt state.
3. **`lifecycle { prevent_destroy = true }`** on all stateful production resources (RDS, ElastiCache, S3).
4. **`default_tags`** on the AWS provider — every resource gets consistent tagging automatically.
5. **`-var-file` per environment** — never use workspaces as the sole environment separator; use separate state keys or directories.
6. **Run `terraform plan` on every PR** — post the plan as a PR comment; never apply without reviewing the plan.
7. **Use `manage_master_user_password = true`** on RDS — auto-rotates via Secrets Manager without Terraform re-apply.
8. **Never use `allow_overwrite = true`** on DNS records in production — accidental overwrites cause outages.
9. **Lock provider versions** (`~>` minor) — patch upgrades are safe; minor and major can break resources.
10. **Run `tfsec` or `checkov`** on every PR — catch security misconfigurations (public S3, unencrypted RDS) before apply.
