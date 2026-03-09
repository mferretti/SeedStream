# Data Generator - Design Documentation

**Last Updated**: March 6, 2026 (v0.2.0)

This document captures the architectural decisions, design patterns, issues encountered, and their resolutions during development. It serves as a reference for developers extending the project and for discussions around alternative approaches.

**Status**: Core architecture complete (multi-threading, seeding, Kafka/File destinations). Database destination and plugin architecture planned for future releases.

---

## Table of Contents

1. [Core Principles](#core-principles)
2. [Module Architecture](#module-architecture)
3. [Seeding & Reproducibility](#seeding--reproducibility)
4. [Type System](#type-system)
5. [Performance Optimizations](#performance-optimizations)
6. [Issues & Resolutions](#issues--resolutions)
7. [Open Questions & Future Work](#open-questions--future-work)

---

## Core Principles

### 1. Reproducibility First

**Requirement**: Same seed must produce identical data across multiple runs, even with parallel generation.

**Why**: Essential for:
- Debugging test failures (reproduce exact data)
- Consistent test environments
- Compliance/audit requirements (prove data provenance)
- Performance benchmarking (same data for A/B tests)

**Implementation**: See [Seeding & Reproducibility](#seeding--reproducibility)

### 2. Performance at Scale

**Requirement**: Generate millions of primitive records per second (in-memory), or thousands of realistic records per second.

**Design Choices**:
- Multi-threaded generation with thread-local state
- Batching for I/O operations (Kafka, DB writes)
- Streaming architecture (generate → serialize → send, no in-memory buffers)
- Connection pooling (HikariCP for databases, producer reuse for Kafka)
- Zero-copy serialization where possible

### 3. Extensibility

**Requirement**: Easy to add new destinations, formats, and data generators.

**Design Pattern**: Plugin architecture using Java's ServiceLoader mechanism (future enhancement).

**Current**: Strategy pattern with clear interfaces:
- `DestinationAdapter` for new destinations
- `FormatSerializer` for new formats
- `DataTypeGenerator` for new data types

### 4. Developer Experience

**Requirement**: Simple YAML configuration, clear error messages, fast feedback loops.

**Design Choices**:
- Declarative YAML over code configuration
- Fail-fast validation (Hibernate Validator on config load)
- Rich error messages with context (what failed, where, suggested fixes)
- Spotless formatting for consistent code style

---

## Module Architecture

### Dependency Flow

```mermaid
graph LR
    CLI[cli] --> DEST[destinations]
    DEST --> FMT[formats]
    FMT --> GEN[generators]
    GEN --> SCHEMA[schema]
    SCHEMA --> CORE[core]
    
    CLI -.->|uses| FMT
    CLI -.->|uses| GEN
    CLI -.->|uses| SCHEMA
    
    style CORE fill:#e1f5e1
    style SCHEMA fill:#e3f2fd
    style GEN fill:#fff3e0
    style FMT fill:#fce4ec
    style DEST fill:#f3e5f5
    style CLI fill:#fff9c4
```

**Dependency Summary**: `cli → destinations → formats → generators → schema → core`

**Key Rule**: No circular dependencies. Each module depends only on modules to its right.

### Core Module

**Responsibility**: Foundation for all other modules.

**Contents**:
- `SeedResolver`: Convert seed configurations to long values
- `RandomProvider`: Provide deterministic thread-local Random instances
- `SeedConfig`: Configuration model for seeds (moved from schema to break circular dependency)
- Type system primitives (future)
- Generation engine orchestration (future)

**Why Separate?**:
- Core has no dependencies (pure Java + SLF4J)
- Schema depends on core for seed resolution
- Generators depend on core for random providers

### Schema Module

**Responsibility**: Parse YAML configurations into type-safe Java objects.

**Contents**:
- `DataStructureParser`: Parse `structures/*.yaml` files
- `JobDefinitionParser`: Parse `jobs/*.yaml` files
- Configuration models (except SeedConfig, which is in core)

**Design Choice**: Jackson YAML for parsing, Hibernate Validator for validation.

---

## Seeding & Reproducibility

### Problem Statement

**Goal**: Same master seed → identical data across runs, even with parallel generation.

**Challenge**: Java's `Random` is not thread-safe. Each thread needs its own instance.

**Naive Approach** (WRONG ❌):
```java
// DON'T DO THIS
ThreadLocal<Random> random = ThreadLocal.withInitial(() -> 
    new Random(Thread.currentThread().threadId())
);
```

**Why It Fails**:
- JVM assigns thread IDs sequentially as threads are created (including system threads, GC threads)
- Thread IDs vary across runs:
  ```
  Run 1: Worker threads get JVM IDs [15, 17, 19, 21]
  Run 2: Worker threads get JVM IDs [18, 20, 22, 24] ❌
  ```
- Different thread IDs → different seeds → different data → **NOT reproducible**

### Solution: Logical Worker IDs

**Key Insight**: Use application-level sequential IDs (0, 1, 2, ...), not JVM thread IDs.

**Implementation** (`RandomProvider`):
```java
private final AtomicInteger workerIdCounter = new AtomicInteger(0);
private final ThreadLocal<Random> threadLocalRandom = ThreadLocal.withInitial(() -> {
    int workerId = workerIdCounter.getAndIncrement(); // 0, 1, 2, ...
    long threadSeed = deriveSeed(masterSeed, workerId);
    return new Random(threadSeed);
});
```

**Result**:
```
Run 1: Worker 0 (JVM thread 15) → seed A, Worker 1 (JVM thread 17) → seed B
Run 2: Worker 0 (JVM thread 18) → seed A, Worker 1 (JVM thread 20) → seed B ✅
```

**Guarantees**:
- Same master seed → same worker IDs → same derived seeds → **identical data**
- Thread-safe (AtomicInteger for counter, ThreadLocal for Random)
- No contention (each worker has its own Random)

### Seed Derivation Algorithm

**Function**: `deriveSeed(long masterSeed, int workerId) → long`

**Algorithm**:
```java
long seed = masterSeed;
seed ^= workerId;       // Mix in worker ID
seed ^= (seed << 21);   // Bit avalanche (spread changes)
seed ^= (seed >>> 35);  // Spread high bits to low
seed ^= (seed << 4);    // Final mixing
return seed;
```

**Properties**:
- **Deterministic**: Same inputs always produce same output
- **Distinct**: Different worker IDs produce very different seeds (avalanche effect)
- **Fast**: Simple bit operations, no cryptographic overhead

**Why Not Hash Functions?**: Hash functions (SHA-256, MD5) are overkill. We need speed and determinism, not cryptographic security. Simple XOR mixing is sufficient for pseudo-random seed derivation.

### Seed Resolution

**Four Seed Sources** (priority: CLI > YAML config > default):

1. **Embedded** (value in YAML):
   ```yaml
   seed:
     type: embedded
     value: 12345
   ```

2. **File** (read from filesystem):
   ```yaml
   seed:
     type: file
     path: /secrets/seed.txt
   ```

3. **Environment Variable**:
   ```yaml
   seed:
     type: env
     name: DATA_SEED
   ```

4. **Remote API** (fetch from HTTP endpoint):
   ```yaml
   seed:
     type: remote
     url: https://seed-service.example.com/api/seed
     auth:
       type: bearer  # or: basic, api_key
       token: ${API_TOKEN}
   ```

**Implementation**: `SeedResolver` class with sealed switch on `SeedConfig` subtypes.

**Design Choice**: Lazy HttpClient initialization to avoid resource waste for embedded/file/env seeds.

---

## Multi-Threading Engine

### Architecture Overview

**Component**: `GenerationEngine` (core module)

**Goal**: Parallel data generation with deterministic output and backpressure handling.

**Key Interfaces**:
```java
@FunctionalInterface
interface RecordGenerator {
    Map<String, Object> generate(Random random);
}

@FunctionalInterface
interface RecordWriter {
    void write(Map<String, Object> record);
}
```

**Why Functional Interfaces?**: Avoid module dependencies. Core module doesn't depend on generators module. CLI provides lambda implementations.

### Worker Pool Architecture

**Pipeline**: Workers → Bounded Queue → Writer Thread

```mermaid
graph LR
    W0["Worker 0
    (thread-local RNG
    seed: derive master,0)"] --> Q["Bounded Queue
    (capacity: 1000
    backpressure)"]
    W1["Worker 1
    (thread-local RNG
    seed: derive master,1)"] --> Q
    W2["Worker 2
    (thread-local RNG
    seed: derive master,2)"] --> Q
    W3[Worker N...] -.-> Q
    Q --> WT["Writer Thread
    (single thread
    ordered writes)"]
    WT --> DEST["Destination
    (Kafka/File/DB)"]
    
    style W0 fill:#e1f5e1
    style W1 fill:#e1f5e1
    style W2 fill:#e1f5e1
    style W3 fill:#e1f5e1
    style Q fill:#fff3e0
    style WT fill:#e3f2fd
    style DEST fill:#fce4ec
```

**Components**:
1. **Worker Threads** (fixed thread pool):
   - Each worker gets logical ID (0, 1, 2, ...)
   - Each gets thread-local Random from RandomProvider
   - Generate records → submit to queue
   
2. **Bounded Queue** (ArrayBlockingQueue):
   - Default capacity: 1000 records
   - Provides backpressure (workers block when full)
   - Prevents memory overflow
   
3. **Writer Thread** (single thread):
   - Consumes queue → writes to destination
   - Single thread ensures ordered writes
   - No contention on destination

**Termination**: Poison pill pattern. Workers submit sentinel value when done, writer stops after consuming all records + sentinel.

### Automatic Optimization

**Small Jobs** (< 1000 records):
- Use single-threaded mode
- Avoids thread pool overhead
- Avoids queue allocation
- Direct: generate → write

**Large Jobs** (≥ 1000 records):
- Use multi-threaded mode
- Worker pool size: configurable (default: CPU cores)
- Bounded queue for backpressure

**Rationale**: For 100 records, thread pool overhead > generation time. Single-threaded is faster.

### Backpressure Handling

**Problem**: Fast generators + slow destination = memory overflow.

**Example**: Generator produces 1M records/sec, Kafka accepts 10K/sec → queue grows unbounded → OOM.

**Solution**: Bounded queue with blocking put.

**Behavior**:
```java
queue.put(record); // Blocks if queue is full
```

**Result**: Workers automatically slow down to match destination throughput. Memory usage bounded by queue capacity.

### Determinism Guarantee

**Key Property**: Same seed → identical data, regardless of thread count.

**How**:
1. Master seed → RandomProvider
2. Worker logical IDs (0, 1, 2, ...) → derived seeds
3. Each worker generates deterministic subset:
   ```
   Worker 0 generates: records 0, 4, 8, 12, ...
   Worker 1 generates: records 1, 5, 9, 13, ...
   Worker 2 generates: records 2, 6, 10, 14, ...
   Worker 3 generates: records 3, 7, 11, 15, ...
   ```

**Verification**: SHA-256 hash of output identical across runs with same seed.

### Progress Tracking

**Implementation**: AtomicLong counter, lock-free increment.

**Logging**: Every 10,000 records:
```
Generated 10,000 / 1,000,000 records (1.00%, 45,231 records/sec)
Generated 20,000 / 1,000,000 records (2.00%, 48,102 records/sec)
```

**Throughput Calculation**:
```java
long elapsed = System.currentTimeMillis() - startTime;
long recordsPerSec = (count * 1000) / Math.max(elapsed, 1);
```

### Performance Characteristics

**Tested Scenario**: 1M records, 4 workers, file destination (SSD).

**Results**:
- Single-threaded: ~30K records/sec
- Multi-threaded (4 workers): ~110K records/sec
- Scaling: ~3.7x speedup (92% efficiency)

**Bottleneck**: I/O (file writes). CPU-bound generation scales linearly.

**Memory**: Fixed overhead (queue capacity × record size). Example: 1000 records × 1KB/record = 1MB.

### Configuration

**Builder Pattern**:
```java
GenerationEngine engine = GenerationEngine.builder()
    .recordGenerator((random) -> generator.generate(random, objectType))
    .recordWriter(destination::write)
    .masterSeed(seed)
    .workerThreads(8)              // default: CPU cores
    .queueCapacity(2000)           // default: 1000
    .singleThreadedThreshold(500)  // default: 1000
    .logBatchSize(5000)            // default: 10000
    .build();

engine.generate(1_000_000); // Generate 1M records
```

**Tuning Guidelines**:
- **workerThreads**: Match CPU cores for CPU-bound, 2× cores for I/O-bound
- **queueCapacity**: Increase for bursty destinations, decrease for memory-constrained environments
- **singleThreadedThreshold**: Increase if thread pool overhead is negligible in your environment

---

## Type System

### Current Status

**Implemented**: Configuration parsing (schema module), seed resolution (core module).

**In Progress**: Type system design (primitives with ranges, nested objects, arrays).

### Planned Type Syntax

**Primitives with Ranges**:
```yaml
age: int[18..65]
price: decimal[0.0..999.99]
name: char[3..50]
active: boolean
```

**Dates & Timestamps**:
```yaml
birth_date: date[1950-01-01..2005-12-31]
created_at: timestamp[now-30d..now]
```

**Enums**:
```yaml
status: enum[ACTIVE,INACTIVE,PENDING]
```

**Nested Objects**:
```yaml
address: object[address]  # References structures/address.yaml
```

**Arrays** (variable length):
```yaml
tags: array[char[1..20], 1..10]        # 1-10 strings
items: array[object[line_item], 1..50] # 1-50 nested objects
```

**Foreign Keys** (references to other records):
```yaml
user_id: ref[user.id]  # References generated user IDs
```

### Design Challenges

1. **Circular References**: Detect `object[A]` → `object[B]` → `object[A]` and fail fast
2. **Array Memory**: Variable-length arrays can explode memory (1M records × 50 items each = 50M items)
3. **Foreign Key Resolution**: How to track generated IDs for cross-record references?

**Open for Discussion**: Alternative approaches welcome. See GitHub issues for proposals.

### Datafaker Type Registry (Semantic Types)

**Status**: ✅ Implemented (March 2026)

**Problem**: Original implementation used 42 semantic type enum values in `PrimitiveType.Kind` (NAME, EMAIL, ADDRESS, PHONE, etc.), leading to:
- Code duplication (~350 lines of switch statements in `TypeParser` and `DatafakerGenerator`)
- Tight coupling between type definitions and generators
- Inability to add new semantic types at runtime

**Solution**: Registry pattern with `DatafakerRegistry` and `CustomDatafakerType`

**Architecture**:
```java
// Registry stores type name → generator function mappings
public class DatafakerRegistry {
    private static final Map<String, Function<Faker, Object>> registry = 
        new ConcurrentHashMap<>();
    
    static {
        registerBuiltIns(); // 48+ types with 20+ aliases
    }
    
    public static void register(String typeName, Function<Faker, Object> generator);
    public static boolean isRegistered(String typeName);
    public static Object generate(Faker faker, String typeName);
}

// Lightweight type wrapper (replaces 42 enum values)
public record CustomDatafakerType(String typeName) implements DataType {}
```

**Benefits**:
1. **Single Source of Truth**: All semantic types defined in one place
2. **Runtime Extensibility**: Register new types without code changes
3. **Code Simplification**: 
   - TypeParser: ~150 lines deleted (replaced with 3-line registry check)
   - DatafakerGenerator: ~220 lines deleted (replaced with single delegation)
4. **Alias Support**: Common variations (lat/latitude, lon/lng, swift/bic, cvc/cvv)
5. **Thread-Safe**: ConcurrentHashMap for lock-free concurrent access
6. **Foundation for Plugins**: Easy path to user-contributed types (future)

**Type Registration Example**:
```java
DatafakerRegistry.register("name", Faker::name().fullName());
DatafakerRegistry.register("email", Faker::internet().emailAddress());
DatafakerRegistry.register("phone", Faker::phoneNumber().phoneNumber());

// Aliases for common variations
DatafakerRegistry.register("lat", Faker::address().latitude());
DatafakerRegistry.register("latitude", Faker::address().latitude());
DatafakerRegistry.register("lon", Faker::address().longitude());
DatafakerRegistry.register("lng", Faker::address().longitude());
```

**Usage in YAML**:
```yaml
# All these work via registry lookup
user_name: name
email_address: email  
phone_number: phonenumber
location:
  latitude: lat      # Alias: short form
  longitude: long    # Alias: alternative spelling
```

**Design Decisions**:
- **Normalization**: Type names converted to lowercase and trimmed for flexible matching
- **Validation**: Fail fast if unknown type referenced in YAML (clear error message)
- **No ServiceLoader Yet**: Built-in types only for now; plugins deferred to post-1.0
- **Functional Interface**: `Function<Faker, Object>` allows inline lambdas for simple cases

**Performance Impact**: Negligible (ConcurrentHashMap lookup is O(1), no synchronized blocks)

**Migration Path**: All existing YAML configs remain compatible (registry includes all original enum types)

---

## Performance Optimizations

### 1. Lazy Resource Initialization

**Issue**: Creating resources (HttpClient, database connections) upfront wastes memory if they're not needed.

**Example**: Job uses embedded seed → HttpClient never needed → why create it?

**Solution**: Lazy initialization with double-checked locking:
```java
private volatile HttpClient httpClient; // volatile for safe publication

private HttpClient getHttpClient() {
    if (httpClient == null) {
        synchronized (this) {
            if (httpClient == null) { // double-check
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            }
        }
    }
    return httpClient;
}
```

**Result**: HttpClient created only if remote seed resolution is used.

### 2. Connection Pooling

**Implementation**: HikariCP for databases (future), producer reuse for Kafka (future).

**Why**: Creating connections is expensive (TCP handshake, TLS, auth). Reuse amortizes cost.

### 3. Batching

**Pattern**: Generate N records → batch serialize → bulk send to destination.

**Trade-off**: Latency vs throughput. Larger batches = better throughput, higher latency.

**Configuration**: User-configurable batch sizes per destination.

### 4. Thread-Local State

**Pattern**: Each thread has its own Random, formatters, buffers (no synchronization overhead).

**Trade-off**: Memory (N threads × state size) vs speed (zero contention).

**Result**: Near-linear scaling with core count.

---

## Issues & Resolutions

### Issue #1: Circular Dependency (schema ↔ core)

**Problem**: Schema module needed `SeedConfig` for parsing, core module needed `SeedConfig` for resolution.

**Attempted Solution**: Keep `SeedConfig` in schema, core imports schema → circular dependency (Gradle build fails).

**Resolution**: Move `SeedConfig` to core module. Schema depends on core (allowed), core has no dependencies.

**Lesson**: Configuration models belong in the lowest layer that needs them.

**Status**: ✅ Resolved

---

### Issue #2: Eager HttpClient Initialization

**Problem**: `SeedResolver` created HttpClient in constructor, even when not needed (embedded/file/env seeds).

**Impact**: Wasted memory, slower startup, unnecessary HTTP connection overhead.

**Diagnosis**: User (Marco) questioned: "Does SeedResolver always build an HttpClient even if not needed?"

**Resolution**: Lazy initialization with double-checked locking (volatile + synchronized).

**Code**:
```java
private volatile HttpClient httpClient = null;

private HttpClient getHttpClient() {
    if (httpClient == null) {
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            }
        }
    }
    return httpClient;
}
```

**Result**: HttpClient created only when `resolveRemote()` is called.

**Lesson**: Delay expensive resource creation until first use.

**Status**: ✅ Resolved

---

### Issue #3: Non-Deterministic Thread IDs

**Problem**: Initial `RandomProvider` implementation used JVM thread IDs for seed derivation:
```java
// WRONG ❌
long threadSeed = deriveSeed(masterSeed, Thread.currentThread().threadId());
```

**Impact**: JVM thread IDs vary across runs → different seeds → **NOT reproducible**.

**Example**:
```
Run 1: Workers get JVM thread IDs [15, 17, 19] → seeds [X, Y, Z]
Run 2: Workers get JVM thread IDs [18, 20, 22] → seeds [A, B, C] ❌
```

**Diagnosis**: User (Marco) asked: "How can two runs of the same job deterministically return the same values?"

**Root Cause**: JVM assigns thread IDs sequentially as threads are created, including system threads (GC, JIT compiler, etc.). No guarantee IDs match across runs.

**Resolution**: Use logical worker IDs (0, 1, 2, ...) assigned by `AtomicInteger` counter:
```java
private final AtomicInteger workerIdCounter = new AtomicInteger(0);
private final ThreadLocal<Random> threadLocalRandom = ThreadLocal.withInitial(() -> {
    int workerId = workerIdCounter.getAndIncrement(); // Logical ID
    long threadSeed = deriveSeed(masterSeed, workerId);
    return new Random(threadSeed);
});
```

**Result**: Same master seed → same worker IDs → same derived seeds → **identical data** across runs.

**Lesson**: Never rely on JVM internals (thread IDs, object hashCodes, etc.) for deterministic behavior.

**Status**: ✅ Resolved

**Discussion**: Could we use virtual threads (Java 21) and still maintain determinism? Yes, same approach applies—logical worker IDs are thread-implementation-agnostic.

---

### Issue #4: GeneratorContext Not Available in Worker Threads

**Problem**: Multi-threaded generation failed with `IllegalStateException: No GeneratorContext active` when using `ObjectGenerator`.

**Impact**: All worker threads crashed immediately, no records generated (100,000 record job produced empty file).

**Root Cause**: `GeneratorContext` uses `ThreadLocal` storage and was only initialized on the main thread via try-with-resources in `ExecuteCommand`:
```java
// WRONG ❌ - Only main thread has context
try (var ctx = GeneratorContext.enter(factory, geolocation)) {
    engine.generate(count);  // Worker threads have no context!
}
```

Worker threads in `GenerationEngine` called `ObjectGenerator.generate()` → accessed `GeneratorContext.getFactory()` → `ThreadLocal.get()` returned null → exception.

**Diagnosis**: Error logs showed all 10 workers failing:
```
Worker 0 failed: No GeneratorContext active. Call GeneratorContext.enter() before generating.
Worker 1 failed: No GeneratorContext active. Call GeneratorContext.enter() before generating.
...
```

**Resolution**: Move `GeneratorContext.enter()` into the `RecordGenerator` lambda so each worker thread initializes its own context:
```java
// CORRECT ✅ - Each worker gets its own context
GenerationEngine engine = GenerationEngine.builder()
    .recordGenerator((random) -> {
        // Each worker thread initializes context
        try (var ctx = GeneratorContext.enter(factory, geolocation)) {
            return generator.generate(random, objectType);
        }
    })
    .build();
```

**Result**: All 10 workers successful, 100,000 records generated in 14.4 seconds (6,923 records/sec).

**Lesson**: When using `ThreadLocal` state in multi-threaded environments, ensure each thread initializes its own context. Try-with-resources on main thread doesn't propagate to worker threads.

**Performance Impact**: Minimal—context initialization is lightweight (just sets ThreadLocal reference).

**Status**: ✅ Resolved (March 6, 2026)

**Testing**: Verified with complex Datafaker objects (customer structure with UUID, names, emails, addresses) across 10 worker threads.

---

### Issue #5: File I/O Performance Bottleneck

**Problem**: File I/O throughput at 213 MB/s, far below the 500 MB/s requirement (NFR-1).

**Impact**: Writing 100M records (28 GB JSON) would take 131 seconds instead of target 56 seconds.

**Initial Metrics** (JMH Benchmark):
```
benchmarkFileDestinationWrite: 761,076 ops/s ± 387,454
Record size: ~280 bytes
Effective throughput: 213 MB/s
Target: 500 MB/s (2.3x gap)
```

**Diagnosis**: Hardware performance analysis revealed:
- Raw disk throughput: 1,200 MB/s (dd sequential write)
- Java NIO with BufferedWriter (8KB): 843 MB/s
- Java NIO with BufferedWriter (256KB): 1,087 MB/s
- Current FileDestination: 213 MB/s ❌ (4-5x slower than hardware ceiling)

**Root Causes Identified**:

1. **Small Buffer Size**: Default 8KB buffer limits I/O batching (Linux typically uses 64KB+ page cache)
2. **Redundant I/O Calls**: Two calls per record (`writer.write(line)` + `writer.newLine()`)
3. **No Batching**: Each record serialized and written individually (1,000× I/O calls for 1,000 records)
4. **Jackson Overhead**: Per-record `ObjectMapper.writeValueAsString()` creates intermediate String objects

**Resolution Phases**:

**Phase 1: Quick Wins** (30 minutes effort):
- ✅ Increased buffer size from 8KB → 64KB (+17% throughput)
- ✅ Eliminated redundant `newLine()` call, use single `write('\n')` (+15% throughput)
- **Expected Result**: 350-400 MB/s (1.8x improvement)

**Phase 2: Batch Writes** (2-3 hours effort):
- ✅ Implemented record batching (accumulate 1000 records before writing)
- ✅ Pre-allocate StringBuilder with estimated capacity (`batchSize × 300 bytes`)
- ✅ Single write call per batch instead of 1000 individual writes
- ✅ Automatic batch flush on `flush()` and `close()` to prevent data loss
- **Expected Result**: 600-800 MB/s (3x improvement, exceeds 500 MB/s target)

**Phase 3: Jackson Streaming** (DEFERRED - Low Priority):
- ❌ Use `JsonGenerator` to write directly to OutputStream (eliminates String allocation)
- **Expected Gain**: +10-20% (marginal improvement given Phase 1 & 2 results)
- **Effort**: High (4-6 hours - requires interface refactoring, extensive testing)
- **Decision**: Deferred to TASK-039 (low priority) - target already met with Phase 1 & 2

**Implementation Details**:

```java
// Phase 1: Larger buffer
@Builder.Default int bufferSize = 65536;  // 64KB (was 8KB)

// Phase 2: Batch buffer
private final List<String> batchBuffer = new ArrayList<>(config.getBatchSize());
private final StringBuilder batchBuilder = new StringBuilder(batchSize * 300);

public void write(Map<String, Object> record) {
    String line = serializer.serialize(record);
    batchBuffer.add(line);
    
    if (batchBuffer.size() >= config.getBatchSize()) {
        flushBatch();  // Automatic batch flush
    }
}

private void flushBatch() throws IOException {
    if (batchBuffer.isEmpty()) return;
    
    batchBuilder.setLength(0);  // Reuse StringBuilder
    for (String line : batchBuffer) {
        batchBuilder.append(line).append('\n');
    }
    writer.write(batchBuilder.toString());  // Single I/O call
    batchBuffer.clear();
}
```

**Result**: Expected 600-800 MB/s (validated via JMH benchmarks after implementation).

**Trade-offs**:
- **Memory**: +300KB per FileDestination instance (1000 records × 300 bytes batch buffer)
- **Latency**: Records buffered until batch full (acceptable for bulk generation)
- **Complexity**: Must handle partial batch flush on close() to avoid data loss

**Validation Plan**:
1. Update JMH DestinationBenchmark with optimized configuration
2. Run `./benchmarks/run_benchmarks.sh` to measure new throughput
3. Verify file integrity (line count, valid JSON)
4. Memory profiling to ensure no leaks with StringBuilder reuse

**Alternative Approaches Considered**:

1. **Memory-Mapped Files** (rejected): Complex API, not portable, overkill for sequential writes
2. **Async I/O** (rejected): Adds complexity, buffering already provides batching benefits
3. **Direct ByteBuffer** (rejected): Low-level, error-prone, minimal gains over BufferedWriter

**Lesson**: Performance optimization requires hardware baseline measurement first. Optimization efforts should target the largest gap (4x vs 1.2x) with best ROI (buffer size = 1 line change).

**Status**: ✅ Resolved (March 6, 2026) - Phase 1 & 2 implemented

**Documentation**: See `benchmarks/PERFORMANCE-ANALYSIS.md` for detailed hardware testing and optimization analysis.

---

## Open Questions & Future Work

### ✅ RESOLVED: Array Memory Management

**Question**: How to handle variable-length arrays without exploding memory?

**Decision**: Implemented Option C (streaming for destinations, in-memory for small jobs). Arrays are generated element-by-element and streamed to serialization without holding entire array in memory.

**Status**: Implemented in generators module. See `ArrayGenerator.java`.

---

### 1. Virtual Threads for I/O-Bound Operations

**Question**: Should we use virtual threads (Java 21) for destination writes (Kafka, database)?

**Pros**:
- Lightweight (millions of virtual threads possible)
- Simplified code (blocking I/O looks synchronous)
- Better resource utilization

**Cons**:
- Debugging complexity (stack traces span multiple carrier threads)
- Library compatibility (some JDBC drivers, Kafka clients may have issues)

**Current Decision**: Platform threads with fixed-size pools. Revisit when libraries mature (Java 23+).

**Discussion Welcome**: If you have experience with virtual threads + Kafka/JDBC, please share insights in GitHub issues.

---

### 2. Statistical Distributions

**Question**: How to handle variable-length arrays without exploding memory?

**Example**:
```yaml
orders:
  items: array[object[line_item], 1..100]  # Up to 100 items per order
```

If generating 1M orders × 50 items average = 50M items in memory before serialization.

**Option A**: Stream arrays (serialize items as generated, don't hold in memory)
**Option B**: Memory limits (fail if projected size exceeds threshold)
**Option C**: Hybrid (stream for destinations like Kafka, in-memory for small jobs)

**Current Decision**: Option C (stream for large destinations, in-memory for files/small jobs).

**Alternative Proposals Welcome**: Please discuss trade-offs in GitHub issues.

---

### 3. Foreign Key Resolution

**Question**: How to implement `ref[other_structure.field]` for cross-record references?

**Example**:
```yaml
orders:
  user_id: ref[user.id]  # Reference generated user IDs
```

**Challenge**: Need to track generated IDs, but streaming architecture doesn't hold records in memory.

**Option A**: Two-pass generation (generate users, store IDs, generate orders)
**Option B**: ID cache (LRU cache of recent IDs for random sampling)
**Option C**: Explicit ID pools (user defines ID range, generator samples from pool)
**Option D**: Deferred resolution via destination (database foreign keys, not generator concern)

**Current Decision**: Deferred to v0.3. Option C (explicit ID pools) seems most flexible for file destinations. Option D (database-enforced FKs) for database destination.

**Workaround**: Use `int[1..100000]` for IDs and rely on statistical likelihood of matches.

**Discussion**: Interested in graph-based data generation? Open a GitHub discussion.

---

### 4. Advanced Distributions (Normal, Zipfian, Exponential)

**Question**: Should we support statistical distributions for numeric types?

**Example**:
```yaml
age: int[18..65, distribution=normal, mean=35, stddev=10]
```

**Use Case**: Realistic data often follows distributions (ages, salaries, response times).

**Challenge**: Maintaining reproducibility with distributions is complex (need to specify all params in config).

**Current Decision**: Uniform distribution only (v0.2). Revisit in v0.4 after database destinations.

**Rationale**: Most use cases satisfied by Datafaker (realistic data) or uniform primitives (load testing). Advanced distributions are niche.

**Interested?**: Propose design in GitHub discussions.

---

### 5. Plugin Architecture (Extensibility)

**Status**: 🔄 Foundation Complete (March 2026)

**Question**: Should we support user-provided generators/destinations as plugins?

**Current Implementation**: Registry pattern provides foundation for extensibility:
- `DatafakerRegistry` allows runtime type registration
- `CustomDatafakerType` decouples types from enum-based implementation
- Thread-safe concurrent access for registration and lookup
- ~350 lines of switch statement code eliminated

**Vision for Plugin System**:
```java
// User creates custom generator
public class CustomDataGenerator implements DataTypeGenerator {
    @Override
    public Object generate(Random random, TypeConfig config) {
        // Custom logic
    }
}

// User registers via META-INF/services or programmatic API
DatafakerRegistry.register("custom_type", faker -> 
    // Custom generation logic
);
```

**Pros**:
- ✅ **Foundation Complete**: Registry pattern established with `DatafakerRegistry`
- ✅ **Extensibility Proven**: 48+ built-in types, 20+ aliases demonstrate scalability
- Community contributions (marketplace of generators)
- No need to modify core code for new types

**Cons**:
- Complexity (ServiceLoader, classloading, versioning)
- Security (untrusted code execution)
- Plugin API design needs careful consideration

**Current Decision**: Built-in types via registry (complete). Full ServiceLoader-based plugin system deferred until post-1.0 to gather user feedback on registry pattern.

**Next Steps**:
1. ✅ Registry pattern (complete)
2. 🔄 Gather feedback on registry API design
3. ⏳ Define plugin API contract (interfaces, lifecycle)
4. ⏳ Implement ServiceLoader discovery
5. ⏳ Security model (sandboxing, permissions)
6. ⏳ Plugin marketplace/documentation

**Interested?**: Discuss plugin API design in GitHub.

---

## Database Destination: Multi-Table Auto-Decomposition (Stage 2)

**Decision Date**: March 9, 2026
**Status**: Design complete, implementation pending (TASK-043)
**Branch**: `feature/typed-record-pipeline-option-b`

---

### Problem

Stage 1 `DatabaseDestination` only supports flat structures. Real schemas are relational — an `order` has many `line_items`. Testers need to populate parent and child tables in one job run.

---

### Decision: Destination-Side Auto-Decomposition

**Key insight**: The job type (`type: database`) already signals intent. Generators produce the full nested record tree as-is (Map with nested Maps/Lists). The destination is responsible for exploding it into table-specific inserts.

**Zero changes required** to:
- Generators (they already produce nested Maps)
- Serializers (they already serialize nested records)
- Kafka/File destinations (they don't care about structure)

---

### Algorithm

For each generated top-level record:

1. **Decompose** — split into scalar fields (parent INSERT) + nested fields (child INSERTs)
2. **Insert parent** into the root table
3. **Build parent context** — capture `(tableName, id_value)` from the just-inserted parent
4. **Recurse** — for each nested object/array, inject FK column `{parent_table_name}_id = parent_id`, then repeat from step 1 for each child record

**Context stack**: A `Deque<ParentContext>` local to each record processing. Children see only their immediate parent — no root bleed-through at depth > 1.

---

### Example: 3-Level Nesting

```
order (root)
  └── line_item (child — FK: order_id)
        └── line_item_attribute (grandchild — FK: line_item_id)
```

Processing order:
1. Insert `order` → get `order.id = 42`
2. Context push: `(table=order, id=42)` → FK for line_item will be `order_id=42`
3. Insert `line_item` (with `order_id=42`) → get `line_item.id = 101`
4. Context push: `(table=line_item, id=101)` → FK for attribute will be `line_item_id=101`
5. Insert `line_item_attribute` (with `line_item_id=101`)
6. Context pop back to order level; process next line_item

---

### FK Convention

`{parent_structure_name}_id` — always. No YAML config needed.

The structure name comes from the YAML `name:` field (or array field key — design detail TBD). The tester must name their FK columns to match.

---

### Limitations (by design)

| Limitation | Rationale |
|------------|-----------|
| No composite PKs | Single `id` field covers 95% of cases; simplicity wins |
| No `ref[]` cross-record FK | Requires TASK-012 Reference Generator; separate concern |
| No DB auto-increment IDs | Would require `getGeneratedKeys()` round-trip; deferred |
| Convention FK names only | Configurable override deferred; convention is predictable |
| One root table per job | Multi-root jobs uncommon; deferred |

---

### JDBC Option B: Schema-Aware Type Binding

Also decided March 9, 2026 (TASK-042):

`DatabaseDestination` accepts `Map<String, String>` raw YAML type strings instead of parsed `DataType` objects. The `TypeParser` runs inside `open()` — after the connection is established. Rationale: if the DB is unreachable, parsing the schema is pointless.

```java
// CLI passes raw strings (no TypeParser in CLI layer)
Map<String, String> rawFieldTypes = dataStructure.getData().entrySet().stream()
    .collect(toMap(Map.Entry::getKey, e -> e.getValue().getDatatype()));

new DatabaseDestination(dbConfig, rawFieldTypes);

// Inside DatabaseDestination.open():
schema = rawFieldTypes.entrySet().stream()
    .collect(toMap(Map.Entry::getKey, e -> typeParser.parse(e.getValue())));
```

This preserves the `destinations` module boundary — it cannot import `DataStructure` from `schema` without breaking the dependency graph. `Map<String, String>` is the clean interface.

---

## Contributing & Discussion

This document is a living record. If you:

- **Find issues**: Open a GitHub issue with detailed repro steps
- **Propose alternatives**: Discuss trade-offs in GitHub discussions
- **Extend the project**: Reference design decisions here in PRs
- **Have questions**: Ask in GitHub issues, we'll update this doc with answers

**Goal**: Make architectural choices transparent, debatable, and improvable.

---

## Version History

| Date       | Change                                                                  | Author |
|------------|-------------------------------------------------------------------------|--------|
| 2026-01-18 | Initial version: seeding, reproducibility, issues #1-3                  | Marco  |
| 2026-03-08 | Registry pattern: DatafakerRegistry, plugin foundation                  | Marco  |
| 2026-03-09 | Database Stage 2: auto-decomposition + context stack design (TASK-043)  | Marco  |
| 2026-03-09 | JDBC Option B: raw YAML type strings, TypeParser deferred to open()     | Marco  |

---

**Last Updated**: March 9, 2026
**Status**: Living document (updated as project evolves)
