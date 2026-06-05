# TASK-034: Security - Secret Management (Scoped: Interface + Vault)

**Status**: ✅ Complete (June 5, 2026, v0.6.0)  
**Priority**: P2 (Medium)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: TASK-005 (Seed Resolution)  
**Human Supervision**: HIGH  
**Related**: TASK-055 (AWS), TASK-056 (Azure), TASK-057 (Encrypted File)

---

## Objective

Add a pluggable `SecretResolver` interface and a HashiCorp Vault backend. Existing `${ENV_VAR}`
substitution is preserved as-is. Users who need AWS/Azure/encrypted-file backends can track those
via TASK-055/056/057 (deferred).

---

## Scope

**In scope:**
- `SecretResolver` interface (in `schema` module)
- `EnvSecretResolver` — wraps existing env-var lookup (default, backward-compatible)
- `VaultSecretResolver` — KV v2 API over HTTPS; token read from `VAULT_TOKEN` env var
- `${SECRET:path/to/secret}` new syntax for resolver-backed values
- `${ENV_VAR}` existing syntax: unchanged, still resolved via env vars
- Optional `secrets:` block in job YAML to configure resolver
- Secrets masked as `***` in all log output
- Unit tests with mocked `SecretResolver`; no Vault container required

**Out of scope (deferred):**
- AWS Secrets Manager → TASK-055
- Azure Key Vault → TASK-056
- Encrypted config files → TASK-057
- Secret rotation / runtime refresh
- Audit logging

---

## Configuration Syntax

### Job YAML — using Vault

```yaml
seed:
  type: embedded
  value: 42

secrets:
  resolver: vault
  vault_addr: https://vault.example.com:8200   # or ${VAULT_ADDR}
  vault_namespace: myteam                       # optional
  # vault_token always read from VAULT_TOKEN env var — never in YAML

destination:
  type: database
  conf:
    jdbc_url: "jdbc:postgresql://localhost:5432/mydb"
    username: "appuser"
    password: "${SECRET:secret/data/myapp/db#password}"
```

### Job YAML — env vars only (default, no change)

```yaml
destination:
  type: database
  conf:
    password: "${DB_PASSWORD}"   # existing behavior, unchanged
```

---

## Implementation Details

### SecretResolver interface (`schema` module)

```java
public interface SecretResolver {
    /** Resolve a secret path to its plaintext value. Never returns null. */
    String resolve(String secretPath);
}
```

### EnvSecretResolver (default)

Wraps current `${VAR_NAME}` logic. Throws `ConfigurationException` if env var not set.

### VaultSecretResolver

- HTTP GET `{vault_addr}/v1/{secretPath}` with `X-Vault-Token: $VAULT_TOKEN`
- KV v2: parses `data.data.<field>` from JSON response
- Optional `X-Vault-Namespace` header if `vault_namespace` set
- Uses JDK `HttpClient` (no new dependency)
- Connection timeout: 5s; read timeout: 10s
- On non-200: throw `ConfigurationException` with status code, path masked

### Substitution in ConfigParser

Extend current substitution logic:
- `${VAR_NAME}` → `EnvSecretResolver` (unchanged)
- `${SECRET:path}` → configured `SecretResolver` (Vault or custom)

Masking: before any log statement, replace resolved secret values with `***`.

---

## Acceptance Criteria

- ✅ `SecretResolver` interface in `schema` module (`schema/secret/` package)
- ✅ `EnvSecretResolver` wraps existing env-var logic (backward compatible, `System.getProperty` fallback for tests)
- ✅ `VaultSecretResolver` resolves KV v2 secrets over HTTPS; KV v1 auto-detected via `data.data` structure
- ✅ `${SECRET:path}` syntax wired into config substitution via `ConfigSubstitutor`
- ✅ `${ENV_VAR}` syntax unchanged and still works (always resolved from env, independent of configured resolver)
- ✅ `secrets:` block parsed from job YAML (`SecretsConfig`); defaults to `EnvSecretResolver` if absent
- ✅ Vault token from `VAULT_TOKEN` env var only — never read from YAML
- ✅ Secrets never appear in logs (log only path at DEBUG, never value)
- ✅ `SecretResolutionException` on missing env var, Vault unreachable, or secret not found
- ✅ `SecretResolverFactory` creates correct resolver from `SecretsConfig`
- ✅ Kafka credential fields (`sasl_jaas_config`, SSL passwords) now support substitution
- ✅ Database fields (`jdbc_url`, `username`, `password`, `table`) migrated to `ConfigSubstitutor`
- ✅ Unit tests: `EnvSecretResolverTest` (3 tests), `ConfigSubstitutorTest` (7 tests), `VaultSecretResolverTest` (10 tests, mock `HttpClient`)
- ✅ `#field` suffix for extracting specific field from Vault KV JSON secret

---

**Completion Date**: June 5, 2026
