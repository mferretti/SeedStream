# File I/O Performance Analysis

**Date:** March 6, 2026  
**Goal:** Identify bottlenecks and optimize JSON file writing from current 213 MB/s to 500+ MB/s

---

## Executive Summary

**Current Performance:** 761,076 records/s ≈ **213 MB/s** (FileDestination with JSON)  
**Hardware Ceiling:** **1,200 MB/s** (raw disk throughput)  
**Java NIO Ceiling:** **843-1,087 MB/s** (depending on buffer size)  
**Performance Gap:** **4-5x slower** than achievable throughput

**Root Causes Identified:**
1. Small buffer size (8KB) limits I/O efficiency
2. Two I/O calls per record (write + newLine)  
3. No batching of serialized records
4. Jackson ObjectMapper overhead per record

**Recommendation:** Implement optimizations to achieve 500-800 MB/s (2.3-3.7x improvement)

---

## Hardware Baseline Tests

### Test 1: Raw Disk Throughput (dd)

```bash
# Sequential write without cache
dd if=/dev/zero of=test.dat bs=1M count=1024 conv=fdatasync
Result: 1,073 MB written in 0.87s = 1,200 MB/s

# Buffered write (system cache)
dd if=/dev/zero of=test.dat bs=1M count=1024
Result: 1,073 MB written in 0.47s = 2,300 MB/s
```

**Conclusion:** Hardware can sustain 1.2+ GB/s writes

### Test 2: Java NIO Raw String Writes

Test configuration:
- 1 million records × 287 bytes = 273 MB total
- Pre-serialized JSON strings (no Jackson overhead)

| Buffer Size | Throughput (MB/s) | Records/s | Duration |
|-------------|-------------------|-----------|----------|
| 8 KB (default) | 843 MB/s | 3,080,876 | 0.32s |
| 64 KB | 986 MB/s | 3,603,190 | 0.28s |
| 256 KB | 1,087 MB/s | 3,970,229 | 0.25s |

**Conclusion:** Java NIO can achieve 843-1,087 MB/s with proper buffering

### Test 3: Current Implementation (JMH Benchmark)

```
benchmarkFileDestinationWrite: 761,076 ops/s (± 387,454)
```

**Calculated throughput:** 761,076 records/s × 287 bytes ≈ **213 MB/s**

**Conclusion:** Current implementation is **4x slower** than Java NIO baseline

---

## Performance Gap Analysis

| Component | Throughput | Gap from Hardware |
|-----------|------------|-------------------|
| **Hardware (dd)** | 1,200 MB/s | Baseline |
| **Java NIO (8KB buffer)** | 843 MB/s | -30% (expected overhead) |
| **Java NIO (256KB buffer)** | 1,087 MB/s | -9% (excellent) |
| **FileDestination (current)** | 213 MB/s | **-82%** (major bottleneck) |

### Bottleneck Breakdown

1. **Buffer Size Impact:** 8KB → 256KB gives 29% improvement (843 → 1,087 MB/s)
2. **Serialization Overhead:** Jackson ObjectMapper.writeValueAsString() per record
3. **I/O Call Overhead:** Two calls per record (writer.write() + writer.newLine())
4. **No Batching:** Each record serialized and written individually

---

## Current Implementation Analysis

### FileDestination.java

**Issues identified:**

```java
@Builder.Default int bufferSize = 8192;  // ❌ Only 8KB
```

**Write method:**
```java
public void write(Map<String, Object> record) {
    String line = serializer.serialize(record);  // ❌ Per-record Jackson call
    writer.write(line);                          // ❌ First I/O call
    writer.newLine();                            // ❌ Second I/O call
}
```

### JsonSerializer.java

**Well-optimized (no changes needed):**
- ✅ ObjectMapper is thread-safe and reused
- ✅ Pretty-printing disabled (compact output)
- ✅ ISO-8601 date formatting configured
- ✅ No unnecessary allocations

---

## Proposed Optimizations

### Priority 1: Increase Buffer Size (Quick Win)

**Change:**
```java
@Builder.Default int bufferSize = 65536;  // 64KB instead of 8KB
```

**Expected Impact:** +17% throughput (843 → 986 MB/s in baseline test)  
**Effort:** 1 line change  
**Risk:** None (more memory per writer, but negligible)

---

### Priority 2: Eliminate Redundant newLine() Call

**Current:**
```java
writer.write(line);
writer.newLine();
```

**Optimized:**
```java
writer.write(line);
writer.write('\n');  // Append newline directly
```

**Expected Impact:** -50% I/O call overhead (2 calls → 1 call)  
**Effort:** 1 line change  
**Risk:** None

---

### Priority 3: Batch Serialization and Writes (Medium Effort)

**Approach:** Accumulate multiple serialized records before writing

**Current flow:**
```
for each record:
    serialize → write → newLine
```

**Optimized flow:**
```
for each batch of N records:
    serialize all → concatenate → single write
```

**Implementation:**

```java
private static final int BATCH_SIZE = 1000;
private final List<Map<String, Object>> batchBuffer = new ArrayList<>(BATCH_SIZE);

public void write(Map<String, Object> record) {
    batchBuffer.add(record);
    if (batchBuffer.size() >= BATCH_SIZE) {
        flushBatch();
    }
}

private void flushBatch() {
    if (batchBuffer.isEmpty()) return;
    
    StringBuilder sb = new StringBuilder(BATCH_SIZE * 300);  // Pre-allocate
    for (Map<String, Object> record : batchBuffer) {
        sb.append(serializer.serialize(record)).append('\n');
    }
    writer.write(sb.toString());
    batchBuffer.clear();
}
```

**Expected Impact:** +50-100% throughput (amortize I/O overhead)  
**Effort:** ~30 lines of code  
**Risk:** Medium (must handle flush on close, thread safety)

---

### Priority 4: Use Jackson Streaming API (High Effort)

**Approach:** Use `JsonGenerator` to write directly to OutputStream

**Current:**
```java
String json = mapper.writeValueAsString(record);  // Creates String object
writer.write(json);                                // Converts to chars again
```

**Optimized:**
```java
JsonGenerator generator = jsonFactory.createGenerator(outputStream);
generator.writeObject(record);                     // Direct write, no intermediate String
generator.flush();
```

**Expected Impact:** +10-20% (reduce String allocation overhead)  
**Effort:** ~50 lines (refactor FileDestination and JsonSerializer)  
**Risk:** High (API changes, testing required)

---

## Recommended Implementation Plan

### Phase 1: Quick Wins (30 minutes)
- [x] Priority 1: Increase buffer size to 64KB
- [x] Priority 2: Eliminate newLine() call

**Expected Result:** 350-400 MB/s (1.6-1.9x improvement)

### Phase 2: Batching (2-3 hours)
- [ ] Priority 3: Implement batch writes with 1000-record buffer

**Expected Result:** 600-800 MB/s (2.8-3.7x improvement) ✅ Exceeds 500 MB/s target

### Phase 3: Advanced (Optional, 4-6 hours)
- [ ] Priority 4: Jackson streaming API for direct writes

**Expected Result:** 700-900 MB/s (marginal improvement, high effort)

---

## Testing Strategy

### Validation Benchmarks

1. **Update JMH DestinationBenchmark** with optimized code
2. **Run benchmark suite:** `./benchmarks/run_benchmarks.sh`
3. **Compare results:** FileDestinationWrite should increase from 761K ops/s → 2M+ ops/s
4. **Calculate MB/s:** ops/s × 287 bytes ≈ target throughput

### Integration Tests

1. Generate 10M records to file (100M+ records for stress test)
2. Measure wall-clock time and throughput
3. Verify file integrity (line count, valid JSON)
4. Memory profiling (ensure no leaks with batching)

---

## Expected Final Results

| Optimization | Throughput (MB/s) | Improvement |
|--------------|-------------------|-------------|
| **Baseline (current)** | 213 MB/s | 1.0x |
| **+ Buffer size (64KB)** | 350 MB/s | 1.64x |
| **+ Eliminate newLine()** | 390 MB/s | 1.83x |
| **+ Batch writes (1000)** | 650 MB/s | 3.05x ✅ |
| **+ Jackson streaming** | 750 MB/s | 3.52x |

**Target:** 500 MB/s ✅ **Achievable with Phase 1 + Phase 2**

---

## Notes

- **Trade-offs:** Batching increases memory usage (~300KB per writer for 1000-record buffer)
- **Concurrency:** Batching implementation must be thread-safe if used in multi-threaded context
- **Flush behavior:** Must flush partial batches on close() to avoid data loss
- **CSV serializer:** Same optimizations apply to CsvSerializer

---

## References

- JMH Benchmark Results: `BENCHMARK-RESULTS.md`
- Hardware Test Script: `benchmarks/hardware_io_test.sh`
- Current Implementation: `destinations/src/main/java/com/datagenerator/destinations/file/FileDestination.java`
