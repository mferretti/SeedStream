# TASK-056: Security - Azure Key Vault Backend

**Status**: ✅ Done  
**Priority**: P3 (Low)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: TASK-034 (SecretResolver interface)  
**Human Supervision**: MEDIUM

---

## Objective

Add an Azure Key Vault implementation of `SecretResolver` so jobs running in Azure environments
can resolve secrets without env-var indirection.

---

## Scope

**Prerequisite**: TASK-034 must be complete (`SecretResolver` interface exists).

**In scope:**
- `AzureKeyVaultResolver` implementing `SecretResolver`
- Auth via Azure DefaultAzureCredential chain (env vars → managed identity → Azure CLI)
- `vault_uri` in `secrets:` block (e.g. `https://myvault.vault.azure.net`)
- Secret name as path: `${SECRET:my-db-password}`
- Optional secret version suffix: `${SECRET:my-db-password/abc123}`
- Unit tests with mocked `SecretClient`

**Out of scope:**
- Key operations (encrypt/decrypt)
- Certificate management
- Cross-tenant access

---

## Configuration Syntax

```yaml
secrets:
  resolver: azure_keyvault
  vault_uri: https://myvault.vault.azure.net

destination:
  type: database
  conf:
    password: "${SECRET:my-db-password}"
```

---

## Acceptance Criteria

- [x] `AzureKeyVaultResolver` implements `SecretResolver`
- [x] DefaultAzureCredential chain used (no explicit client secret in YAML)
- [x] `vault_uri` config key required; `ConfigurationException` if absent
- [x] Optional version suffix in secret path
- [x] `ConfigurationException` on secret not found or permission denied
- [x] Secrets never logged
- [x] Unit tests with mocked `SecretClient`

---

**Completion Date**: 2026-06-05
