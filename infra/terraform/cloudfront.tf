# Managed policy ids are stable AWS-wide; the data sources spell out which one
# is which instead of leaving four UUIDs in the distribution block.
data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.project}-s3-oac"
  description                       = "Signs CloudFront's reads of the SPA bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_router" {
  name    = "${var.project}-spa-router"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrites Angular client-side routes to /index.html"
  publish = true
  code    = file("${path.module}/functions/spa-router.js")
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name    = "${var.project}-security-headers"
  comment = "HSTS and the usual hardening headers, applied at the edge"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = false
      override                   = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }

    # script-src has no 'unsafe-inline', which is the directive that actually
    # matters. Keeping it that way needed a build change -- see section 8 of
    # docs/ENGINEERING_NOTES.md. style-src still needs it: the
    # build inlines the Google Fonts @font-face rules into a <style> block, and
    # the font files themselves are fetched from gstatic.
    content_security_policy {
      content_security_policy = join("; ", [
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
        "font-src 'self' data: https://fonts.gstatic.com",
        "img-src 'self' data:",
        "connect-src 'self'",
        "object-src 'none'",
        "frame-ancestors 'none'",
        "base-uri 'self'",
        "form-action 'self'",
      ])
      override = true
    }
  }
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.project} SPA + API"
  default_root_object = "index.html"
  price_class         = var.price_class

  origin {
    origin_id                = "s3-spa"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  origin {
    origin_id   = "api"
    domain_name = var.api_origin_domain

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      # The API sleeps on a free plan and can take most of a minute to wake up.
      origin_read_timeout = 60
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-spa"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id

    # SPA fallback. Deliberately only on this behaviour -- see the comment in
    # functions/spa-router.js for why this is not custom_error_response.
    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_router.arn
    }
  }

  # Serving the API under the same hostname is the whole reason for the second
  # origin: the browser makes same-origin calls, so there is no preflight on the
  # request path and no Render URL baked into the bundle.
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "api"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # No custom domain, so the default *.cloudfront.net certificate is used. AWS
  # pins the TLS policy for it; minimum_protocol_version is rejected here.
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
