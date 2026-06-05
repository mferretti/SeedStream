# TASK-055: Security - AWS Secrets Manager Backend

**Status**: ✅ Complete (June 5, 2026, v0.6.0)  
**Priority**: P3 (Low)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: TASK-034 (SecretResolver interface)  
**Human Supervision**: MEDIUM

---

## Objective

Add an AWS Secrets Manager implementation of `SecretResolver` so jobs running in AWS environments
can resolve secrets without env-var indirection.

---

## Scope

**Prerequisite**: TASK-034 must be complete (`SecretResolver` interface exists).

**In scope:**
- `AwsSecretsManagerResolver` implementing `SecretResolver`
- Reads secret by ARN or name: `${SECRET:arn:aws:secretsmanager:...}` or `${SECRET:myapp/db}`
- Auth via default AWS credential chain (env vars → EC2 instance profile → ECS task role)
- Optional `aws_region` in `secrets:` block (fallback: `AWS_DEFAULT_REGION`)
- JSON secrets: optional `#field` suffix to extract a single key
- Unit tests with mocked `SecretsManagerClient`

**Out of scope:**
- Secret rotation callbacks
- Cross-account access
- KMS customer-managed keys (transparent via SDK)

---

## Configuration Syntax

```yaml
secrets:
  resolver: aws
  aws_region: us-east-1   # optional; fallback: AWS_DEFAULT_REGION env var

destination:
  type: database
  conf:
    password: "${SECRET:myapp/db#password}"
```

---

## Acceptance Criteria

- [x] `AwsSecretsManagerResolver` implements `SecretResolver`
- [x] Default AWS credential chain used (no explicit key/secret in YAML)
- [x] `aws_region` config key respected; fallback to `AWS_DEFAULT_REGION` env var
- [x] JSON secret with `#field` suffix extracts single value
- [x] Plain-string secrets returned as-is
- [x] `SecretResolutionException` on secret not found or SDK error
- [x] Secrets never logged
- [x] Unit tests with mocked `SecretsManagerClient` (9 tests)

---

**Completion Date**: June 5, 2026
