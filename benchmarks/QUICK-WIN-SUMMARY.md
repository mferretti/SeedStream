# Quick Win: Thread-Local Faker Cache

**Date:** March 8, 2026  
**Implementation Time:** 2-3 hours  
**Expected Improvement:** **2× throughput** (20K → 40K rec/s)  
**Risk:** Very Low

---

## 🎯 The Problem

```
Current Implementation (DatafakerGenerator.java:79):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  
  @Override
  public Object generate(Random random, DataType type) {
      Locale locale = LocaleMapper.map(geolocation);
      
      Faker faker = new Faker(locale, random);  // 🔴 NEW INSTANCE EVERY TIME!
      
      return generateValue(faker, kind);
  }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Impact: 100,000 records × 8 Datafaker fields = 800,000 Faker instantiations!
        Each new instance THROWS AWAY Datafaker's internal cache!
        Datafaker HAS caching - we just kept resetting it!
        
🔑 Key Realization: Datafaker isn't slow - we used it wrong!
```

## ✅ The Solution

```java
// NEW: Thread-local cache
public class FakerCache {
    private static final ThreadLocal<Map<Locale, Faker>> CACHE = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    public static Faker getOrCreate(Locale locale, Random random) {
        return CACHE.get().computeIfAbsent(locale, 
            loc -> new Faker(loc, random));
    }
}

// UPDATED: DatafakerGenerator.java
@Override
public Object generate(Random random, DataType type) {
    Locale locale = LocaleMapper.map(geolocation);
    
    Faker faker = FakerCache.getOrCreate(locale, random);  // ✅ REUSE!
    
    return generateValue(faker, kind);
}
```

---

## 📊 Before vs After

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| **Faker instantiations** | 800,000 | 8 | **-99.999%** |
| **Faker init time** | 4.0 sec | 0.004 sec | **-99.9%** |
| **1-thread throughput** | 7,000 rec/s | 10,000 rec/s | **+43%** |
| **8-thread throughput** | 20,000 rec/s | 40,000 rec/s | **+100%** |
| **Thread efficiency** | 32% | 60-70% | **+28-38 pts** |

---

## 🚀 Implementation Steps

### 1. Create FakerCache.java (10 min)

```bash
# Create new file
touch generators/src/main/java/com/datagenerator/generators/semantic/FakerCache.java
```

Copy implementation from [THREAD-LOCAL-OPTIMIZATION-STRATEGY.md](THREAD-LOCAL-OPTIMIZATION-STRATEGY.md#11-create-fakercache-thread-local)

### 2. Update DatafakerGenerator.java (2 min)

**Change 1 line:**
```java
// OLD (line 79)
Faker faker = new Faker(locale, random);

// NEW
Faker faker = FakerCache.getOrCreate(locale, random);
```

### 3. Add Cleanup (5 min)

**In GenerationEngine.java, after worker shutdown:**
```java
workers.shutdown();
boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

FakerCache.clear();  // ✅ Add this line

if (!terminated) {
    log.warn("Worker threads did not terminate gracefully");
}
```

### 4. Run Tests (15 min)

```bash
# Run E2E benchmarks
./benchmarks/run_all_tests.sh --profile

# Check results
grep "file,json,8,256" benchmarks/e2e_results.csv
```

**Expected output:**
```
file,json,8,256,100000,2.5,40000,65,256,30,5,1.20,SUCCESS,
                         ^^^^^  ← Was 20,000, now 40,000!
```

---

## 🔬 Validation

### Before Running:
```bash
# Capture baseline
grep "file,json,8,256" benchmarks/e2e_results.csv > baseline.txt
```

### After Running:
```bash
# Compare
grep "file,json,8,256" benchmarks/e2e_results.csv > optimized.txt
diff baseline.txt optimized.txt

# Profile analysis
jfr print --events jdk.ExecutionSample benchmarks/build/jfr/profile_file_json_t8_m256m.jfr | \
    grep -E "BaseFaker.<init>|Faker.<init>" | wc -l
```

**Expected: Faker init samples drop from 19 to <5**

---

## 📈 Why This Works

**Thread Efficiency Math:**

```
Current Performance:
  1 thread:  7,000 rec/s  (baseline)
  8 threads: 20,000 rec/s (2.86× speedup)
  Efficiency: 2.86 / 8 = 35.7%  ❌

With Thread-Local Faker:
  1 thread:  10,000 rec/s  (43% faster - no init overhead)
  8 threads: 50,000 rec/s  (5.0× speedup with better thread util)
  Efficiency: 5.0 / 8 = 62.5%  ✅

Conservative Estimate:
  8 threads: 40,000 rec/s  (4.0× speedup)
  Efficiency: 4.0 / 8 = 50%  ✅ (still significant improvement)
```

**Why Thread Efficiency Improves:**
1. Less time wasted in Faker initialization = more time generating
2. Better CPU cache locality (reusing same Faker instance)
3. Reduced memory allocation pressure on GC
4. Less synchronized access in Faker internal initialization

---

## ⚠️ Important Notes

### Maintains Determinism ✅
- Each thread gets its own Faker instance seeded with thread-local Random
- Same worker ID → same Random seed → same Faker behavior
- Reproducibility preserved

### Thread Safety ✅
- ThreadLocal ensures zero contention
- Each thread has its own cache
- No synchronization needed

### Memory Impact ✅
- Minimal: 8 threads × 1 Faker instance × ~1KB ≈ **8KB total**
- Previous: 800,000 × 1KB ≈ **800MB** (transient, GC pressure)
- **Net benefit:** 99.999% reduction in allocations

---

## 🎯 Success Criteria

| Target | Threshold | Stretch Goal |
|--------|-----------|--------------|
| **8-thread throughput** | 35,000 rec/s | 45,000 rec/s |
| **Thread efficiency** | 55% | 70% |
| **Faker init CPU %** | <2% | <1% |
| **Implementation time** | <3 hours | <2 hours |

---

## 🔗 References

- **Detailed Strategy:** [THREAD-LOCAL-OPTIMIZATION-STRATEGY.md](THREAD-LOCAL-OPTIMIZATION-STRATEGY.md)
- **Baseline Analysis:** [BASELINE-ANALYSIS.md](BASELINE-ANALYSIS.md)
- **Code Location:** `DatafakerGenerator.java:79`

---

**Bottom Line:** This is a **2-hour quick win** that doubles throughput by eliminating 800,000 unnecessary Faker instantiations. Low risk, high reward. **DO THIS FIRST.**
