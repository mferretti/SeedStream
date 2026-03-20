# SeedStream

[![Build Status](https://github.com/mferretti/SeedStream/actions/workflows/build.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/build.yml)
[![Security Scan](https://github.com/mferretti/SeedStream/actions/workflows/security.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/security.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5ddc8a45a98c4ea4b5a8968152634f2f)](https://app.codacy.com/gh/mferretti/SeedStream/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Gradle](https://img.shields.io/badge/Gradle-9.4-brightgreen.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg)](https://codecov.io/gh/mferretti/SeedStream)

High-performance, seed-based test data generator for enterprise applications. Generate realistic, reproducible data to Kafka, databases, and files using simple YAML configuration.

---

## Features

- 🚀 **High Performance**: Multi-threaded generation — 12–258M records/sec for primitives, 25–33K rec/sec for realistic Datafaker data
- 🔄 **Reproducible**: Same seed → identical output, byte-for-byte, across machines and thread counts
- 🌍 **Locale-Aware**: 62 locales supported via Datafaker (Italian names, US addresses, etc.)
- 📝 **Multiple Formats**: JSON (NDJSON), CSV (RFC 4180), Protobuf (binary), CBEFF (biometric envelope)
- 💾 **Multiple Destinations**: File (NIO, gzip), Kafka (SASL/SSL, async/sync), JDBC databases (HikariCP, FK injection)
- ⚙️ **YAML Configuration**: Declarative structure and job definitions — no code required
- 🔌 **Extensible Type System**: 48+ Datafaker semantic types with runtime registration (`DatafakerRegistry`)
- 🔐 **Secure by Default**: File permission validation, `${ENV_VAR}` substitution for credentials

---

## Requirements

- **Java 21+** (Amazon Corretto, OpenJDK, or GraalVM)
- **Gradle 9.4+** wrapper included — no system install needed
- **Docker** (optional, for integration tests with Testcontainers)
- **JDBC driver** (optional, for database destination — drop into `extras/`)

---

## Quick Start

### Option 1 — Fat JAR (no build required)

Download the release JAR and run immediately. You still need the config files, so clone first:

```bash
git clone https://github.com/mferretti/SeedStream.git && cd SeedStream
wget https://github.com/mferretti/SeedStream/releases/latest/download/seedstream-0.4.0.jar
java -jar seedstream-0.4.0.jar execute --job config/jobs/file_address.yaml --count 100
```

### Option 2 — Distribution zip

```bash
wget https://github.com/mferretti/SeedStream/releases/latest/download/cli-0.4.0.zip
unzip cli-0.4.0.zip
# Point to your own job configs or clone the repo for examples
cli-0.4.0/bin/datagenerator execute --job /path/to/job.yaml --count 100
```

### Option 3 — Build from source

```bash
git clone https://github.com/mferretti/SeedStream.git && cd SeedStream
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --count 100"
```

### Common examples

```bash
# Generate 10,000 US customers as CSV
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 10000"

# Stream 1M events to Kafka with 8 threads
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --count 1000000 --threads 8"

# Reproducible output — same seed, same data every time
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 12345 --count 1000"

# Validate a configuration without running
./gradlew :cli:run --args="validate --job config/jobs/file_invoice.yaml"
```

### CLI options

| Option | Default | Description |
|--------|---------|-------------|
| `--job` | required | Path to job YAML |
| `--format` | `json` | `json`, `csv`, `protobuf` |
| `--count` | `100` | Records to generate |
| `--seed` | from config | Override seed for this run |
| `--threads` | CPU cores | Worker threads |
| `--verbose` | off | Detailed logging |
| `--debug` | off | Trace sampling (see `--trace-sample-rate`) |

---

## Performance

Validated throughput from JMH benchmarks (March 2026):

| Data type | Throughput |
|-----------|-----------|
| Primitive (int, boolean) | 12–258M records/sec |
| Datafaker (names, emails, etc.) | 13–154K records/sec |
| Real-world (10-field customer, E2E) | ~25–33K records/sec |
| File I/O | 600–800 MB/s |

**Scaling**: 3.7× speedup with 4 workers (92% efficiency). Datafaker workloads are I/O-bound — 4 threads is usually optimal regardless of core count.

See [PERFORMANCE.md](docs/PERFORMANCE.md) for full benchmarks, tuning guide, and hardware recommendations.

---

## Architecture

```
cli → destinations → formats → generators → schema → core
```

Six independent modules with clean one-way dependencies. Each layer is pluggable: add a destination by implementing `DestinationAdapter`, a format by implementing `FormatSerializer`, or a new semantic type by registering it with `DatafakerRegistry`.

See [DESIGN.md](docs/DESIGN.md) for architecture decisions, the multi-threading reproducibility model, and extension points.

---

## Documentation

| Document | Contents |
|----------|----------|
| [config/README.md](config/README.md) | Type system reference, job/structure examples, Kafka & database config |
| [docs/DESIGN.md](docs/DESIGN.md) | Architecture, threading model, reproducibility, extensibility |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Benchmarks, tuning guide, hardware recommendations |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common errors, debug mode, FAQ |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Setup, development workflow, code standards |
| [docs/QUALITY.md](docs/QUALITY.md) | Coverage, SpotBugs, Spotless configuration |
| [CHANGELOG.md](CHANGELOG.md) | Release history and roadmap |

---

## Contributing

Contributions welcome — bug reports, new generators, destinations, or formats.

```bash
git clone https://github.com/mferretti/SeedStream.git
cd SeedStream
./gradlew build test
```

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for setup, workflow, and code standards.

---

## License

Copyright 2024-2026 Marco Ferretti

Licensed under the [Apache License 2.0](LICENSE).
