# CLAUDE.md — Data Generator

## Project Overview
High-performance test data generator for enterprise apps. Makes realistic, reproducible test data to many destinations (Kafka, databases, files) via YAML config.

**Key Design Goals:**
- **Performance**: Multi-threaded generation, batching
- **Reproducibility**: Seed-based pseudo-random generation, deterministic output
- **Extensibility**: Plugin architecture for destinations, formats, data generators
- **Enterprise-ready**: Locale-aware data, statistical distributions, connection pooling

## Architecture & Multi-Module Structure

```
datagenerator/
├── core/           # Generation engine, type system, seeding, parallel execution
├── schema/         # YAML parsers for data structures and job definitions
├── generators/     # Data generators (primitives + Datafaker for realistic data)
├── formats/        # Output serializers (JSON, CSV, Protobuf, Avro, Avro+Registry, CBEFF)
├── destinations/   # Destination adapters (Kafka, DB with HikariCP, File with NIO)
├── inspector/      # `inspect` subcommand: OpenAPI / SQL DDL / Protobuf → structure YAML
└── cli/            # Picocli-based CLI
```

**Module Dependencies:** `cli` → `destinations` → `formats` → `generators` → `schema` → `core`; and `cli` → `inspector` → `schema` → `core`

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
- Java 21 (toolchain enforced, use virtual threads for I/O-bound ops)
- Gradle 9.5+ with Kotlin DSL, Version Catalog at `gradle/libs.versions.toml`
- Lombok (boilerplate reduction, on in all modules)
- Spotless (code formatting — run `./gradlew spotlessApply` before commit)
- Datafaker (realistic locale-specific data generation)
- Jackson YAML (config parsing)
- Picocli (CLI framework)

## Dependency Management
- All versions in `gradle/libs.versions.toml` (single source of truth)
- Use type-safe accessors: `implementation(libs.kafka.clients)`
- Add dependency: add version to `[versions]`, define in `[libraries]`, reference in module `build.gradle.kts`

## Code Style
- Google Java Style Guide — opening braces `{` on same line
- Max line length: 120 chars
- Use Java 21 features: records, pattern matching, switch expressions, virtual threads
- Prefer functional style (streams) over imperative loops
- Use Lombok `@Builder` for classes with 4+ params
- Return `Optional<T>` for methods that may lack value
- Immutability: config objects use Lombok `@Value`
- Never return null collections (return empty)

**Import Rules:**
- No wildcard imports (exception: `import static org.assertj.core.api.Assertions.*;` in tests)
- Explicit per-class imports only
- Never use fully-qualified names inline (e.g., `@lombok.Value`) — always import
- Exception: when class names collide, use fully-qualified for all instances
- Remove unused imports

## Error Handling
- Hibernate Validator for config validation (`@NotNull`, `@Valid`, `@Min`, `@Max`)
- Fail fast: validate at boundaries (parsers, CLI)
- Custom exceptions: `ConfigurationException`, `GeneratorException`, `DestinationException`
- Always log or rethrow with context; use try-with-resources for all I/O

## Performance Patterns
1. Thread-local Random with deterministic seeding (job seed → worker thread seeds)
2. Batching: generate and flush in configurable batches to amortize I/O
3. Streaming: generate → serialize → send pipeline (no loading all in memory)
4. Connection pooling: HikariCP for databases, reuse Kafka producers
5. All generators must be thread-safe (use thread-local state)
6. Hot paths (generators, serializers): optimize aggressively
7. Cold paths (parsers, CLI): prioritize readability

## Testing Strategy
- Unit tests: JUnit 5 + Mockito + AssertJ
- Integration tests: Testcontainers for Kafka/databases, real file I/O
- Mock external deps; use real objects for pure logic
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
- Foreign keys: `ref[other_structure.field, min..max]` or `ref[other_structure.field, 1..count]` (range required — bare `ref[s.field]` is rejected)
- Circular reference detection: fail fast

## Configuration Patterns
- All configs support `alias` for field name mapping
- `geolocation` drives Datafaker locale (e.g., `italy` → Italian names/addresses)
- `structures_path`: optional in job config (default: `config/structures/`)
- Seed resolution: CLI `--seed` > YAML config > default 0 (with warning)
- Seed types: `embedded`, `file`, `env`, `remote` (API with bearer/basic/api_key auth)

## Pre-Authorized Operations (proceed without asking)
- Read any file in workspace
- Create/edit files within module `src/` directories
- Run `./gradlew build`, `./gradlew test`, module-specific tests
- Edit `gradle/libs.versions.toml` and module `build.gradle.kts`
- Create test files alongside implementation
- Run git status/diff/log/blame
- Create example config files in `config/`
- Run CLI with test jobs
- Create feature branch when starting new feature work (commit/push still need confirmation)

## Require Explicit Confirmation
- `./gradlew clean` or deleting build artifacts
- Modifying root `build.gradle.kts` or `settings.gradle.kts`
- Adding new modules to project
- Installing system packages
- Git commit or push (feature-branch creation pre-authorized; other branch creation still needs confirmation)
- Docker commands or Testcontainers setup
- Modifying `.gitignore`, `.github/`, or workspace meta files