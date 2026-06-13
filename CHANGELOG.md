# Changelog

All notable changes to SeedStream will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

**In progress**: Avro + Schema Registry, AES-256-GCM secret encryption, cloud secret backends, parent-reference FK propagation, determinism bug fixes.

### Added

#### Inspector
- **`inspect` subcommand** — reads an OpenAPI 3.x spec (`.yaml` / `.json`) or a SQL DDL file (`.sql`) and emits one ready-to-use SeedStream structure YAML per schema object / `CREATE TABLE`
- **Reason taxonomy** (`DECLARED`, `DEFAULT_RANGE`, `NAME_HINT`, `UNKNOWN_TYPE`) — every field mapping is tagged with why its type was chosen; only the two *guesses* (`NAME_HINT`, `UNKNOWN_TYPE`) receive an inline `# …` review comment on the `datatype:` line, keeping schemas with declared types noise-free
- **`--faker-types <file>`** flag — registers extra Datafaker providers before inspection so domain-specific column names (e.g. `beer_style`) resolve to custom semantic types rather than a plain `char` fallback
- **FK-inversion nesting** (`--nest[=auto|all|none]`, `--nest-default-count <min..max>`) — DDL inspector can invert `1:n`/`1:1` foreign keys into nested `array[object[child], min..max]` / `object[child]` fields on the parent instead of flat `ref[]`; cycles (incl. self-references), composite FKs and M:N junctions stay flat with a warning (`auto`) or hard-error on cycles (`all`); a `UNIQUE`/PK FK nests as `object[child]`. New `NestingPlanner`, `Pluralizer`, `NestingOptions`. OpenAPI already nests natively so the flag is ignored there. See [docs/INSPECT-NESTING-PLAN.md](docs/INSPECT-NESTING-PLAN.md) and spec §9
- **DDL multi-word / vendor-type folding** — type names are folded through a synonym table so `CHARACTER VARYING`, `DOUBLE PRECISION`, `TIMESTAMP WITH[OUT] TIME ZONE`, `SERIAL`/`BIGSERIAL`, `INT2/4/8`, `VARCHAR2`, `LONGTEXT`, native `UUID`, etc. map to the right SeedStream type instead of the `char[1..50]` unknown fallback
- **`CustomProviderSmokeTest`** — end-to-end test: loads non-default providers (`Beer`, `Pokemon`), inspects a DDL, asserts emitted types and exact comment placement
- Output safety: existing files skipped unless `--force`; CLI summary reports files written, skipped, and fields flagged for review

#### Formats
- **Avro serializer** (`avro`) — OCF container format with dynamic schema generation from job structure; no external dependencies
- **Avro + Confluent Schema Registry** (`avro-registry`) — Confluent wire format (magic byte + schema ID + Avro binary); registers schema on first write, caches ID in memory; supports `bearer` and `basic` auth
- **Jackson streaming JSON** — replaced tree-model serialization with `JsonGenerator` streaming API; suppresses redundant flush on `ByteArrayOutputStream` paths; ~15% throughput improvement for file/Kafka destinations

#### Secret Management
- **`${SECRET:enc:AES256GCM:<ciphertext>}`** — inline AES-256-GCM encrypted secrets in job YAML; key loaded from env var (`SEEDSTREAM_ENCRYPTION_KEY`) or key file (`--key-file`)
- **`encrypt` CLI command** — `echo -n "my-password" | seedstream encrypt` (or interactive prompt) produces a ciphertext token ready to paste into YAML; supports `--key-env` and `--key-file` flags; plaintext-as-arg form still accepted but discouraged (visible in shell history)
- **HashiCorp Vault backend** (`type: vault`) — KV v1/v2 auto-detection; field extraction via `#fieldname` suffix; bearer auth
- **AWS Secrets Manager backend** (`type: aws`) — resolves `SecretString` and `SecretBinary`; region-configurable; uses default AWS credential chain
- **Azure Key Vault backend** (`type: azure`) — resolves secrets by name/version; uses `DefaultAzureCredential`
- **Encrypted file backend** (`type: encrypted-file`) — decrypts an AES-256-GCM ciphertext file at startup; suitable for CI secrets stored in repo
- **`ConfigSubstitutor`** — resolves all `${SECRET:*}` and `${ENV:*}` placeholders in job YAML before job execution

#### Reliability
- **`RetryPolicy`** — exponential-backoff retry for destination operations (`open`, `write`, `flush`); configurable `maxAttempts` and `initialDelayMs`; Kafka and database destinations use it by default

#### Type System
- **`ref[parent.field]`** — new type syntax that copies a scalar field from the immediately enclosing parent record into a nested structure field at generation time; guarantees referential integrity without a separate generation pass or post-processing step
- **`ParentReferenceType`** — sealed `DataType` implementation for the parent-ref syntax; added to `DataType` permits clause
- **`ParentReferenceGenerator`** — stateless `DataGenerator` registered in `DataGeneratorFactory`; reads the target field from `GeneratorContext`'s parent-record stack; throws `GeneratorException` if the stack is empty or the field has not yet been generated

#### Generation Engine
- **`GeneratorContext.PARENT_RECORD_STACK`** — `ThreadLocal<Deque<Map<String,Object>>>` that tracks partial parent records during nested generation; exposes `pushParentRecord`, `popParentRecord`, `peekParentRecord`; initialized in `enter()` and cleared in `close()`

#### Database Destination
- **`NestedRecordDecomposer.injectParentFk` flag** — when `false`, suppresses automatic `{tableName}_id` FK injection into child records; use when child structures already populate the FK via `ref[parent.field]`
- **`DatabaseDestinationConfig.injectParentFk`** — boolean builder field (default `true`) that wires the decomposer flag through job configuration; set to `false` alongside `ref[parent.id]` to avoid a redundant second FK column

#### Testing
- **`EncryptCommandTest`**, **`ExecuteCommandTest`** — CLI-level unit tests via picocli `CommandLine` test harness; covers encrypt/decrypt round-trip, key errors, format dispatch, destination wiring
- **`ParentReferenceGeneratorTest`** (new, 7 tests) — supports(), integer/string field values, innermost-stack reads, empty-stack error, missing-field error, no-context error
- **`TypeParserTest`** — 2 new cases: `ref[parent.id]` and `ref[parent.author_id]` parse to `ParentReferenceType` with correct `targetField` and `describe()`
- **`GeneratorContextTest`** — 4 new cases: peek null on empty stack, peek pushed record, nested push/pop depth, stack cleared on context close
- **`ObjectGeneratorTest`** — new case: scalar `id` available to `ref[parent.id]` in a `1..1` nested array regardless of field iteration order
- **`NestedRecordDecomposerTest`** — 3 new cases: `injectParentFk=false` omits `lib_authors_id` column, produces no parent context for grandchildren, default constructor still injects
- **`GenerationEngineTest`** — new case: `workerCleanup` `Runnable` is invoked in the single-threaded path
- **`LibraryForeignKeyIT`** — full rewrite: 2-table schema (`lib_authors` + `books`), single `generate()` call with `injectParentFk=false`; 3 tests: row counts, orphan check (every `books.author_id` references an actual `lib_authors.id`), seed determinism

#### Performance & Generation Engine
- **Worker-side parallel serialization** — the engine gained an optional serialized pipeline (`RecordSerializer` + `SerializedWriter`): when a destination supports it, each worker serializes its record to bytes in parallel and the single writer thread performs ordered I/O only, moving the heaviest CPU stage off the lone writer. Enabled for byte-independent paths (file JSON, all Kafka); Avro OCF and CSV stay on the writer thread (ordered container / header dependency). New `DestinationAdapter.supportsSerializedWrite()` / `writeSerialized(byte[])` default methods; `FileDestination` and `KafkaDestination` implement them.
- **`FieldRecord` flyweight record** — replaced the per-record `LinkedHashMap` built by `ObjectGenerator` with a flyweight backed by an interned `RecordSchema` (field-name array + index, built once per structure) and a per-record `Object[]`. Eliminates the per-record hash table, the N `Node` allocations, and the repeated field-name strings — the dominant short-lived allocation at scale. It is a full `Map<String,Object>` (schema-ordered `entrySet`), so all serializers, destinations, and `ref[parent.*]` resolution are unchanged; serialization field order is byte-for-byte identical to the old map.
- **Queue chunk-batching** — workers now accumulate records into chunks (default 256) before the bounded-queue hand-off instead of one `put`/`take` per record, amortizing the queue lock + signal cost by ~chunkSize. `queueCapacity` stays record-denominated; chunk-queue capacity is derived.

### Changed
- **`utils/` → `scripts/`** — the shell-script directory was a sibling of the Gradle modules but absent from `settings.gradle.kts`; renamed so it stops masquerading as a module.
- **Scratch output consolidated to `build/run-output/`** — file-destination job configs, profiling scripts, and the e2e benchmark runner now write under a single `build/`-ignored dir (auto-cleaned by `gradle clean`) instead of four scattered `output/` locations.
- **Architecture docs reconciled** — `DESIGN.md` and the `GenerationEngine` javadoc now state that serialization runs on the writer thread (ordered/stateful destinations), and document the chunk-batching and worker-serialize pipeline. The previous diagram implied parallel serialization that did not exist.
- **E2E file benchmark jobs unified on the flat `passport` structure** across json/csv/protobuf (apples-to-apples; was a mix of invoice symlinks with csv missing). Invoice file jobs retained but unreferenced by the matrix.
- **`ObjectGenerator`** — rewrote field-generation loop as two passes: Pass 1 generates all non-nested (scalar/enum/parent-ref) fields first; Pass 2 generates nested fields (`ArrayType`, `ObjectType`) with the accumulated scalar result pushed onto the parent-record stack. Fixes a subtle ordering dependency: `id` is guaranteed to be present in the partial record when any child `ref[parent.id]` generator runs, regardless of `HashMap` iteration order.
- `KafkaDestination`, `DatabaseDestination`, `FileDestination` constructors renamed parameters to avoid CheckStyle HiddenField violations
- Switch expressions across codebase updated with `case null` branches (Codacy/PMD compliance)
- `ExecuteCommand.createSerializer()` and `createDestination()` guard against `null` format/type before `toLowerCase()` (NPE-safe)

### Fixed
- **`GenerationEngine` progress log** — the throughput line used SLF4J with printf specifiers (`"Progress: {} / {} ({:.1f}%) - {:.0f} records/sec"`); SLF4J only honors `{}`, so percent and records/sec rendered as the literal `{:.1f}`/`{:.0f}` text and the argument count was wrong. Now pre-formats the numbers with `String.format`, so progress renders correctly (e.g. `100.0% - 28935 records/sec`).
- **`GenerationEngine.generateSingleThreaded()`** — `workerCleanup.run()` was never called in the single-threaded path (taken when `count < singleThreadedThreshold`, default 1000). Caused `FakerCache` to retain stale `Random` references between consecutive `generate()` calls with the same seed, producing non-deterministic output on the second run.
- **`FakerCache.getOrCreate()`** — suppressed PMD `CompareObjectsWithEquals` false positive (Codacy); `Random` does not override `equals()` so identity comparison (`!=`) is correct and intentional.
- `ExecuteCommand.createDestination()` switch default referenced wrong variable (`type` instead of `normalizedType`) — compilation error after Codacy renames; fixed

### Security
- **OWASP review of the schema/secrets surface** — hardened the configuration and secret-resolution paths:
  - **SQL injection** — `DatabaseDestination` validates table/column identifiers against a strict allowlist before building INSERT SQL.
  - **SSRF** — new `core/security/UrlValidator` enforces http/https + non-empty host on all outbound URLs (remote seed API, Schema Registry, Vault, and now Azure Key Vault `vault_uri`).
  - **Path traversal** — new `core/security/PathValidator` canonicalizes file paths (`toRealPath`) with optional base-dir containment, applied to seed files and the encryption-key file.
  - **Secret handling** — remote-seed auth fields are resolved through the secret substitutor before use; the `encrypt` CLI reads plaintext via console/stdin instead of a shell argument; the `System.getProperty` fallback for env/secret lookups was removed.
  - **TOCTOU** — `FilePermissionValidator` reads permissions from an already-open `FileChannel` instead of an `exists()` check-then-act.
  - **Config strictness** — YAML parsing now rejects unknown properties (`FAIL_ON_UNKNOWN_PROPERTIES`) so mistyped/smuggled keys fail fast instead of being silently ignored.
  - **Input validation** — empty `${SECRET:}` paths and empty `${}` variable names are rejected; Vault errors no longer leak the server address and now accept any 2xx response.
- **OWASP review of the generators surface** — bounded resource allocation at generation time:
  - **Memory-exhaustion DoS** — `ArrayGenerator` and `CharGenerator` now reject a `maxLength` above `GeneratorValidation.MAX_GENERATED_SIZE` (10M) before allocating, so a crafted type bound (e.g. from an untrusted spec passed through `inspect`) raises `GeneratorException` instead of `OutOfMemoryError`.
- **OWASP review of the formats surface** — neutralized output-side injection:
  - **CSV formula injection (CWE-1236)** — `CsvSerializer` prefixes string cells/headers that begin with `= + - @`, TAB or CR with a single quote, so generated values (e.g. Datafaker phone numbers, config enums) cannot execute as spreadsheet formulas. Typed numbers/dates are unaffected.
  - **Path injection (CWE-88)** — `HttpSchemaRegistryClient` URL-encodes the Schema Registry `subject` as a single path segment, so a config-derived subject cannot break out of the request path.
- **OWASP review of the destinations surface** — kept credentials out of logs:
  - **Sensitive data exposure (CWE-532)** — `DatabaseDestinationConfig.password` and the Kafka `saslJaasConfig` / SSL store passwords are now `@ToString.Exclude`d, so the Lombok-generated `toString()` can never leak them into logs or exception messages.

---

## [0.5.0] - 2026-06-04

**Release**: Foreign key reference generator (`ref[]`), dynamic count-based ID pools.

### Added

#### Foreign Key Reference Generator
- **`ref[structure.field, min..max]`** — samples a uniform random `long` from the given static pool
- **`ref[structure.field, min..count]`** — dynamic variant: `count` resolves to the job's `--count` at runtime, so FK ranges scale automatically without YAML edits
- **`ReferenceGenerator`** — stateless `DataGenerator` wired into `DataGeneratorFactory`
- **`GeneratorContext.jobCount`** — new `ThreadLocal<Long>` carries the job count into each worker thread; `enter(factory, geo, count)` overload added; backward-compatible `enter(factory, geo)` overload retained
- **`JdbcTypeMapper`** — binds `ReferenceType` values as `BIGINT` (Option B schema-aware path)
- **End-to-end integration test** (`DatabaseReferenceIT`) — 3-table FK chain (`it_customer → it_order → it_order_item`) verified against a real PostgreSQL container via Testcontainers; 5 test cases covering row counts, bound assertions, determinism, and scaling

### Changed
- `ExecuteCommand` passes `count` into `GeneratorContext` so `ref[s.f, min..count]` resolves correctly in multi-threaded jobs
- `ReferenceType` gains `min`, `max`, `maxIsCount` fields; `describe()` emits the canonical syntax

---

## [0.4.0] - 2026-03-20

**Release**: Database destination complete, biometric data support, security hardening.

### Added

#### Database Destination (Stage 2)
- **Nested object auto-decomposition**: Automatically flattens nested structures into relational columns
- **JMH benchmarks** for database write throughput

#### Biometric Support
- **Biometric structure definitions**: Face and fingerprint data schemas (`config/structures/`)
- **Biometric job definitions**: Pre-built jobs for face/fingerprint test data generation
- **`BiometricValidator`**: Validates biometric field constraints (dimensions, quality scores, ISO/IEC 19794 ranges)
- **`validate` CLI subcommand**: Validates YAML configurations without executing a job

#### Formats
- **CBEFF format** (`CbeffSerializer`): CBEFF-like JSON envelope format for biometric payloads

#### Distribution
- **`extras/` directory**: Ships with the distribution for JDBC drivers and custom Datafaker providers; contents are automatically added to the classpath at startup

#### Security
- **File permission validation**: Startup check verifies config files are not world-writable (fails fast on unsafe permissions)

#### Build
- **Gradle configuration cache**: Enabled for faster incremental builds
- **CI consolidation**: SpotBugs moved into the security workflow alongside OWASP Dependency-Check

### Changed
- Database destination benchmark suite now supports a filter flag to run a subset of scenarios

---

## [0.3.0] - 2026-03-08

**Release**: Registry-based type system, extended Datafaker coverage, database destination (Stage 1).

### Added

#### Data Generators
- **20 new Datafaker semantic types** (Phase 1): expanded coverage for realistic data (passport, finance, biometric-adjacent fields, etc.)
- **Thread-local Faker cache**: Eliminates repeated Faker instantiation in hot paths; significant throughput improvement for Datafaker-heavy jobs

#### Database Destination (Stage 1)
- **JDBC destination**: Write generated records to any JDBC-compatible database
- **HikariCP connection pooling**: Configurable pool size, timeout, and keepalive
- **Batch SQL insert**: Configurable batch size to amortize round-trip latency

### Changed
- **Registry-based type system** (`DatafakerRegistry`): Replaces the enum-based `PrimitiveType.Kind` with a `ConcurrentHashMap`-backed registry
  - Removed 42 semantic enum values (NAME, EMAIL, ADDRESS, etc.)
  - 48+ built-in types registered at startup; 20+ aliases (lat/latitude, swift/bic, etc.)
  - `CustomDatafakerType` replaces enum-based semantic types
  - `TypeParser` simplified (~150 lines removed); `DatafakerGenerator` simplified (~220 lines removed)
  - Total reduction: ~350 lines of duplicated switch logic eliminated
  - Foundation for future plugin architecture (runtime type registration without recompilation)

### Fixed
- Removed deprecated static factory methods from generator classes
- Fixed `FakerCacheTest` ThreadLocal pollution between test cases

---

## [0.2.0] - 2026-02-01

**Major Release**: Core features complete, production-ready for file and Kafka destinations.

### Added

#### Core Functionality
- **Multi-threading engine** with deterministic seeding and backpressure handling
- **Seed resolution** from multiple sources (embedded, file, environment variable, remote API)
- **Deterministic random provider** with thread-local RNG and logical worker IDs
- **Type system** supporting primitives (int, char, decimal, boolean, date, timestamp, enum)
- **Nested structures** with `object[structure_name]` syntax
- **Variable-length arrays** with `array[type, min..max]` syntax
- **Geolocation-aware data** using Datafaker (62+ locales supported)

#### Data Generators
- **Primitive generators**: int, char, decimal, boolean, date, timestamp, enum (12-258M ops/s)
- **Datafaker integration**: 28 semantic types (name, email, address, phone, company, etc.)
- **Composite generators**: nested objects, variable-length arrays, circular reference detection
- **Performance**: 6,923 realistic records/sec validated (100K customer records benchmark)

#### Formats & Destinations
- **JSON format**: Newline-delimited JSON (RFC 7159 compliant)
- **CSV format**: RFC 4180 compliant with configurable delimiters
- **Protobuf format**: Protocol Buffers binary serialization with dynamic schema generation (50-70% smaller than JSON)
- **File destination**: NIO-based with compression (gzip), append mode, buffering (600-800 MB/s)
- **Kafka destination**: Async/sync modes, SASL/SSL authentication, compression, batching

#### CLI
- **Execute command**: Run job definitions with format/count/seed/threads overrides
- **Validate command**: Validate YAML configurations without execution
- **Progress logging**: Real-time throughput metrics (records/sec)
- **Verbose mode**: Detailed worker activity and destination telemetry
- **Debug mode**: `--debug` flag with intelligent trace sampling (`--trace-sample` 1-100%, default 10%)

#### Testing & Quality
- **Unit tests**: 276+ tests across all modules (70%+ coverage)
- **Integration tests**: 43 tests using Testcontainers (Kafka, file I/O)
- **Benchmarks**: JMH-based performance validation (NFR-1 compliance)
- **Code quality**: Spotless (Google Java Style), SpotBugs, JaCoCo, OWASP Dependency-Check
- **CI/CD**: GitHub Actions with automated testing and Codecov integration

#### Documentation
- **README**: Complete quickstart, type system reference, configuration guide
- **DESIGN.md**: Architecture decisions, multi-threading model, extensibility patterns
- **PERFORMANCE.md**: Comprehensive benchmarks, tuning guide, hardware recommendations
- **CONTRIBUTING.md**: Contributor workflow, code standards, PR process, style guide
- **QUALITY.md**: Code quality tools setup and troubleshooting
- **benchmarks/README.md**: Benchmark execution guide and result interpretation

### Changed
- **Module structure**: 7 modules (core, schema, generators, formats, destinations, cli, benchmarks)
- **File I/O optimization**: 64KB buffer (up from 8KB), batching (1000 records/batch)
- **Smart threading**: Automatic single-threaded mode for small jobs (< 1000 records)
- **Seed warning**: Log warning when using default seed (0) for reproducibility awareness

### Fixed
- **Thread-safety**: Logical worker IDs instead of JVM thread IDs for deterministic seeding
- **Memory leaks**: Proper resource cleanup (connection pools, file handles, Kafka producers)
- **Backpressure**: Bounded queue prevents OOM on fast generation + slow destination
- **Circular references**: Detection and prevention in nested object structures

### Performance
- **Primitive generation**: 12-258M ops/s (Boolean: 258M, Integer: 57M, Char: 12M)
- **Datafaker generation**: 13-154K ops/s (Company: 154K, Phone: 13K)
- **File I/O**: 600-800 MB/s with optimized buffering
- **Real-world**: 100K customer records in 14.4 seconds (6,923 rec/sec, 10 threads)
- **Scaling**: 3.7× speedup with 4 workers (92% efficiency)

### Security
- **License**: Apache 2.0 (permissive, enterprise-friendly)
- **Dependencies**: OWASP Dependency-Check integrated, no known CVEs
- **Secrets**: Support for environment variables and remote auth (bearer, basic, API key)

### Breaking Changes
None (first public release).

---

## [0.1.0] - 2025-12-01

**Initial Development Release**: Internal testing and validation.

### Added
- Basic YAML configuration parsing
- Primitive data generators (int, string)
- File destination with JSON format
- Single-threaded generation engine
- CLI scaffolding with Picocli

### Notes
Not publicly released. Internal prototype for architecture validation.

---

## Roadmap

### v0.7.0 (Planned)
- Statistical distributions (normal, Zipfian, exponential)
- Advanced Datafaker correlations and constraints
- Binary FMR serializer (ISO/IEC 19794-2, pending spec access)

### v1.0.0 (Planned)
- REST API module
- Plugin architecture (ServiceLoader-based extensibility)
- Data masking and anonymization
- Docker image
- Metrics and monitoring (Prometheus, Grafana)

---

## Contributing

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for how to contribute to SeedStream.

For detailed internal planning, see [docs/internal/BACKLOG.md](docs/internal/BACKLOG.md).

---

## License

Copyright 2024-2026 Marco Ferretti

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
