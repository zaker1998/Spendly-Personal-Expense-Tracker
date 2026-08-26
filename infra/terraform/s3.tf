# Bucket names are globally unique, so the account id keeps this reproducible
# without dragging in the random provider and a value that lives only in state.
locals {
  bucket_name = "${var.project}-web-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "web" {
  bucket = local.bucket_name
}

# The bucket is never public. CloudFront reaches it with Origin Access Control
# and signed SigV4 requests; the bucket policy below is the only grant.
resource "aws_s3_bucket_public_access_block" "web" {
  bucket = aws_s3_bucket.web.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Deploys are `s3 sync --delete`, so versioning is what makes a bad release
# recoverable: the previous objects are still there to restore.
resource "aws_s3_bucket_versioning" "web" {
  bucket = aws_s3_bucket.web.id

  versioning_configuration {
    status = "Enabled"
  }
}

# ...and this is what stops that turning into an ever-growing bill, since every
# deploy replaces every content-hashed asset.
resource "aws_s3_bucket_lifecycle_configuration" "web" {
  bucket     = aws_s3_bucket.web.id
  depends_on = [aws_s3_bucket_versioning.web]

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

data "aws_iam_policy_document" "bucket" {
  statement {
    sid     = "AllowCloudFrontRead"
    actions = ["s3:GetObject"]

    resources = ["${aws_s3_bucket.web.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    # Scoping to this distribution is the point of OAC: the service principal
    # alone would let any CloudFront distribution in any account read the bucket.
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.bucket.json

  depends_on = [aws_s3_bucket_public_access_block.web]
}
