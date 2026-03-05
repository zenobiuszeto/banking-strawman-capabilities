variable "environment" {
  description = "Deployment environment (staging, production)"
  type        = string
  default     = "staging"
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Environment must be staging or production."
  }
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "banking-platform"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_cluster_version" {
  description = "Kubernetes version for EKS"
  type        = string
  default     = "1.30"
}

variable "eks_node_instance_types" {
  description = "EC2 instance types for EKS node group"
  type        = list(string)
  default     = ["m6i.xlarge"]
}

variable "eks_min_nodes" {
  type    = number
  default = 3
}

variable "eks_max_nodes" {
  type    = number
  default = 10
}

variable "eks_desired_nodes" {
  type    = number
  default = 3
}

variable "rds_instance_class" {
  description = "RDS PostgreSQL instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "rds_storage_gb" {
  type    = number
  default = 100
}

variable "elasticache_node_type" {
  description = "Redis ElastiCache node type"
  type        = string
  default     = "cache.t3.medium"
}

variable "elasticache_num_nodes" {
  type    = number
  default = 2
}

variable "documentdb_instance_class" {
  description = "DocumentDB instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "documentdb_instance_count" {
  type    = number
  default = 3
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "banking_admin"
  sensitive   = true
}

variable "db_password" {
  description = "PostgreSQL master password — use Terraform Cloud workspace variable"
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Common resource tags"
  type        = map(string)
  default = {
    Project     = "banking-platform"
    ManagedBy   = "terraform"
    Owner       = "platform-engineering"
  }
}
