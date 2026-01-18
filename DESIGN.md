# Data Generator - Design Documentation

This document captures the architectural decisions, design patterns, issues encountered, and their resolutions during development. It serves as a reference for developers extending the project and for discussions around alternative approaches.

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

**Requirement**: Generate millions of records per second.

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

```
cli → destinations → formats → generators → schema → core
```

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

## Open Questions & Future Work

### 1. Virtual Threads for I/O-Bound Operations

**Question**: Should we use virtual threads (Java 21) for destination writes (Kafka, database)?

**Pros**:
- Lightweight (millions of virtual threads possible)
- Simplified code (blocking I/O looks synchronous)
- Better resource utilization

**Cons**:
- Debugging complexity (stack traces span multiple carrier threads)
- Library compatibility (some JDBC drivers, Kafka clients may have issues)

**Current Decision**: Platform threads with fixed-size pools. Revisit when libraries mature.

**Discussion Welcome**: If you have experience with virtual threads + Kafka/JDBC, please share insights in GitHub issues.

---

### 2. Array Memory Management

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

**Current Decision**: Deferred to future release. Option C seems most flexible.

**Discussion**: Interested in graph-based data generation? See GitHub issue #TBD.

---

### 4. Statistical Distributions

**Question**: Should we support normal, Zipfian, exponential distributions for numeric types?

**Example**:
```yaml
age: int[18..65, distribution=normal, mean=35, stddev=10]
```

**Use Case**: Realistic data often follows distributions (ages, salaries, response times).

**Challenge**: Maintaining reproducibility with distributions is complex (need to specify all params in config).

**Current Decision**: Uniform distribution only. Revisit after MVP.

**Interested?**: Propose design in GitHub discussions.

---

### 5. Plugin Architecture

**Question**: Should we support user-provided generators/destinations as plugins?

**Vision**:
```java
// User creates custom generator
public class CustomDataGenerator implements DataTypeGenerator {
    @Override
    public Object generate(Random random, TypeConfig config) {
        // Custom logic
    }
}

// User registers via META-INF/services
```

**Pros**:
- Extensibility without modifying core code
- Community contributions (marketplace of generators)

**Cons**:
- Complexity (ServiceLoader, classloading, versioning)
- Security (untrusted code execution)

**Current Decision**: Fixed generators in codebase. Revisit after 1.0 release.

**Interested?**: Discuss plugin API design in GitHub.

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

| Date       | Change                                                      | Author |
|------------|-------------------------------------------------------------|--------|
| 2026-01-18 | Initial version: seeding, reproducibility, issues #1-3      | Marco  |

---

**Last Updated**: January 18, 2026  
**Status**: Living document (updated as project evolves)
