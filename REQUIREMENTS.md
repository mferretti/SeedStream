# SeedStream - System Requirements Document

**Version**: 1.1  
**Last Updated**: February 21, 2026  
**Status**: Living Document  
**Purpose**: Comprehensive requirements specification for multi-developer collaboration

**Implementation Progress**: ✅ 9/16 Functional Requirements Complete (56%)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Project Vision & Objectives](#project-vision--objectives)
3. [Functional Requirements](#functional-requirements)
4. [Non-Functional Requirements](#non-functional-requirements)
5. [System Architecture](#system-architecture)
6. [Data Model & Type System](#data-model--type-system)
7. [Configuration Specification](#configuration-specification)
8. [API & Interfaces](#api--interfaces)
9. [Security & Compliance](#security--compliance)
10. [Quality Standards](#quality-standards)
11. [Development Standards](#development-standards)
12. [Implementation Phases](#implementation-phases)
13. [Success Criteria](#success-criteria)
14. [Constraints & Assumptions](#constraints--assumptions)
15. [Glossary](#glossary)

---

## 1. Executive Summary

**SeedStream** is a high-performance, enterprise-grade test data generator for Java applications. It generates large volumes of realistic, reproducible test data to multiple destinations (Kafka, databases, files) using declarative YAML-based configuration.

### Current Implementation Status (v0.2 - March 2026)

**Completed Core Features:**
- ✅ Primitive data types (char, int, decimal, boolean, date, timestamp, enum)
- ✅ Composite types (objects, arrays with variable length)
- ✅ Seed management (embedded, file, env, remote with auth)
- ✅ Deterministic randomization (verified SHA-256 identical output)
- ✅ YAML configuration (structures and jobs)
- ✅ JSON serialization (NDJSON, field aliases, 16 tests)
- ✅ CSV serialization (RFC 4180, always-quoted, 17 tests)
- ✅ File destination (NIO, gzip compression, append mode, 16 tests)
- ✅ Kafka destination (async/sync, SASL/SSL, batching, 8 tests)
- ✅ Multi-threading engine (worker pool, backpressure, 7 tests)
- ✅ Datafaker integration (realistic data, 62+ locales)
- ✅ CLI interface (Picocli with --job, --format, --count, --seed, --threads, --verbose)

**Deferred to Phase 8:**
- ⏸️ Database destination with JDBC and HikariCP (requires careful design)
- ⏸️ Reference generator for foreign keys

**Test Coverage**: 267 tests passing across 6 modules (75%+ coverage)

### Key Differentiators

- **Deterministic Reproducibility**: Same seed = identical data across all runs
- **Multi-threaded Performance**: Millions of records per second with thread-safe generation
- **Locale-Aware Data**: Realistic data for 62+ geolocations (names, addresses, phones in native formats)
- **Extensible Architecture**: Pluggable destinations, formats, and generators
- **Zero-Code Configuration**: Pure YAML definitions for complex nested structures

### Target Users

1. **QA Engineers**: Generate consistent test datasets for functional testing
2. **Performance Engineers**: Create large-scale data for load testing
3. **Data Engineers**: Populate development/staging databases
4. **DevOps Teams**: Automate test environment provisioning

---

## 2. Project Vision & Objectives

### 2.1 Vision Statement

Create the de-facto standard for enterprise test data generation in the Java ecosystem, enabling teams to generate production-like data with zero code and full reproducibility.

### 2.2 Business Objectives

- **BO-1**: Reduce test data preparation time by 80% (vs manual SQL scripts)
- **BO-2**: Enable reproducible test failures through deterministic data generation
- **BO-3**: Support 100+ million records/hour on commodity hardware (16-core machine)
- **BO-4**: Achieve 90% customer satisfaction in open-source adoption (GitHub stars/feedback)

### 2.3 Technical Objectives

- **TO-1**: 100% reproducible output (same seed → identical data, byte-for-byte)
- **TO-2**: Linear scaling with CPU cores (multi-threaded generation)
- **TO-3**: Memory-efficient streaming (generate → serialize → send, no intermediate buffers)
- **TO-4**: Sub-second startup time for job execution
- **TO-5**: 70%+ unit test coverage, 90%+ integration test coverage

---

## 3. Functional Requirements

### 3.1 Data Generation (Core)

#### FR-1: Primitive Data Types
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL generate the following primitive types with configurable ranges:

| Type | Syntax | Example | Description |
|------|--------|---------|-------------|
| String | `char[min..max]` | `char[3..50]` | Random alphanumeric string |
| Integer | `int[min..max]` | `int[1..999]` | Random integer in range |
| Decimal | `decimal[min..max]` | `decimal[0.0..999.99]` | Random decimal with scale preservation |
| Boolean | `boolean` | `boolean` | Random true/false (50/50 distribution) |
| Date | `date[start..end]` | `date[2020-01-01..2025-12-31]` | Random date in ISO-8601 format |
| Timestamp | `timestamp[start..end]` | `timestamp[now-30d..now]` | Random instant with relative syntax |
| Enum | `enum[values]` | `enum[ACTIVE,INACTIVE,PENDING]` | Random selection from comma-separated values |

**Acceptance Criteria**:
- All types support reproducible generation (same seed → same values)
- Range validation at parse time (fail fast if min > max)
- Overflow protection for integer types
- Scale preservation for decimal types (e.g., `decimal[0.00..100.00]` always has 2 decimal places)

**Test Cases**: 29 unit tests passing (CharGenerator, IntegerGenerator, DecimalGenerator, BooleanGenerator, DateGenerator, TimestampGenerator, EnumGenerator)

---

#### FR-2: Composite Data Types
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL support nested and composite structures:

##### FR-2.1: Nested Objects
```yaml
# Syntax: object[structure_name]
address:
  datatype: object[address]  # References structures/address.yaml
```

**Requirements**:
- Auto-load referenced structure from `structures_path` (default: `config/structures/`)
- Recursive nesting support (objects within objects)
- Circular reference detection (fail fast with descriptive error)
- Thread-safe generation with StructureRegistry

**Acceptance Criteria**:
- ✅ Load nested structures from separate YAML files
- ✅ Detect circular references (A → B → A) at parse time
- ✅ Generate nested objects recursively
- 12 unit tests passing (ObjectGenerator)

---

##### FR-2.2: Variable-Length Arrays
```yaml
# Syntax: array[inner_type, min..max]
tags:
  datatype: array[char[1..20], 1..10]  # 1-10 strings

line_items:
  datatype: array[object[line_item], 1..50]  # 1-50 nested objects
```

**Requirements**:
- Deterministic array length generation (same seed → same length)
- Support any inner type (primitives, enums, objects, nested arrays)
- Memory-efficient generation (stream large arrays to destination)

**Acceptance Criteria**:
- ✅ Generate arrays with reproducible lengths [min..max]
- ✅ Support nested arrays (e.g., `array[array[int[1..10], 2..5], 3..8]`)
- ✅ Thread-safe generation with recursive inner type generation
- 11 unit tests passing (ArrayGenerator)

---

#### FR-3: Realistic Data (Datafaker Integration)
**Priority**: P1 (High)  
**Status**: ✅ Implemented

The system SHALL generate realistic locale-specific data using Datafaker library:

**Supported Locales**: 62 locales (en, it, es, fr, de, pt, ru, zh, ja, ko, ar, etc.)

**Core Data Providers** (~40 everyday types):

| Category | Types | Examples |
|----------|-------|----------|
| **Person** | name, first_name, last_name, full_name, username, email, phone_number, password, title, occupation | `John Doe`, `john.doe@example.com`, `+1-555-123-4567` |
| **Address** | address, street_name, street_number, city, state, postal_code, country, coordinates (lat/long) | `123 Main St, New York, NY 10001` |
| **Finance** | bank, company, credit_card_number, cvv, iban, bitcoin_address, price, currency | `1234-5678-9012-3456`, `€123.45` |
| **DateTime** | date, time, datetime, timestamp, century, day_of_week, month, year, timezone, duration | `2025-01-20T14:30:00Z` |
| **Internet** | domain, url, ip_v4, ip_v6, mac_address, user_agent, http_method, http_status_code | `example.com`, `192.168.1.1` |
| **Commerce** | product_name, department, price, promotion_code, color, material | `Wireless Mouse`, `Electronics`, `$29.99` |
| **Business** | company, industry, buzzword, catch_phrase, bs (business speak) | `Acme Corp`, `synergize vertical markets` |
| **Code** | isbn, ean, imei, issn, barcode | `978-3-16-148410-0` |

**Implementation Plan**:
1. Extend `PrimitiveType.Kind` enum with semantic types (NAME, EMAIL, PHONE, ADDRESS, etc.)
2. Create `DatafakerGenerator` using `net.datafaker.Faker` with locale support
3. Pass geolocation context from YAML config → type system → generator
4. Map semantic types to Datafaker providers (NAME → `faker.name().fullName()`)
5. Update `TypeParser` to support semantic type syntax (no brackets): `name`, `email`, `address`
6. Update `DataStructureParser` to propagate geolocation to field types

**Syntax Example**:
```yaml
name: user
geolocation: italy
data:
  name:
    datatype: name          # Uses Datafaker with Italian locale
    alias: "nome"
  email:
    datatype: email         # Uses Datafaker email generator
    alias: "email"
  phone:
    datatype: phone_number  # Italian format: +39-xxx-xxxxxxx
    alias: "telefono"
```

**Acceptance Criteria**:
- Support all 40 core Datafaker providers
- Locale-specific output (Italian names for `geolocation: italy`)
- Deterministic generation (same seed → same realistic data)
- Fallback to English for unsupported locales with warning log

**Test Requirements**:
- Unit tests for each Datafaker provider
- Locale-specific output validation (e.g., Italian names have correct charset)
- Seed determinism tests (verify reproducibility)

---

#### FR-4: Foreign Key References (Future)
**Priority**: P2 (Medium)  
**Status**: 🔄 Deferred

The system SHALL support foreign key references between structures:

```yaml
# Syntax: ref[structure.field]
orders:
  user_id:
    datatype: ref[user.id]  # References generated user IDs
```

**Requirements**:
- Track generated IDs from referenced structures
- Random sampling from ID pool for cross-record references
- Order-aware generation (users before orders)

**Design Considerations**:
- **Option A**: Two-pass generation (generate users → store IDs → generate orders)
- **Option B**: LRU cache of recent IDs (memory-bounded random sampling)
- **Option C**: Explicit ID pools (user defines range, generator samples from pool)

**Current Decision**: Option C (most flexible, no memory concerns)

**Acceptance Criteria**: TBD (when implemented)

---

### 3.2 Seed Management

#### FR-5: Seed Configuration Types
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL support four seed sources with priority resolution:

**Resolution Order**: CLI `--seed` > YAML config > default (0 with warning)

##### FR-5.1: Embedded Seed
```yaml
seed:
  type: embedded
  value: 12345
```

**Requirement**: Read seed value directly from YAML configuration.

**Acceptance Criteria**: ✅ Parse and resolve embedded seed value (6 tests passing)

---

##### FR-5.2: File-Based Seed
```yaml
seed:
  type: file
  path: /secrets/seed.txt
```

**Requirements**:
- Read seed from filesystem (absolute or relative path)
- Trim whitespace from file content
- Parse long value (fail fast on invalid format)

**Acceptance Criteria**: ✅ Read seed from file with error handling (5 tests passing)

---

##### FR-5.3: Environment Variable Seed
```yaml
seed:
  type: env
  name: DATA_SEED
```

**Requirements**:
- Read seed from environment variable
- Fail fast if variable not set
- Parse long value from string

**Acceptance Criteria**: ✅ Resolve seed from environment with validation (4 tests passing)

---

##### FR-5.4: Remote API Seed
```yaml
seed:
  type: remote
  url: https://seed-service.example.com/api/seed
  auth:
    type: bearer  # or: basic, api_key
    token: ${API_TOKEN}
```

**Requirements**:
- Fetch seed from HTTP endpoint via GET request
- Support three authentication methods:
  1. **Bearer Token**: `Authorization: Bearer <token>`
  2. **Basic Auth**: `Authorization: Basic <base64(username:password)>`
  3. **API Key**: `X-API-Key: <key>`
- Parse long value from response body (plain text or JSON)
- 10-second connection timeout
- Lazy HttpClient initialization (avoid resource waste for non-remote seeds)

**Acceptance Criteria**: ✅ HTTP fetch with auth handling and lazy initialization (4 tests passing)

---

#### FR-6: Deterministic Randomization
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL guarantee bit-identical output across runs for the same seed:

**Requirements**:
1. **Master Seed**: Single seed value for entire job (from config or CLI)
2. **Logical Worker IDs**: Sequential IDs (0, 1, 2, ...) assigned to generation threads
3. **Derived Seeds**: Each worker gets unique seed via `deriveSeed(masterSeed, workerId)`
4. **Thread-Local Random**: Each worker has isolated `java.util.Random` instance

**Seed Derivation Algorithm**:
```java
long deriveSeed(long masterSeed, int workerId) {
    long seed = masterSeed;
    seed ^= workerId;       // Mix in worker ID
    seed ^= (seed << 21);   // Bit avalanche
    seed ^= (seed >>> 35);  // Spread high bits
    seed ^= (seed << 4);    // Final mixing
    return seed;
}
```

**Guarantees**:
- ✅ Same master seed → same worker seeds → identical data
- ✅ Independent of JVM thread IDs (which vary across runs)
- ✅ Thread-safe (no shared mutable state)
- ✅ Fast (simple XOR operations)

**Acceptance Criteria**:
- ✅ Two runs with same seed produce byte-identical output
- ✅ Logical worker IDs are sequential (0, 1, 2, ...)
- ✅ Derived seeds are distinct (Hamming distance validation)
- 11 unit tests passing (RandomProvider)

---

### 3.3 Configuration Management

#### FR-7: Data Structure Definitions
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL parse YAML data structure definitions:

**Structure**:
```yaml
name: <structure_name>
geolocation: <locale>  # Optional: italy, usa, france, etc.
data:
  <field_name>:
    datatype: <type_syntax>
    alias: "<output_name>"  # Optional field renaming
```

**Requirements**:
- Validate `name` field (required, non-empty)
- Validate `data` section (required, at least one field)
- Parse `datatype` using TypeParser (fail fast on syntax errors)
- Support optional `alias` for field renaming in output
- Support optional `geolocation` for locale-specific data generation

**Acceptance Criteria**: ✅ Parse complex structures with nested objects and arrays (6 tests passing)

**Example**: See [config/structures/invoice.yaml](config/structures/invoice.yaml)

---

#### FR-8: Job Definitions
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL parse YAML job definitions:

**Structure**:
```yaml
source: <structure_file.yaml>
type: <destination_type>  # kafka, file, database
seed:
  type: <seed_type>       # embedded, file, env, remote
  # ... type-specific seed config
conf:
  # ... destination-specific configuration
```

**Requirements**:
- Validate `source` field (required, references structure file)
- Validate `type` field (required, one of: kafka, file, database)
- Parse `seed` section (required, delegate to SeedConfig subtypes)
- Parse `conf` section (destination-specific, delegate to typed parsers)
- Support optional `structures_path` for nested structure loading (default: `config/structures/`)

**Acceptance Criteria**: ✅ Parse job definitions for all destination types with seed validation (9 tests passing)

**Examples**:
- Kafka: [config/jobs/kafka_address.yaml](config/jobs/kafka_address.yaml)
- File: [config/jobs/file_address.yaml](config/jobs/file_address.yaml)

---

### 3.4 Output Destinations

#### FR-9: File Destination
**Priority**: P1 (High)  
**Status**: ✅ Implemented

The system SHALL write generated data to filesystem:

**Configuration**:
```yaml
type: file
conf:
  path: output/addresses  # Base output path
  compression: gzip       # Optional: none, gzip, bzip2
  append: false           # Optional: append vs overwrite
```

**Requirements**:
- Use Java NIO for fast I/O (FileChannel, MappedByteBuffer)
- Support multiple formats (JSON, CSV, Protobuf)
- Optional compression (transparent to format serializers)
- Append mode for incremental data generation
- Atomic writes (temp file + rename for crash safety)

**File Naming**:
- Single file: `{path}.{format}` (e.g., `output/addresses.json`)
- Multiple files (large jobs): `{path}-{partition}.{format}` (e.g., `output/addresses-00001.json`)

**Acceptance Criteria**:
- ✅ Write JSON/CSV to filesystem (16 tests passing)
- ✅ Support gzip compression
- ✅ Append mode preserves existing data
- ✅ Buffer management with configurable buffer sizes (default: 64KB)
- ✅ Automatic parent directory creation
- ✅ Proper resource cleanup with AutoCloseable

**Performance Target**: 500 MB/s write throughput on SSD

---

#### FR-10: Kafka Destination
**Priority**: P1 (High)  
**Status**: 🔄 Planned

The system SHALL publish generated data to Apache Kafka:

**Configuration**:
```yaml
type: kafka
conf:
  bootstrap: localhost:9092
  topic: addresses
  auth: sasl_ssl          # Optional: none, sasl_ssl, ssl
  cert: path/to/cert.crt  # Optional: SSL certificate
  username: kafka_user    # Optional: SASL username
  password: path/to/pass  # Optional: SASL password
  batch_size: 1000        # Records per batch (default: 100)
  compression: lz4        # Optional: none, gzip, snappy, lz4, zstd
```

**Requirements**:
- Use `org.apache.kafka:kafka-clients` library
- Support authentication: none, SASL/SSL, SSL
- Producer connection pooling (reuse across batches)
- Configurable batching for throughput optimization
- Message key support (for partitioning)
- Error handling with retry logic (transient failures)

**Acceptance Criteria**:
- Publish records to Kafka with SASL/SSL auth
- Batch records for performance (configurable batch size)
- Retry transient errors (network issues, broker failover)
- Clean shutdown (flush all pending records)

**Performance Target**: 100,000 records/second on 16-core machine

---

#### FR-11: Database Destination
**Priority**: P1 (High)  
**Status**: 🔄 Planned

The system SHALL write generated data to relational databases:

**Configuration**:
```yaml
type: database
conf:
  jdbc_url: jdbc:postgresql://localhost:5432/testdb
  username: dbuser
  password: path/to/password
  table: addresses
  batch_size: 1000       # Rows per batch insert (default: 100)
  pool_size: 10          # HikariCP connection pool size
```

**Requirements**:
- Use JDBC with HikariCP connection pooling
- Support PostgreSQL and MySQL (drivers as `compileOnly`, users provide at runtime)
- Batch inserts for performance (JDBC `PreparedStatement.addBatch()`)
- Auto-create tables from data structure definition (optional)
- Transaction management (commit per batch)

**Acceptance Criteria**:
- Insert records via JDBC batch inserts
- Connection pooling with HikariCP
- Support PostgreSQL and MySQL
- Optional auto-table-creation (DDL generation from structure)

**Performance Target**: 50,000 inserts/second with batch inserts

---

### 3.5 Output Formats

#### FR-12: JSON Serialization
**Priority**: P1 (High)  
**Status**: ✅ Implemented

The system SHALL serialize data to JSON format:

**Format**: Newline-Delimited JSON (NDJSON)

**Example**:
```json
{"nome": "Mario", "cognome": "Rossi", "via": "Via Roma", "n.": 123}
{"nome": "Luigi", "cognome": "Bianchi", "via": "Via Milano", "n.": 456}
```

**Requirements**:
- Use Jackson for JSON serialization
- Support field aliases (output field names from `alias` config)
- Newline-delimited format for streaming (one record per line)
- UTF-8 encoding
- Escape special characters (quotes, newlines, backslashes)

**Acceptance Criteria**:
- ✅ Serialize primitives, objects, arrays to JSON (16 tests passing)
- ✅ Apply field aliases from structure definition
- ✅ NDJSON format (one JSON object per line)
- ✅ Valid JSON per RFC 8259
- ✅ Compact output (no whitespace)
- ✅ ISO-8601 date formatting
- ✅ Proper null handling

---

#### FR-13: CSV Serialization
**Priority**: P1 (High)  
**Status**: ✅ Implemented

The system SHALL serialize data to CSV format:

**Example**:
```csv
nome,cognome,via,n.
Mario,Rossi,Via Roma,123
Luigi,Bianchi,Via Milano,456
```

**Requirements**:
- Header row with field names (aliases if configured)
- RFC 4180 compliance (quoted fields with commas/newlines/quotes)
- UTF-8 encoding
- Flatten nested objects (dot notation: `address.city`)
- Expand arrays (one column per element or JSON-encode)

**Acceptance Criteria**:
- ✅ Serialize primitives to CSV with header row (17 tests passing)
- ✅ Quote all fields for RFC 4180 compliance
- ✅ Escape quotes by doubling (" → "")
- ✅ Handle special characters (commas, newlines, quotes)
- ✅ Serialize nested objects as JSON strings
- ✅ Serialize arrays as JSON strings
- ✅ Proper field ordering matching data structure

---

#### FR-14: Protobuf Serialization (Future)
**Priority**: P2 (Medium)  
**Status**: 🔄 Planned

The system SHALL serialize data to Protocol Buffers format:

**Requirements**:
- Generate `.proto` schema from data structure definition
- Compile schema to Java classes at runtime (protoc)
- Serialize data using generated classes
- Support all Protobuf scalar types (int32, int64, double, string, bool, bytes)
- Support nested messages and repeated fields

**Acceptance Criteria**: TBD

---

### 3.6 Command-Line Interface

#### FR-15: Job Execution Command
**Priority**: P0 (Critical)  
**Status**: ✅ Implemented

The system SHALL provide CLI for job execution:

**Command Syntax**:
```bash
cli execute --job <path> [--format <format>] [--count <n>] [--seed <value>]
```

**Parameters**:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--job` | Yes | - | Path to job YAML file |
| `--format` | No | `json` | Output format (json, csv, protobuf) |
| `--count` | No | `100` | Number of records to generate |
| `--seed` | No | From config or 0 | Override seed value |

**Requirements**:
- Parse CLI arguments using Picocli library
- Validate job file exists and is readable
- Override seed if `--seed` provided (takes precedence over config)
- Display progress bar during generation (optional, configurable)
- Exit codes: 0 (success), 1 (validation error), 2 (runtime error)

**Acceptance Criteria**:
- ✅ Execute job with default parameters (format=json, count=100)
- ✅ Override format, count, seed via CLI
- ✅ Fail fast on invalid parameters (non-existent file, invalid format, etc.)
- ✅ Display generation statistics (records/second, elapsed time)
- ✅ Picocli-based command framework with @Command annotations
- ✅ Smart path resolution for structure files (supports sibling directories)
- ✅ Proper exit codes on success/failure

**Example**:
```bash
# Default execution
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml"

# Custom parameters
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format csv --count 50000 --seed 99999"
```

---

#### FR-16: Verbose Logging
**Priority**: P1 (High)  
**Status**: ✅ Implemented

The system SHALL support verbose logging modes:

**Log Levels**:
- **ERROR**: User-facing errors (invalid config, runtime failures)
- **WARN**: Degraded performance (fallback to default seed, missing optional config)
- **INFO**: Job lifecycle (start, progress, completion)
- **DEBUG**: Internal details (seed derivation, thread allocation, batch flushes)

**CLI Flags**:
```bash
--verbose    # Enable INFO level
--debug      # Enable DEBUG level
--quiet      # Suppress all output except errors
```

**Requirements**:
- Use SLF4J with Logback as implementation
- Default log level: WARN
- Log to stderr for CLI output
- Include timestamp, thread ID, logger name in debug mode

**Acceptance Criteria**:
- ✅ Default: INFO level (job progress and statistics)
- ✅ `--verbose`: DEBUG level (detailed internals)
- ✅ SLF4J with Logback implementation
- ✅ Structured logging with logger names
- ✅ Hibernate Validator logging for configuration validation

---

## 4. Non-Functional Requirements

### 4.1 Performance

#### NFR-1: Throughput
**Priority**: P0 (Critical)

The system SHALL achieve the following throughput targets:

| Scenario | Target | Hardware | Measurement |
|----------|--------|----------|-------------|
| **In-memory generation** | 10 million records/second | 16-core machine | 1,000,000 records with primitives only |
| **File output (JSON)** | 500 MB/second | SSD storage | Write 10 GB file |
| **Kafka output** | 100,000 records/second | Local broker, 16 cores | Publish 1,000,000 records |
| **Database output** | 50,000 inserts/second | PostgreSQL, batch inserts | Insert 1,000,000 rows |

**Measurement Method**: JMH benchmarks with warmup and averaging

**Acceptance Criteria**:
- Meet or exceed all throughput targets
- Linear scaling with CPU cores (up to 16 cores)
- No memory leaks during 1-hour generation runs

---

#### NFR-2: Latency
**Priority**: P1 (High)

The system SHALL meet the following latency targets:

| Operation | Target | Percentile | Measurement |
|-----------|--------|------------|-------------|
| **Job startup** | < 1 second | p50 | Time from CLI invocation to first record generated |
| **Seed resolution** | < 100 ms | p95 | Embedded/file/env seeds |
| **Remote seed fetch** | < 500 ms | p95 | HTTP API call with bearer auth |
| **Type parsing** | < 50 ms | p95 | Parse complex nested structure (invoice example) |

**Acceptance Criteria**:
- Meet latency targets at specified percentiles
- No blocking operations on hot path (generation loop)
- Lazy initialization for expensive resources (HttpClient, DB connections)

---

#### NFR-3: Memory Efficiency
**Priority**: P0 (Critical)

The system SHALL maintain constant memory usage during generation:

**Requirements**:
- **Heap Usage**: < 512 MB for 10 million record generation
- **No Memory Leaks**: Stable heap usage over 1-hour runs
- **Streaming Architecture**: Generate → serialize → send pipeline (no in-memory buffers)
- **Thread-Local Cleanup**: Proper cleanup of ThreadLocal resources

**Acceptance Criteria**:
- Heap usage remains constant (± 10%) during long runs
- No `OutOfMemoryError` for any record count
- GC pressure < 10% of CPU time (measured via JFR)

---

### 4.2 Reliability

#### NFR-4: Fault Tolerance
**Priority**: P1 (High)

The system SHALL handle failures gracefully:

**Requirements**:
- **Transient Errors**: Retry network operations (Kafka publish, HTTP seed fetch) with exponential backoff
- **Permanent Errors**: Fail fast with descriptive error message (invalid config, disk full, auth failure)
- **Partial Failures**: Continue generation if some workers fail (log error, don't crash)
- **Resource Cleanup**: Close connections, flush buffers, release locks on shutdown (even on crash)

**Acceptance Criteria**:
- Retry transient network errors (max 3 retries)
- Fail fast on config validation errors (before generation starts)
- Clean shutdown on SIGTERM/SIGINT (flush pending records)
- No resource leaks (connections, file handles) after crash

---

#### NFR-5: Data Integrity
**Priority**: P0 (Critical)

The system SHALL guarantee data correctness:

**Requirements**:
- **Reproducibility**: Same seed → byte-identical output (deterministic PRNG)
- **Type Safety**: Generated values always within configured ranges (no overflows, no invalid dates)
- **Circular Reference Detection**: Fail fast on circular object dependencies at parse time
- **Atomic Writes**: File writes are atomic (no partial records on crash)

**Acceptance Criteria**:
- 100% reproducibility (verify with byte-level diff of two runs)
- All generated values pass type constraints (fuzzing tests)
- Circular references detected before generation starts
- No corrupted files after JVM crash (atomic write verification)

---

### 4.3 Usability

#### NFR-6: Configuration Simplicity
**Priority**: P1 (High)

The system SHALL provide intuitive YAML configuration:

**Requirements**:
- **Declarative Syntax**: No code required for common use cases
- **Self-Documenting**: Clear field names, inline comments in examples
- **Error Messages**: Descriptive errors with file/line number and suggested fixes
- **Fail-Fast Validation**: All config errors reported at parse time (not during generation)

**Acceptance Criteria**:
- New users can create basic job in < 5 minutes (timed usability test)
- Error messages include file path, line number, and suggested fix
- All validation errors reported in one pass (not one-at-a-time)

**Example Error Message**:
```
Error parsing config/structures/address.yaml:
  Line 12: Invalid datatype syntax 'int[1..max]'
           Expected: int[min..max] where min and max are integers
           Suggested fix: int[1..999]
```

---

#### NFR-7: Documentation Quality
**Priority**: P1 (High)

The system SHALL provide comprehensive documentation:

**Requirements**:
- **README**: Quick start guide (< 5 minutes to first successful run)
- **DESIGN.md**: Architectural decisions, issue resolutions, design trade-offs
- **REQUIREMENTS.md**: This document (comprehensive specification)
- **API Documentation**: JavaDoc for all public APIs (classes, methods, interfaces)
- **Examples**: Working examples for all features (in `config/` directory)

**Acceptance Criteria**:
- README covers 80% of common use cases
- All public APIs have JavaDoc with examples
- At least 5 working example configurations in `config/`

---

### 4.4 Maintainability

#### NFR-8: Code Quality
**Priority**: P1 (High)

The system SHALL maintain high code quality:

**Requirements**:
- **Code Style**: Google Java Style Guide (with custom brace placement)
- **Formatting**: Spotless enforced in CI (auto-format on commit)
- **Static Analysis**: SpotBugs with MAX effort, fail on high-severity issues
- **Test Coverage**: 70% unit tests, 90% integration tests
- **Security Scanning**: OWASP Dependency-Check for known vulnerabilities

**Acceptance Criteria**:
- 100% Spotless compliance (auto-formatted)
- Zero SpotBugs high-severity issues
- 70%+ line coverage (JaCoCo report)
- Zero known high-severity CVEs in dependencies

**Enforcement**: GitHub Actions CI fails builds on violations

---

#### NFR-9: Modularity
**Priority**: P0 (Critical)

The system SHALL maintain clean module boundaries:

**Module Dependency Rules**:
```
cli → destinations → formats → generators → schema → core
```

**Requirements**:
- **No Circular Dependencies**: Gradle build enforces module dependency graph
- **Single Responsibility**: Each module has one clear purpose (separation of concerns)
- **Minimal Coupling**: Modules communicate via interfaces (strategy pattern)
- **Dependency Injection**: Use constructor injection (no static state)

**Acceptance Criteria**:
- Gradle build succeeds with no circular dependency warnings
- Each module can be unit tested in isolation (mock dependencies)
- Adding new module requires changes to < 3 existing modules

---

### 4.5 Security

#### NFR-10: Secret Management
**Priority**: P0 (Critical)

The system SHALL protect sensitive configuration:

**Requirements**:
- **No Plaintext Secrets**: Passwords, tokens in YAML files stored as file paths or env vars
- **Credential Files**: Permissions check (fail if world-readable)
- **Environment Variables**: Support for secret injection (Kubernetes secrets, AWS Secrets Manager)
- **Remote API Auth**: Support bearer tokens, basic auth, API keys

**Acceptance Criteria**:
- Passwords never logged (even in debug mode)
- File-based secrets require restrictive permissions (fail if 0644+)
- Environment variable secrets work in containerized environments

**Example** (secure):
```yaml
conf:
  password: /secrets/db_password  # File with 0600 permissions
  # OR
  api_key: ${API_KEY}            # Environment variable
```

**Anti-Example** (insecure):
```yaml
conf:
  password: mypassword123  # ❌ NEVER DO THIS
```

---

#### NFR-11: Dependency Security
**Priority**: P1 (High)

The system SHALL use secure dependencies:

**Requirements**:
- **Vulnerability Scanning**: OWASP Dependency-Check in CI
- **Auto-Updates**: Dependabot PRs for dependency updates (weekly)
- **Fail Build**: CVSS 7.0+ vulnerabilities fail CI build
- **Suppressions**: Document false positives in `config/dependency-check-suppressions.xml`

**Acceptance Criteria**:
- Zero high-severity CVEs in production dependencies
- Dependabot enabled and PRs reviewed weekly
- All suppressions documented with justification

---

## 5. System Architecture

### 5.1 Module Structure

**Multi-Module Gradle Project** (Java 21, Gradle 8.5+)

```
datagenerator/
├── core/           # Foundation (seeding, type system, PRNG)
├── schema/         # YAML parsing (Jackson + Hibernate Validator)
├── generators/     # Data generators (primitives + Datafaker)
├── formats/        # Serializers (JSON, CSV, Protobuf)
├── destinations/   # Adapters (Kafka, DB, File)
└── cli/           # Command-line interface (Picocli)
```

**Dependency Flow**:
```
cli → destinations → formats → generators → schema → core
```

**Key Design Decisions**:
- **No Circular Dependencies**: Enforced by Gradle build
- **Core Has No Dependencies**: Only JDK + SLF4J (no external libraries)
- **Schema Depends on Core**: SeedConfig lives in core to avoid circular dependency
- **Generators Are Stateless**: Thread-safe by design (use thread-local Random)

---

### 5.2 Component Architecture

#### Core Components

##### SeedResolver
**Responsibility**: Convert seed configurations to long values

**Inputs**: `SeedConfig` (sealed interface with 4 subtypes)  
**Output**: `long` seed value

**Implementation**:
- Sealed switch on `SeedConfig` type
- Lazy HttpClient initialization for remote seeds
- Error handling with descriptive exceptions

**Thread-Safety**: Immutable, stateless (except lazy HttpClient singleton)

---

##### RandomProvider
**Responsibility**: Provide deterministic thread-local Random instances

**Key Features**:
- Logical worker IDs (0, 1, 2, ...) via AtomicInteger
- Seed derivation: `deriveSeed(masterSeed, workerId)` → unique per-worker seed
- Thread-local Random instances (no synchronization)

**Guarantees**:
- Same master seed → same worker seeds → identical data
- Thread-safe (AtomicInteger + ThreadLocal)
- Fast (XOR bit mixing for seed derivation)

---

#### Schema Components

##### DataStructureParser
**Responsibility**: Parse YAML structure definitions

**Input**: `structures/*.yaml` files  
**Output**: `DataStructure` object (name, geolocation, field definitions)

**Implementation**:
- Jackson YAML deserialization
- Hibernate Validator for field validation
- TypeParser for datatype syntax parsing

---

##### JobDefinitionParser
**Responsibility**: Parse YAML job definitions

**Input**: `jobs/*.yaml` files  
**Output**: `JobDefinition` object (source, type, seed, destination config)

**Implementation**:
- Jackson YAML deserialization
- Polymorphic destination config (KafkaConfig, FileConfig, DatabaseConfig)
- SeedConfig parsing (delegates to SeedResolver)

---

#### Generator Components

##### DataTypeGenerator (Interface)
**Responsibility**: Generate values for a specific data type

**Signature**:
```java
public interface DataTypeGenerator<T> {
    T generate(Random random, TypeConfig config);
}
```

**Implementations**:
- **CharGenerator**: Random strings [minLength..maxLength]
- **IntegerGenerator**: Random integers [min..max]
- **DecimalGenerator**: Random decimals [min..max] with scale
- **BooleanGenerator**: Random true/false (50/50)
- **DateGenerator**: Random LocalDate [start..end]
- **TimestampGenerator**: Random Instant [start..end]
- **EnumGenerator**: Random selection from values
- **ArrayGenerator**: Variable-length arrays [minLength..maxLength]
- **ObjectGenerator**: Nested object generation with StructureRegistry
- **DatafakerGenerator** (planned): Realistic data from Datafaker providers

---

##### DataGeneratorFactory
**Responsibility**: Create and manage generators

**Implementation**:
- Registry-based factory (map of type → generator)
- Stateless generators (primitives, enums)
- Stateful generators (ObjectGenerator with StructureRegistry)
- GeneratorContext (ThreadLocal) for clean factory access

---

#### Format Components

##### FormatSerializer (Interface)
**Responsibility**: Serialize records to output format

**Signature**:
```java
public interface FormatSerializer {
    byte[] serialize(Map<String, Object> record);
    byte[] serializeHeader();  // For CSV headers
}
```

**Implementations**:
- **JsonSerializer**: NDJSON with Jackson
- **CsvSerializer**: RFC 4180 with field aliases
- **ProtobufSerializer** (planned): Dynamic Protobuf serialization

---

#### Destination Components

##### DestinationAdapter (Interface)
**Responsibility**: Write records to destination

**Signature**:
```java
public interface DestinationAdapter extends AutoCloseable {
    void write(byte[] record);
    void flush();
    void close();
}
```

**Implementations**:
- **FileDestination**: NIO-based file writes with optional compression
- **KafkaDestination**: Kafka producer with connection pooling
- **DatabaseDestination**: JDBC batch inserts with HikariCP

---

### 5.3 Execution Flow

**Job Execution Pipeline**:

```
1. Parse Job Definition (YAML → JobDefinition)
   ↓
2. Resolve Seed (SeedConfig → long)
   ↓
3. Load Data Structure (YAML → DataStructure)
   ↓
4. Parse Types (type syntax → DataType hierarchy)
   ↓
5. Initialize Workers (create thread pool)
   ↓
6. Initialize RandomProvider (master seed → worker seeds)
   ↓
7. Generate Records (parallel workers)
   ├─ Generate fields (recursive for nested objects/arrays)
   ├─ Apply aliases (field renaming)
   └─ Batch records (configurable batch size)
   ↓
8. Serialize Records (record → byte[])
   ↓
9. Write to Destination (batched writes)
   ↓
10. Shutdown (flush buffers, close connections)
```

**Parallelization**:
- Workers generate records independently (no shared state)
- Each worker has thread-local Random (deterministic seed)
- Batching amortizes serialization and I/O overhead
- Backpressure handling (slow destination slows workers)

---

### 5.4 Thread Model

**Worker Threads**:
- Fixed-size thread pool (default: # of CPU cores)
- Each worker generates records independently
- Thread-local state (Random, formatters, buffers)

**Logical Worker IDs**:
- Sequential IDs (0, 1, 2, ...) assigned via AtomicInteger
- Independent of JVM thread IDs (which vary across runs)
- Used for deterministic seed derivation

**Main Thread**:
- Orchestrates job execution (parse, initialize, start workers, wait for completion)
- Handles shutdown and cleanup
- Displays progress (optional, configurable)

---

## 6. Data Model & Type System

### 6.1 Type Hierarchy

**Sealed Interface Hierarchy** (Java 21 sealed types):

```
DataType (sealed)
├── PrimitiveType (sealed)
│   ├── CharType (min/max length)
│   ├── IntegerType (min/max value)
│   ├── DecimalType (min/max, scale)
│   ├── BooleanType
│   ├── DateType (start/end)
│   └── TimestampType (start/end)
├── EnumType (allowed values)
├── ObjectType (structure name, nested fields)
├── ArrayType (inner type, min/max length)
└── ReferenceType (structure.field) [future]
```

**Design Benefits**:
- Exhaustive pattern matching (compiler ensures all cases handled)
- Type-safe hierarchy (no instanceof chains)
- Clear separation of concerns

---

### 6.2 Type Syntax Grammar

**EBNF Grammar**:

```ebnf
type_syntax ::= primitive_type | enum_type | object_type | array_type | ref_type

primitive_type ::= "char" range
                 | "int" range
                 | "decimal" range
                 | "boolean"
                 | "date" date_range
                 | "timestamp" timestamp_range

enum_type ::= "enum" "[" value_list "]"

object_type ::= "object" "[" structure_name "]"

array_type ::= "array" "[" type_syntax "," range "]"

ref_type ::= "ref" "[" structure_name "." field_name "]"

range ::= "[" number ".." number "]"

date_range ::= "[" iso_date ".." iso_date "]"

timestamp_range ::= "[" timestamp_value ".." timestamp_value "]"
                  | "[" relative_time ".." relative_time "]"

relative_time ::= "now" | "now" ("+"|"-") duration

duration ::= number ("d"|"h"|"m"|"s")

value_list ::= identifier ("," identifier)*
```

**Examples**:
```yaml
name: char[3..50]
age: int[18..65]
price: decimal[0.00..999.99]
active: boolean
birth_date: date[1950-01-01..2005-12-31]
created_at: timestamp[now-30d..now]
status: enum[ACTIVE,INACTIVE,PENDING]
address: object[address]
tags: array[char[1..20], 1..10]
line_items: array[object[line_item], 1..50]
user_id: ref[user.id]
```

---

### 6.3 Structure Registry

**Responsibility**: Load and cache data structure definitions

**Key Features**:
- Lazy loading (load structures on first reference)
- Circular reference detection (fail fast with descriptive error)
- Thread-safe caching (ConcurrentHashMap)
- Configurable base path (default: `config/structures/`)

**Algorithm** (circular reference detection):
```java
Set<String> visitedStructures = new HashSet<>();

void detectCycles(String structureName) {
    if (visitedStructures.contains(structureName)) {
        throw new ConfigurationException("Circular reference: " + visitedStructures + " → " + structureName);
    }
    visitedStructures.add(structureName);
    
    DataStructure structure = load(structureName);
    for (Field field : structure.getFields()) {
        if (field.getType() instanceof ObjectType objectType) {
            detectCycles(objectType.getStructureName());
        }
    }
    
    visitedStructures.remove(structureName);
}
```

---

## 7. Configuration Specification

### 7.1 Data Structure Configuration

**File Location**: `config/structures/<name>.yaml`

**Schema**:
```yaml
name: <string>                  # REQUIRED: Structure name (alphanumeric + underscore)
geolocation: <string>           # OPTIONAL: Locale (italy, usa, france, etc.)
data:                          # REQUIRED: Field definitions (min 1 field)
  <field_name>:                # Field key (alphanumeric + underscore)
    datatype: <type_syntax>    # REQUIRED: Type syntax (see Type System)
    alias: <string>            # OPTIONAL: Output field name (default: field_name)
```

**Validation Rules**:
- `name`: Non-empty, alphanumeric + underscore, unique
- `geolocation`: Valid locale code (see Datafaker locale list)
- `data`: At least one field
- `datatype`: Valid type syntax (parse with TypeParser)
- `alias`: Non-empty if present

**Example**:
```yaml
name: address
geolocation: italy
data:
  name:
    datatype: char[3..15]
    alias: "nome"
  city:
    datatype: char[3..40]
    alias: "citta"
  postal_code:
    datatype: char[5..5]
    alias: "cap"
```

---

### 7.2 Job Definition Configuration

**File Location**: `config/jobs/<name>.yaml`

**Schema**:
```yaml
source: <string>               # REQUIRED: Structure file name (e.g., address.yaml)
type: <string>                 # REQUIRED: Destination type (kafka, file, database)
structures_path: <string>      # OPTIONAL: Base path for nested structures (default: config/structures/)
seed:                         # REQUIRED: Seed configuration
  type: <string>              # REQUIRED: embedded, file, env, or remote
  # ... type-specific fields (see Seed Configuration)
conf:                         # REQUIRED: Destination-specific configuration
  # ... destination-specific fields (see Destination Configuration)
```

**Validation Rules**:
- `source`: File exists in `structures_path`
- `type`: One of [kafka, file, database]
- `seed`: Valid SeedConfig subtype
- `conf`: Valid destination config for specified type

---

### 7.3 Seed Configuration

#### Embedded Seed
```yaml
seed:
  type: embedded
  value: <long>                # REQUIRED: Seed value (long integer)
```

---

#### File-Based Seed
```yaml
seed:
  type: file
  path: <string>               # REQUIRED: Absolute or relative file path
```

**File Format**: Plain text, single line, long integer (whitespace trimmed)

---

#### Environment Variable Seed
```yaml
seed:
  type: env
  name: <string>               # REQUIRED: Environment variable name
```

**Environment Variable Format**: Long integer string (e.g., `export DATA_SEED=12345`)

---

#### Remote API Seed
```yaml
seed:
  type: remote
  url: <string>                # REQUIRED: HTTP(S) endpoint URL
  auth:                        # REQUIRED: Authentication config
    type: <string>             # REQUIRED: bearer, basic, or api_key
    # Bearer token auth:
    token: <string>            # REQUIRED for bearer: Token value (supports ${ENV_VAR})
    # Basic auth:
    username: <string>         # REQUIRED for basic: Username
    password: <string>         # REQUIRED for basic: Password (supports file path)
    # API key auth:
    key: <string>              # REQUIRED for api_key: Key value (supports ${ENV_VAR})
```

**HTTP Response Format**: Plain text or JSON (extract first long value)

---

### 7.4 Destination Configuration

#### Kafka Destination
```yaml
type: kafka
conf:
  bootstrap: <string>          # REQUIRED: Bootstrap servers (host:port,host:port)
  topic: <string>              # REQUIRED: Topic name
  auth: <string>               # OPTIONAL: none (default), sasl_ssl, or ssl
  cert: <string>               # OPTIONAL: SSL certificate path (if auth=ssl or sasl_ssl)
  username: <string>           # OPTIONAL: SASL username (if auth=sasl_ssl)
  password: <string>           # OPTIONAL: SASL password (if auth=sasl_ssl, supports file path)
  batch_size: <int>            # OPTIONAL: Records per batch (default: 100)
  compression: <string>        # OPTIONAL: none (default), gzip, snappy, lz4, zstd
```

---

#### File Destination
```yaml
type: file
conf:
  path: <string>               # REQUIRED: Output file base path
  compression: <string>        # OPTIONAL: none (default), gzip, bzip2
  append: <boolean>            # OPTIONAL: Append vs overwrite (default: false)
```

---

#### Database Destination
```yaml
type: database
conf:
  jdbc_url: <string>           # REQUIRED: JDBC connection string
  username: <string>           # REQUIRED: Database username
  password: <string>           # REQUIRED: Database password (supports file path)
  table: <string>              # REQUIRED: Target table name
  batch_size: <int>            # OPTIONAL: Rows per batch (default: 100)
  pool_size: <int>             # OPTIONAL: Connection pool size (default: 10)
```

---

## 8. API & Interfaces

### 8.1 Public API Surface

**Core Module**:
- `SeedResolver`: Convert SeedConfig → long
- `RandomProvider`: Provide thread-local Random instances
- `SeedConfig`: Sealed interface (EmbeddedSeed, FileSeed, EnvSeed, RemoteSeed)

**Schema Module**:
- `DataStructureParser`: Parse structure YAML files
- `JobDefinitionParser`: Parse job YAML files
- `DataStructure`: Structure definition model
- `JobDefinition`: Job definition model

**Generators Module**:
- `DataTypeGenerator<T>`: Interface for all generators
- `DataGeneratorFactory`: Create generators by type
- Implementations: CharGenerator, IntegerGenerator, DecimalGenerator, etc.

**Formats Module**:
- `FormatSerializer`: Interface for serializers
- Implementations: JsonSerializer, CsvSerializer, ProtobufSerializer

**Destinations Module**:
- `DestinationAdapter`: Interface for destinations
- Implementations: FileDestination, KafkaDestination, DatabaseDestination

**CLI Module**:
- `ExecuteCommand`: Picocli command for job execution
- Main class: CLI entry point

---

### 8.2 Extension Points

**Adding New Destination**:
1. Implement `DestinationAdapter` interface
2. Add destination type to JobDefinitionParser
3. Create destination config class (YAML schema)
4. Add tests (unit + integration)

**Adding New Format**:
1. Implement `FormatSerializer` interface
2. Add format type to CLI enum
3. Handle field aliases
4. Add tests (serialization + deserialization)

**Adding New Generator**:
1. Implement `DataTypeGenerator<T>` interface
2. Register in `DataGeneratorFactory`
3. Add type syntax to TypeParser (if new primitive)
4. Add tests (determinism + range validation)

---

## 9. Security & Compliance

### 9.1 Secret Management

**Requirements**:
- ✅ No plaintext secrets in YAML files
- ✅ Support file-based secrets (with permission checks)
- ✅ Support environment variable secrets
- ✅ Never log secrets (even in debug mode)

**Best Practices**:
```yaml
# ✅ GOOD: File-based password
conf:
  password: /secrets/db_password

# ✅ GOOD: Environment variable
conf:
  api_key: ${API_KEY}

# ❌ BAD: Plaintext password
conf:
  password: mypassword123
```

---

### 9.2 Dependency Security

**Tools**:
- OWASP Dependency-Check (CVSS 7.0+ fails build)
- Dependabot (weekly PRs for updates)
- Suppressions (documented false positives)

**Policy**:
- Zero high-severity CVEs in production dependencies
- Review all Dependabot PRs within 7 days
- Document all suppressions with justification

---

### 9.3 Data Privacy

**Requirements**:
- Generated data is synthetic (no real PII)
- Locale-specific data follows cultural norms (realistic but fake)
- No external API calls without explicit user configuration

**Considerations**:
- GDPR compliance: Synthetic data is not PII
- Use for testing only (never use for production data)

---

## 10. Quality Standards

### 10.1 Code Quality

**Tools**:
- **Spotless**: Google Java Style Guide (auto-format on commit)
- **SpotBugs**: Static analysis (MAX effort, fail on high severity)
- **JaCoCo**: Code coverage (70% minimum)
- **OWASP Dependency-Check**: Vulnerability scanning

**Enforcement**: GitHub Actions CI fails builds on violations

---

### 10.2 Testing Strategy

**Unit Tests** (70% coverage target):
- All public APIs have unit tests
- Mock external dependencies (Kafka, DB, HTTP)
- JUnit 5 + Mockito + AssertJ
- Test naming: `shouldGenerateCorrectDataWhenSeedIsProvided`

**Integration Tests** (90% coverage target):
- Testcontainers for Kafka, PostgreSQL, MySQL
- Real file I/O with temporary directories
- End-to-end job execution tests
- Seed resolution tests (all types: embedded, file, env, remote)

**Performance Tests**:
- JMH benchmarks for critical paths (generation, serialization)
- Throughput tests (records/second)
- Memory profiling (heap usage, GC pressure)

---

### 10.3 Documentation Standards

**Requirements**:
- JavaDoc for all public APIs (classes, methods, interfaces)
- README covers 80% of common use cases
- DESIGN.md explains architectural decisions
- REQUIREMENTS.md (this document) is comprehensive
- Examples for all features in `config/` directory

**Quality Checks**:
- JavaDoc completeness (Checkstyle plugin)
- Markdown lint (markdownlint)
- Link validation (dead link checker)

---

## 11. Development Standards

### 11.1 Tech Stack

- **Language**: Java 21 (toolchain enforced)
- **Build Tool**: Gradle 8.5+ (Kotlin DSL)
- **Libraries**:
  - Jackson YAML (configuration parsing)
  - Hibernate Validator (config validation)
  - Datafaker 2.1.0 (realistic data generation)
  - Picocli (CLI framework)
  - Kafka Clients (Kafka destination)
  - HikariCP (database connection pooling)
  - Lombok (boilerplate reduction)
  - SLF4J + Logback (logging)

---

### 11.2 Coding Conventions

**Style Guide**: Google Java Style Guide with one exception:
- **Opening Braces**: Same line (not next line)
  ```java
  // ✅ CORRECT
  public class Foo {
      public void bar() {
          if (condition) {
              // ...
          }
      }
  }
  
  // ❌ WRONG
  public class Foo
  {
      public void bar()
      {
          if (condition)
          {
              // ...
          }
      }
  }
  ```

**Formatting Rules**:
- Max line length: 120 characters
- Indentation: 4 spaces (no tabs)
- Use Java 21 features: records, pattern matching, switch expressions, sealed types
- Prefer functional style (streams) over imperative loops
- Use Lombok @Builder for classes with 4+ parameters
- Return `Optional<T>` for methods that may not have a value
- Never return null collections (return empty)

**Import Rules**:
- ✅ **NO wildcard imports**: NEVER use `import package.*;`
- ✅ **Explicit imports**: Import each class individually
- ✅ **Exception**: Static test assertions (`import static org.assertj.core.api.Assertions.*;`)
- ✅ **Import at top**: Use simple names in code (NOT `@lombok.Value`)
- ✅ **Collision case**: Use fully-qualified names for ALL instances when names collide

**Examples**:
```java
// ✅ CORRECT
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import lombok.Value;
import lombok.Builder;

@Value
@Builder
public class Address {
    String street;
    String city;
}

// ❌ WRONG
import java.util.*;
import lombok.*;

@lombok.Value  // Never use fully-qualified annotations inline
public class Address {
    String street;
    String city;
}
```

---

### 11.3 Error Handling

**Principles**:
- **Fail Fast**: Validate inputs at boundaries (parsers, CLI)
- **Typed Exceptions**: Custom exceptions (ConfigurationException, GeneratorException, DestinationException)
- **Context**: Always include context in error messages (file, line, field, suggested fix)
- **Logging**: ERROR (user-facing), WARN (degraded), DEBUG (internals)
- **Resource Cleanup**: Always use try-with-resources for I/O

**Example**:
```java
// ✅ CORRECT
public long resolveEmbedded(EmbeddedSeed seed) {
    if (seed == null) {
        throw new ConfigurationException("Embedded seed cannot be null");
    }
    return seed.getValue();
}

// ❌ WRONG
public long resolveEmbedded(EmbeddedSeed seed) {
    return seed.getValue();  // NullPointerException if seed is null
}
```

---

### 11.4 Git Workflow

**Branch Strategy**: GitHub Flow (main + feature branches)

**Branch Naming**:
- Feature: `feature/add-json-serializer`
- Bugfix: `bugfix/fix-circular-reference-detection`
- Docs: `docs/update-readme`

**Commit Messages**:
- Format: `<type>: <description>` (imperative mood)
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Examples:
  - `feat: add JSON serializer with field aliases`
  - `fix: resolve circular reference detection in StructureRegistry`
  - `docs: update README with Kafka destination examples`

**Pull Requests**:
- Title: Same format as commit messages
- Description: What changed, why, testing done
- CI must pass (formatting, build, tests)
- At least 1 approval before merge

---

## 12. Implementation Phases

### Phase 1: Core Foundation ✅ (Complete)

**Status**: ✅ 100% Complete

**Deliverables**:
- ✅ Project scaffolding (Gradle multi-module)
- ✅ Schema module (YAML parsers for structures + jobs)
- ✅ Core module (type system, seeding, randomization)
- ✅ Generators module (primitives + composites)
- ✅ Code quality setup (Spotless, JaCoCo, SpotBugs, OWASP)

**Test Coverage**: 165 tests passing (schema: 15, core: 30, generators: 52, formats: 33, destinations: 16, imports: 5, integration: 14)

---

### Phase 2: Data Generation 🔄 (In Progress)

**Priority**: P1 (High)  
**Target Date**: Q1 2026

**Remaining Tasks**:
- [ ] Datafaker integration (realistic data for 40+ types)
- [ ] Locale-specific data generation (62 locales)
- [ ] ReferenceGenerator (foreign keys) - deferred to Phase 4

**Acceptance Criteria**:
- Generate realistic names, addresses, emails for 62 locales
- Deterministic output (same seed → same realistic data)
- 90% test coverage for Datafaker generators

---

### Phase 3: Output Formats ✅ (70% Complete)

**Priority**: P1 (High)  
**Target Date**: Q1 2026

**Tasks**:
- ✅ JSON serializer (NDJSON with field aliases) - 16 tests passing
- ✅ CSV serializer (RFC 4180 compliant) - 17 tests passing
- ❌ Protobuf serializer (dynamic schema generation) - Planned

**Acceptance Criteria**:
- ✅ Serialize all data types to JSON/CSV
- ✅ Apply field aliases from structure definitions
- ✅ 90% test coverage for JSON/CSV serializers (33/33 tests passing)
- ❌ Protobuf support pending

---

### Phase 4: Destinations ✅ (35% Complete)

**Priority**: P1 (High)  
**Target Date**: Q2 2026

**Tasks**:
- ✅ File destination (NIO with compression) - 16 tests passing
- ❌ Kafka destination (connection pooling + auth) - Planned
- ❌ Database destination (JDBC + HikariCP) - Planned

**Acceptance Criteria**:
- ✅ Write to file with batching and compression
- ✅ File destination meets performance targets
- ✅ Integration tests for file destination
- ❌ Kafka and database destinations pending

---

### Phase 5: CLI & Execution ✅ (65% Complete)

**Priority**: P0 (Critical)  
**Target Date**: Q2 2026

**Tasks**:
- ✅ CLI command interface (Picocli) - Fully functional
- ✅ Execute command with all options (--job, --format, --count, --seed, --verbose)
- ✅ Smart path resolution for structure files
- ✅ Generation statistics (records/sec, elapsed time)
- ❌ Multi-threading engine (worker pool + batching) - Planned
- ❌ Progress reporting (% complete) - Planned

**Acceptance Criteria**:
- ✅ Execute jobs with CLI parameters
- ✅ Seed override via CLI
- ✅ Format selection (JSON/CSV)
- ✅ Verbose logging support
- ✅ End-to-end pipeline working (parse → generate → serialize → write)
- ✅ Deterministic output verified (SHA-256 matching across runs)
- ❌ Multi-threading and progress reporting pending

---

### Phase 6: Quality & Performance

**Priority**: P1 (High)  
**Target Date**: Q2 2026

**Tasks**:
- [ ] Integration tests (Testcontainers for Kafka/DB)
- [ ] JMH benchmarks (generation, serialization, destinations)
- [ ] Memory profiling (heap usage, GC pressure)

**Acceptance Criteria**:
- 70% unit test coverage
- 90% integration test coverage
- Meet all performance targets (see NFR-1)

---

### Phase 7: Documentation

**Priority**: P1 (High)  
**Target Date**: Q2 2026

**Tasks**:
- [ ] README (quick start + examples)
- [ ] Example configurations (all destination types)
- [ ] JavaDoc completion (all public APIs)

**Acceptance Criteria**:
- README covers 80% of common use cases
- 5+ working example configurations
- 100% JavaDoc coverage for public APIs

---

## 13. Success Criteria

### 13.1 Functional Completeness

**Criteria**:
- ✅ All Phase 1-5 deliverables complete
- ✅ All functional requirements (FR-1 through FR-16) implemented
- ✅ All acceptance criteria met

---

### 13.2 Performance Targets

**Criteria**:
- ✅ Meet all throughput targets (NFR-1)
- ✅ Meet all latency targets (NFR-2)
- ✅ Constant memory usage (NFR-3)

---

### 13.3 Quality Standards

**Criteria**:
- ✅ 70% unit test coverage
- ✅ 90% integration test coverage
- ✅ Zero SpotBugs high-severity issues
- ✅ Zero high-severity CVEs

---

### 13.4 User Satisfaction

**Criteria**:
- ✅ README covers 80% of common use cases
- ✅ New users can create basic job in < 5 minutes
- ✅ Descriptive error messages with suggested fixes

---

## 14. Constraints & Assumptions

### 14.1 Constraints

**Technical Constraints**:
- Java 21 required (no support for earlier versions)
- Linux/macOS primary platforms (Windows support best-effort)
- Gradle 8.5+ required for build

**Resource Constraints**:
- Target hardware: 16-core machine, 32 GB RAM, SSD storage
- No GPU acceleration (CPU-only)

**Security Constraints**:
- No external API calls without user configuration
- Secrets must be external (file or env var, not YAML)

---

### 14.2 Assumptions

**User Assumptions**:
- Users have basic YAML knowledge
- Users understand test data generation concepts
- Users can configure Kafka/database connections

**Technical Assumptions**:
- Kafka/database drivers provided by users (compileOnly dependencies)
- Network connectivity for remote seed resolution
- Filesystem write permissions for file destinations

**Data Assumptions**:
- Generated data is synthetic (not real PII)
- Locale-specific data follows cultural norms
- Reproducibility is critical (same seed → identical data)

---

## 15. Glossary

**Seed**: Long integer value used to initialize pseudo-random number generator (PRNG) for deterministic data generation.

**Structure**: Data schema definition (fields, types, aliases) in YAML format.

**Job**: Configuration file specifying what to generate, where to send it, and with what seed.

**Destination**: Output target for generated data (Kafka, database, file).

**Format**: Serialization format for output (JSON, CSV, Protobuf).

**Geolocation**: Locale identifier (e.g., "italy", "usa") for locale-specific realistic data generation.

**Alias**: Alternative field name for output (e.g., "nome" instead of "name").

**Worker**: Generation thread with unique logical ID and thread-local Random instance.

**Batch**: Group of records generated/serialized/sent together for performance optimization.

**NDJSON**: Newline-Delimited JSON (one JSON object per line).

**HikariCP**: High-performance JDBC connection pool library.

**Datafaker**: Java library for generating realistic fake data (names, addresses, emails, etc.).

**Testcontainers**: Java library for Docker-based integration testing.

**JMH**: Java Microbenchmark Harness for performance testing.

**Spotless**: Code formatting tool with Google Java Style Guide.

**SpotBugs**: Static analysis tool for finding bug patterns.

**OWASP Dependency-Check**: Security vulnerability scanner for dependencies.

---

## Document Control

**Revision History**:

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-20 | Marco | Initial comprehensive requirements document |
| 1.1 | 2026-02-21 | Marco | Updated implementation status: FR-9, FR-12, FR-13, FR-15, FR-16 completed; Phases 3-5 progress updated; 165 tests passing |
| 1.2 | 2026-03-05 | Marco | Updated implementation status: FR-3 (Datafaker), FR-10 (Multi-threading), FR-13 (Kafka) completed; 276 tests passing; Version bumped to v0.2 |

**Approval**:
- Project Owner: Marco Ferretti
- Technical Lead: Marco Ferretti

**Distribution**:
- All developers
- QA team
- DevOps team
- Open-source community (GitHub)

---

**End of Document**
