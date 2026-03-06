# Changelog

All notable changes to SeedStream will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0] - March 2026

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
- **File destination**: NIO-based with compression (gzip), append mode, buffering (600-800 MB/s)
- **Kafka destination**: Async/sync modes, SASL/SSL authentication, compression, batching

#### CLI
- **Execute command**: Run job definitions with format/count/seed/threads overrides
- **Validate command**: Validate YAML configurations without execution
- **Progress logging**: Real-time throughput metrics (records/sec)
- **Verbose mode**: Detailed worker activity and destination telemetry

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

### Known Limitations
- **Database destination**: Not yet implemented (planned for v0.3)
- **Reference generator**: Foreign keys (`ref[structure.field]`) not supported
- **Protobuf format**: Not yet implemented (planned for v0.3)
- **Statistical distributions**: Only uniform distribution (normal/Zipfian planned for v0.4)
- **Plugin architecture**: Fixed generators/destinations (extensibility planned for v1.0)

### Security
- **License**: Apache 2.0 (permissive, enterprise-friendly)
- **Dependencies**: OWASP Dependency-Check integrated, no known CVEs
- **Secrets**: Support for environment variables and remote auth (bearer, basic, API key)

### Breaking Changes
None (first public release).

---

## [0.1.0] - December 2025

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

### v0.3.0 (Planned - Q2 2026)
- Database destination adapter (PostgreSQL, MySQL)
- Reference generator for foreign keys (`ref[structure.field]`)
- Protobuf format support
- Connection pooling (HikariCP)
- Batch SQL insert optimization

### v0.4.0 (Planned - Q3 2026)
- Statistical distributions (normal, Zipfian, exponential)
- Advanced Datafaker integration (correlations, constraints)
- Performance tuning for Kafka (benchmark-driven optimization)
- Memory profiling tooling

### v0.5.0 (Planned - Q4 2026)
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
