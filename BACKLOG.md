# Project Backlog

## Completed

- [x] **Project scaffolding and build setup**
  - Create Gradle multi-module structure, configure Java 21, set up basic build configuration with common dependencies

- [x] **Schema module - YAML parsing**
  - Implement data structure definition parser (address.yaml format) using Jackson YAML
  - Support field definitions, datatypes, aliases, geolocation
  - Tests: 6 tests passing

- [x] **Schema module - Job configuration parser**
  - Implement job definition parser with seed at root level
  - All seed types: embedded, file, env, remote (with auth)
  - Type-specific destination configs (KafkaConfig/FileConfig/DatabaseConfig)
  - Source structure references with optional structures_path
  - Tests: 9 tests passing

- [x] **Core module - Type system**
  - Sealed interface hierarchy: DataType → PrimitiveType, EnumType, ObjectType, ArrayType, ReferenceType
  - TypeParser with full syntax support: char[min..max], int[min..max], decimal[min..max], date ranges, timestamps
  - Nested structures: object[structure_name] with auto-loading
  - Variable-length arrays: array[type, min..max] with recursive type support
  - Circular reference detection via StructureRegistry (fail-fast)
  - Tests: 25 tests passing

- [x] **Documentation - Example configurations (complex nested structures)**
  - Created invoice example with nested objects (company) and arrays (line_items)
  - Italian locale configuration with field aliases
  - Demonstrates real-world use case

- [x] **Core module - Seeding & randomization**
  - SeedResolver: 4 seed types (embedded, file, env, remote) with 3 auth methods (bearer, basic, api_key)
  - RandomProvider: Thread-local Random with logical worker IDs for true determinism across JVM restarts
  - Lazy HttpClient initialization for resource efficiency
  - Seed derivation algorithm with bit mixing for distinct per-worker seeds
  - Tests: 30 tests passing (19 SeedResolver + 11 RandomProvider)
  - Documentation: DESIGN.md created with architectural decisions and issue resolutions

- [x] **Generators module - Primitive generators**
  - CharGenerator: Generates random strings [minLength..maxLength] from alphabet (a-zA-Z)
  - IntegerGenerator: Generates integers [min..max] with overflow protection
  - DecimalGenerator: Generates BigDecimal [min..max] with scale preservation
  - BooleanGenerator: Uniform distribution (50/50)
  - DateGenerator: Generates LocalDate [start..end] with ISO-8601 format
  - TimestampGenerator: Generates Instant [start..end] with relative format support (now-30d)
  - EnumGenerator: Random selection from allowed values
  - Tests: 29 tests passing

- [x] **Generators module - Composite generators**
  - ArrayGenerator: Variable-length arrays [minLength..maxLength] with recursive inner type generation
  - ObjectGenerator: Nested object generation using StructureRegistry with recursive field generation
  - DataGeneratorFactory: Registry-based factory with stateless (primitives) and stateful (ObjectGenerator) support
  - GeneratorContext: ThreadLocal context for clean factory access in nested generation
  - Tests: 23 tests passing (11 ArrayGenerator + 12 ObjectGenerator)

- [x] **Code quality - Import refactoring**
  - Replaced all wildcard imports with explicit class imports
  - Added import rules to copilot-instructions.md
  - Zero unused imports or variables

## Phase 2: Data Generation

- [x] **Generators module - Locale-specific data (Datafaker 2.5.4 integration)**
  - ✅ Integrated Datafaker 2.5.4 (latest stable as of March 2026)
  - ✅ Extended PrimitiveType.Kind with 28 semantic types (person, address, contact, finance, internet, code)
  - ✅ Updated TypeParser to support no-bracket syntax for semantic types (name, email, phone, etc.)
  - ✅ Created DatafakerGenerator with locale support (62+ locales: en, it, es, fr, de, pt, ru, zh, ja, ko, ar...)
  - ✅ Added geolocation field to GeneratorContext for locale propagation
  - ✅ Deterministic generation: Seeded Random passed to Faker for reproducible data
  - ✅ Fallback to English for unknown geolocations with warning log
  - ✅ Tests: 25+ unit tests + 8 integration tests passing
  - **Example**: `geolocation: italy` + `datatype: name` → "Mario Rossi", `datatype: city` → "Milano"
  - **Supported types**: NAME, FIRST_NAME, LAST_NAME, EMAIL, PHONE_NUMBER, ADDRESS, CITY, COMPANY, URL, UUID, IBAN, and more

- [ ] **Generators module - ReferenceGenerator (deferred)**
  - **Rationale**: Deferred until database destinations are implemented (no immediate use case)
  - Foreign key reference support: `ref[other_structure.field]`
  - **Requirements**:
    - Cross-record reference registry to maintain previously generated records
    - Lookup mechanism to find specific field values from referenced structures
    - Order dependency resolution (referenced structures must be generated first)
    - Performance optimization needed for large datasets (potential memory concerns)
  - **Complexity**: High - requires significant infrastructure beyond simple value generation
  - **When to implement**: Before Phase 4 database adapter implementation

- [ ] **Generators module - Distribution support (future)**
  - Implement statistical distributions (normal, uniform, Zipfian)
  - Geographical dispersion for location data
  - Configurable variance controls

## Phase 3: Output Formats

- [x] **Formats module - JSON serializer**
  - FormatSerializer interface for pluggable serialization
  - JsonSerializer with Jackson (newline-delimited JSON)
  - ISO-8601 date/time formatting, compact output
  - Tests: 16 tests passing

- [x] **Formats module - CSV serializer**
  - CsvSerializer with OpenCSV (always-quoted fields)
  - Proper escaping for quotes, commas, newlines
  - Nested objects/arrays serialized as JSON strings
  - Header row generation with serializeHeader()
  - Tests: 17 tests passing

- [ ] **Formats module - Protobuf serializer**
  - Implement Protobuf format serializer
  - Schema generation from data structure definitions

## Phase 4: Destinations

- [x] **Destinations module - File adapter**
  - DestinationAdapter interface for pluggable destinations
  - FileDestination with Java NIO for fast I/O
  - Support JSON and CSV formats with automatic header for CSV
  - Optional gzip compression
  - Append mode support
  - Automatic parent directory creation
  - Configurable buffer size for performance
  - Tests: 16 tests passing

- [ ] **Destinations module - Kafka adapter**
  - Implement Kafka destination with connection pooling
  - SASL/SSL authentication support
  - Configurable batching for performance
  - Producer reuse and proper resource management

- [ ] **Destinations module - Database adapter**
  - Implement JDBC destination with HikariCP connection pooling
  - Batch inserts for performance
  - Support PostgreSQL and MySQL (drivers as compileOnly)
  - Transaction management

## Phase 5: CLI & Execution

- [x] **CLI module - Command interface**
  - Picocli-based command-line interface
  - ExecuteCommand with options: --job, --format, --count, --seed, --verbose
  - Intelligent path resolution (relative to job file location)
  - Seed resolution from config or CLI override
  - Progress logging and performance metrics
  - End-to-end execution: parse → generate → serialize → write
  - Tested with JSON and CSV formats

- [~] **CLI module - Verbose logging** (Partially Complete)
  - ✅ --verbose flag implemented with progress logging every 100 records
  - ✅ Basic INFO level logging throughout
  - ❌ Missing: --debug flag, programmatic log level switching (DEBUG/TRACE levels)

- [ ] **Core - Multi-threading engine**
  - Implement parallel generation workers with configurable thread pools
  - Batching strategy for optimal performance
  - Streaming pipeline: generate → serialize → send
  - Backpressure handling

## Phase 6: Quality & Performance

- [ ] **Testing - Unit tests foundation**
  - Set up JUnit 5, Mockito, AssertJ ✅ (already configured)
  - Unit tests for core components (seeding, type system ✅, parsers ✅)
  - High code coverage for critical paths (Current: 40 tests passing across schema + core)

- [ ] **Testing - Integration tests**
  - Add integration tests with Testcontainers (Kafka, PostgreSQL, MySQL)
  - File system tests with temporary directories
  - Seed resolution tests (all types: embedded/file/env/remote)
  - End-to-end job execution tests

- [ ] **Performance benchmarking**
  - Add JMH benchmarks for generation speed
  - Measure records/second for various data types
  - Benchmark different destinations (file, Kafka, DB)
  - Memory profiling and optimization

## Phase 7: Documentation

- [x] **Documentation - Example configurations (partial)**
  - Created complex nested structure examples: company, line_item, invoice
  - Italian locale with field aliases demonstration
  - Still needed: examples for all destination types and seed types

- [ ] **Documentation - README**
  - Comprehensive README with architecture overview
  - Installation and quick start guide
  - Configuration format documentation
  - Usage examples for all features

- [ ] **Documentation - Example configurations (complete)**
  - Create example data structures (users, orders, events, transactions)
  - Example jobs for all destination types (file ✅, Kafka, database)
  - Examples for all seed types (embedded ✅, file, env, remote)
  - Real-world use case scenarios

## Future Enhancements

### Mimesis (Python) vs Datafaker Comparison

| Category | Datafaker (Java) | Mimesis (Python) | Winner |
|----------|------------------|------------------|---------|
| **License** | Apache 2.0 | MIT | ✅ Both compatible |
| **Total Providers** | 256 | ~20 | ✅ Datafaker |
| **Base/Core Types** | ~40 everyday types | ~15 everyday types | ✅ Datafaker |
| **Maintenance** | Active (5 days ago) | Active | ✅ Both maintained |
| **Locale Support** | 62 locales | 37 locales | ✅ Datafaker |
| **Special Features** | Native image, expressions, transformations | Schema generation, custom providers | Tie |
| **Language** | Java (native to project) | Python (requires bridge/API) | ✅ Datafaker |
| **Performance** | Native JVM | Python (slower) | ✅ Datafaker |

**Decision**: Use **Datafaker** for all realistic data generation needs.

**Nice-to-Have Types from Mimesis** (not in Datafaker):
- **BinaryFile**: audio(), video(), image(), document(), compressed() - Generate actual file bytes (PDF, MP4, PNG, etc.)
- **Cryptographic**: API keys, JWT tokens, certificate fingerprints, mnemonic phrases
- **Path**: Platform-specific paths (Linux/Windows/macOS) with home(), project_dir(), dev_dir()
- **Schema**: Built-in relational data generation with context, references, and transformations
- **Payment**: PayPal accounts, Ethereum addresses (more detailed than Datafaker)

**Implementation Priority**:
1. **High**: BinaryFile generators (useful for testing file uploads, storage systems)
2. **Medium**: Cryptographic data (API keys, tokens - common in modern APIs)  
3. **Low**: Platform-specific paths (niche use case)
4. **Defer**: Schema/Relational data (we already have ObjectGenerator with nested structures)

- [ ] **Additional Datafaker Providers** (Entertainment, Sports, Videogames - ~216 specialized types)
  - Entertainment: Movies, TV Shows, Books, Games (256 providers total in Datafaker)
  - Examples: StarTrek, GameOfThrones, HarryPotter, Pokemon, Simpsons, etc.
  - **Priority**: Low - niche use cases, implement on-demand

- [ ] **REST/gRPC API module**
  - Design and implement API module for programmatic job submission
  - Job execution and monitoring endpoints
  - Async job execution with status tracking
  - Web UI for job management (optional)

- [ ] **Advanced Features**
  - Support for Avro and Parquet formats
  - Schema registry integration (Confluent, AWS Glue)
  - Data masking and anonymization patterns
  - Foreign key relationship handling between structures
  - Plugin marketplace for custom generators and destinations
  - Metrics and monitoring integration (Prometheus, Grafana)

## Before Going Public 🚀

- [ ] **Complete open source licensing** (Partially Complete)
  - ✅ Added Apache 2.0 LICENSE file
  - ✅ Added license badge to README
  - ❌ Missing: Source file headers, NOTICE file, build.gradle.kts metadata, Spotless enforcement
  - Task: TASK-031-licensing.md

---

**Note**: This backlog is iteratively refined as the project evolves. Priority and scope may change based on user feedback and emerging requirements.
