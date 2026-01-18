# Data Generator

A high-performance, configurable test data generator for enterprise applications.

## Overview

Data Generator is a Java-based tool designed to generate large volumes of realistic test data for various destinations (Kafka, databases, files) with reproducible results using seed-based pseudo-random generation.

## Features

- 🚀 **High Performance**: Multi-threaded generation with batching and streaming
- 🔄 **Reproducible**: Same seed generates identical data across runs
- 🌍 **Locale-Aware**: Generate realistic data for specific geolocations
- 🔌 **Pluggable Architecture**: Extensible destinations and formats
- 📊 **Statistical Distributions**: Support for normal, uniform, and Zipfian distributions
- ⚙️ **YAML Configuration**: Simple, declarative data structure and job definitions

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

# Run a job (defaults: json format, 100 records, seed from config or 0)
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml"

# Run with custom parameters
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format json --count 10000 --seed 99999"
```

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

Define how and where to generate data (e.g., `config/jobs/kafka_address.yaml`):

```yaml
source: address.yaml
type: kafka
seed:
  type: embedded    # embedded, remote, file, or env
  value: 12345      # for embedded type
conf:
  bootstrap: localhost:9092
  topic: addresses
  auth: sasl_ssl
  cert: path/to/cert.crt
  username: user
  password: path/to/password
```

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
├── formats/        # Output serializers (JSON, CSV, Protobuf)
├── destinations/   # Destination adapters (Kafka, Database, File)
└── cli/           # Command-line interface
```

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

[To be determined]

## Contributing

Contributions welcome! This project is intended to be open source.
