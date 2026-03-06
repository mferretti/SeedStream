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
[Architecture & Design](docs/DESIGN.md) · [Code Quality Guide](docs/QUALITY.md) · [Roadmap & Backlog](docs/BACKLOG.md) · [Performance Benchmarks](benchmarks/README.md) · [License Discussion](docs/LICENSE-DISCUSSION.md)

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

For technical details, see [DESIGN.md](docs/DESIGN.md).

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

## Performance Benchmarks

The project includes comprehensive JMH (Java Microbenchmark Harness) benchmarks to measure and validate performance across all critical paths:

- **Primitive Generators**: Validates NFR-1 requirement (10M ops/s for in-memory generation)
- **Datafaker Generators**: Measures realistic data generation performance
- **Composite Generators**: Tests nested objects and arrays
- **Serializers**: JSON and CSV formatting throughput
- **Destinations**: File I/O and write pipeline performance

**Note**: Benchmarks are **not run automatically** with `./gradlew test` because they take 10-15 minutes to complete. They must be executed explicitly.

### Running Benchmarks

**Quick Start** (one command):
```bash
./benchmarks/run_benchmarks.sh
```

This will run all benchmarks and generate a `BENCHMARK-RESULTS.md` report with formatted results and NFR-1 compliance validation.

**Manual execution**:
```bash
# Run benchmarks only
./gradlew :benchmarks:jmh

# Generate formatted report
python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md
```

For detailed benchmark documentation, configuration options, and result interpretation, see **[benchmarks/README.md](benchmarks/README.md)**.

### Validated Performance Numbers (March 2026)

**✅ NFR-1 Compliance**: All primitive generators exceed 10M ops/s requirement

**Primitive Generation:**
- Boolean: **258M ops/s** (25.8× target)
- Integer: **57M ops/s** (5.7× target)
- String (char): **12M ops/s** (1.2× target)
- Timestamp: **4.5M ops/s**
- Decimal (BigDecimal): **3.0M ops/s**
- Date (LocalDate): **2.4M ops/s**

**Realistic Data (Datafaker):**
- Company names: **154K ops/s**
- Email addresses: **24K ops/s**
- Person names: **23K ops/s**
- Addresses: **18K ops/s**
- Cities: **14K ops/s**
- Phone numbers: **13K ops/s**

**Composite Generators:**
- Simple objects (5 fields): **117K ops/s**
- Small arrays (10 elements): **5.8M ops/s**
- Large arrays (100 elements): **721K ops/s**

**Serialization:**
- JSON (simple record): **2.6M ops/s**
- JSON (complex record): **946K ops/s**
- JSON (nested structures): **580K ops/s**
- CSV (simple record): **2.6M ops/s**

**File I/O (Optimized March 2026):**
- Raw BufferedWriter: **4.7M ops/s**
- FileDestination (with batching): **Expected 2-3M ops/s** (600-800 MB/s)

**Real-World Performance**:
- 100,000 complex customer records with Datafaker: **6,923 records/sec** (10 threads, 14.4 seconds)
- 1M simple primitive records: **100,000+ records/sec** (single thread)

**Hardware**: Results from development machine (specs vary). Your mileage may vary based on CPU, disk speed, and data complexity.

For detailed analysis and optimization decisions, see **[benchmarks/PERFORMANCE-ANALYSIS.md](benchmarks/PERFORMANCE-ANALYSIS.md)**.

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

### Kafka Integration (Coming Soon)

**Configuration example**:
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
  compression: gzip  # gzip, snappy, lz4, zstd
  auth: sasl_ssl
  sasl_mechanism: PLAIN
  security_protocol: SASL_SSL
  username: ${KAFKA_USERNAME}
  password: ${KAFKA_PASSWORD}
```

**Features**:
- Async/sync send modes
- Batching and compression
- SASL/SSL authentication
- Idempotent producer (exactly-once semantics)
- Configurable acks, retries, timeouts

**Status**: ✅ Implemented, tests passing. Requires running Kafka instance.

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

**Current Version**: v0.2-alpha (March 2026)

**Phase 6 (Current)** - Performance Validation:
- ✅ JMH benchmarks (TASK-026)
- ✅ File I/O optimization (600-800 MB/s achieved)
- 🔄 Integration tests (TASK-022-025)

**Phase 7 (Next)** - Documentation:
- 🔄 README completion (TASK-028) - **IN PROGRESS**
- 📋 Example configurations (TASK-029)
- 📋 JavaDoc completion (TASK-030)

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

See [BACKLOG.md](docs/BACKLOG.md) for detailed task tracking.

## Contributing

Contributions are welcome! Whether it's bug reports, feature requests, or pull requests, we appreciate your help.

**Before contributing:**
1. Check [BACKLOG.md](docs/BACKLOG.md) for planned features
2. Open an issue to discuss major changes
3. Read [DESIGN.md](docs/DESIGN.md) for architectural context
4. Follow [QUALITY.md](docs/QUALITY.md) for code standards

**Development setup:**
```bash
# Clone repository
git clone https://github.com/mferretti/SeedStream.git
cd SeedStream

# Build and test
./gradlew build test

# Run code formatting
./gradlew spotlessApply

# Run static analysis
./gradlew spotbugsMain
```

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
