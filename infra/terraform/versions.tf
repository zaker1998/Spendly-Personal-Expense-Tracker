terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # State is local on purpose: one operator, one environment, and the whole
  # stack can be rebuilt from scratch in a few minutes. The moment a second
  # person can run `apply`, move it to S3 -- native locking since Terraform
  # 1.10, so no DynamoDB table is needed any more.
  #
  # backend "s3" {
  #   bucket       = "spendly-tfstate"
  #   key          = "frontend/terraform.tfstate"
  #   region       = "eu-central-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
