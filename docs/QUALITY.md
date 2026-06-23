# Code Quality & Security Setup

## Overview

SeedStream uses multiple tools to ensure code quality, security, and maintainability:

- **Spotless**: Code formatting (Google Java Style with custom brace placement)
- **JaCoCo**: Test coverage reporting (70% minimum target)
- **SpotBugs**: Static analysis for bug patterns
- **OWASP Dependency-Check**: Security vulnerability scanning
- **SonarQube**: Continuous code quality + coverage analysis (opt-in — see [Local SonarQube setup](#local-sonarqube-setup))
- **Dependabot**: Automated dependency updates

## Local Development

### Run all checks
```bash
./gradlew build
```

### Individual checks

**Code formatting:**
```bash
# Check formatting
./gradlew spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply
```

**Test coverage:**
```bash
./gradlew test jacocoTestReport

# View report
open core/build/reports/jacoco/test/html/index.html
```

**Static analysis:**
```bash
./gradlew spotbugsMain spotbugsTest

# View reports
open core/build/reports/spotbugs/main.html
```

**Security scanning:**
```bash
./gradlew dependencyCheckAll

# View reports (generated per-module and aggregated)
open core/build/reports/dependency-check-report.html
open generators/build/reports/dependency-check-report.html
# ... or check any module's build/reports/ directory
```

## CI/CD Integration

GitHub Actions automatically runs all checks on:
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop`

### Artifacts

Build artifacts are uploaded for every run:
- **Test results**: JUnit XML reports
- **Coverage reports**: JaCoCo HTML/XML reports
- **SpotBugs reports**: HTML reports with bug patterns
- **Dependency-Check reports**: Security vulnerability reports

Access artifacts: GitHub Actions run → Artifacts section

## Configuration

### SpotBugs

Configuration: `build.gradle.kts`
- **Effort**: MAX (most thorough analysis)
- **Report Level**: LOW (report all issues — `ignoreFailures = false`, so any reported issue fails the build)
- **Exclusions**: `config/spotbugs-exclude.xml`

Common exclusions:
- Lombok generated code
- Test utilities
- Known false positives

### JaCoCo

Configuration: `build.gradle.kts`
- **Minimum coverage**: 70%
- **Reports**: XML (for CI), HTML (for viewing)
- **Scope**: **unit tests only** — the `test` task uses `excludeTags("integration")`,
  so the Testcontainers `integrationTest` suite and `*IT.java` classes are **not**
  reflected in the JaCoCo/Codecov figure. Integration behaviour is verified by the
  separate `integrationTest` task and the e2e benchmark suite. See
  [CODECOV-SETUP.md](CODECOV-SETUP.md#what-the-percentage-measures-scope) — this is
  why `destinations` (~75%) and `cli` (~78%) report low despite heavy IT coverage.

Coverage reports are generated automatically after tests run.

### OWASP Dependency-Check

Configuration: `build.gradle.kts`
- **Task**: `./gradlew dependencyCheckAll` - runs `dependencyCheckAnalyze` on all subprojects
- **Fail build on CVSS**: 7.0+ (high severity)
- **Suppressions**: `config/dependency-check-suppressions.xml`
- **NVD API Key**: Optional - set `NVD_API_KEY` env var for faster updates (50 req/30s vs 5 req/30s rate limit)
- **Auto-update**: Enabled - downloads/updates NVD database automatically
- **CI Integration**: Runs on every push/PR with GitHub Actions caching for the NVD database

**Important**: We use `dependencyCheckAnalyze` (per-module) instead of `dependencyCheckAggregate` because aggregate doesn't scan Gradle dependencies properly in multi-module builds.

To suppress false positives, add to `config/dependency-check-suppressions.xml`:
```xml
<suppress>
    <notes>Reason for suppression</notes>
    <packageUrl regex="true">^pkg:maven/org\.example/.*$</packageUrl>
    <cve>CVE-2023-12345</cve>
</suppress>
```

**Current Security Status**: tracked in the README's *Security* section (the canonical, time-stamped list of open CVEs and their suppression expiry dates lives there to avoid drift between docs).

## Dependency Management

SeedStream uses [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) for centralized dependency management.

### Version Catalog Location

All dependency versions are defined in: **`gradle/libs.versions.toml`**

Example structure (illustrative — current pinned versions live in `gradle/libs.versions.toml`):
```toml
[versions]
jackson = "2.22.0"
kafka = "4.3.0"
protobuf = "4.35.0"

[libraries]
kafka-clients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafka" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }

[bundles]
jackson = ["jackson-databind", "jackson-datatype-jsr310"]
```

### Using Dependencies in Build Files

Access dependencies via type-safe `libs.*` references:

```kotlin
dependencies {
    // Single library
    implementation(libs.kafka.clients)
    
    // Bundle (multiple related libraries)
    implementation(libs.bundles.jackson)
    
    // Versions are centrally managed in gradle/libs.versions.toml
}
```

### Updating Dependencies

1. **Edit**: Change version in `gradle/libs.versions.toml`
2. **Apply**: Version automatically updates in all modules that use it
3. **Verify**: Run `./gradlew build` and `./gradlew dependencyCheckAll`

**Benefits:**
- ✅ Single source of truth for all versions
- ✅ IDE autocomplete and type safety
- ✅ No version conflicts across modules
- ✅ Easier to audit and update dependencies

## Dependabot

Configuration: `.github/dependabot.yml`

Automatically creates PRs for:
- **Gradle dependencies**: Weekly on Mondays at 9 AM
- **GitHub Actions**: Weekly on Mondays at 9 AM

PRs are automatically assigned to @mferretti for review.

### Managing Dependabot PRs

1. Review the changelog and compatibility
2. Check that CI passes
3. Merge if tests pass and changes look safe
4. Dependabot can rebase PRs automatically if conflicts occur

## GitHub Settings Required

### Enable Dependabot

Settings → Security → Dependabot:
1. ✅ Enable "Dependabot alerts"
2. ✅ Enable "Dependabot security updates"
3. ✅ Enable "Dependabot version updates" (reads .github/dependabot.yml)

### Optional: Branch Protection

Settings → Branches → Add rule for `main`:
- ✅ Require status checks before merging
- ✅ Require "Build and Test" check to pass
- ✅ Require branches to be up to date

## Troubleshooting

### SpotBugs fails build
If SpotBugs finds critical issues, either:
1. Fix the issues (recommended)
2. Add exclusions to `config/spotbugs-exclude.xml`
3. Temporarily set `ignoreFailures = true` (not recommended)

### Dependency-Check is slow
The first run downloads the NVD database (~200MB). Subsequent runs are faster.

**Local development**: Set `NVD_API_KEY` environment variable to speed up updates:
```bash
export NVD_API_KEY=your-api-key-here
```
Request free API key: https://nvd.nist.gov/developers/request-an-api-key

**CI/CD**: The NVD database is automatically cached between workflow runs. First run on new cache will be slower (~2-3 minutes), subsequent runs are fast (~30 seconds).

### False positive vulnerabilities
Add suppressions to `config/dependency-check-suppressions.xml` with justification.

### Coverage too low
Current target: 70%. To adjust:
```kotlin
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal() // Change this
            }
        }
    }
}
```

## Local SonarQube setup

The SonarQube Gradle plugin and `.githooks/pre-push` quality gate are **opt-in**.
Out of the box, `./gradlew sonar` is not registered and the hook is not active —
zero noise for contributors who don't run a Sonar instance.

### 1. Run a SonarQube server

Anything that speaks the SonarQube API works: a local Docker container, a team
instance, or SonarCloud.

```bash
# Quick local instance
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

### 2. Configure credentials per-developer

Put these in `~/.gradle/gradle.properties` (preferred — never checked in):

```properties
sonar.host.url=http://localhost:9000
sonar.token=<generate at /account/security on your Sonar instance>
```

Or export as env vars: `SONAR_HOST_URL`, `SONAR_TOKEN`.

The project key/name default to `seedstream` / `Seedstream` (hardcoded fallbacks in
`build.gradle.kts`); override them via `sonar.projectKey` / `sonar.projectName` in
`gradle.properties` or `-P` flags if needed.

### 3. Run analysis

```bash
./gradlew test jacocoTestReport sonar
```

The Sonar plugin is only applied when `sonar.host.url` is set, so the task is
absent otherwise.

### 4. (Optional) Enable the pre-push quality gate

```bash
git config core.hooksPath .githooks
```

`.githooks/pre-push` runs the scanner and blocks the push if the quality gate
returns ERROR. It silently skips if `SONAR_HOST_URL`/`SONAR_TOKEN` are unset or
if `sonar-scanner` is not installed. Install the scanner via
[the official package](https://docs.sonarqube.org/latest/analyzing-source-code/scanners/sonarscanner/).
