variable "region" {
  description = "Region for the S3 bucket. CloudFront itself is global."
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Name prefix for every resource."
  type        = string
  default     = "spendly"
}

variable "api_origin_domain" {
  description = <<-EOT
    Hostname of the running API, without scheme. CloudFront proxies /api/* to it
    so the SPA and the API share one origin and the browser never makes a
    cross-origin request.
  EOT
  type        = string
  default     = "spendly-33ek.onrender.com"

  validation {
    condition     = !can(regex("^https?://", var.api_origin_domain))
    error_message = "Give the bare hostname, not a URL."
  }
}

variable "github_repository" {
  description = "owner/repo allowed to assume the deploy role via OIDC."
  type        = string
  default     = "zaker1998/Spendly-Personal-Expense-Tracker"

  validation {
    condition     = can(regex("^[^/]+/[^/]+$", var.github_repository))
    error_message = "Expected the owner/repo form."
  }
}

variable "github_deploy_branch" {
  description = "Only this branch may assume the deploy role."
  type        = string
  default     = "main"
}

variable "create_github_oidc_provider" {
  description = <<-EOT
    Create the GitHub OIDC provider. An AWS account can only hold one per issuer,
    so set this to false if another stack in the same account already created it.
  EOT
  type        = bool
  default     = true
}

variable "price_class" {
  description = "PriceClass_100 is Europe + North America, which is the cheapest and covers the audience."
  type        = string
  default     = "PriceClass_100"
}
