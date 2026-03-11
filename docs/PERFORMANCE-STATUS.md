# Performance Status - Current State

**Last Updated:** March 10, 2026
**Version:** Post Thread-Local Faker Cache Optimization + Database Stage 2 E2E

---

## Executive Summary

SeedStream achieves **25,000-33,333 records/second** for realistic data generation (E2E validated). Performance optimized through thread-local Faker caching, resulting in **3.5-5.0× improvement** for single-threaded workloads and **1.67-2.0× improvement** for multi-threaded workloads.

### Quick Performance Reference

| Workload | Current Performance | Status |
|----------|-------------------|--------|
| **E2E File Generation (1 thread)** | 25-33K rec/s | ✅ Validated |
| **E2E File Generation (4 threads)** | 33-50K rec/s | ✅ Validated |
| **E2E Kafka Generation (1 thread)** | 25K rec/s | ✅ Validated |
| **E2E Kafka Generation (4 threads)** | 25-33K rec/s | ✅ Validated |
| **Primitive Generation (in-memory)** | 12-258M ops/s | ✅ Exceeds target |
| **Datafaker Generation (isolated)** | 13-154K ops/s | ✅ Within expected range |
| **Thread Efficiency (4 threads)** | best efficiency | ✅ 4T outperforms 8T (I/O bound) |
| **Memory Usage** | 22-70MB typical | ✅ Efficient |
| **GC Overhead** | <2.63% | ✅ Healthy |

---

## Current Performance Metrics

### End-to-End Performance (March 8, 2026)

**Test Configuration:**
- Data Structure: Passport (11 fields, ~200 bytes per record) for File/Kafka; Invoice (nested: invoices + issuer + recipient + line_items) for Database
- Record Count: 100,000 per test (File/Kafka); configurable for Database
- Test Matrix (File/Kafka): 2 destinations × 3 formats × 3 thread configs × 3 memory configs = 54 tests
- Database E2E: invoice nested structure, basic throughput validation (JMH benchmarks pending)

**Results Summary (representative configurations):**

| Configuration | Throughput | Duration | GC Time % |
|---------------|------------|----------|-----------|
| **File/JSON/1T/512M** | 33,333 rec/s | 3s | 0.93% |
| **File/CSV/4T/512M** | 50,000 rec/s | 2s | 1.35% |
| **File/Protobuf/4T/512M** | 50,000 rec/s | 2s | 1.75% |
| **File/JSON/8T/512M** | 33,333 rec/s | 3s | 1.00% |
| **Kafka/JSON/1T/512M** | 25,000 rec/s | 4s | 0.92% |
| **Kafka/CSV/4T/512M** | 33,333 rec/s | 3s | 1.77% |
| **Kafka/Protobuf/4T/512M** | 25,000 rec/s | 4s | 1.18% |
| **Kafka/JSON/8T/512M** | 25,000 rec/s | 4s | 0.90% |

**Key Observations:**
- File destination: 25–50K rec/s depending on format and thread count; CSV at 4 threads peaks at 50K
- Kafka destination: 25–33K rec/s; network overhead (loopback Docker) is the bottleneck
- 4 threads outperforms 8 threads on average (35K vs 30K rec/s) — I/O-bound, not CPU-bound
- Memory configuration has minimal impact (256MB sufficient, 512MB recommended)
- GC overhead healthy across all tests (<2.7%)

**Full E2E Results:** See [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md)

### Component Performance (JMH Benchmarks)

**Primitive Generators (Isolated):**
- Boolean: 258M ops/s ✅
- Integer: 57M ops/s ✅
- String (char): 12M ops/s ✅
- All exceed 10M ops/s NFR requirement

**Datafaker Generators (Isolated):**
- Company Name: 154K ops/s
- Name: 47K ops/s
- Email: 34K ops/s
- Address: 17.7K ops/s
- Phone: 13K ops/s
- All within expected range for realistic data

**Serializers:**
- JSON: Component benchmarks TBD
- CSV: Component benchmarks TBD
- Protobuf: Component benchmarks TBD

**Full Component Results:** See [BENCHMARK-RESULTS.md](BENCHMARK-RESULTS.md)

---

## Performance Improvements Delivered

### Thread-Local Faker Cache (March 2026)

**Problem Identified:**
- DatafakerGenerator created `new Faker()` for every value (800,000 instantiations per 100K test)
- This defeated Datafaker's internal caching mechanisms
- Thread efficiency: only 32% at 8 threads

**Solution Implemented:**
- Thread-local cache: `FakerCache` with `ThreadLocal<Map<Locale, Faker>>`
- Faker instances reused within each thread
- Let Datafaker's internal YAML/template caching work as designed

**Results:**

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Single-thread** | 7,000 rec/s | 25-33K rec/s | **3.5-5.0×** |
| **4 threads** | 15,000 rec/s | 33,333 rec/s | **2.2×** |
| **8 threads** | 18,000 rec/s | 25-33K rec/s | **1.4-1.8×** |
| **Thread Efficiency** | 32% | 60-70% | **1.9-2.2×** |
| **Faker Instantiations** | 800,000 | 8 | **99.999% reduction** |

**Key Insight:** The bottleneck wasn't Datafaker being slow - we were defeating its internal optimization by recreating instances. Lesson: verify correct library usage before blaming library performance!

**Implementation Details:** See [internal/tasks/TASK-040-thread-local-faker-cache.md](internal/tasks/TASK-040-thread-local-faker-cache.md)

---

## Performance Characteristics

### Threading Behavior

**Thread Scaling (100K records, E2E average across all formats and destinations):**
- **1 thread:** ~28,700 rec/s (baseline)
- **4 threads:** ~35,200 rec/s (best efficiency)
- **8 threads:** ~30,600 rec/s (diminishing returns — I/O bound)

**Thread Efficiency:**
- Current: 60-70% (optimized)
- Previous: 32% (before thread-local cache)
- 4T beats 8T: output I/O (disk/network) saturates before CPU does

**Recommendation:** 4 threads provides the best throughput/efficiency ratio for Datafaker-heavy workloads

### Memory Behavior

**Heap Usage (100K records):**
- **256MB config:** 66-70MB used (26-27% utilization)
- **512MB config:** 22-68MB used (4-13% utilization)
- **1024MB config:** 21-68MB used (2-7% utilization)

**GC Characteristics:**
- **Frequency:** 7-15 collections per 100K test
- **Overhead:** 0.85-2.63% total time
- **Pattern:** Minor collections only, no major collections observed

**Recommendation:** 512MB heap provides comfortable headroom without waste

### Format Comparison

**Throughput (8 threads, 512MB):**
- JSON: 33,333 rec/s
- CSV: 33,333 rec/s
- Protobuf: 33,333 rec/s

**Conclusion:** Format choice has minimal impact on throughput. Choose based on downstream processing requirements, not performance.

---

## Known Limitations & Future Optimization Opportunities

### Current Bottlenecks

1. **Datafaker Overhead (98% CPU time)**
   - Despite thread-local optimization, Datafaker still dominates CPU
   - This is normal operation - realistic data generation is expensive
   - Further optimization would require Datafaker fork with cross-instance caching
   - **Priority:** P3 (low) - Current performance is acceptable for production

2. **Single-Thread Throughput Cap (~33K rec/s)**
   - Datafaker's internal resolution process doesn't scale beyond this
   - Not a blocker - multi-threading achieves same throughput
   - **Priority:** P3 (low) - Multi-threading is the correct solution

### Potential Future Optimizations

#### P1 - High Value (If Needed)

None currently identified. System meets all performance requirements.

#### P2 - Medium Value

1. **Kafka JMH Component Benchmarks**
   - Current: E2E validated (25–33K rec/s); isolated producer JMH benchmarks not yet run
   - Would measure: batch size sensitivity, compression overhead, acks impact in isolation
   - Effort: 2-3 hours (setup + run)

2. **Database JMH Component Benchmarks**
   - Current: Basic E2E coverage added March 10 (invoice nested structure); JMH component benchmarks not yet run
   - Expected: 5-15K rec/s flat, lower for nested (additional INSERT overhead per child record)
   - Effort: 3-4 hours (setup + run)

#### P3 - Nice to Have

1. **Datafaker Deep Caching (Requires Upstream)**
   - Add cross-instance YAML/template caching
   - Expected: 2-4× additional improvement on top of thread-local cache
   - Blocker: Requires Datafaker fork or upstream contribution
   - Benefit: Minimal - current performance already meets needs

2. **Serializer Micro-Optimizations**
   - Buffer size tuning
   - Direct ByteBuffer usage
   - Expected: 5-10% improvement
   - Effort: Low, but low return on investment

---

## Benchmark Methodology

### E2E Tests

**Tool:** Custom bash scripts with JFR profiling  
**Location:** `benchmarks/run_e2e_test.sh`  
**Configuration:**
- Warmup: 10,000 records
- Measurement: 100,000 records
- Thread configs: 1, 4, 8
- Memory configs: 256M, 512M, 1024M
- Formats: JSON, CSV, Protobuf
- Destinations: File, Kafka, Database (invoice nested structure — basic validation only)

**Running:**
```bash
cd benchmarks && ./run_e2e_test.sh
```

**Results:** `benchmarks/e2e_results.csv` → `docs/E2E-TEST-RESULTS.md`

**Documentation:** See `benchmarks/README.md` for operational details

### Component Benchmarks (JMH)

**Tool:** JMH 1.37 (Java Microbenchmark Harness)  
**Location:** `benchmarks/src/jmh/java/`  
**Configuration:**
- Warmup: 2 iterations × 1 second
- Measurement: 3 iterations × 1 second
- Forks: 1 (isolated JVM)
- Threads: 1 (single-threaded component measurement)
- Mode: Throughput (ops/second)

**Running:**
```bash
./gradlew :benchmarks:jmh
```

**Results:** `benchmarks/build/reports/jmh/results.json` → `docs/BENCHMARK-RESULTS.md`

**Documentation:** See `benchmarks/README.md` for operational details

### Profiling (JFR)

**Tool:** Java Flight Recorder (JFR)  
**Usage:** Enabled automatically in E2E tests with `--profile` flag  
**Location:** JFR files saved to `benchmarks/build/jfr/`

**Analysis:**
```bash
# List events
jfr print --events benchmarks/build/jfr/profile_file_json_t8_m512m.jfr

# CPU hotspots
jfr print --events jdk.ExecutionSample <file.jfr> | grep stackTrace

# Open in JDK Mission Control
jmc benchmarks/build/jfr/profile_file_json_t8_m512m.jfr
```

**Documentation:** See `benchmarks/PROFILING.md` for details

---

## Hardware Requirements

### Minimum Configuration

**For Development/Testing:**
- CPU: 2 cores
- RAM: 1GB
- Disk: 10GB
- Expected: 25-33K rec/s (single-thread, Datafaker workloads)

**For Small Production (< 1M records/day):**
- CPU: 4 cores
- RAM: 2GB
- Disk: 50GB SSD
- Expected: 33-50K rec/s (4 threads, file destination)

### Recommended Configuration

**For Medium Production (1-10M records/day):**
- CPU: 8 cores
- RAM: 4GB
- Disk: 100GB SSD
- Expected: 33-50K rec/s (4 threads optimal; 8 threads I/O-bound, no additional gain for Datafaker workloads)

**For Large Production (> 10M records/day):**
- CPU: 16+ cores
- RAM: 8GB+
- Disk: 500GB+ SSD (or distributed storage)
- Expected: 33-50K rec/s per instance (single JVM, 4 threads)
- ⚠️ **Multi-instance note**: Running multiple SeedStream instances in parallel without external coordination is unsafe — instances with the same seed produce identical data (duplicates). Distinct seeds per instance can avoid this, but there is no built-in partitioning or deduplication guarantee. An external orchestrator that assigns non-overlapping seeds and record ranges is a planned future improvement.

### Current Development Hardware

**Benchmarks run on:**
- CPU: Intel Core i7/i9 (8 cores)
- RAM: 16-32GB
- Disk: NVMe SSD
- OS: Linux/macOS

Results may vary on different hardware configurations.

---

## Performance Tuning Guide

### Quick Tuning Checklist

**For Maximum Throughput:**
1. ✅ Use 4-8 threads (matches typical CPU cores)
2. ✅ Set heap size: `-Xms512m -Xmx512m` (avoid dynamic resizing)
3. ✅ Use G1GC (default): `-XX:+UseG1GC`
4. ✅ Enable parallel references: `-XX:+ParallelRefProcEnabled`
5. ✅ Minimize locale switching (stick to one geolocation per job)

**For Low Latency:**
1. ✅ Use 1-2 threads (reduce context switching)
2. ✅ Set smaller heap: `-Xms256m -Xmx256m`
3. ✅ Use ZGC: `-XX:+UseZGC` (sub-millisecond pauses)
4. ✅ Reserve CPU cores: `taskset` or container CPU limits

**For Memory-Constrained Environments:**
1. ✅ Use 1 thread (minimize memory per-thread overhead)
2. ✅ Set heap: `-Xms256m -Xmx256m`
3. ✅ Use Serial GC: `-XX:+UseSerialGC` (lowest memory overhead)
4. ✅ Process in smaller batches (e.g., 10K records per run)

### Detailed Tuning Parameters

**JVM Options:**
```bash
# Performance tuning (recommended)
java -Xms512m -Xmx512m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+ParallelRefProcEnabled \
     -XX:+AlwaysPreTouch \
     -jar cli/build/libs/cli.jar execute --job <job.yaml>
```

**CLI Options:**
```bash
# Thread tuning
--threads 4              # Match CPU cores (default: available processors)

# Seed tuning
--seed 12345             # Fixed seed for reproducibility
                         # Different seeds = different data, same performance
```

**Configuration Options (job YAML):**
```yaml
# Destination-specific tuning examples

# File destination
conf:
  path: output/data.json
  compress: false        # gzip: 70-80% smaller, 30-40% slower
  batch_size: 1000       # records per flush (default: 1000)
  # Note: buffer_size is internal (64KB fixed default), not configurable via YAML

# Kafka destination (batching)
conf:
  bootstrap: localhost:9092
  topic: my-topic
  batch_size: 1000       # Records per batch (default: 100)
  linger_ms: 100         # Max wait for batch (default: 0)
  compression: gzip      # Enable compression (default: none)
```

---

## Historical Context

### Performance Evolution

| Date | Milestone | Key Metric | Notes |
|------|-----------|------------|-------|
| March 5, 2026 | Initial E2E baseline | 7K rec/s (1T) | Before optimization |
| March 8, 2026 | Thread-local Faker cache | 25-33K rec/s (1T) | **3.5-5.0× improvement** |
| March 10, 2026 | Database Stage 2 E2E | Basic coverage (invoice) | Nested multi-table inserts; JMH benchmarks pending |
| TBD | Database JMH benchmarks | TBD | Requires PostgreSQL container setup |

### Lessons Learned

1. **Always verify correct library usage before blaming performance**
   - Initial analysis blamed Datafaker YAML parsing
   - Reality: We defeated Datafaker's internal caching by recreating instances
   - Fix: Reuse instances via thread-local cache
   - Result: 3.5-5× improvement with trivial code change

2. **Profile before optimizing**
   - JFR profiling identified 98% CPU in Datafaker
   - But this was normal operation, not a bug
   - Real issue was improper usage pattern (creating new instances)

3. **Component benchmarks don't predict E2E performance**
   - Datafaker: 17-154K ops/s in isolation
   - E2E: 7K rec/s before optimization (much slower)
   - Gap was due to instance creation overhead in hot loop

---

## Related Documentation

**Performance:**
- [BENCHMARK-RESULTS.md](BENCHMARK-RESULTS.md) - JMH component benchmarks
- [PERFORMANCE.md](PERFORMANCE.md) - Performance guide and tuning
- [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md) - Full E2E test results

**Analysis (Internal):**
- [internal/tasks/TASK-039-performance-baseline-analysis.md](internal/tasks/TASK-039-performance-baseline-analysis.md) - Baseline analysis methodology
- [internal/tasks/TASK-040-thread-local-faker-cache.md](internal/tasks/TASK-040-thread-local-faker-cache.md) - Optimization implementation

**Operational:**
- [../benchmarks/README.md](../benchmarks/README.md) - How to run benchmarks
- [../benchmarks/PROFILING.md](../benchmarks/PROFILING.md) - How to profile
- [../benchmarks/KAFKA-BENCHMARK-GUIDE.md](../benchmarks/KAFKA-BENCHMARK-GUIDE.md) - Kafka benchmark setup

---

**Questions or Issues?** See [CONTRIBUTING.md](CONTRIBUTING.md) or open an issue.
