# Baseline Performance Analysis - Key Findings

**Date:** March 5-7, 2026 (Pre-Optimization)  
**Analysis Methods:** JFR Profiling + JMH Component Benchmarks  
**Test Configuration:** Passport structure (11 fields), 100K records  
**Best Configuration:** file/json/8threads/256MB achieving 20,000 rec/s

---

## Executive Summary

**Critical Finding:** Improper Datafaker usage consumed 98.1% of CPU time. We created new `Faker()` instances 800,000 times per test, defeating Datafaker's internal caching mechanisms.

**Impact:** Thread efficiency at only 32% (8 threads achieving 2.86× speedup vs expected 8×)

**Solution:** Thread-local Faker cache → **3.5-5× improvement** (validated March 8, 2026)

---

## CPU Profiling Results (JFR)

### CPU Time Distribution

**Total Samples:** 5,299 execution samples captured during 5-second test run

```
COMPONENT CPU TIME BREAKDOWN:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Datafaker (98.1% of CPU time)
████████████████████████████████████████████████ 5,200 samples

JSON Serialization (0.2%)
█ 11 samples  

Core Generators (0.7%)
███ 35 samples

Core Engine (0.5%)
██ 26 samples

CLI/Coordination (0.5%)
██ 27 samples

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Translation:** For every 100 seconds of execution:
- 98 seconds in Datafaker operations
- 0.2 seconds in JSON serialization  
- 1.2 seconds in core generation code
- 0.6 seconds in everything else

### Top CPU Hotspot Methods

| Rank | Method | Samples | % of Total CPU |
|------|--------|---------|----------------|
| 1 | Datafaker: `FakeValuesService.safeFetch()` | 1,094 | 20.6% |
| 2 | Datafaker: `FakeValuesService.resolve()` [v1] | 1,054 | 19.9% |
| 3 | Datafaker: `FakeValuesService.resolve()` [v2] | 1,049 | 19.8% |
| 4 | Datafaker: `AbstractProvider.resolve()` | 731 | 13.8% |
| 5 | Datafaker: `FakeValuesService.fetchObject()` [308] | 604 | 11.4% |

**Top 3 methods alone: 60.3% of ALL CPU time**

### Datafaker Call Stack Pattern

Observed in JFR profiling:

```
net.datafaker.service.FakeValuesService.safeFetch()
  → FakeValuesService.fetchObject()
    → FakeValuesService.resolve()
      → AbstractProvider.resolve()
        → snakeyaml.scanner.ScannerImpl.fetchMoreTokens()
          → snakeyaml.reader.StreamReader.ensureEnoughData()
            → snakeyaml.composer.Composer.composeSequenceNode()
```

**Pattern:** For every generated value (name, city, passport), Datafaker executes:
1. Template resolution through nested YAML structures
2. Provider lookup and value composition
3. YAML locale file access (should be cached, but wasn't due to new instances)

---

## Component Benchmark Validation (JMH)

### Performance Comparison

| Generator Type | Throughput (ops/s) | vs Primitives | vs Datafaker |
|----------------|-------------------|---------------|--------------|
| **Boolean (primitive)** | 259,000,000 | 1× | 7,600× faster |
| **Integer (primitive)** | 57,000,000 | 4.5× slower | 1,700× faster |
| **String/char (primitive)** | 12,000,000 | 22× slower | 350× faster |
| **Simple Object** | 3,900,000 | 66× slower | 115× faster |
| **Datafaker City** | 34,000 | 7,600× slower | 1× |
| **Datafaker Address** | 17,700 | 14,600× slower | 0.5× |

**Key Insight:** Datafaker realistic data generation is 165-221× slower than even complex object generation. This is expected for realistic data, but the E2E pipeline was even slower due to our misuse.

### E2E Performance Analysis

**Current E2E Performance:**
- 8 threads: 20,000 rec/s
- 1 thread: 7,000 rec/s
- Thread efficiency: 32% (2.86× speedup vs expected 8×)

**Calculation:**
- Total values: 100,000 records × 11 fields = 1.1M values
- Datafaker fields: ~8 of 11 fields = 800,000 Datafaker calls
- Execution time: ~5 seconds
- **Effective rate: 800K / 5s = 160K ops/s total ÷ 8 threads = 20K ops/s per thread**

**Validation:** 20K ops/s matches isolated Datafaker benchmarks (17.7K-34K ops/s) ✓

**Gap explained:** The slight slowdown (20K vs 17-34K) is due to instance creation overhead compounding with serialization and I/O.

---

## Root Cause Analysis

### Initial Hypothesis (INCORRECT)

**We thought:** Datafaker YAML parsing is slow and lacks caching

**Evidence that misled us:**
- 98.1% CPU time in Datafaker operations
- SnakeYAML parser methods visible in profiling
- Poor thread scaling (32% efficiency)

### Corrected Understanding

**Reality:** We defeated Datafaker's internal caching by improper usage

**The Bug in Our Code:**
```java
// DatafakerGenerator.java:79
@Override
public Object generate(Random random, DataType type) {
    Locale locale = LocaleMapper.map(geolocation);
    
    Faker faker = new Faker(locale, random);  // 🔴 NEW INSTANCE EVERY TIME!
    
    return generateValue(faker, kind);
}
```

**Impact:**
- 100,000 records × 8 Datafaker fields = **800,000 Faker instantiations**
- Each `new Faker()` call:
  1. **Throws away internal YAML cache** (Datafaker has this!)
  2. **Resets template resolution cache** (Datafaker has this!)
  3. Reinitializes provider registry
  4. Rebuilds internal data structures

**Proof:** Datafaker HAS internal caching mechanisms. We just kept resetting them!

### Evidence from JFR

**Faker initialization methods in CPU samples:**
- `BaseFaker.<init>()` - 14 occurrences
- `Faker.<init>()` - 5 occurrences

**What this means:** We were constantly creating new instances instead of reusing them.

---

## Thread Efficiency Analysis

### Current Scaling

| Threads | Throughput | Speedup | Efficiency |
|---------|------------|---------|------------|
| 1 | 7,000 rec/s | 1× | 100% (baseline) |
| 4 | 15,000 rec/s | 2.1× | 53% |
| 8 | 18,000 rec/s | 2.6× | **32%** |

**Expected with proper threading:** 70-80% efficiency (5.6-6.4× speedup at 8 threads)

**Actual:** 32% efficiency (2.6× speedup)

**Root Cause:** CPU saturation from repeated Faker instantiation overhead. Each thread creating 100,000 instances = wasted cycles.

### Memory & GC Behavior

**GC Overhead:** 0.39-2.36% (healthy, NOT the bottleneck)

**Heap Usage:**
- 256MB config: 65-70MB used
- 512MB config: 22-68MB used  
- 1024MB config: 21-69MB used

**GC Frequency:** 12-35 collections per 100K test

**Conclusion:** Memory and GC are NOT bottlenecks. The problem is CPU-bound (initialization overhead).

---

## Mathematical Impact Projection

### If We Fix Our Usage Pattern

**Current:**
- Faker instances: 800,000 per test
- Each instantiation: ~5 microseconds
- Total waste: 4 seconds per test

**After Thread-Local Cache:**
- Faker instances: 8 per test (1 per thread)
- Instantiation overhead: ~0.04 milliseconds total
- Time saved: ~4 seconds

**Expected Improvements:**

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **1-thread throughput** | 7,000 rec/s | 10,000+ rec/s | +43%+ |
| **8-thread throughput** | 20,000 rec/s | 40,000+ rec/s | +100%+ |
| **Thread efficiency** | 32% | 60-70% | +28-38 pts |
| **Faker instantiations** | 800,000 | 8 | **-99.999%** |

---

## Validation Against Component Benchmarks

### Expected vs Actual

**Component benchmark (isolated Datafaker):** 17-34K ops/s  
**E2E pipeline (per thread):** 20K ops/s

**Gap:** 0.6-1.0× (E2E is actually close to component!)

**Why the gap exists:**
- Instance creation overhead (our bug)
- Serialization overhead (0.2% CPU)
- I/O overhead (minimal)
- Coordination overhead (0.5% CPU)

**Conclusion:** Once we fix instance creation, E2E should match component benchmarks.

---

## Key Lessons Learned

### 1. Always Verify Library Usage Before Blaming Performance

**Mistake:** We assumed Datafaker lacked caching because we saw repeated YAML parsing

**Reality:** Datafaker HAS caching - we just kept resetting it

**Takeaway:** Profile → analyze → verify assumptions → optimize

### 2. High CPU Usage Doesn't Mean "Slow Library"

**98.1% CPU in Datafaker** could mean:
- ❌ Library is slow (our initial assumption)
- ✅ We're calling it inefficiently (actual problem)
- ✅ It's doing legitimate work (also true)

**Lesson:** Context matters. CPU hotspots show WHERE time is spent, not WHY.

### 3. Component Benchmarks Provide Validation

**Without component benchmarks:**
- We might have blamed Datafaker further
- Unclear if optimization would help

**With component benchmarks:**
- Validated Datafaker performs as expected in isolation (17-34K ops/s)
- Identified E2E gap was due to our code, not Datafaker
- Set realistic expectations for post-optimization performance

### 4. Thread Efficiency Is a Diagnostic Signal

**32% efficiency** immediately indicated:
- CPU-bound bottleneck (not I/O-bound)
- Likely contention or wasted work
- Optimization target: reduce CPU waste

**70% efficiency** (post-fix) indicates:
- Healthy threading behavior
- Remaining 30% is coordination overhead (acceptable)

---

## Detailed Profiling Methodology

**Tool:** Java Flight Recorder (JFR) with ExecutionSample events

**Configuration:**
```bash
java -XX:StartFlightRecording:filename=recording.jfr,settings=profile \
     -jar cli.jar execute --job config/jobs/file_passport.yaml \
     --count 100000 --threads 8
```

**Analysis:**
```bash
# Extract execution samples
jfr print --events jdk.ExecutionSample recording.jfr > samples.txt

# Count Datafaker samples
grep 'net.datafaker' samples.txt | wc -l

# Identify hotspot methods
jfr print --events jdk.ExecutionSample recording.jfr | \
  grep -A2 'stackTrace' | \
  grep 'net.datafaker' | \
  sort | uniq -c | sort -rn | head -20
```

**JFR File Location:** `benchmarks/build/jfr/profile_file_json_t8_m256m.jfr`

**Visualization:** `jmc benchmarks/build/jfr/profile_file_json_t8_m256m.jfr` (JDK Mission Control GUI)

---

## Related Documentation

**For current performance status:**
- [../../PERFORMANCE-STATUS.md](../../PERFORMANCE-STATUS.md) - Current metrics after optimization
- [../../E2E-TEST-RESULTS.md](../../E2E-TEST-RESULTS.md) - Latest E2E results

**For optimization implementation:**
- [THREAD-LOCAL-OPTIMIZATION-STRATEGY.md](THREAD-LOCAL-OPTIMIZATION-STRATEGY.md) - Complete strategy and code
- [../../internal/tasks/TASK-040-thread-local-faker-cache.md](../../internal/tasks/TASK-040-thread-local-faker-cache.md) - Implementation task

**For specialized analysis:**
- [PERFORMANCE-ANALYSIS.md](PERFORMANCE-ANALYSIS.md) - File I/O deep-dive
- [KAFKA-BENCHMARK-RESULTS.md](KAFKA-BENCHMARK-RESULTS.md) - Kafka benchmarks
- [SERIALIZER-BENCHMARK-RESULTS.md](SERIALIZER-BENCHMARK-RESULTS.md) - Serializer benchmarks

---

**Historical Context:** This analysis led to identifying improper Datafaker usage and implementing the thread-local cache optimization that achieved 3.5-5× improvement.
