# Memory Profiling Results and Optimizations

Memory-efficiency profiling of SeedStream's streaming generation pipeline,
covering 100K–10M records across single- and multi-threaded runs.

---

## Executive Summary

Memory profiling conducted using JVM Flight Recorder (JFR) with 4 test scenarios covering 100K to 10M records with both single-threaded and multi-threaded execution. All runs use production-level logging (INFO) on current `main`.

**Key Results:**
- ✅ **No memory leaks detected** - Stable 9-15 MB after GC across all tests
- ✅ **Bounded heap** - Peak heap stays bounded regardless of record count (streaming); peak 457 MB at 10M records
- ✅ **Low GC pause time** - 13–77 ms total GC time per run; individual pauses 0.7–8.5 ms, all Young Generation
- ✅ **Thread-safe design** - No contention in multi-threaded mode
- ✅ **NFR-3 compliant** - Peak heap under the 512 MB target

> These figures reflect current `main` with production (INFO) logging and the per-worker `GeneratorContext` lifecycle (#286); they supersede figures from the first profiling pass, which predated those changes.

---

## Test Results

### Test 1: 100,000 Records (Single-threaded)

**Configuration:**
- Job: config/jobs/file_address.yaml
- Format: JSON
- Threads: 1
- JVM: Java Corretto-21.0.9 (21.0.9+10-LTS), -Xms512m -Xmx4g -XX:+UseG1GC
- System: 12 CPUs, ~30 GB RAM

**Performance:**
- Duration: 379 ms (engine generation + flush)
- Throughput: 263,852 records/sec
- Output file size: ~16 MB

**Memory Behavior:**
- Peak heap before GC: 60 MB
- Heap after GC: ~9 MB (stable across all cycles)
- Committed heap: 514 MB
- Reserved heap: 4 GB

**Garbage Collection:**
- Total GC cycles: 3 (all Young Generation / G1 Evacuation Pause)
- GC pause times: 3.7ms, 5.2ms range
- Total GC time: 13.1 ms
- No Full GC events

### Test 2: 1,000,000 Records (Single-threaded)

**Configuration:**
- Job: config/jobs/file_address.yaml
- Format: JSON
- Threads: 1
- JVM: Java Corretto-21.0.9 (21.0.9+10-LTS), -Xms512m -Xmx4g -XX:+UseG1GC

**Performance:**
- Duration: 2.001 seconds
- Throughput: 499,750 records/sec
- Output file size: ~160 MB

**Memory Behavior:**
- Peak heap before GC: 313 MB
- Heap after GC: ~9 MB (stable - **no memory leak detected**) ✅
- Committed heap: 514 MB
- Eden region growth: Linear until GC triggers

**Garbage Collection:**
- Total GC cycles: 7 (all Young Generation)
- GC pause times: Range 3.5ms - 8.5ms
- Total GC time: 35.0 ms
- No Full GC events

**Key Findings:**
1. ✅ **No memory leaks**: After-GC heap remains stable at ~9 MB across all cycles
2. ✅ **Bounded heap**: Peak ~313 MB for 1M records
3. ✅ **Predictable pauses**: All GC pauses under 9ms
4. ✅ **Only young GCs**: No Old Generation or Full GC events triggered

### Test 3: 4,000,000 Records (Multi-threaded - 4 threads)

**Configuration:**
- Job: config/jobs/file_address.yaml
- Format: JSON
- Threads: 4
- JVM: Java Corretto-21.0.9 (21.0.9+10-LTS), -Xms512m -Xmx4g -XX:+UseG1GC

**Performance:**
- Duration: 2.400 seconds
- Throughput: 1,666,667 records/sec
- Output file size: ~670 MB

**Memory Behavior:**
- Peak heap before GC: 457 MB
- Heap after GC: ~9-15 MB (stable - **no memory leak in multi-threaded mode**) ✅
- Committed heap: 742 MB (G1 expands under the higher parallel allocation rate)
- Reserved heap: 4 GB

**Garbage Collection:**
- Total GC cycles: 21 (all Young Generation)
- GC pause times: Range 0.7ms - 5.1ms
- Total GC time: 57.5 ms
- No Full GC events

**Multi-threading Insights:**
1. ✅ **No thread contention**: GC pauses remain short and consistent
2. ✅ **Memory stability**: Heap after GC remains at ~9-15 MB consistently
3. ✅ **Higher peak under parallelism**: Peak 457 MB and committed 742 MB — more concurrent in-flight chunks than single-threaded, still well within the 4 GB reserve
4. ✅ **No Full GC**: All collections Young Generation

### Test 4: 10,000,000 Records (Multi-threaded - 6 threads)

**Configuration:**
- Job: config/jobs/file_address.yaml
- Format: JSON
- Threads: 6
- JVM: Java Corretto-21.0.9 (21.0.9+10-LTS), -Xms512m -Xmx4g -XX:+UseG1GC
- Logging: Production level (INFO)

**Performance:**
- Duration: 4.989 seconds
- Throughput: 2,004,410 records/sec
- Output file size: 1.6 GB

**Memory Behavior:**
- Peak heap before GC: **457 MB** ✅ (under NFR-3 512 MB target)
- Heap after GC: ~9-14 MB (stable - **no memory leak in 6-thread mode**) ✅
- Committed heap: 742 MB
- Reserved heap: 4 GB

**Garbage Collection:**
- Total GC cycles: 42 (all Young Generation)
- GC pause times: Range 0.7ms - 5.7ms (average ~1.8ms)
- Total GC time: 76.8 ms
- No Full GC events

**Production Validation:**
1. ✅ **NFR-3 Compliance**: 457 MB < 512 MB requirement
2. ✅ **Memory stability**: Stable ~9-14 MB after GC across all 42 cycles
3. ✅ **Short pauses**: GC pauses 0.7–5.7ms, all Young Generation
4. ✅ **Thread scalability**: 6 threads with no contention or memory anomalies
5. ✅ **Bounded heap**: Same 457 MB peak as the 4M run — heap does not grow with record count

**Key Findings:**
1. ✅ **Memory-efficient**: Peak 457 MB for 10M records, bounded by streaming
2. ✅ **NFR-3 validated**: Under the 512 MB target
3. ✅ **No memory leaks**: Stable after-GC heap across all 42 cycles
4. ✅ **Predictable GC**: Frequent but very fast GC pauses (mostly 1-2ms), zero Full GC
5. ✅ **Thread-safe**: 6 concurrent workers with no contention or anomalies

---

## Summary of All Tests

| Test | Records | Threads | Duration | Throughput (rec/s) | Peak Heap | After GC | Committed | GC Cycles | GC Time |
|------|---------|---------|----------|--------------------|-----------|----------|-----------|-----------|---------|
| Test 1 | 100K | 1 | 0.38s | 263,852 | 60 MB | ~9 MB | 514 MB | 3 | 13.1 ms |
| Test 2 | 1M | 1 | 2.00s | 499,750 | 313 MB | ~9 MB | 514 MB | 7 | 35.0 ms |
| Test 3 | 4M | 4 | 2.40s | 1,666,667 | 457 MB | ~9-15 MB | 742 MB | 21 | 57.5 ms |
| Test 4 | **10M** | **6** | 4.99s | **2,004,410** | **457 MB** ✅ | **~9-14 MB** | 742 MB | 42 | 76.8 ms |

> Duration is the CLI `Time elapsed` figure — `engine.generate()` + `destination.flush()`, excluding the ~0.3–0.4s JVM + JFR startup. Throughput is as reported by the CLI.

**Overall Conclusions:**
- ✅ **No memory leaks** across all tests (stable 9-15 MB after GC)
- ✅ **Bounded heap**: Peak heap does not grow with record count (457 MB at both 4M and 10M) — streaming architecture confirmed
- ✅ **NFR-3 compliant**: Peak heap under 512 MB
- ✅ **Low GC pause time**: 13–77 ms total per run, all Young Generation, zero Full GC
- ✅ **Multi-threading**: higher committed heap (742 MB) under parallel allocation, no contention, still within the 4 GB reserve
- ✅ **Predictable behavior** under load

---

## Requirements Alignment

### Memory Efficiency Targets

**Design targets:**

| Requirement | Target | Actual Result | Status |
|-------------|--------|---------------|--------|
| Heap Usage | < 512 MB for 10M records | **457 MB for 10M records** | ✅ **PASS** |
| No Memory Leaks | Stable over repeated GC cycles | Stable 9-15 MB after GC, all 4 tests | ✅ Verified |
| Streaming Architecture | No in-memory buffers | Generate → serialize → send; peak heap bounded (457 MB at 4M and 10M) | ✅ Verified |
| GC Pressure | < 10% of CPU time | 13–77 ms total GC time per run | ✅ **PASS** |
| Thread-Local Cleanup | Proper cleanup | No leaks in 1-6 threads | ✅ Verified |

**Notes:**
- Peak heap at 10M is **457 MB**, about **11% under** the 512 MB target. The margin is tighter on multi-threaded runs than single-threaded (peak 60–313 MB) because G1 keeps more in-flight chunks resident and expands the committed heap to 742 MB.
- Heap is **bounded, not linear** in record count: 4M and 10M both peak at 457 MB, confirming the streaming pipeline holds no full dataset in memory.
- Runs complete in seconds, so GC time is a few percent of the (short) generation window though absolute GC time is tiny (13–77 ms); against total process runtime including JVM/JFR startup it is lower.

---

## Testing Methodology

### Profiling Script
Script: `scripts/profile-memory.sh`
- Uses Java Flight Recorder (JFR) with profile settings
- Captures allocation rates, GC activity, heap usage
- Generates `.jfr` recording and GC logs

**Usage:**
```bash
./scripts/profile-memory.sh <job-file> <record-count> [threads]
```

**Example:**
```bash
./scripts/profile-memory.sh config/jobs/file_address.yaml 1000000 4
```

**Output Location:** `build/run-output/profiling/` directory
- `memory-profile-*.jfr` - JFR recording
- `gc-*.log` - GC activity log

---

## JVM Configuration Recommendations

### For High-Throughput Workloads

**Recommended JVM Flags**:
```bash
-Xms512m               # Initial heap (adjust based on workload)
-Xmx4g                 # Max heap (4GB for multi-million records)
-XX:+UseG1GC           # G1 GC for low pause times
-XX:MaxGCPauseMillis=100  # Target 100ms max pause
-XX:G1HeapRegionSize=4m   # Larger regions for large objects
```

### For Memory-Constrained Environments

**Recommended JVM Flags**:
```bash
-Xms128m               # Smaller initial heap
-Xmx1g                 # 1GB max heap
-XX:+UseSerialGC       # Lower overhead for small heaps
```

---

## Profiling Tools

### Java Flight Recorder (JFR)

**View with JDK Mission Control:**
```bash
jmc build/run-output/profiling/memory-profile-*.jfr
```

**CLI Analysis:**
```bash
jfr print --events jdk.GarbageCollection build/run-output/profiling/memory-profile-*.jfr
jfr print --events jdk.GCHeapSummary build/run-output/profiling/memory-profile-*.jfr
jfr summary build/run-output/profiling/memory-profile-*.jfr
```

### Alternative Tools

- **VisualVM**: Real-time heap monitoring and CPU profiling
- **JConsole**: MBean monitoring and memory tracking
- **GC Logs**: `-Xlog:gc*:file=gc.log` for detailed GC analysis

---

## Conclusions

**All NFR-3 (Memory Efficiency) requirements met:**
1. ✅ Heap usage < 512 MB (peak 457 MB for 10M records)
2. ✅ No memory leaks (stable 9-15 MB after GC)
3. ✅ Streaming architecture verified (peak heap bounded regardless of record count)
4. ✅ GC pressure low (13–77 ms total per run, zero Full GC)
5. ✅ Thread-safe design (no contention detected)

**System Status:** Production-ready from memory perspective

**Recommendations:**
- Monitor GC metrics in production for workload-specific tuning
- Multi-threaded runs commit a larger heap (742 MB observed); size `-Xmx` with headroom above the ~460 MB peak when running many workers

---

**Last Updated**: October 7, 2026
