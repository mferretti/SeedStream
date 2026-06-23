# Codecov Integration Setup

This project uses [Codecov](https://codecov.io) for dynamic code coverage reporting integrated with GitHub Actions.

## Setup Steps

### 1. Enable Codecov for Your Repository

1. Go to [https://codecov.io](https://codecov.io)
2. Sign in with your GitHub account
3. Enable Codecov for the `mferretti/SeedStream` repository
4. **Get your upload token**:
   - Click on your repository in Codecov dashboard
   - Go to Settings → General
   - Copy the "Repository Upload Token"

### 2. Add Token to GitHub Secrets

**Required since 2024**: Codecov now requires upload tokens even for public repositories.

1. Go to your GitHub repository: `https://github.com/mferretti/SeedStream`
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Name: `CODECOV_TOKEN`
5. Value: Paste the upload token from Codecov
6. Click **Add secret**

### 3. Verify Integration (After First Push)

Once you push commits to the `main` branch:

1. GitHub Actions will run the build workflow
2. JaCoCo generates XML coverage reports
3. Codecov action uploads coverage data
4. Coverage badge in README.md updates automatically with real percentages

### 4. View Coverage Reports

- **Badge in README**: Shows overall coverage percentage (updates automatically)
- **Codecov Dashboard**: Visit `https://codecov.io/gh/mferretti/SeedStream` for detailed reports
  - Line-by-line coverage visualization
  - Coverage trends over time
  - Pull request coverage impact analysis
  - Module-specific coverage breakdown

## Configuration

Coverage settings are defined in `codecov.yml`:

- **Target Coverage**: 70% (matches Gradle's minimum requirement)
- **Threshold**: Project can drop up to 2% before failing status checks
- **Patch Coverage**: New code must have 70% coverage (5% threshold)
- **Ignored Paths**: Test files, build outputs, benchmarks, demo files

## What the percentage measures (scope)

> **The Codecov number is _unit-test_ coverage only.** CI runs
> `./gradlew test jacocoTestReport`, and the `test` task is configured with
> `excludeTags("integration")` — so the Testcontainers integration suite
> (`integrationTest` task and `*IT.java` classes) **does not contribute** to the
> reported figure. The coverage is uploaded under the `unittests` flag.

This is why the headline sits around **78%** rather than higher, and why the
integration-heavy modules look low even though they are well exercised:

| Module | Unit coverage | Note |
|--|--|--|
| inspector / generators / schema / core | 90–95% | logic-heavy, unit-testable |
| formats | ~82% | |
| cli | ~78% | Picocli command glue, validated mostly via integration/e2e |
| destinations | ~75% | 10 Testcontainers `*IT` files (Kafka/DB/File adapters) **not counted** |

`destinations` and `cli` are the two modules pulling the aggregate down, purely
because the code paths their integration tests cover are excluded from the
report. Integration behaviour is verified separately by the `integrationTest`
task (and the e2e benchmark suite), not by this number. Treat the Codecov
percentage as a **unit-coverage** signal, not total test coverage.

> If we ever want the badge to reflect total (unit + integration) coverage, the
> change is to run `integrationTest` in CI and either upload its JaCoCo report
> under a second `integration` flag or merge the exec data into one report. That
> trade-off (accurate number vs. slower CI) is intentionally **not** taken today.

## Coverage in Pull Requests

Codecov automatically comments on pull requests with:
- Coverage change (increase/decrease)
- New code coverage percentage
- Files with coverage changes
- Line-by-line diff with coverage annotations

## Local Coverage Reports

Generate and view coverage locally:

```bash
# Generate coverage report
./gradlew test jacocoTestReport

# View HTML report (all modules)
open core/build/reports/jacoco/test/html/index.html
open generators/build/reports/jacoco/test/html/index.html
# ... etc for each module

# Verify coverage meets 70% threshold
./gradlew jacocoTestCoverageVerification
```

## Troubleshooting

**Upload failing with "missing token" error?**
- Ensure you've added `CODECOV_TOKEN` to GitHub repository secrets
- Token name must be exactly `CODECOV_TOKEN` (case-sensitive)
- Verify the token is from the correct repository in Codecov dashboard

**Badge not updating?**
- Ensure workflow runs successfully on GitHub Actions
- Check Codecov dashboard for upload errors
- Verify repository is enabled in Codecov settings
- First upload may take 2-3 minutes to appear

**Coverage lower than expected?**
- Check `codecov.yml` ignore patterns
- Review `core/build/reports/jacoco/test/jacocoTestReport.xml` for detailed line coverage
- Use `./gradlew test --info` to see test execution details

## Badge Customization

The README badge URL:
```markdown
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg)](https://codecov.io/gh/mferretti/SeedStream)
```

The upload token (for GitHub Actions) is different from the badge token. Badge display works without additional tokens for public repositories.
