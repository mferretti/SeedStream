# Code Quality & Security Setup

## Overview

SeedStream uses multiple tools to ensure code quality, security, and maintainability:

- **Spotless**: Code formatting (Google Java Style with custom brace placement)
- **JaCoCo**: Test coverage reporting (70% minimum target)
- **SpotBugs**: Static analysis for bug patterns
- **OWASP Dependency-Check**: Security vulnerability scanning
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
./gradlew dependencyCheckAnalyze

# View report
open build/reports/dependency-check-report.html
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
- **Report Level**: LOW (report all issues, but don't fail build)
- **Exclusions**: `config/spotbugs-exclude.xml`

Common exclusions:
- Lombok generated code
- Test utilities
- Known false positives

### JaCoCo

Configuration: `build.gradle.kts`
- **Minimum coverage**: 70%
- **Reports**: XML (for CI), HTML (for viewing)

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
