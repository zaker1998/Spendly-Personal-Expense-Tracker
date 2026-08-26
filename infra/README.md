# Infrastructure

Terraform for the frontend half of the deployment: the Angular bundle is served
from S3 through CloudFront, and CloudFront also proxies `/api/*` to the Spring
Boot API. The API and its database are not managed here — see the Deployment
section of the root README.

```
CloudFront  ──/*──────▶  S3 (private, Origin Access Control)
            └─/api/*──▶  Render (Spring Boot)  ──▶  Neon (PostgreSQL)
```

## Why the SPA moved off the API host

The single-container deployment served the SPA from the same nginx that proxied
the API, which meant every static asset waited on a free-tier instance that
sleeps after 15 minutes. Static files have no reason to share that fate: on
CloudFront they come from an edge cache in Vienna in a few milliseconds, cold
instance or not. The app now renders immediately and only the first data call
pays the wake-up cost — a much better failure mode than a blank page.

Serving `/api/*` through the same distribution is what makes that work without
new problems: the browser only ever talks to one origin, so no CORS preflight
sits in front of the login request and the Render hostname is not baked into the
bundle. Moving the API elsewhere is a change to one Terraform variable.

## What gets created

| Resource | Notes |
|---|---|
| S3 bucket | Private. Versioned, with non-current versions expiring after 30 days |
| Origin Access Control | CloudFront signs its reads; the bucket policy is scoped to this one distribution |
| CloudFront distribution | Two origins (S3 and the API), `PriceClass_100` |
| CloudFront Function | Viewer-request rewrite of client-side routes to `/index.html`, attached to the static behaviour only |
| Response headers policy | HSTS, CSP, `X-Content-Type-Options`, `frame-options: DENY`, referrer policy |
| GitHub OIDC provider + IAM role | Lets CI deploy without a long-lived access key |

Everything sits inside the AWS free tier at portfolio traffic — CloudFront's
1 TB/month egress allowance does not expire, and the bundle is about 1 MB.

## Apply

Needs Terraform ≥ 1.9 (or OpenTofu) and credentials for an account you own.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # then edit
terraform init
terraform plan
terraform apply
```

The distribution takes a few minutes to reach `Deployed`.

## Wire up the deploy

`terraform output` prints everything the pipeline needs. Add these as repository
**variables** (Settings → Secrets and variables → Actions → Variables) — none of
them is sensitive, and the credential itself is a short-lived OIDC token:

| Variable | From |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `terraform output deploy_role_arn` |
| `AWS_S3_BUCKET` | `terraform output s3_bucket` |
| `AWS_CLOUDFRONT_DISTRIBUTION_ID` | `terraform output cloudfront_distribution_id` |
| `AWS_CLOUDFRONT_DOMAIN` | host part of `terraform output app_url` |
| `AWS_REGION` | optional, defaults to `eu-central-1` |

`.github/workflows/deploy-frontend.yml` skips itself while `AWS_DEPLOY_ROLE_ARN`
is unset, so a fork without an AWS account still gets a green pipeline.

## One setting on the API side

Set `CORS_ALLOWED_ORIGINS` on Render to the value of
`terraform output cors_allowed_origins`.

This is not redundant with the same-origin routing. The browser sees one origin
and skips the preflight, but CloudFront forwards the viewer's `Origin` header to
Render while rewriting `Host` to the Render hostname — so the API sees an
`Origin` that does not match its own host and applies the CORS check anyway.
`GET`s are unaffected (browsers omit `Origin` on same-origin `GET`s); the login
`POST` is the request that fails without this. Written up in
[docs/ENGINEERING_NOTES.md](../docs/ENGINEERING_NOTES.md).

## Teardown

```bash
terraform destroy
```

The bucket is versioned, so empty it first (`aws s3 rm s3://<bucket> --recursive`
removes current objects; non-current versions need `--force` on the bucket or a
lifecycle pass) or the destroy fails on a non-empty bucket.

## Notes on the state file

State is local. That is a deliberate call for a single-operator project whose
entire stack can be rebuilt in minutes, not an oversight — `versions.tf` carries
the commented S3 backend block to switch to, using Terraform 1.10+ native
locking rather than a DynamoDB table. `.terraform.lock.hcl` is gitignored here
only because the config is validated with OpenTofu and applied with Terraform,
and the two write different registry hostnames into it; in a single-tool repo it
belongs in version control.
