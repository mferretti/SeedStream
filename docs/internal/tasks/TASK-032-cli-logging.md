# TASK-032: CLI Module - Verbose Logging Modes

**Status**: ✅ Complete  
**Priority**: P2 (Medium)  
**Phase**: 5 - CLI & Execution  
**Dependencies**: TASK-019 (CLI Commands)  
**Human Supervision**: NONE

---

## ✅ Completion Summary (March 7, 2026)

**Completed:**
- ✅ `--verbose` CLI flag implemented (INFO + DEBUG logging)
- ✅ `--debug` CLI flag implemented (TRACE level logging)
- ✅ `--trace-sample` parameter for controlling TRACE log volume
- ✅ Programmatic log level switching (INFO → DEBUG → TRACE)
- ✅ LogUtils utility class for sampling-based TRACE logging
- ✅ Strategic TRACE logs in key execution paths:
  - GenerationEngine worker threads (record generation with sampling)
  - ObjectGenerator field generation (sampled field-level details)
  - KafkaDestination async sends (sampled partition/offset logs)
  - DataStructureParser structure loading (structure details)
  - SeedResolver seed resolution (resolution steps by type)

**Implementation Highlights:**
- Default TRACE sample rate: 10% (safe for most use cases)
- Configurable sample rate: 1-100% via `--trace-sample` parameter
- Thread-safe sampling using thread-local Random instances
- Smart sampling prevents overwhelming log volume for large datasets
- System property propagation: `com.datagenerator.traceSampleRate`

**Implementation Paths:**
- `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java`
- `core/src/main/java/com/datagenerator/core/util/LogUtils.java`
- `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`
- `generators/src/main/java/com/datagenerator/generators/composite/ObjectGenerator.java`
- `destinations/src/main/java/com/datagenerator/destinations/kafka/KafkaDestination.java`
- `schema/src/main/java/com/datagenerator/schema/parser/DataStructureParser.java`
- `core/src/main/java/com/datagenerator/core/seed/SeedResolver.java`

**Usage Examples:**
```bash
# Default: INFO logging only
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml"

# Verbose: DEBUG logging (configuration and major operations)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose"

# Debug: TRACE logging with 10% sampling (default, safe for large datasets)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --debug"

# Debug with full TRACE (100% sampling, useful for small datasets)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --debug --trace-sample 100"

# Debug with minimal sampling (1%, useful for million-record runs)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --count 1000000 --debug --trace-sample 1"
```

---

## Objective

Implement verbose and debug logging modes for troubleshooting, controlled via CLI flags `--verbose` and `--debug`.

---

## Implementation Details

### Logging Levels
- **Default**: INFO (errors, warnings, progress)
- **Verbose** (`--verbose`): DEBUG (configuration, major operations)
- **Debug** (`--debug`): TRACE (all levels including sampled record-level details)

### TRACE Sampling
TRACE logs use statistical sampling to prevent overwhelming output:
- Default sample rate: 10% (generates ~10 log lines per 100 records)
- Configurable via `--trace-sample` parameter (1-100)
- Thread-safe sampling using thread-local Random instances
- System property: `com.datagenerator.traceSampleRate`

### Configuration
Logback with programmatic level switching:

```java
Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
if (debug) {
    root.setLevel(Level.TRACE);
    System.setProperty("com.datagenerator.traceSampleRate", String.valueOf(traceSample));
} else if (verbose) {
    root.setLevel(Level.DEBUG);
}
```

### Sampling Logic
```java
// In LogUtils utility class
public static boolean shouldTrace() {
    int sampleRate = getTraceSampleRate(); // From system property
    if (sampleRate >= 100) return true;
    if (sampleRate <= 0) return false;
    return ThreadLocal<Random>.get().nextInt(100) < sampleRate;
}

// Usage in code
if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
    log.trace("Detailed operation: {}", details);
}
```

### TRACE Log Locations
1. **GenerationEngine**: Worker thread record generation
   - `Worker 0 generated record 1: {name=John, city=Milan}`
   
2. **ObjectGenerator**: Field-level generation for nested structures
   - `Generated field address: city = Milan`
   
3. **KafkaDestination**: Async record send confirmations
   - `Sent record to partition 2 offset 12345`
   
4. **DataStructureParser**: Structure loading and field parsing
   - `Parsed structure: name=address, fields=[name, city], geolocation=italy`
   
5. **SeedResolver**: Seed resolution by type (embedded/file/env/remote)
   - `Resolved embedded seed value: 12345`
   - `Read seed from file: path=/tmp/seed.txt, content=12345, seed=12345`
   - `Resolved env seed: variable=SEED, value=12345, seed=12345`

### Output Format
```
14:23:45.123 [main] INFO  c.d.cli.ExecuteCommand - Starting data generation job
14:23:45.124 [main] DEBUG c.d.cli.ExecuteCommand - Debug mode enabled - log level set to TRACE (sample rate: 10%)
14:23:45.130 [main] DEBUG c.d.s.p.DataStructureParser - Parsing data structure from: config/structures/address.yaml
14:23:45.145 [main] TRACE c.d.s.p.DataStructureParser - Parsed structure: name=address, fields=[name, city], geolocation=italy
14:23:45.200 [main] INFO  c.d.cli.ExecuteCommand - Generating 100 records...
14:23:45.210 [worker-0] TRACE c.d.c.e.GenerationEngine - Worker 0 generated record 5: {name=John, city=Milan}
14:23:45.220 [worker-0] TRACE c.d.g.c.ObjectGenerator - Generated field address: name = John
14:23:45.500 [main] INFO  c.d.cli.ExecuteCommand - Generation complete!
```

---

## Acceptance Criteria

- ✅ `--verbose` enables DEBUG logging (configuration and major operations)
- ✅ `--debug` enables TRACE logging with configurable sampling
- ✅ `--trace-sample` controls sampling rate (1-100%, default 10%)
- ✅ Default mode shows only INFO+ (errors, warnings, progress)
- ✅ Configurable via CLI flags
- ✅ Does not impact performance significantly (sampling prevents overhead)
- ✅ Thread-safe sampling implementation
- ✅ Strategic TRACE logs in key execution paths

---

**Completion Date**: March 7, 2026
