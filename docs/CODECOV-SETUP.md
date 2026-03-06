# Codecov Integration Setup

This project uses [Codecov](https://codecov.io) for dynamic code coverage reporting integrated with GitHub Actions.

## Setup Steps

### 1. Enable Codecov for Your Repository

1. Go to [https://codecov.io](https://codecov.io)
2. Sign in with your GitHub account
3. Enable Codecov for the `mferretti/SeedStream` repository
4. No API token required - Codecov automatically integrates with public GitHub repositories

### 2. Verify Integration (After First Push)

Once you push commits to the `main` branch:

1. GitHub Actions will run the build workflow
2. JaCoCo generates XML coverage reports
3. Codecov action uploads coverage data
4. Coverage badge in README.md updates automatically with real percentages

### 3. View Coverage Reports

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

**Badge not updating?**
- Ensure workflow runs successfully on GitHub Actions
- Check Codecov dashboard for upload errors
- Verify repository is enabled in Codecov settings

**Coverage lower than expected?**
- Check `codecov.yml` ignore patterns
- Review `core/build/reports/jacoco/test/jacocoTestReport.xml` for detailed line coverage
- Use `./gradlew test --info` to see test execution details

## Badge Customization

The README badge URL:
```markdown
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg)](https://codecov.io/gh/mferretti/SeedStream)
```

Optional: Add token to badge URL for private repositories (not needed for public repos):
```markdown
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg?token=YOUR_TOKEN)](https://codecov.io/gh/mferretti/SeedStream)
```
