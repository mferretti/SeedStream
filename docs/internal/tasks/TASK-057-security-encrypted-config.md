# TASK-057: Security - Encrypted Configuration Files

**Status**: ✅ Complete  
**Priority**: P3 (Low)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: TASK-034 (SecretResolver interface)  
**Human Supervision**: HIGH

---

## Objective

Allow sensitive values in job YAML to be stored as encrypted ciphertext, decrypted at runtime
using a key from env var or key file. Useful for teams that commit configs to source control
but cannot use a secrets manager.

---

## Scope

**Prerequisite**: TASK-034 must be complete (`SecretResolver` interface exists).

**In scope:**
- `EncryptedFileResolver` implementing `SecretResolver`
- AES-256-GCM encryption (JDK standard, no new dependency)
- `seedstream encrypt` CLI subcommand: encrypts a plaintext value → prints ciphertext
- `${SECRET:enc:BASE64_CIPHERTEXT}` syntax for inline encrypted values
- Decryption key from `SEEDSTREAM_ENCRYPTION_KEY` env var (32-byte hex) or key file path
- Unit tests for encrypt/decrypt round-trip

**Out of scope:**
- Key rotation (re-encryption of existing ciphertexts)
- Key derivation from password (KDF) — use raw 256-bit key only
- HSM integration

---

## Configuration Syntax

```yaml
secrets:
  resolver: encrypted_file
  key_env: SEEDSTREAM_ENCRYPTION_KEY   # default; or key_file: /etc/seedstream/key.hex

destination:
  type: database
  conf:
    # encrypt with: ./seedstream encrypt --key $SEEDSTREAM_ENCRYPTION_KEY "mypassword"
    password: "${SECRET:enc:AES256GCM:BASE64CIPHERTEXT...}"
```

---

## CLI Subcommand

```bash
# Encrypt a value (prints ciphertext for pasting into YAML)
./seedstream encrypt "my-plaintext-password"

# Output:
# AES256GCM:BASE64CIPHERTEXT...
```

---

## Acceptance Criteria

- [ ] `EncryptedFileResolver` implements `SecretResolver`
- [ ] AES-256-GCM encryption; IV stored with ciphertext (prefix or header)
- [ ] `seedstream encrypt` CLI subcommand produces copy-pasteable ciphertext
- [ ] Decryption key from `SEEDSTREAM_ENCRYPTION_KEY` env var or `key_file` path
- [ ] `ConfigurationException` on wrong key (authentication tag mismatch)
- [ ] Plaintext never logged; ciphertext logged only at TRACE
- [ ] Unit tests: encrypt → decrypt round-trip; tampered ciphertext rejected
- [ ] Documentation: key generation instructions (`openssl rand -hex 32`)

---

**Completion Date**: [Mark when complete]
