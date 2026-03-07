# SeedStream

[![Build Status](https://github.com/mferretti/SeedStream/actions/workflows/build.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/build.yml)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Gradle](https://img.shields.io/badge/Gradle-9.3-brightgreen.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg)](https://codecov.io/gh/mferretti/SeedStream)
![SpotBugs](https://img.shields.io/badge/SpotBugs-passing-brightgreen.svg)

A high-performance, configurable test data generator for enterprise applications.

---

**📚 Documentation**  
[Architecture & Design](docs/DESIGN.md) · [Performance Guide](docs/PERFORMANCE.md) · [Contributing Guide](docs/CONTRIBUTING.md) · [Code Quality](docs/QUALITY.md) · [Benchmarks](benchmarks/README.md)

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

## Dependency Management

SeedStream uses **[Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html)** for centralized dependency management, providing a single source of truth for all library versions across modules.

### Key Benefits

- ✅ **Centralized versions**: All dependencies defined in `gradle/libs.versions.toml`
- ✅ **Type-safe accessors**: IDE autocomplete for `libs.kafka.clients`, `libs.jackson.databind`, etc.
- ✅ **Consistency**: Same version across all modules automatically
- ✅ **Easy updates**: Change one line to update all modules

### Usage Example

In any module's `build.gradle.kts`:

```kotlin
dependencies {
    // Reference from catalog (version managed centrally)
    implementation(libs.kafka.clients)
    implementation(libs.bundles.jackson)  // Bundle of related libraries
    
    testImplementation(libs.bundles.testing)
}
```

**Version Catalog Location**: [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

**Current Versions** (all latest stable as of March 2026):
- Jackson: 2.21.1
- Kafka: 4.2.0
- Protobuf: 4.34.0
- MySQL Connector: 9.6.0
- JUnit: 6.0.3
- See full list in [gradle/libs.versions.toml](gradle/libs.versions.toml)

**Security Status**: ✅ 0 known vulnerabilities (CVSS 7.0+) - See [SECURITY.md](SECURITY.md)

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

**Note**: Performance varies based on data complexity. Simple primitive types achieve **millions of records/sec** (57M for int, 12M for char) for in-memory generation, while complex Datafaker objects with nested structures generate at 5,000-10,000 records/sec.

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

**Kafka destination example** (`config/jobs/kafka_address.yaml`):

```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092
  topic: addresses
  batch_size: 1000
  linger_ms: 10
  compression: gzip  # gzip, snappy, lz4, zstd, none
  acks: "1"          # "0", "1", or "all"
  sync: false        # false for async, true for sync
```

See [Kafka Integration](#kafka-integration) section for full configuration options.

**Database destination** (planned for Phase 8).

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

SeedStream follows a modular architecture with clean dependencies:

```
cli → destinations → formats → generators → schema → core
```

Each module has a clear responsibility (generation, serialization, delivery) and can be extended independently.

**Current status** (v0.2 - March 2026): Core, schema, generators, formats (JSON, CSV), destinations (File, Kafka), and CLI are fully implemented with 70%+ test coverage. Database destination and Protobuf format are planned.

For detailed architecture, design decisions, and the multi-threading reproducibility model, see **[DESIGN.md](docs/DESIGN.md)**.

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

## Performance

**Validated throughput** (March 2026, JMH benchmarks):

- **Primitive types**: 12-258M records/sec (in-memory) — Boolean fastest (258M), char slowest (12M)
- **Realistic Datafaker data**: 13-154K records/sec — Company names fastest (154K), phones slowest (13K)
- **Real-world example**: 100,000 customer records (10 Datafaker fields) in 14.4 seconds = **6,923 records/sec**

**Rule of thumb**: Datafaker is ~1,000× slower than primitives. Use primitives for volume, Datafaker for realism.

For comprehensive benchmarks, tuning guidance, and hardware recommendations, see **[PERFORMANCE.md](docs/PERFORMANCE.md)**.

To run benchmarks yourself:
```bash
./benchmarks/run_benchmarks.sh  # Takes 10-15 minutes
```

## Type System Reference

SeedStream supports a rich type system for generating diverse data. All types are specified in the `datatype` field of data structure definitions.

### Primitive Types

#### Strings (char)
```yaml
name:
  datatype: char[3..15]  # Random string, 3 to 15 characters (a-zA-Z)
```

#### Integers
```yaml
age:
  datatype: int[18..65]  # Random integer between 18 and 65 (inclusive)
id:
  datatype: int[1..999999]  # 6-digit ID numbers
```

#### Decimals
```yaml
price:
  datatype: decimal[0.01..999.99]  # Price with 2 decimal places
balance:
  datatype: decimal[1000.00..50000.00]  # Account balance
```

#### Booleans
```yaml
is_active:
  datatype: boolean  # true or false (50/50 distribution)
```

#### Dates
```yaml
birth_date:
  datatype: date[1960-01-01..2005-12-31]  # ISO-8601 format
hire_date:
  datatype: date[2020-01-01..2026-12-31]
```

#### Timestamps
```yaml
created_at:
  datatype: timestamp[now-365d..now]  # Supports relative format
updated_at:
  datatype: timestamp[2024-01-01T00:00:00Z..2026-12-31T23:59:59Z]  # ISO-8601
```

#### Enums
```yaml
status:
  datatype: enum[PENDING,ACTIVE,COMPLETED,CANCELLED]  # Comma-separated values
priority:
  datatype: enum[LOW,MEDIUM,HIGH,CRITICAL]
```

### Semantic Types (Datafaker Integration)

Generate realistic context-aware data using Datafaker. These types respect the `geolocation` field for locale-specific data.

**Person & Identity:**
```yaml
data:
  uuid:
    datatype: uuid  # e.g., "ce344f82-baf2-4e17-b871-8808047a09c5"
  name:
    datatype: name  # Full name: "John Smith"
  first_name:
    datatype: first_name  # "John"
  last_name:
    datatype: last_name  # "Smith"
  email:
    datatype: email  # "john.smith@example.com"
  phone_number:
    datatype: phone_number  # "(555) 123-4567"
  ssn:
    datatype: ssn  # Social Security Number (US format)
```

**Location:**
```yaml
data:
  address:
    datatype: address  # "123 Main St, Apt 4B"
  city:
    datatype: city  # "New York"
  state:
    datatype: state  # "California"
  country:
    datatype: country  # "United States"
  postal_code:
    datatype: postal_code  # "90210"
  latitude:
    datatype: latitude  # "37.7749"
  longitude:
    datatype: longitude  # "-122.4194"
```

**Business:**
```yaml
data:
  company:
    datatype: company  # "Tech Solutions Inc."
  industry:
    datatype: industry  # "Information Technology"
  job_title:
    datatype: job_title  # "Senior Software Engineer"
  department:
    datatype: department  # "Engineering"
```

**Internet:**
```yaml
data:
  url:
    datatype: url  # "https://example.com"
  domain:
    datatype: domain  # "example.com"
  ip_address:
    datatype: ip_address  # "192.168.1.100"
  username:
    datatype: username  # "john.smith42"
```

**Finance:**
```yaml
data:
  iban:
    datatype: iban  # "GB82 WEST 1234 5698 7654 32"
  credit_card:
    datatype: credit_card  # "4532-1234-5678-9010"
```

**28 semantic types supported**. See [generators module](generators/) for the complete list.

### Composite Types

#### Nested Objects
```yaml
data:
  billing_address:
    datatype: object[address]  # References address.yaml structure
  company_info:
    datatype: object[company]  # References company.yaml structure
```

The referenced structure files must exist in the `structures_path` directory (default: `config/structures/`).

**Example** - Invoice with nested company objects:
```yaml
name: invoice
geolocation: italy
data:
  invoice_number:
    datatype: int[1..999999]
  issuer:
    datatype: object[company]  # Nested company structure
  recipient:
    datatype: object[company]  # Another nested company
```

#### Arrays
```yaml
data:
  tags:
    datatype: array[char[5..10], 3..8]  # Array of 3-8 strings
  scores:
    datatype: array[int[0..100], 5..15]  # Array of 5-15 integers
  line_items:
    datatype: array[object[line_item], 1..50]  # Array of nested objects
```

**Array syntax**: `array[inner_type, min_length..max_length]`

**Example** - Invoice with variable-length line items:
```yaml
name: invoice
data:
  line_items:
    datatype: array[object[line_item], 1..20]  # 1-20 line items per invoice
    alias: "righe"
```

### Field Aliases

Use `alias` to rename fields in output (useful for internationalization):

```yaml
name: address
geolocation: italy
data:
  name:
    datatype: char[3..15]
    alias: "nome"  # Output field will be "nome" instead of "name"
  city:
    datatype: char[3..40]
    alias: "citta"  # Output: "citta"
  postal_code:
    datatype: int[10000..99999]
    alias: "cap"  # Output: "cap"
```

**Output example:**
```json
{
  "nome": "Mario",
  "citta": "Milano",
  "cap": "20100"
}
```

### Geolocation & Locales

Set `geolocation` at the structure level to generate locale-specific data:

```yaml
name: customer
geolocation: usa  # US English locale
data:
  name:
    datatype: name  # Generates US names: "John Smith"
  phone_number:
    datatype: phone_number  # US format: "(555) 123-4567"
```

**Supported locales** (62 total):
- **Americas**: `usa`, `canada`, `mexico`, `brazil`, `argentina`, `chile`
- **Europe**: `uk`, `ireland`, `france`, `germany`, `italy`, `spain`, `portugal`, `netherlands`, `belgium`, `switzerland`, `austria`, `sweden`, `norway`, `denmark`, `finland`, `poland`, `czech_republic`, `slovakia`, `hungary`, `romania`, `ukraine`, `russia`, `greece`, `turkey`
- **Asia**: `china`, `japan`, `korea`, `india`, `indonesia`, `thailand`, `vietnam`, `malaysia`, `singapore`, `philippines`, `pakistan`, `bangladesh`
- **Middle East**: `saudi_arabia`, `uae`, `israel`
- **Oceania**: `australia`, `new_zealand`
- **Africa**: `south_africa`, `egypt`, `nigeria`, `kenya`

**Fallback**: Unknown geolocations fall back to English (US) with a warning log.

For the complete locale mapping, see [LocaleMapper.java](generators/src/main/java/com/datagenerator/generators/locale/LocaleMapper.java).

## Advanced Topics

### Multi-Threaded Generation

For large datasets, use the `--threads` option to parallelize generation:

```bash
# Use all CPU cores (default)
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --count 1000000"

# Explicit thread count
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --count 1000000 --threads 8"

# Single-threaded (useful for debugging)
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --threads 1"
```

**Automatic Optimization**:
- Jobs < 1000 records: Single-threaded (avoids threading overhead)
- Jobs ≥ 1000 records: Multi-threaded with worker pool

**Thread Safety**:
- Each worker has its own `Random` instance with a derived seed
- ThreadLocal `GeneratorContext` for nested object generation
- Single writer thread ensures ordered output

**Performance Scaling**:
- Linear scaling for primitive types (10M ops/s × N threads)
- Sub-linear scaling for Datafaker (I/O bound, ~20K ops/s regardless of threads)
- Optimal thread count: CPU cores for primitive-heavy data, 2-4× cores for Datafaker-heavy data

### Reproducibility & Determinism

**Guarantee**: Same seed → identical output, byte-for-byte.

**How it works**:
1. Master seed from config or CLI
2. Each worker gets a logical ID (0, 1, 2, ...)
3. Worker seed = `deriveSeed(masterSeed, workerID)`
4. Each worker generates a deterministic subset of records

**Validation**:
```bash
# Generate data twice with same seed
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 12345 --count 1000"
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 12345 --count 1000"

# Verify identical output
shasum -a 256 cli/output/addresses.json
# Both runs produce identical SHA-256 hash
```

**Use cases**:
- Debugging: Reproduce exact data for failed test cases
- Testing: Consistent test data across CI/CD runs
- Compliance: Prove data provenance with seed audit trail
- Benchmarking: Same data for performance A/B tests

### Performance Tuning

**1. File I/O Optimization** (March 2026 updates):
- **Buffer size**: 64KB (default, configurable in FileDestinationConfig)
- **Batch writes**: 1000 records per batch (configurable via `batchSize`)
- **Compression**: Use `compress: true` for 70-80% size reduction (slower writes)

**2. Generator Selection**:
- **Primitive types** (int, char, boolean): 10M+ ops/s
- **Semantic types** (Datafaker): ~20K ops/s (100-500× slower)
- **Trade-off**: Use primitives for volume, semantic types for realism

**3. Data Complexity**:
- **Flat objects** (5 fields): ~100K ops/s
- **Nested objects** (2-3 levels deep): ~10-50K ops/s
- **Arrays** (10-100 elements): ~50K-1M ops/s

**4. Thread Tuning**:
```bash
# Primitive-heavy: Use CPU cores
--threads $(nproc)

# Datafaker-heavy: Use 2-4× cores (I/O bound)
--threads $(($(nproc) * 2))

# Memory-constrained: Reduce threads
--threads 2
```

**5. Format Selection**:
- **JSON**: Larger files, slower serialization (~2.6M ops/s)
- **CSV**: Smaller files, faster serialization (~2.6M ops/s)
- **Tip**: Use CSV for simple tabular data, JSON for nested structures

### Kafka Integration

**Status**: ✅ Fully implemented with comprehensive testing (43 integration tests).

**Configuration example**:
```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092        # Kafka broker(s), comma-separated
  topic: addresses                 # Target topic
  batch_size: 1000                 # Records per batch (default: 100)
  linger_ms: 10                    # Wait time for batching (default: 0)
  compression: gzip                # gzip, snappy, lz4, zstd, none (default: none)
  acks: "all"                      # "0" (no ack), "1" (leader), "all" (all replicas)
  sync: false                      # false=async (default), true=sync
  # Optional SASL/SSL authentication:
  sasl_mechanism: PLAIN            # PLAIN, SCRAM-SHA-256, SCRAM-SHA-512
  security_protocol: SASL_SSL      # PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
  username: ${KAFKA_USERNAME}      # Environment variable reference
  password: ${KAFKA_PASSWORD}      # Environment variable reference
```

**Features**:
- ✅ **Async/sync send modes**: Choose between throughput (async) or reliability (sync)
- ✅ **Batching**: Configurable batch size and linger time for optimal throughput
- ✅ **Compression**: Support for gzip, snappy, lz4, zstd (70-90% size reduction)
- ✅ **SASL/SSL authentication**: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512 mechanisms
- ✅ **Idempotent producer**: Exactly-once semantics with `acks=all`
- ✅ **Error handling**: Retries, timeouts, delivery guarantees
- ✅ **Performance**: Tested with 100K+ records/sec throughput

**Requirements**: Running Kafka instance (local, Docker, or cloud). See config examples in `config/jobs/kafka_*.yaml`.

### Database Integration (Planned)

**Coming in Phase 8:**
- PostgreSQL and MySQL support
- HikariCP connection pooling
- Batch inserts (configurable batch size)
- Auto-table creation from structure definitions
- Complex type mapping (arrays → JSONB, objects → nested columns)
- Transaction management

**Design decisions pending**: Schema auto-creation vs manual, migration strategy

## Troubleshooting

### Common Errors

**1. "No GeneratorContext active"**

**Cause**: You're using `ObjectGenerator` in a custom multi-threaded setup without initializing context per thread.

**Solution**: Wrap generation code in `GeneratorContext.enter()`:
```java
try (var ctx = GeneratorContext.enter(factory, geolocation)) {
    generator.generate(random, objectType);
}
```

**2. "Circular reference detected: A → B → A"**

**Cause**: Your structure definitions have circular dependencies.

**Example**:
```yaml
# user.yaml
data:
  profile:
    datatype: object[profile]

# profile.yaml
data:
  user:
    datatype: object[user]  # ❌ Circular!
```

**Solution**: Redesign structures to avoid cycles. Use primitive types or terminate recursion.

**3. "Seed resolution failed: Remote API returned 404"**

**Cause**: Remote seed API endpoint is unreachable or misconfigured.

**Solution**:
- Check `url` in seed configuration
- Verify authentication credentials
- Test API manually: `curl -H "Authorization: Bearer TOKEN" https://seed-api.example.com/api/seed`
- Use `--seed` CLI override as fallback

**4. "Failed to parse data structure: Unknown datatype 'xyz'"**

**Cause**: Typo or unsupported datatype in structure definition.

**Solution**: Check spelling against [Type System Reference](#type-system-reference). Available types:
- Primitives: `char`, `int`, `decimal`, `boolean`, `date`, `timestamp`, `enum`
- Semantic: `uuid`, `name`, `email`, `phone_number`, `address`, `city`, `company`, etc.
- Composite: `object[...]`, `array[...]`

**5. "FileNotFoundException: config/structures/address.yaml"**

**Cause**: Referenced structure file doesn't exist.

**Solution**: Ensure file exists at the path relative to the job configuration. Check:
- File name matches exactly (case-sensitive on Linux)
- File is in the `structures_path` directory
- Default `structures_path` is `config/structures/`

### Debug Mode

Enable verbose logging for troubleshooting:

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose"
```

**Output includes**:
- Seed resolution details
- File paths (absolute)
- Progress updates every 100 records
- Throughput metrics
- Worker thread activity

**For deeper debugging**, add JVM debug flags:
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose" \
  --debug-jvm
```

### Performance Issues

**Symptom**: Generation slower than expected

**Diagnostics**:
1. **Check data complexity**: Semantic types (Datafaker) are 100-500× slower than primitives
2. **Measure with benchmarks**: Run `./benchmarks/run_benchmarks.sh` to baseline hardware
3. **Profile with JMH**: Add custom benchmarks for your specific data structures
4. **Monitor threads**: Use `--verbose` to see worker activity

**Common causes**:
- Too many Datafaker fields (use primitives where possible)
- Deeply nested objects (flatten structures)
- Small batch sizes for Kafka/DB (increase `batch_size`)
- Disk I/O bottleneck (check with `iostat`, consider compression off)

### FAQ

**Q: Can I generate data without a seed?**  
A: Yes, but you'll get a warning. Default seed (0) is used. For production, always specify a seed.

**Q: How do I generate different data each run?**  
A: Use a random seed or timestamp:
```bash
--seed $(date +%s)  # Unix timestamp
--seed $RANDOM      # Random number
```

**Q: What's the maximum array size?**  
A: No hard limit, but large arrays (> 1000 elements) may impact performance and memory. Consider streaming for very large arrays.

**Q: Can I use custom Datafaker providers?**  
A: Not yet. Planned for future releases. Current providers: 28 semantic types.

**Q: How do I generate date ranges relative to today?**  
A: Use relative timestamp syntax:
```yaml
created_at:
  datatype: timestamp[now-30d..now]  # Last 30 days
```

**Q: What's the difference between `char` and `name`?**  
A: `char` generates random alphanumeric strings (e.g., "AbCdEf"). `name` uses Datafaker to generate realistic person names (e.g., "John Smith").

**Q: Can I contribute new generators or destinations?**  
A: Yes! See [CONTRIBUTING.md](CONTRIBUTING.md) (coming soon) for guidelines. We welcome PRs for:
- New semantic types (e.g., vehicle VIN, ISBN)
- New destinations (e.g., S3, Azure Blob, Elasticsearch)
- New formats (e.g., Avro, Parquet)
- Performance optimizations

**Q: Is there a REST API?**  
A: Not yet. Planned for Phase 9. Current interface is CLI only.

**Q: How do I handle sensitive data (PII)?**  
A: All generated data is **synthetic** and not real PII. However:
- Store seeds securely (they can reproduce data)
- Use encryption for data at rest
- Follow your organization's data governance policies

## Roadmap

**Current Version**: v0.2.0 (March 2026)

**Phase 6** - Performance Validation: ✅ **COMPLETE**
- ✅ JMH benchmarks (TASK-026) - NFR-1 validated
- ✅ File I/O optimization (600-800 MB/s achieved)
- ✅ Memory profiling (TASK-027) - No leaks, linear scaling
- ✅ Integration tests (TASK-022-025) - 43 tests passing

**Phase 7** - Documentation: ✅ **COMPLETE**
- ✅ README completion (TASK-028)
- ✅ Example configurations (TASK-029)
- ✅ JavaDoc completion (TASK-030)

**Phase 8 (Future)** - Database & Advanced Features:
- 📋 Database destination adapter (PostgreSQL, MySQL)
- 📋 Reference generator for foreign keys
- 📋 Protobuf and Avro formats
- 📋 Statistical distributions
- 📋 REST/gRPC API module

**Phase 9 (Long-term)** - Enterprise Features:
- 📋 Schema registry integration (Confluent, AWS Glue)
- 📋 Data masking and anonymization
- 📋 Plugin marketplace
- 📋 Metrics and monitoring (Prometheus, Grafana)
- 📋 Web UI

See [BACKLOG.md](docs/internal/BACKLOG.md) for detailed task tracking (internal).

## Documentation

Comprehensive documentation is available in the `docs/` directory:

**Getting Started:**
- **[README.md](README.md)** - This file: Overview, installation, quickstart, type system reference
- **[config/README.md](config/README.md)** - Configuration guide: data structures, job definitions, examples

**Architecture & Design:**
- **[DESIGN.md](docs/DESIGN.md)** - Architecture, design decisions, multi-threading model, extensibility
- **[PERFORMANCE.md](docs/PERFORMANCE.md)** - Benchmarks, tuning guide, hardware recommendations

**Contributing:**
- **[CONTRIBUTING.md](docs/CONTRIBUTING.md)** - Contributor guide: workflow, standards, PR process, style guide
- **[QUALITY.md](docs/QUALITY.md)** - Code quality tools: Spotless, JaCoCo, SpotBugs configuration

**Additional Resources:**
- **[CHANGELOG.md](CHANGELOG.md)** - Version history, release notes, and roadmap
- **[AGENTIC-PLATFORM-DISCUSSION.md](AGENTIC-PLATFORM-DISCUSSION.md)** - AI-assisted development discussion
- **[benchmarks/README.md](benchmarks/README.md)** - Benchmark execution guide

**Internal Planning** (for project contributors):
- **[docs/internal/](docs/internal/)** - Requirements, backlog, memory profiling, internal notes

## Contributing

Contributions are welcome! Whether bug reports, feature requests, or pull requests, we appreciate your help.

**Quick Start:**
```bash
git clone https://github.com/mferretti/SeedStream.git
cd SeedStream
./gradlew build test
```

**Before submitting PRs:**
- Run `./gradlew spotlessApply` (code formatting)
- Ensure tests pass: `./gradlew test`
- Maintain 70%+ code coverage
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

For comprehensive contributor guidelines (workflow, code standards, PR process, testing), see **[CONTRIBUTING.md](docs/CONTRIBUTING.md)**.

## License

Copyright 2024-2026 Marco Ferretti

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
