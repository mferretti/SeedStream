# Changelog

All notable changes to SeedStream will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- **Debug mode**: `--debug` flag with intelligent trace sampling (`--trace-sample-rate` 1-100%, default 10%)

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
- **Module structure**: 6 modules (core, schema, generators, formats, destinations, cli)
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

### v0.5.0 (Planned - Q2 2026)
- Reference generator for foreign keys (`ref[structure.field]`)
- Statistical distributions (normal, Zipfian, exponential)
- Advanced Datafaker correlations and constraints
- Memory profiling tooling

### v0.6.0 (Planned - Q3 2026)
- REST API module
- gRPC API module
- Docker image and Kubernetes deployment
- Helm chart

### v1.0.0 (Planned - 2027)
- Plugin architecture (ServiceLoader-based extensibility)
- Schema registry integration (Confluent, AWS Glue)
- Data masking and anonymization
- Metrics and monitoring (Prometheus, Grafana)
- Web UI for configuration management

---

## Contributing

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for how to contribute to SeedStream.

For detailed internal planning, see [docs/internal/BACKLOG.md](docs/internal/BACKLOG.md).

---

## License

Copyright 2024-2026 Marco Ferretti

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
