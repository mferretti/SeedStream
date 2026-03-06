# Contributing to SeedStream

First off, thank you for considering contributing to SeedStream! It's people like you that make SeedStream such a great tool.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Code Quality Standards](#code-quality-standards)
- [Running Tests](#running-tests)
- [Pull Request Process](#pull-request-process)
- [Style Guide](#style-guide)
- [Where to Get Help](#where-to-get-help)

---

## Code of Conduct

This project and everyone participating in it is governed by the [SeedStream Code of Conduct](../CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

**TL;DR**: Be respectful, constructive, and welcoming. We're building great software together.

---

## Getting Started

### Prerequisites

- **Java 21** or higher (Amazon Corretto, OpenJDK, or GraalVM)
- **Gradle 8.5+** (wrapper included, no system installation required)
- **Git** for version control

Recommended: Use [SDKMAN!](https://sdkman.io/) to manage Java and Gradle versions:
```bash
sdk install java 21.0.9-amzn
sdk install gradle 8.5
```

### Clone and Build

```bash
# Clone repository
git clone https://github.com/mferretti/SeedStream.git
cd SeedStream

# Build project
./gradlew build

# Run tests
./gradlew test
```

### Understanding the Codebase

Before making changes, familiarize yourself with:
- **[DESIGN.md](docs/DESIGN.md)** - Architecture and design decisions
- **[README.md](README.md)** - Features and quick start
- **Module structure**: `core → schema → generators → formats → destinations → cli`

The project follows a **dependency-first architecture**: each module only depends on modules to its right (no circular dependencies).

---

## Development Workflow

### 1. Create a Branch

```bash
# Always branch from main
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name

# Or bugfix branch
git checkout -b fix/issue-123-description
```

**Branch naming conventions:**
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation changes
- `refactor/` - Code refactoring
- `test/` - Test additions or fixes

### 2. Make Your Changes

- Write clean, readable code (see [Style Guide](#style-guide))
- Add tests for new functionality
- Update documentation as needed
- Keep commits focused and atomic

### 3. Run Quality Checks

**Before committing**, ensure all checks pass:

```bash
# Format code (REQUIRED)
./gradlew spotlessApply

# Run all tests
./gradlew test

# Run static analysis
./gradlew spotbugsMain

# Full build (includes all checks)
./gradlew build
```

### 4. Commit Your Changes

```bash
git add -A
git commit -m "feat: Add support for new data type"
```

**Commit message format:**
```
<type>: <subject>

<optional body>

<optional footer>
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation only
- `style:` - Code style (formatting, no logic change)
- `refactor:` - Code restructuring (no feature change)
- `test:` - Adding or updating tests
- `chore:` - Build process, dependencies, tooling

**Examples:**
```bash
git commit -m "feat: Add Protobuf serializer support"
git commit -m "fix: Correct seed derivation for worker thread 0"
git commit -m "docs: Update README with Kafka configuration examples"
```

### 5. Push and Create PR

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

---

## Code Quality Standards

SeedStream maintains high code quality standards:

### Minimum Requirements ✅

- ✅ **Code formatting**: Spotless check must pass
- ✅ **Test coverage**: Maintain or improve coverage (target: 70%+)
- ✅ **All tests pass**: Unit and integration tests
- ✅ **No new SpotBugs warnings**: Static analysis clean
- ✅ **Documentation updated**: If adding features

### Code Formatting

We use **Spotless** with **Google Java Style Guide** (with one exception: opening braces `{` on same line).

```bash
# Check if code is formatted
./gradlew spotlessCheck

# Auto-format code (ALWAYS run before committing)
./gradlew spotlessApply
```

**Key rules:**
- Max line length: 120 characters
- Use spaces, not tabs (indent: 2 spaces)
- Braces on same line: `if (condition) {` not `if (condition)\n{`
- **No wildcard imports**: Use explicit imports (e.g., `import java.util.List;` not `import java.util.*;`)
- Exception: Static test imports allowed (`import static org.assertj.core.api.Assertions.*;`)

### Test Coverage

Minimum coverage: **70%** (enforced by JaCoCo)

```bash
# Generate coverage report
./gradlew test jacocoTestReport

# View HTML report
open core/build/reports/jacoco/test/html/index.html

# Verify coverage meets minimum
./gradlew jacocoTestCoverageVerification
```

**Testing guidelines:**
- Write tests for all new features
- Test both success and failure scenarios
- Use descriptive test names: `shouldGenerateCorrectDataWhenSeedIsProvided`
- Use AssertJ for fluent assertions
- Mock external dependencies (use real objects for pure logic)

---

## Running Tests

### Unit Tests

```bash
# Run all unit tests (excludes integration tests)
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :generators:test

# Run with verbose output
./gradlew test --info
```

### Integration Tests

Integration tests use **Testcontainers** (requires Docker):

```bash
# Run integration tests (takes longer, requires Docker)
./gradlew integrationTest

# Run specific integration test
./gradlew :destinations:integrationTest
```

**Note**: Integration tests are excluded from regular `./gradlew test` to keep the feedback loop fast.

### Performance Benchmarks

Benchmarks are **not run automatically** (they take 10-15 minutes):

```bash
# Run all benchmarks
./benchmarks/run_benchmarks.sh

# Or manually
./gradlew :benchmarks:jmh
python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md
```

---

## Pull Request Process

### Before Submitting

**Checklist:**
- [ ] Code is formatted (`./gradlew spotlessApply`)
- [ ] All tests pass (`./gradlew test`)
- [ ] Coverage is maintained or improved
- [ ] Documentation is updated (README, JavaDoc, etc.)
- [ ] Commit messages follow convention
- [ ] Branch is up to date with `main`

### PR Description

Use this template:

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature that breaks existing functionality)
- [ ] Documentation update

## Testing
How has this been tested?
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code formatted with Spotless
- [ ] All tests pass
- [ ] Coverage maintained (70%+)
- [ ] Documentation updated
- [ ] CHANGELOG.md updated (if applicable)
```

### Review Process

1. **Automated checks**: GitHub Actions runs all tests and checks
2. **Code review**: Maintainer reviews code and provides feedback
3. **Iteration**: Address feedback and push updates
4. **Approval**: Once approved, your PR will be merged

**Typical review time**: 2-3 days

### After Merge

Your changes will be included in the next release. Thank you for your contribution! 🎉

---

## Style Guide

### Java Code Style

**Follow Google Java Style Guide** with these specifics:

#### Naming Conventions
- **Classes**: `PascalCase` (e.g., `DataGenerator`, `KafkaDestination`)
- **Methods**: `camelCase` (e.g., `generateData()`, `writeToFile()`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_BATCH_SIZE`)
- **Packages**: `lowercase` (e.g., `com.datagenerator.core`)

#### Import Organization
```java
// GOOD ✅
import java.util.List;
import java.util.Map;
import com.datagenerator.core.type.DataType;

// BAD ❌
import java.util.*;
import com.datagenerator.core.type.*;
```

**Exception**: Static test imports allowed
```java
import static org.assertj.core.api.Assertions.*;  // OK for tests
```

#### Lombok Usage
Use Lombok to reduce boilerplate:
- `@Value` for immutable classes (config objects)
- `@Builder` for classes with 4+ parameters
- `@Slf4j` for logging
- **Import Lombok classes at top**, use simple names in code

```java
// GOOD ✅
import lombok.Value;
import lombok.Builder;

@Value
@Builder
public class Config {
    String name;
    int count;
}

// BAD ❌
@lombok.Value  // Don't use fully-qualified
public class Config { }
```

#### Optionals
Return `Optional<T>` for methods that may not have a value:
```java
// GOOD ✅
public Optional<User> findUserById(String id) {
    // ...
}

// BAD ❌
public User findUserById(String id) {
    return null;  // Never return null
}
```

#### Collections
Never return null collections:
```java
// GOOD ✅
public List<String> getNames() {
    return List.of();  // Empty list
}

// BAD ❌
public List<String> getNames() {
    return null;
}
```

#### Java 21 Features
Use modern Java features:
- **Records** for simple data carriers
- **Pattern matching** for instanceof checks
- **Switch expressions** instead of switch statements
- **Text blocks** for multi-line strings
- **Virtual threads** for I/O-bound operations

### YAML Configuration Style

```yaml
# Use 2-space indentation
name: address
geolocation: usa

# Use quotes for strings with special characters
alias: "nome"

# Comments for complex configurations
conf:
  bootstrap: localhost:9092  # Kafka broker
  topic: addresses           # Target topic
```

### Documentation Style

#### JavaDoc
Required for:
- All public classes
- All public methods
- All public fields/constants
- Complex algorithms

```java
/**
 * Generates random data based on a seed value.
 * 
 * <p>This generator ensures reproducible output: the same seed always produces
 * identical data across multiple runs, even with multi-threaded generation.
 *
 * @param seed the seed value for deterministic generation
 * @param count the number of records to generate
 * @return a list of generated records
 * @throws GeneratorException if generation fails
 */
public List<Map<String, Object>> generate(long seed, int count) {
    // ...
}
```

#### Inline Comments
Use sparingly - code should be self-documenting. Comment the "why", not the "what":

```java
// GOOD ✅
// Use logical worker IDs instead of JVM thread IDs for reproducibility
int workerId = workerIdCounter.getAndIncrement();

// BAD ❌
// Increment the counter
int workerId = workerIdCounter.getAndIncrement();
```

---

## Where to Get Help

- **Questions?** Open a [GitHub Discussion](https://github.com/mferretti/SeedStream/discussions)
- **Bug reports?** Open a [GitHub Issue](https://github.com/mferretti/SeedStream/issues)
- **Feature requests?** Open a [GitHub Issue](https://github.com/mferretti/SeedStream/issues) with `enhancement` label
- **Architecture questions?** Read [DESIGN.md](docs/DESIGN.md) first, then ask in Discussions

---

## Types of Contributions

We welcome many types of contributions:

### 🐛 Bug Reports
Found a bug? Please include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Version/commit hash
- Java version and OS

### ✨ Feature Requests
Have an idea? Great! Please include:
- Use case (what problem does it solve?)
- Proposed solution
- Alternatives considered
- Willingness to implement it yourself

### 📝 Documentation
Documentation is always appreciated:
- Fix typos or unclear explanations
- Add examples or tutorials
- Improve JavaDoc
- Translate documentation

### 🧪 New Generators
Want to add a new data generator?
1. Implement `DataTypeGenerator` interface
2. Add tests (80%+ coverage)
3. Update type system documentation
4. Add examples in `config/structures/`

### 🔌 New Destinations
Want to add a new destination (S3, Azure, Elasticsearch)?
1. Implement `DestinationAdapter` interface
2. Add configuration model (extends `DestinationConfig`)
3. Add comprehensive tests (unit + integration with Testcontainers)
4. Update documentation

### 🎨 New Formats
Want to add a new serialization format (Protobuf, Avro)?
1. Implement `FormatSerializer` interface
2. Add tests for all data types
3. Update CLI to support new format
4. Document format-specific configuration

---

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0, the same license as SeedStream.

---

**Thank you for making SeedStream better! 🚀**

For more details on the project architecture and design decisions, see [DESIGN.md](docs/DESIGN.md).
