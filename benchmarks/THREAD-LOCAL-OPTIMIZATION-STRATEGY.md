# Thread-Local Optimization Strategy

**Date:** March 8, 2026  
**Goal:** Improve thread scaling efficiency from 32% to 70%+ (2-2.5× improvement at 8 threads)  
**Current:** 20,000 rec/s (8 threads) → **Target:** 40,000-50,000 rec/s (8 threads)

---

## 🔍 Root Cause Analysis

### Current Implementation Status

| Component | Thread-Local? | Status | Issue |
|-----------|--------------|--------|-------|
| **RandomProvider** | ✅ Yes | Correct | Thread-local Random with logical worker IDs |
| **GeneratorContext** | ✅ Yes | Correct | Thread-local factory + geolocation per record |
| **DataGeneratorFactory** | ❌ Shared | Correct | Immutable, thread-safe - designed to be shared |
| **Faker instances** | ❌ **NEW PER VALUE** | 🔴 **PROBLEM** | **800,000+ Faker instantiations per 100K record test!** |

### The Critical Issue: Faker Instantiation Overhead

**Location:** `DatafakerGenerator.java:79`

```java
@Override
public Object generate(Random random, DataType type) {
    // ... type checking ...
    
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = LocaleMapper.map(geolocation);
    
    // 🔴 PROBLEM: Creates NEW Faker for EVERY VALUE
    Faker faker = new Faker(locale, random);  // Line 79
    
    return generateValue(faker, kind);
}
```

**Impact Calculation:**
- 100,000 records × 11 fields = 1.1M total values
- ~8 of 11 fields use Datafaker (realistic data)
- **800,000 Faker instantiations** per test run
- 8 threads × ~100,000 instantiations per thread
- Each instantiation triggers:
  1. Locale setup
  2. Provider registration
  3. Internal map initialization
  4. YAML locale file access (then parsed by Datafaker's internal code)

**JFR Evidence:**
- `BaseFaker.<init>()` appears 14 times in top CPU samples (line 55)
- `Faker.<init>()` appears 5 times (line 31)
- Combined with YAML parsing overhead = **major bottleneck**

---

## 📋 Optimization Strategy

### Phase 1: Thread-Local Faker Cache (HIGH IMPACT - 1.5-2× improvement)

**Goal:** Reuse Faker instances within each thread, create only once per locale per thread

**Implementation Plan:**

#### 1.1 Create FakerCache (Thread-Local)

**New File:** `generators/src/main/java/com/datagenerator/generators/semantic/FakerCache.java`

```java
package com.datagenerator.generators.semantic;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Thread-local cache for Datafaker instances to avoid repeated initialization.
 * 
 * <p>Each thread maintains a cache of Faker instances keyed by Locale.
 * This dramatically reduces overhead when generating many values with same locale.
 * 
 * <p><b>Performance Impact:</b>
 * - Without cache: NEW Faker for every value (800K instantiations per 100K records)
 * - With cache: ONE Faker per locale per thread (typically 1-2 instances total)
 * 
 * <p><b>Thread Safety:</b> ThreadLocal ensures each thread has its own cache with
 * no contention.
 * 
 * <p><b>Determinism:</b> Each cached Faker is seeded with thread-local Random,
 * maintaining reproducible output.
 */
@Slf4j
public class FakerCache {
    
    // Thread-local cache: Locale -> Faker instance
    private static final ThreadLocal<Map<Locale, Faker>> CACHE = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    // Thread-local Random from RandomProvider (passed via context)
    private static final ThreadLocal<Random> THREAD_RANDOM = new ThreadLocal<>();
    
    /**
     * Get or create a Faker instance for the given locale.
     * 
     * @param locale Target locale for data generation
     * @param random Thread-local Random for seeding (stored on first call)
     * @return Cached Faker instance for this locale
     */
    public static Faker getOrCreate(Locale locale, Random random) {
        // Store random on first access (for this thread)
        if (THREAD_RANDOM.get() == null) {
            THREAD_RANDOM.set(random);
            log.debug("Initialized FakerCache for thread: {}", Thread.currentThread().getName());
        }
        
        Map<Locale, Faker> cache = CACHE.get();
        
        // Get or create Faker for this locale
        return cache.computeIfAbsent(locale, loc -> {
            log.debug("Creating new Faker for locale: {} in thread: {}", 
                     loc, Thread.currentThread().getName());
            return new Faker(loc, THREAD_RANDOM.get());
        });
    }
    
    /**
     * Clear the cache for the current thread.
     * Should be called when thread pool shuts down to prevent memory leaks.
     */
    public static void clear() {
        CACHE.remove();
        THREAD_RANDOM.remove();
        log.debug("Cleared FakerCache for thread: {}", Thread.currentThread().getName());
    }
    
    /**
     * Get cache statistics for current thread (for debugging/profiling).
     */
    public static int getCacheSize() {
        return CACHE.get().size();
    }
}
```

#### 1.2 Modify DatafakerGenerator to Use Cache

**File:** `generators/src/main/java/com/datagenerator/generators/semantic/DatafakerGenerator.java`

**Change:**
```java
@Override
public Object generate(Random random, DataType type) {
    // ... type checking ...
    
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = LocaleMapper.map(geolocation);
    
    // ✅ NEW: Use cached Faker instance (reuse)
    Faker faker = FakerCache.getOrCreate(locale, random);
    
    return generateValue(faker, kind);
}
```

**Expected Impact:**
- **Before:** 800,000 Faker instantiations (100K test)
- **After:** 8 Faker instantiations (1 per thread, assuming single locale)
- **Reduction:** 99.999% fewer instantiations
- **Performance gain:** 1.5-2× improvement in throughput

#### 1.3 Add Cache Cleanup in GenerationEngine

**File:** `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`

**Add cleanup after worker shutdown:**
```java
// Shutdown workers and wait for completion
workers.shutdown();
boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

// ✅ NEW: Clean up thread-local caches
FakerCache.clear(); // Add this line

if (!terminated) {
    log.warn("Worker threads did not terminate gracefully");
}
```

---

### Phase 2: Profile Lock Contention (QUICK CHECK)

**Goal:** Verify there are no hidden synchronization bottlenecks

**Steps:**

1. **Run E2E test with lock profiling:**
   ```bash
   # Option A: JFR lock profiling
   ./benchmarks/run_e2e_test.sh file json 8 256m --profile-locks
   
   # Option B: async-profiler (if installed)
   async-profiler -e lock -d 30 -f locks.html <java-pid>
   ```

2. **Analyze lock events:**
   ```bash
   jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait \
       benchmarks/build/jfr/profile_file_json_t8_m256m.jfr | \
       head -100
   ```

3. **Look for:**
   - Contention on shared data structures
   - Synchronization in Datafaker internals
   - Blocking on queue operations (BlockingQueue)

**Expected:** Minimal lock contention with thread-local Faker cache

---

### Phase 3: Additional Optimizations (IF NEEDED)

Only proceed if Phase 1 doesn't achieve 70%+ thread efficiency.

#### 3.1 Pre-warm Faker Instances

**Goal:** Initialize Faker during worker thread startup, not on first generation

```java
// In GenerationEngine.generateWorkerRecords():
private void generateWorkerRecords(...) {
    Random random = randomProvider.getRandom();
    
    // ✅ Pre-warm Faker cache with expected locale
    String geolocation = ...; // Pass from engine
    Locale locale = LocaleMapper.map(geolocation);
    FakerCache.getOrCreate(locale, random); // Warm up
    
    // Now start generating
    while (workerGenerated < count) {
        // ...
    }
}
```

#### 3.2 Increase Queue Capacity

**Goal:** Reduce blocking on queue puts/takes

**Current:** `queueCapacity = 1000` (default in GenerationEngine)

**Try:** `queueCapacity = 10000` for high-throughput scenarios

```java
GenerationEngine engine = GenerationEngine.builder()
    .queueCapacity(10000)  // ✅ Increase from 1000
    // ...
    .build();
```

#### 3.3 Batch Record Generation

**Goal:** Generate multiple records before queue put (reduces queue operations)

```java
// Generate in micro-batches of 10 records
List<Map<String, Object>> batch = new ArrayList<>(10);
while (workerGenerated < count) {
    batch.add(recordGenerator.generate(random));
    
    if (batch.size() == 10 || workerGenerated == count - 1) {
        for (var record : batch) {
            queue.put(record);
        }
        batch.clear();
    }
    workerGenerated++;
}
```

---

## 📊 Expected Results

### Performance Projections

| Phase | Optimization | Expected Thread Efficiency | 8-Thread Throughput | Improvement |
|-------|--------------|---------------------------|---------------------|-------------|
| **Current** | None | 32% | 20,000 rec/s | Baseline |
| **Phase 1** | Faker cache | 55-65% | 34,000-40,000 rec/s | 1.7-2.0× |
| **Phase 1 + 2** | + Lock profiling fixes | 65-75% | 40,000-46,000 rec/s | 2.0-2.3× |
| **Phase 1 + 3** | + Additional opts | 70-80% | 43,000-50,000 rec/s | 2.2-2.5× |

**Target:** 70%+ efficiency = **43,000+ rec/s** (8 threads)

### Mathematical Justification

**Current state:**
- 8 threads achieve 20,000 rec/s
- 1 thread achieves 7,000 rec/s
- Actual speedup: 20K / 7K = 2.86×
- Thread efficiency: 2.86 / 8 = 35.7% ❌

**With Faker cache (Phase 1):**
- Eliminate 99.999% of Faker instantiations
- Reduce CPU time in Faker init from ~5% to ~0.01%
- Each thread can generate faster: 7K → 10-12K rec/s (1.5× faster)
- 8 threads: 10K × 5.5 (55% efficiency) = 55,000 rec/s
- **Conservative estimate: 40,000 rec/s** (accounting for Datafaker YAML overhead)

---

## 🧪 Validation Plan

### Step 1: Implement Phase 1 (Faker Cache)

**Time:** 1-2 hours
**Risk:** Low (simple caching, well-understood pattern)

1. Create `FakerCache.java`
2. Modify `DatafakerGenerator.java` (1 line change)
3. Add cleanup in `GenerationEngine.java` (1 line)
4. Write unit tests for FakerCache

### Step 2: Run Benchmarks

```bash
# Run full E2E suite with profiling
./benchmarks/run_all_tests.sh --profile

# Check results
cat benchmarks/e2e_results.csv | grep "file,json,8"
```

**Success Criteria:**
- Single-thread: 10,000+ rec/s (up from 7,000)
- 4-thread: 30,000+ rec/s (up from 15,000)
- 8-thread: 40,000+ rec/s (up from 20,000)
- Thread efficiency: 60%+ (up from 32%)

### Step 3: Profile Again

```bash
# Capture new JFR profile
jfr print --events jdk.ExecutionSample benchmarks/build/jfr/profile_file_json_t8_m256m.jfr | \
    grep -E "Faker|BaseFaker" | wc -l
```

**Expected:**
- Faker init samples drop from 19 to <5 (73% reduction)
- Datafaker YAML parsing still dominates, but reduced overhead from init

### Step 4: Compare Before/After

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| 1-thread throughput | 7,000 rec/s | 10,000+ rec/s | +43%+ |
| 8-thread throughput | 20,000 rec/s | 40,000+ rec/s | +100%+ |
| Thread efficiency | 32% | 60-70% | +28-38 pts |
| Faker instantiations | 800,000 | 8 | -99.999% |
| Faker init CPU % | ~5% | ~0.01% | -99.8% |

---

## 🎯 Implementation Priority

### MUST DO (High Impact, Low Risk):
✅ **Phase 1:** Faker cache (Expected: 2× improvement)

### SHOULD DO (Quick Validation):
✅ **Phase 2:** Lock contention profiling (Expected: confirm no hidden issues)

### NICE TO HAVE (Diminishing Returns):
⚠️ **Phase 3:** Only if Phase 1 doesn't reach 70% efficiency

---

## 🚀 Quick Start

1. **Create FakerCache.java** (see code above)
2. **Modify DatafakerGenerator.java:** Replace `new Faker(locale, random)` with `FakerCache.getOrCreate(locale, random)`
3. **Run benchmarks:** `./benchmarks/run_all_tests.sh`
4. **Compare results:** Should see ~2× improvement

**Estimated time:** 2 hours implementation + 15 minutes testing = **2.25 hours total**

**Expected outcome:** Thread efficiency 32% → 60-70%, throughput 20K → 40K rec/s

---

## 📝 Notes

- Datafaker YAML parsing will still dominate (98% CPU → ~96% CPU), but reduced init overhead improves throughput
- This optimization is **complementary** to eventual Datafaker caching - both can stack
- Thread-local approach maintains determinism (each thread has consistent Faker instance)
- No changes needed to our existing RandomProvider or GeneratorContext designs
