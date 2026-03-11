# Security Policy

## Supported Versions

We release security updates for the following versions:

| Version | Supported          | Status |
| ------- | ------------------ | ------ |
| 0.4.x   | :white_check_mark: | Current stable release |
| 0.3.x   | :x:                | No longer supported |
| < 0.3   | :x:                | No longer supported |

**Recommendation**: Always use the latest release for the most up-to-date security fixes.

---

## Current Security Posture

**Status as of March 2026:**

| Metric | Status | Details |
|--------|--------|---------|
| **Known Vulnerabilities (CVSS 7.0+)** | ✅ **0** | All high-severity CVEs resolved |
| **Dependency Versions** | ✅ **Latest Stable** | All libraries upgraded to latest stable releases |
| **OWASP Dependency-Check** | ✅ **Enabled** | Automated scanning in CI/CD pipeline |
| **Suppressions** | ✅ **Minimal** | Only 1 low-risk suppression (log4j 2.25.2 in benchmarks module, CVSS 4.8) |
| **Last Full Audit** | ✅ **March 2026** | All modules scanned, 0 vulnerabilities found |

**Key Dependency Versions:**
- Jackson: 2.21.1 (latest)
- Kafka: 4.2.0 (latest)
- Protobuf: 4.34.0 (latest, resolved CVE-2024-7254)
- MySQL Connector: 9.6.0 (latest)
- All other dependencies at latest stable versions

**Dependency Management**: Centralized in `gradle/libs.versions.toml` ([Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html))

**Scan Yourself**: Run `./gradlew dependencyCheckAll` to verify locally. See [docs/QUALITY.md](docs/QUALITY.md) for details.

---

## Reporting a Vulnerability

**We take security seriously.** If you discover a security vulnerability in SeedStream, please report it responsibly.

### How to Report

**DO NOT** open a public GitHub issue for security vulnerabilities.

Instead, please report security issues privately:

1. **GitHub Security Advisories** (Preferred):
   - Go to https://github.com/mferretti/SeedStream/security/advisories
   - Click "Report a vulnerability"
   - Provide detailed information about the vulnerability

2. **Direct GitHub Contact** (Alternative):
   - Contact the maintainer directly via GitHub: [@mferretti](https://github.com/mferretti)
   - Include in your message:
     - Description of the vulnerability
     - Steps to reproduce
     - Potential impact
     - Suggested fix (if available)

### What to Include

Please provide as much detail as possible:

- **Type of vulnerability** (e.g., code injection, credential exposure, DoS)
- **Location** (file path, line number, affected component)
- **Steps to reproduce** (configuration files, commands)
- **Potential impact** (data exposure, system compromise, availability)
- **Affected versions** (e.g., 0.2.0, all versions)
- **Proof of concept** (if safe to share)

### Response Timeline

We aim to respond to security reports within:

- **Initial Response**: 96 hours / 4 days (acknowledgment)
- **Assessment**: 7 business days (severity evaluation)
- **Fix Timeline**: 
  - **Critical**: Within 14 days
  - **High**: Within 28 days
  - **Medium/Low**: Next scheduled release

**Note**: SeedStream is currently maintained by a single developer as a side project. Response times may be longer during holidays or personal commitments. For urgent security issues, please include "URGENT" in the subject line.

### Disclosure Policy

We follow **coordinated disclosure**:

1. You report the vulnerability privately
2. We confirm and assess severity
3. We develop and test a fix
4. We release a patch and security advisory
5. **90 days after the patch**, or when 95% of users have updated (whichever comes first), we publish full details

We will credit you in the security advisory unless you prefer to remain anonymous.

---

## Security Considerations for SeedStream

### 1. Data Generation

**SeedStream generates synthetic data only** - no real PII is involved. However:

- **Seed security**: Seeds can reproduce data. Store seeds securely if data must not be replicated.
- **Output destination**: Generated data should be treated according to your organization's data governance policies.
- **Configuration files**: Job definitions may contain connection strings or credentials.

### 2. Dependencies

We actively monitor dependencies for known vulnerabilities:

- **OWASP Dependency-Check**: Run locally with `./gradlew dependencyCheckAll`
- **Dependabot**: Enabled on GitHub for automated dependency updates
- **Gradle Version Catalog**: Centralized dependency management in `gradle/libs.versions.toml`
- **Supported libraries**: We use well-maintained dependencies (Jackson, Kafka clients, etc.)

**Check your local setup**:
```bash
./gradlew dependencyCheckAll
# View reports in each module's build/reports/dependency-check-report.html
open core/build/reports/dependency-check-report.html
```

**Current Status**: ✅ 0 known vulnerabilities (see Current Security Posture section above)

### 3. Network Security

**Kafka Destination**:
- Supports SASL/SSL authentication
- Credentials should be stored in environment variables or secure vaults (not in YAML)
- Example: `bootstrap: ${KAFKA_BOOTSTRAP}` instead of hardcoded URLs

**Database Destination** (complete as of v0.4):
- Connection pooling via HikariCP with secure credential handling
- Use connection strings from environment variables: `url: ${DB_URL}`, `password: ${DB_PASSWORD}`

**Remote Seed API**:
- Supports bearer tokens, API keys, and basic auth
- Tokens stored in environment variables: `token: ${API_TOKEN}`

### 4. Code Security

**Static Analysis**:
- **SpotBugs**: Automatically scans for bug patterns and potential security issues
- **JaCoCo**: 70%+ test coverage ensures code paths are validated
- **Spotless**: Enforces consistent code formatting

Run security checks locally:
```bash
./gradlew spotbugsMain
./gradlew test
```

### 5. Secrets Management

**Best Practices**:
- ✅ **DO**: Use environment variables for credentials
  ```yaml
  conf:
    bootstrap: ${KAFKA_BOOTSTRAP}
    sasl_password: ${KAFKA_PASSWORD}
  ```
- ❌ **DON'T**: Hardcode credentials in YAML files
  ```yaml
  conf:
    bootstrap: kafka.example.com:9092
    sasl_password: "my-secret-password"  # DON'T DO THIS
  ```

### 6. Deployment Security

**Docker** (when using containerized deployment):
- Use official base images (e.g., `eclipse-temurin:21-jre`)
- Scan images with tools like Trivy or Snyk
- Don't run as root user

**File Permissions**:
- Configuration files containing credentials should have restrictive permissions: `chmod 600 config/jobs/*.yaml`

---

## Known Security Considerations

### Seed Reproducibility

**By Design**: Seeds enable deterministic data generation. Be aware:

- **Same seed = identical data** across runs
- **Store seeds securely** if reproducibility must be prevented
- **Rotate seeds** for different environments (dev/staging/prod)

### Template Injection Risk

**Not vulnerable**: YAML configuration is parsed, not executed. Variables like `${ENV_VAR}` are substituted before parsing, not evaluated as code.

---

## Security Updates

Security advisories are published at:
- **GitHub Security Advisories**: https://github.com/mferretti/SeedStream/security/advisories
- **CHANGELOG.md**: Security fixes are noted in release notes

Subscribe to releases:
- Watch the repository for "Releases only" notifications
- Check https://github.com/mferretti/SeedStream/releases

---

## Bug Bounty Program

We currently **do not have a bug bounty program**. However, we deeply appreciate security research and will:

- Credit you in security advisories (with your permission)
- Prioritize fixing reported vulnerabilities
- Acknowledge your contribution in release notes

---

## Security Contact

For security-related questions (not vulnerability reports):
- **GitHub Discussions**: https://github.com/mferretti/SeedStream/discussions
- **General Issues**: https://github.com/mferretti/SeedStream/issues

For vulnerability reports, use the private channels described above.

---

**Last Updated**: March 10, 2026
