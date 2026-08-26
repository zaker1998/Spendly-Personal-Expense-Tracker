output "app_url" {
  description = "Public URL of the SPA."
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "cors_allowed_origins" {
  description = <<-EOT
    Set this as CORS_ALLOWED_ORIGINS on the API. The browser treats /api/* as
    same-origin, but the API sees an Origin header that does not match its own
    host, so it still applies the CORS check. See docs/ENGINEERING_NOTES.md.
  EOT
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "s3_bucket" {
  description = "Repository variable AWS_S3_BUCKET."
  value       = aws_s3_bucket.web.id
}

output "cloudfront_distribution_id" {
  description = "Repository variable AWS_CLOUDFRONT_DISTRIBUTION_ID."
  value       = aws_cloudfront_distribution.web.id
}

output "deploy_role_arn" {
  description = "Repository variable AWS_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.deploy.arn
}
