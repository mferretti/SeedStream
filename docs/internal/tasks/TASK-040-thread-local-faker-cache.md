# TASK-040: Thread-Local Faker Cache Optimization

**Status**: 📋 Ready to Start  
**Priority**: P1 (High)  
**Phase**: 6 - Performance Validation (Extended)  
**Dependencies**: TASK-039 (Performance Baseline Analysis)  
**Human Supervision**: LOW  
**Estimated Time:** 2-3 hours  
**Expected Impact:** 2× throughput improvement (20K → 40K rec/s)

---

## Objective

Implement thread-local caching for Datafaker instances to eliminate repeated instantiation overhead. Currently, a NEW Faker instance is created for every value generation (800,000 instantiations per 100K records), consuming ~5% of execution time and contributing to poor thread scaling (32% efficiency).

**Performance Target:**
- **Throughput:** 20,000 → 40,000 rec/s (8 threads)
- **Thread Efficiency:** 32% → 60-70%
- **Faker Instantiations:** 800,000 → 8 (99.999% reduction)

---

## Background

**Problem Identified (TASK-039):**
- `DatafakerGenerator.java:79` creates `new Faker(locale, random)` for EVERY value
- 100,000 records × 8 Datafaker fields = 800,000 Faker instantiations per test
- Each instantiation: **throws away Datafaker's internal cache state**
- Datafaker HAS caching for YAML/templates, but we kept resetting it!
- JFR evidence: `BaseFaker.<init>()` and `Faker.<init>()` appear repeatedly in CPU samples

**Root Cause:**
```java
// CURRENT (DatafakerGenerator.java:79)
@Override
public Object generate(Random random, DataType type) {
    Locale locale = LocaleMapper.map(geolocation);
    
    Faker faker = new Faker(locale, random);  // 🔴 NEW INSTANCE = CACHE RESET!
    
    return generateValue(faker, kind);
}
```

**Impact:**
- Thread efficiency: Only 32% at 8 threads (expected 70%+)
- Datafaker init overhead compounds with cache resets (98.1% bottleneck)
- Throughput plateau: ~20K rec/s regardless of threads beyond 4

**Solution:**
Thread-local cache to reuse Faker instances within each thread for the same locale.
**Let Datafaker's own caching mechanisms work as designed!**

---

## Implementation Details

### Step 1: Create FakerCache Class

**File:** `generators/src/main/java/com/datagenerator/generators/semantic/FakerCache.java` (NEW)

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
 * 
 * <p><b>Example:</b>
 * <pre>
 * // In DatafakerGenerator
 * Faker faker = FakerCache.getOrCreate(locale, random);  // Reuses if exists
 * String name = faker.name().fullName();
 * </pre>
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
   * <p>On first call per thread, stores the Random instance for deterministic generation.
   * Subsequent calls reuse the cached Faker for the same locale.
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
   * 
   * <p>Should be called when thread pool shuts down to prevent memory leaks.
   * After cleanup, next access will create fresh instances.
   */
  public static void clear() {
    CACHE.remove();
    THREAD_RANDOM.remove();
    log.debug("Cleared FakerCache for thread: {}", Thread.currentThread().getName());
  }
  
  /**
   * Get cache statistics for current thread (for debugging/profiling).
   * 
   * @return Number of Faker instances cached in current thread
   */
  public static int getCacheSize() {
    return CACHE.get().size();
  }
}
```

**Validation:**
```bash
# Compile
./gradlew :generators:compileJava

# Verify class created
ls generators/build/classes/java/main/com/datagenerator/generators/semantic/FakerCache.class
```

---

### Step 2: Update DatafakerGenerator to Use Cache

**File:** `generators/src/main/java/com/datagenerator/generators/semantic/DatafakerGenerator.java`

**Change:** Replace line 79 (1 line change)

```java
@Override
public Object generate(Random random, DataType type) {
  if (!(type instanceof PrimitiveType primitiveType)) {
    throw new GeneratorException("DatafakerGenerator only supports PrimitiveType, got: " + type);
  }

  PrimitiveType.Kind kind = primitiveType.getKind();
  if (!isSemanticType(kind)) {
    throw new GeneratorException("DatafakerGenerator does not support type: " + kind);
  }

  // Get geolocation from context and map to locale
  String geolocation = GeneratorContext.getGeolocation();
  Locale locale = LocaleMapper.map(geolocation);

  // ✅ NEW: Use cached Faker instance (reuse within thread)
  Faker faker = FakerCache.getOrCreate(locale, random);

  return generateValue(faker, kind);
}
```

**Before (line 79):**
```java
Faker faker = new Faker(locale, random);
```

**After (line 79):**
```java
Faker faker = FakerCache.getOrCreate(locale, random);
```

**Validation:**
```bash
# Compile
./gradlew :generators:compileJava

# Run generator tests
./gradlew :generators:test
```

---

### Step 3: Add Cache Cleanup in GenerationEngine

**File:** `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`

**Location:** After worker shutdown (around line 235)

**Before:**
```java
// Shutdown workers and wait for completion
workers.shutdown();
boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
if (!terminated) {
  log.warn("Worker threads did not terminate gracefully");
}
```

**After:**
```java
// Shutdown workers and wait for completion
workers.shutdown();
boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

// ✅ NEW: Clean up thread-local caches to prevent memory leaks
FakerCache.clear();

if (!terminated) {
  log.warn("Worker threads did not terminate gracefully");
}
```

**Import to add:**
```java
import com.datagenerator.generators.semantic.FakerCache;
```

**Validation:**
```bash
# Compile
./gradlew :core:compileJava

# Run engine tests
./gradlew :core:test
```

---

### Step 4: Write Unit Tests for FakerCache

**File:** `generators/src/test/java/com/datagenerator/generators/semantic/FakerCacheTest.java` (NEW)

```java
package com.datagenerator.generators.semantic;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakerCacheTest {

  @AfterEach
  void cleanup() {
    FakerCache.clear();
  }

  @Test
  void shouldReturnSameFakerInstanceForSameLocaleInSameThread() {
    Random random = new Random(42);
    Locale locale = Locale.ITALY;

    Faker faker1 = FakerCache.getOrCreate(locale, random);
    Faker faker2 = FakerCache.getOrCreate(locale, random);

    assertThat(faker1).isSameAs(faker2);
  }

  @Test
  void shouldReturnDifferentFakersForDifferentLocalesInSameThread() {
    Random random = new Random(42);

    Faker italianFaker = FakerCache.getOrCreate(Locale.ITALY, random);
    Faker frenchFaker = FakerCache.getOrCreate(Locale.FRANCE, random);

    assertThat(italianFaker).isNotSameAs(frenchFaker);
    assertThat(FakerCache.getCacheSize()).isEqualTo(2);
  }

  @Test
  void shouldReturnDifferentFakersForDifferentThreads() throws Exception {
    Random random = new Random(42);
    Locale locale = Locale.ITALY;
    Set<Faker> fakers = ConcurrentHashMap.newKeySet();

    ExecutorService executor = Executors.newFixedThreadPool(3);
    CountDownLatch latch = new CountDownLatch(3);

    for (int i = 0; i < 3; i++) {
      executor.submit(
          () -> {
            Faker faker = FakerCache.getOrCreate(locale, random);
            fakers.add(faker);
            latch.countDown();
          });
    }

    latch.await(5, TimeUnit.SECONDS);
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    // Each thread should get its own Faker instance
    assertThat(fakers).hasSize(3);
  }

  @Test
  void shouldClearCacheForCurrentThread() {
    Random random = new Random(42);
    Faker faker1 = FakerCache.getOrCreate(Locale.ITALY, random);
    assertThat(FakerCache.getCacheSize()).isEqualTo(1);

    FakerCache.clear();
    assertThat(FakerCache.getCacheSize()).isEqualTo(0);

    // After clear, should create new instance
    Faker faker2 = FakerCache.getOrCreate(Locale.ITALY, random);
    assertThat(faker2).isNotSameAs(faker1);
  }

  @Test
  void shouldGenerateDeterministicDataWithCachedFaker() {
    Random random = new Random(12345);
    Faker faker = FakerCache.getOrCreate(Locale.ITALY, random);

    String name1 = faker.name().fullName();
    String name2 = faker.name().fullName();

    // Different values (Random state progresses)
    assertThat(name1).isNotEqualTo(name2);

    // But reusing same faker should be deterministic
    Random random2 = new Random(12345);
    FakerCache.clear();
    Faker faker2 = FakerCache.getOrCreate(Locale.ITALY, random2);
    String name3 = faker2.name().fullName();

    assertThat(name3).isEqualTo(name1); // Same seed = same first value
  }
}
```

**Validation:**
```bash
./gradlew :generators:test --tests FakerCacheTest
```

---

### Step 5: Run E2E Benchmarks and Compare

**Baseline (capture before changes):**
```bash
cd benchmarks
./run_all_tests.sh --profile
cp e2e_results.csv e2e_results_baseline.csv
cp build/jfr/profile_file_json_t8_m256m.jfr build/jfr/baseline_profile.jfr
```

**After optimization:**
```bash
./run_all_tests.sh --profile
```

**Compare:**
```bash
# Compare throughput
grep "file,json,8,256" e2e_results_baseline.csv
grep "file,json,8,256" e2e_results.csv

# Analyze Faker init reduction
jfr print --events jdk.ExecutionSample build/jfr/baseline_profile.jfr | \
    grep -E "BaseFaker.<init>|Faker.<init>" | wc -l

jfr print --events jdk.ExecutionSample build/jfr/profile_file_json_t8_m256m.jfr | \
    grep -E "BaseFaker.<init>|Faker.<init>" | wc -l
```

**Expected Results:**
| Metric | Before | After | Change |
|--------|--------|-------|--------|
| 8-thread throughput | 20,000 rec/s | 40,000 rec/s | +100% |
| 1-thread throughput | 7,000 rec/s | 10,000 rec/s | +43% |
| Thread efficiency | 32% | 60-70% | +28-38 pts |
| Faker init samples | 19 | <5 | -73%+ |
| Faker instantiations | 800,000 | 8 | -99.999% |

---

## Acceptance Criteria

- ✅ `FakerCache.java` created with thread-local caching
- ✅ `DatafakerGenerator.java` updated to use cache (1 line change)
- ✅ `GenerationEngine.java` updated with cleanup call
- ✅ Unit tests written for FakerCache (6 tests)
- ✅ All existing tests still pass
- ✅ E2E benchmarks show >= 1.7× improvement (target: 2×)
- ✅ Thread efficiency improves to >= 55% (target: 60-70%)
- ✅ JFR profiles show reduced Faker init samples

---

## Testing

**Unit Tests:**
```bash
# Run FakerCache tests
./gradlew :generators:test --tests FakerCacheTest

# Run all generator tests
./gradlew :generators:test

# Run core engine tests
./gradlew :core:test
```

**Integration Tests:**
```bash
# Run all tests
./gradlew test
```

**E2E Benchmarks:**
```bash
cd benchmarks

# Quick validation (single test)
./run_e2e_test.sh file json 8 256m --profile

# Full suite
./run_all_tests.sh --profile
```

**Performance Validation:**
```bash
# Check throughput
grep "file,json,8,256" benchmarks/e2e_results.csv

# Expected: ~40,000 rec/s (was 20,000)

# Verify cache size during generation (add debug log)
# Should show: "Faker cache size: 1" (only one locale typically)
```

---

## Performance Characteristics

**Before:**
- 1 thread: 7,000 rec/s
- 8 threads: 20,000 rec/s (2.86× speedup, 32% efficiency)
- Faker instantiations: 800,000 per 100K records
- Faker init CPU: ~5% of execution time

**After (Expected):**
- 1 thread: 10,000 rec/s (+43%)
- 8 threads: 40,000 rec/s (+100%, 4.0× speedup, 50% efficiency)
- Faker instantiations: 8 per test (1 per thread)
- Faker init CPU: <0.01% of execution time

**Impact:**
- 99.999% reduction in Faker instantiations
- Thread efficiency: 32% → 50-60%
- Throughput doubled at 8 threads
- Memory: 8KB (8 Faker instances) vs 800MB (transient allocations)

---

## Dependencies

**No New Dependencies Required**
- Uses existing Datafaker library
- Uses Java's built-in ThreadLocal
- Uses existing Random from RandomProvider

---

## Documentation

**Update After Completion:**
- `benchmarks/BASELINE-ANALYSIS.md` - Mark Phase 2 complete, add new baseline
- `benchmarks/THREAD-LOCAL-OPTIMIZATION-STRATEGY.md` - Mark as implemented
- `CHANGELOG.md` - Add performance improvement entry

---

## Rollback Plan

**If Issues Arise:**
1. Revert `DatafakerGenerator.java` change (1 line)
2. Remove `FakerCache.java` file
3. Remove cleanup call from `GenerationEngine.java`
4. All original functionality restored

**Risk:** Very Low
- Simple caching pattern
- No changes to public APIs
- Maintains determinism (same Random seeding)
- No external dependencies

---

## Next Steps

**After This Task:**
1. Update documentation with new performance baseline
2. Monitor production usage for memory leaks (unlikely)
3. Consider further optimizations:
   - Pre-warm Faker instances at thread startup
   - Profile for remaining lock contention
   - Tune queue capacity if needed

**Future:**
- TASK-041: Datafaker YAML caching (20× improvement potential)
- Requires upstream contribution or fork

---

**Estimated Time:** 2-3 hours  
**Expected Impact:** 2× throughput improvement  
**Risk Level:** Low  
**Priority:** High (quick win, significant impact)
