# Copilot Instructions - Data Generator

## Project Overview
High-performance test data generator for enterprise applications. Generates realistic, reproducible test data to multiple destinations (Kafka, databases, files) using YAML-based configuration.

**Key Design Goals:**
- **Performance**: Multi-threaded generation with batching - millions of primitive records/second (in-memory), thousands of realistic records/second
- **Reproducibility**: Seed-based pseudo-random generation for deterministic output
- **Extensibility**: Plugin architecture for destinations, formats, and data generators
- **Enterprise-ready**: Locale-aware data, statistical distributions, connection pooling

## Architecture & Multi-Module Structure

```
datagenerator/
├── core/           # Generation engine, type system, seeding, parallel execution
├── schema/         # YAML parsers for data structures and job definitions
├── generators/     # Data generators (primitives + Datafaker for realistic data)
├── formats/        # Output serializers (JSON, CSV, Protobuf)
├── destinations/   # Destination adapters (Kafka, DB with HikariCP, File with NIO)
└── cli/           # Picocli-based CLI (future: REST/gRPC API module)
```

**Module Dependencies:** `cli` → `destinations` → `formats` → `generators` → `schema` → `core`

**Configuration Architecture:**
- **Data Structure Definitions** (`config/structures/*.yaml`): Define schema, field types, ranges, aliases, geolocation
- **Job Definitions** (`config/jobs/*.yaml`): Reference structures, seed config (root level), destination-specific config
- **CLI Parameters**: Format, count, seed override at runtime (defaults: `--format json --count 100 --seed <from-config-or-0>`)

## Key Commands

```bash
# Build entire project
./gradlew build

# Run tests (all modules)
./gradlew test

# Run specific module tests
./gradlew :core:test :generators:test

# Check code formatting
./gradlew spotlessCheck

# Auto-format code
./gradlew spotlessApply

# Execute a job (uses defaults: json, 100 records)
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml"

# Execute with custom format and count
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format csv --count 50000"

# Build distribution
./gradlew :cli:installDist

# Clean build
./gradlew clean build
```

## Development Workflow

**Tech Stack:**
- Java 21 (toolchain enforced, use virtual threads for I/O-bound operations)
- Gradle 8.5+ (multi-module build with Kotlin DSL)
- **Gradle Version Catalog** (`gradle/libs.versions.toml` - centralized dependency management)
- Lombok (reduce boilerplate - enabled in all modules)
- Enforced by Spotless: Run `./gradlew spotlessApply` before committing
- Datafaker (realistic data generation with locale support)
- Jackson YAML (configuration parsing)
- Picocli (CLI framework)

**Dependency Management:**
- All dependency versions defined in `gradle/libs.versions.toml` (Gradle Version Catalog)
- Use type-safe accessors in build files: `implementation(libs.kafka.clients)`
- **Adding dependencies**: 
  1. Add/update version in `[versions]` section of `gradle/libs.versions.toml`
  2. Define library in `[libraries]` section
  3. Reference in module's `build.gradle.kts` with `libs.*` syntax
- **Benefits**: Single source of truth, no version conflicts, IDE autocomplete
- **Current versions**: All dependencies at latest stable (Jackson 2.21.1, Kafka 4.2.0, Protobuf 4.34.0, etc.)
- **Security**: Run `./gradlew dependencyCheckAll` to scan all modules for vulnerabilities

**Code Style:**
- Follow Google Java Style Guide with one exception: opening braces `{` on same line
- Max line length: 120 characters
- Use Java 21 features: records, pattern matching, switch expressions, virtual threads
- Prefer functional style (streams) over imperative loops for collections
- Use Lombok @Builder for classes with 4+ parameters
- Return `Optional<T>` for methods that may not have a value
- Immutability: Configuration objects use Lombok @Value (immutable)
- Never return null collections (return empty)
- **Import Style (IDE conventions)**: Import classes/annotations at the top, use simple names in code. NEVER use fully-qualified names inline (e.g., `@lombok.Value`). Exception: When class names collide, use fully-qualified names for ALL instances to avoid confusion. Examples:
  - ✅ `import lombok.Value; ... @Value class Foo {}`
  - ✅ `import lombok.EqualsAndHashCode; ... @EqualsAndHashCode(callSuper = false)`
  - ❌ `@lombok.Value class Foo {}` (no import)
  - ✅ Collision case: `java.util.Date` vs `java.sql.Date` → use fully-qualified for both

**Import Rules:**
- **No wildcard imports**: NEVER use `import package.*;` (except for static test assertions like `import static org.assertj.core.api.Assertions.*;`)
- **Explicit imports**: Import each class individually (e.g., `import java.util.List;` not `import java.util.*;`)
- **Rationale**: Improves code readability, makes dependencies explicit, better IDE performance, avoids naming conflicts
- **Examples**:
  - ✅ `import java.util.List; import java.util.Map; import java.util.ArrayList;`
  - ❌ `import java.util.*;`
  - ✅ `import com.datagenerator.core.type.DataType; import com.datagenerator.core.type.ObjectType;`
  - ❌ `import com.datagenerator.core.type.*;`
  - ✅ Exception: `import static org.assertj.core.api.Assertions.*;` (test assertion methods)
- **Remove unused imports**: Always clean up imports that are not referenced in the code

**Error Handling:**
- Use Hibernate Validator for configuration validation (@NotNull, @Valid, @Min, @Max)
- Fail fast: Validate inputs at boundaries (parsers, CLI)
- Custom typed exceptions: `ConfigurationException`, `GeneratorException`, `DestinationException`
- Log levels: ERROR (user-facing), WARN (degraded performance), DEBUG (internals)
- Always log or rethrow exceptions with context
- Use try-with-resources for all I/O operations

**Performance Patterns:**
1. **Thread-local Random** with deterministic seeding (job seed → worker thread seeds)
2. **Batching**: Generate and flush in configurable batches to amortize I/O
3. **Streaming**: Generate → serialize → send pipeline (avoid loading all in memory)
4. **Connection pooling**: HikariCP for databases, reuse Kafka producers
5. **Thread safety**: All generators must be thread-safe (use thread-local state)
6. **Hot paths** (generators, serializers): Optimize aggressively
7. **Cold paths** (parsers, CLI): Prioritize readability over speed

**Testing Strategy:**
- Approach: Implement feature → Write tests (not strict TDD)
- Coverage targets: 70% unit tests, 90% integration tests
- Unit tests: JUnit 5 + Mockito + AssertJ
- Integration tests: Testcontainers for Kafka/databases, real file I/O
- Mock: External dependencies (Kafka, DB, HTTP). Use real objects for pure logic
- Test naming: `shouldGenerateCorrectDataWhenSeedIsProvided`
- JMH benchmarks for performance claims

**Iteration Style:**spotlessApply build test`
- Mark incomplete work with TODO comments
- Explain only complex/non-obvious decisions
- CI/CD: GitHub Actions runs on all PRs (formatting, build, tests)st`
- Mark incomplete work with TODO comments
- Explain only complex/non-obvious decisions

**Resource Management:**
- Always use try-with-resources for I/O
- Close connections, streams, producers properly
- Avoid memory leaks in long-running generation jobs

## Coding Conventions

**Design Patterns:**
- **Strategy**: Pluggable destinations (`DestinationAdapter`) and formats (`FormatSerializer`)
- **Factory**: Data type generators (`DataTypeGenerator` implementations)
- **Builder**: Complex configuration objects (job config, destination config)
- **Plugin architecture**: ServiceLoader for discovering custom generators/destinations

**Naming Conventions:**
- Data structure files: `{entity}.yaml` (e.g., `address.yaml`, `user.yaml`)
- Job definition files: `{destination}_{entity}.yaml` (e.g., `kafka_address.yaml`, `db_users.yaml`)
- Generators: `{Type}Generator` (e.g., `StringGenerator`, `IntegerGenerator`)
- Destinations: `{Type}Destination` (e.g., `KafkaDestination`, `FileDestination`)
- Exceptions: `{Domain}Exception` (e.g., `ConfigurationException`, `GeneratorException`)
- Test classes: `{ClassName}Test` for unit, `{ClassName}IT` for integration

**Documentation:**
- JavaDoc required for: Public APIs, complex algorithms
- Inline comments: Explain "why", not "what" (code should be self-documenting)
- Update README when adding user-facing features
- TODO comments: Use `TODO: description` or `FIXME: description`

**Type System Syntax (in YAML):**
- **Primitives with ranges**: `char[3..15]`, `int[1..999]`, `decimal[0.0..100.0]`, `boolean`
- **Dates**: `date[2020-01-01..2025-12-31]`, `timestamp[now-30d..now]`
- **Enums**: `enum[VALUE1,VALUE2,VALUE3]`
- **Nested structures**: `object[structure_name]` - Auto-loads from `structures_path/structure_name.yaml`
- **Arrays (variable length)**: `array[inner_type, min..max]`
  - Examples: `array[int[1..100], 5..10]` (5-10 integers), `array[object[line_item], 1..50]` (1-50 nested objects)
- **Foreign keys**: `ref[other_structure.field]` (references to other generated records)
- **Circular reference detection**: Fail fast if `object[A]` → `object[B]` → `object[A]`

**Configuration Patterns:**
- All configs support `alias` for field name mapping (e.g., `"nome"` for `name`)
- `geolocation` drives Datafaker locale selection (e.g., `italy` → Italian names/addresses)
- **structures_path**: Optional path in job config for loading nested structures (default: `config/structures/`)
- **Seed resolution order**: CLI `--seed` > YAML config > default (0 with warning)
- **Seed types**: `embedded` (value in YAML), `file` (read from path), `env` (environment variable), `remote` (API call with auth)
- Destination configs are type-specific within `conf` block (Kafka vs File vs Database have different parameters)

## Integration Points

**Kafka:** `org.apache.kafka:kafka-clients` with SASL/SSL auth, configurable batching, producer pooling

**Databases:** JDBC + HikariCP connection pooling. Support PostgreSQL, MySQL (drivers as `compileOnly`, users provide runtime deps)

**Files:** Java NIO for fast I/O. Support JSON (newline-delimited), CSV, future: Parquet/Avro

**Data Generation:** Datafaker for realistic locale-specific data. Custom generators for performance-critical primitives.

## Pre-Authorized Operations

**Always proceed without asking (fast workflow):**
- Reading any file in the workspace
- Creating/editing files within module src directories (main/test)
- Running `./gradlew build`, `./gradlew test`, or module-specific tests
- Adding dependencies via Version Catalog (edit `gradle/libs.versions.toml` and reference in module build files)
- Creating test files alongside implementation
- Running git commands (status, diff, log, blame)
- Code searches (grep, semantic, file search)
- Creating example configuration files in config/
- Running the CLI with test jobs

**Require explicit confirmation:**
- Running `./gradlew clean` or deleting build artifacts
- Modifying root build.gradle.kts or settings.gradle.kts (except Version Catalog updates)
- Adding new modules to the project structure
- Installing system packages (apt, brew, npm global)
- Modifying .gitignore, .github/, or workspace meta files
- Git operations: commit, push, branch creation
- Running docker commands or Testcontainers setup
- Modifying existing test database schemas

## Working with Claude

**Context Management:**
- Claude handles large context (200K+ tokens) - provide full file contents when discussing changes
- Reference multiple related files in one discussion rather than sequential queries
- Use semantic_search broadly to gather context across modules
- Batch file reads: Request all relevant files at once for comprehensive understanding

**Design Discussions:**
- Discuss architectural trade-offs before implementing (Claude excels at reasoning through options)
- Ask "why" questions for non-obvious patterns or decisions
- Request alternative approaches when facing complex problems
- Present constraints upfront for better solution design

**Efficient Operations:**
- Prefer multi-file edits in parallel when changes are independent
- Use multi_replace_string_in_file for related changes across multiple files
- Gather all context first, then implement (reduces back-and-forth)
- Provide specific examples when explaining desired patterns

**Iteration Workflow:**
- Start discussions broad (architecture, approach) → narrow to implementation
- Review generated code in context - Claude can explain reasoning if asked
- Refine incrementally: small working changes compound quickly
- Use TODO list to track progress across sessions

## Example Usage

**Data Structure** (`config/structures/address.yaml`):
```yaml
name: address
geolocation: italy
data:
  name:
    datatype: char[3..15]
    alias: "nome"
  city:
    datatype: char[3..40]
    alias: "citta"
```

**Job Definition** (`config/jobs/kafka_address.yaml`):
```yaml
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092
  topic: addresses
  batch_size: 1000
```

**Seed Types**:
- `embedded`: Direct value in YAML
- `file`: Read from file path
- `env`: Read from environment variable name
- `remote`: Fetch from API (supports bearer, basic, api_key auth)

**CLI Execution**:
```bash
# Defaults: json format, 100 records, seed from config
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml"

# Override all parameters
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format csv --count 50000 --seed 99999
# Custom: csv format, 50000 records
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format csv --count 50000"
```

---
**Future Enhancements:** REST/gRPC API module, Protobuf support, advanced distribution controls, plugin marketplace
