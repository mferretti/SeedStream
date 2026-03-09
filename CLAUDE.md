# CLAUDE.md — Data Generator

## Project Overview
High-performance test data generator for enterprise applications. Generates realistic, reproducible test data to multiple destinations (Kafka, databases, files) using YAML-based configuration.

**Key Design Goals:**
- **Performance**: Multi-threaded generation with batching
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
└── cli/            # Picocli-based CLI
```

**Module Dependencies:** `cli` → `destinations` → `formats` → `generators` → `schema` → `core`

**Configuration Architecture:**
- **Data Structure Definitions** (`config/structures/*.yaml`): Define schema, field types, ranges, aliases, geolocation
- **Job Definitions** (`config/jobs/*.yaml`): Reference structures, seed config, destination-specific config
- **CLI Parameters**: Format, count, seed override at runtime (defaults: `--format json --count 100 --seed <from-config-or-0>`)

## Key Commands

```bash
./gradlew build                          # Build entire project
./gradlew test                           # Run all tests
./gradlew :core:test :generators:test    # Run specific module tests
./gradlew spotlessCheck                  # Check code formatting
./gradlew spotlessApply                  # Auto-format code
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml"
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format csv --count 50000"
./gradlew :cli:installDist               # Build distribution
./gradlew clean build                    # Clean build
```

## Tech Stack
- Java 21 (toolchain enforced, use virtual threads for I/O-bound operations)
- Gradle 8.5+ with Kotlin DSL, Version Catalog at `gradle/libs.versions.toml`
- Lombok (boilerplate reduction, enabled in all modules)
- Spotless (code formatting — run `./gradlew spotlessApply` before committing)
- Datafaker (realistic locale-specific data generation)
- Jackson YAML (configuration parsing)
- Picocli (CLI framework)

## Dependency Management
- All versions in `gradle/libs.versions.toml` (single source of truth)
- Use type-safe accessors: `implementation(libs.kafka.clients)`
- To add a dependency: add version to `[versions]`, define in `[libraries]`, reference in module `build.gradle.kts`

## Code Style
- Google Java Style Guide — opening braces `{` on same line
- Max line length: 120 characters
- Use Java 21 features: records, pattern matching, switch expressions, virtual threads
- Prefer functional style (streams) over imperative loops
- Use Lombok `@Builder` for classes with 4+ parameters
- Return `Optional<T>` for methods that may not have a value
- Immutability: configuration objects use Lombok `@Value`
- Never return null collections (return empty)

**Import Rules:**
- No wildcard imports (exception: `import static org.assertj.core.api.Assertions.*;` in tests)
- Explicit per-class imports only
- Never use fully-qualified names inline (e.g., `@lombok.Value`) — always import
- Exception: when class names collide, use fully-qualified for all instances
- Remove unused imports

## Error Handling
- Hibernate Validator for configuration validation (`@NotNull`, `@Valid`, `@Min`, `@Max`)
- Fail fast: validate at boundaries (parsers, CLI)
- Custom exceptions: `ConfigurationException`, `GeneratorException`, `DestinationException`
- Always log or rethrow with context; use try-with-resources for all I/O

## Performance Patterns
1. Thread-local Random with deterministic seeding (job seed → worker thread seeds)
2. Batching: generate and flush in configurable batches to amortize I/O
3. Streaming: generate → serialize → send pipeline (avoid loading all in memory)
4. Connection pooling: HikariCP for databases, reuse Kafka producers
5. All generators must be thread-safe (use thread-local state)
6. Hot paths (generators, serializers): optimize aggressively
7. Cold paths (parsers, CLI): prioritize readability

## Testing Strategy
- Unit tests: JUnit 5 + Mockito + AssertJ
- Integration tests: Testcontainers for Kafka/databases, real file I/O
- Mock external dependencies; use real objects for pure logic
- Test naming: `shouldGenerateCorrectDataWhenSeedIsProvided`
- Test classes: `{ClassName}Test` for unit, `{ClassName}IT` for integration
- Coverage targets: 70% unit, 90% integration

## Naming Conventions
- Structure files: `{entity}.yaml` (e.g., `address.yaml`)
- Job files: `{destination}_{entity}.yaml` (e.g., `kafka_address.yaml`)
- Generators: `{Type}Generator`, Destinations: `{Type}Destination`
- Exceptions: `{Domain}Exception`

## Type System Syntax (YAML)
- Primitives: `char[3..15]`, `int[1..999]`, `decimal[0.0..100.0]`, `boolean`
- Dates: `date[2020-01-01..2025-12-31]`, `timestamp[now-30d..now]`
- Enums: `enum[VALUE1,VALUE2,VALUE3]`
- Nested: `object[structure_name]` (auto-loads from `structures_path/structure_name.yaml`)
- Arrays: `array[inner_type, min..max]` (e.g., `array[object[line_item], 1..50]`)
- Foreign keys: `ref[other_structure.field]`
- Circular reference detection: fail fast

## Configuration Patterns
- All configs support `alias` for field name mapping
- `geolocation` drives Datafaker locale (e.g., `italy` → Italian names/addresses)
- `structures_path`: optional in job config (default: `config/structures/`)
- Seed resolution: CLI `--seed` > YAML config > default 0 (with warning)
- Seed types: `embedded`, `file`, `env`, `remote` (API with bearer/basic/api_key auth)

## Pre-Authorized Operations (proceed without asking)
- Reading any file in the workspace
- Creating/editing files within module `src/` directories
- Running `./gradlew build`, `./gradlew test`, module-specific tests
- Editing `gradle/libs.versions.toml` and module `build.gradle.kts`
- Creating test files alongside implementation
- Running git status/diff/log/blame
- Creating example config files in `config/`
- Running the CLI with test jobs

## Require Explicit Confirmation
- `./gradlew clean` or deleting build artifacts
- Modifying root `build.gradle.kts` or `settings.gradle.kts`
- Adding new modules to the project
- Installing system packages
- Git commit, push, or branch creation
- Docker commands or Testcontainers setup
- Modifying `.gitignore`, `.github/`, or workspace meta files
