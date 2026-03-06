# SeedStream

[![Build Status](https://github.com/mferretti/SeedStream/actions/workflows/build.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/build.yml)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Gradle](https://img.shields.io/badge/Gradle-9.3-brightgreen.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
![Coverage](https://img.shields.io/badge/coverage-70%25-yellowgreen.svg)
![SpotBugs](https://img.shields.io/badge/SpotBugs-passing-brightgreen.svg)

A high-performance, configurable test data generator for enterprise applications.

---

**📚 Documentation**  
[Architecture & Design](DESIGN.md) · [Code Quality Guide](QUALITY.md) · [Roadmap & Backlog](BACKLOG.md) · [License Discussion](LICENSE-DISCUSSION.md)

---

## Overview

Data Generator is a Java-based tool designed to generate large volumes of realistic test data for various destinations (Kafka, databases, files) with reproducible results using seed-based pseudo-random generation.

## Features

- 🚀 **High Performance**: Multi-threaded generation with batching and streaming
- 🔄 **Reproducible**: Same seed generates identical data across runs (verified with SHA-256)
- 🌍 **Locale-Aware**: Generate realistic data for specific geolocations
- 🔌 **Pluggable Architecture**: Extensible destinations and formats
- ⚙️ **YAML Configuration**: Simple, declarative data structure and job definitions
- 📝 **Multiple Formats**: JSON (NDJSON), CSV with RFC 4180 compliance
- 💾 **File Destinations**: NIO-based file writing with gzip compression support
- 🖥️ **CLI Interface**: Picocli-based command-line tool with intuitive options

## Requirements

- **Java 21 or higher** (tested with Amazon Corretto, OpenJDK, and GraalVM)
- **Gradle 8.5 or higher** (wrapper included, no system installation required)

## Installation

### Development Environment Setup (Recommended: SDKMAN!)

The easiest way to set up the required Java and Gradle versions is using [SDKMAN!](https://sdkman.io/), a tool for managing parallel versions of multiple SDKs.

#### Install SDKMAN! (if not already installed)

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

#### Install Java 21

```bash
# List available Java versions
sdk list java

# Install Java 21 (Amazon Corretto recommended)
sdk install java 21.0.9-amzn

# Set as default (optional)
sdk default java 21.0.9-amzn

# Or use for current shell only
sdk use java 21.0.9-amzn
```

#### Install Gradle (for initial wrapper generation)

```bash
# Install Gradle 8.5
sdk install gradle 8.5

# Generate wrapper scripts in the project
cd /path/to/datagenerator
gradle wrapper --gradle-version 8.5
```

Once the wrapper is generated, you can use `./gradlew` for all builds. System-wide Gradle is no longer needed.

### Alternative Installation Methods

<details>
<summary>Manual Java Installation</summary>

Download Java 21 from:
- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/#java21)
- [Amazon Corretto](https://aws.amazon.com/corretto/)
- [Eclipse Temurin](https://adoptium.net/)

Set `JAVA_HOME` environment variable and add `$JAVA_HOME/bin` to your PATH.
</details>

<details>
<summary>Using System Package Managers</summary>

**Ubuntu/Debian:**
```bash
# Java 21 (if available in repos, otherwise use SDKMAN)
sudo apt update
sudo apt install openjdk-21-jdk

# Note: Gradle from apt is too old (4.4.1), use SDKMAN or the wrapper
```

**macOS (Homebrew):**
```bash
brew install openjdk@21
brew install gradle
```
</details>

## Quick Start

```bash
# Build the project
./gradlew build

# Run a job (defaults: json format, 100 records, seed from config)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml"

# Generate CSV format with custom count
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --format csv --count 10000"

# Override seed for different data set
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 99999"

# Parallel generation with 8 worker threads
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --count 1000000 --threads 8"

# Verbose output for debugging
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose"
```

### Performance Example

Generate 100,000 realistic customer records with Datafaker using 10 worker threads:

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format json --count 100000 --threads 10"
```

**Results:**
- **Records Generated**: 100,000
- **Worker Threads**: 10
- **Time Elapsed**: 14.4 seconds
- **Throughput**: ~6,923 records/sec
- **Output File Size**: 30 MB
- **Data Types**: UUID, names, emails, addresses, phone numbers, cities, states, postal codes (USA locale)

**Sample Output:**
```json
{
  "customer_id": "ce344f82-baf2-4e17-b871-8808047a09c5",
  "first_name": "Valentine",
  "last_name": "Reynolds", 
  "email": "sherman.king@gmail.com",
  "phone": "(256) 511-6029",
  "billing_address": "Suite 233 33062 Verlie Corners, East Berryberg, WI 35149",
  "city": "Mabletown",
  "state": "New Jersey",
  "postal_code": "16305",
  "country": "Kazakhstan"
}
```

**Note**: Performance varies based on data complexity. Simple primitive types (int, string) achieve 100,000+ records/sec, while complex Datafaker objects with nested structures generate at 5,000-10,000 records/sec.

**Available Options:**
- `--job`: Path to job configuration file (required)
- `--format`: Output format: `json` or `csv` (default: `json`)
- `--count`: Number of records to generate (default: `100`)
- `--seed`: Seed override for deterministic generation (optional)
- `--threads`: Number of worker threads for parallel generation (default: CPU cores, use 1 for single-threaded)
- `--verbose`: Enable detailed logging (optional)

## Configuration

### Data Structure Definition

Define your data structure in YAML (e.g., `config/structures/address.yaml`):

```yaml
name: address
geolocation: italy
data:
  name:
    datatype: char[3..15]
    alias: "nome"
  surname:
    datatype: char[3..25]
    alias: "cognome"
  street:
    datatype: char[10..40]
    alias: "via"
  street_n:
    datatype: int[1..999]
    alias: "n."
  city:
    datatype: char[3..40]
    alias: "citta"
```

### Job Definition

Define how and where to generate data (e.g., `config/jobs/file_address.yaml`):

```yaml
source: address.yaml
type: file
seed:
  type: embedded    # embedded, remote, file, or env
  value: 12345      # for embedded type
conf:
  path: cli/output/addresses
  compress: false     # set to true for gzip compression
  append: false       # set to true to append to existing file
```

**Note**: File extension (`.json` or `.csv`) is automatically added based on `--format` CLI parameter.

For other destination types (in development):

<details>
<summary>Kafka Destination (coming soon)</summary>

```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092
  topic: addresses
  auth: sasl_ssl
  cert: path/to/cert.crt
  username: user
  password: path/to/password
```
</details>

#### Seed Configuration

Seeds ensure reproducible data generation. Four types supported:

**1. Embedded** (value in YAML):
```yaml
seed:
  type: embedded
  value: 12345
```

**2. File** (read from file):
```yaml
seed:
  type: file
  path: /secrets/seed.txt
```

**3. Environment Variable**:
```yaml
seed:
  type: env
  name: DATA_SEED
```

**4. Remote API**:
```yaml
seed:
  type: remote
  url: https://seed-service.example.com/api/seed
  auth:
    type: bearer    # or: basic, api_key
    token: ${API_TOKEN}  # or username/password for basic
```

**CLI Override**: `--seed 12345` overrides any configured seed.

**Default Behavior**: If no seed is specified, default seed (0) is used with a warning logged.

**Note**: Format and count are CLI parameters only. Defaults: `--format json --count 100`

## Architecture

```
datagenerator/
├── core/           # Generation engine, type system, seeding, deterministic randomization
├── schema/         # YAML parsing, configuration management
├── generators/     # Data generators (primitives + Datafaker integration)
├── formats/        # Output serializers (JSON ✅, CSV ✅, Protobuf 🔜)
├── destinations/   # Destination adapters (File ✅, Kafka 🔜, Database 🔜)
└── cli/            # Picocli-based command-line interface ✅
```

### Current Implementation Status

**Implemented (v0.2 - March 2026):**
- ✅ Core modules: type system, seed management, random provider
- ✅ Schema parsing: YAML-based configuration
- ✅ Generators: Primitive types (int, char, enum) and composite types (object, array)
- ✅ Datafaker integration: Realistic data generation with 62+ locales (name, email, address, phone, company, etc.)
- ✅ Formats: JSON (newline-delimited), CSV (RFC 4180 compliant)
- ✅ Destinations: File output with compression and append modes, Kafka with SASL/SSL auth
- ✅ Multi-threading engine: Parallel generation with deterministic seeding and backpressure handling
- ✅ CLI: Full command-line interface with all options including --threads
- ✅ Tests: 276+ unit tests with comprehensive coverage

**Partially Implemented:**
- 🔄 Licensing: Apache 2.0 LICENSE file and README badge (missing: source file headers, NOTICE file)
- 🔄 Verbose logging: `--verbose` flag with progress logging (missing: `--debug` flag, dynamic log levels)

**Deferred to Phase 8:**
- ⏸️ Database destination adapter (PostgreSQL, MySQL) - requires careful design for SQL generation
- ⏸️ Reference generator for foreign keys - requires database destinations

**Planned:**
- 📋 Protobuf and Avro format support
- 📋 Statistical distributions (normal, Zipfian)
- 📋 REST/gRPC API module

### Reproducibility & Multi-Threading

Data Generator guarantees **bit-identical output** across runs when using the same seed. This is achieved through:

1. **Deterministic Seeding**: Master seed from configuration or CLI
2. **Logical Worker IDs**: Each thread gets a sequential ID (0, 1, 2, ...) independent of JVM internals
3. **Thread-Local Random**: Each worker has its own `Random` instance with a derived seed:
   ```
   Worker 0: deriveSeed(masterSeed, 0) → Random instance
   Worker 1: deriveSeed(masterSeed, 1) → Random instance
   Worker 2: deriveSeed(masterSeed, 2) → Random instance
   ```

**Why This Matters**: Using JVM thread IDs would break reproducibility because they vary across runs. Our logical worker ID approach ensures the same master seed **always** produces the same data, regardless of:
- JVM restarts
- System thread scheduling
- Garbage collector threads
- Other background threads

**Performance Optimization**: The engine automatically uses single-threaded mode for small jobs (< 1000 records) to avoid threading overhead. For larger jobs, it uses a configurable worker pool with:
- **Bounded queue** for backpressure handling (prevents memory overflow)
- **Single writer thread** for ordered writes to the destination
- **Progress tracking** with throughput metrics (records/sec)
- **Linear scaling** with worker count for large data sets

For technical details, see [DESIGN.md](DESIGN.md).

## Development

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :core:test

# Build distribution
./gradlew :cli:installDist
```

## License

Copyright 2024-2026

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

See [LICENSE](LICENSE) for the full license text.

## Contributing

Contributions are welcome! This is an open-source project licensed under Apache 2.0.

Please ensure:
- All tests pass: `./gradlew test`
- Code is formatted: `./gradlew spotlessApply`
- New features include tests
- Follow the existing code style (Google Java Style Guide)

For major changes, please open an issue first to discuss what you would like to change.
