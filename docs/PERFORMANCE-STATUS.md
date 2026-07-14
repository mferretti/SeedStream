# Performance Status - Current State

**Last Updated:** July 14, 2026
**Version:** Post worker-side parallel serialization + `FieldRecord` flyweight + queue chunk-batching

> **June 2026 re-measure** with a millisecond timer. Earlier figures used a
> whole-second timer that quantized throughput to 100000/{2,3,4}s and inflated
> peaks (the "50,000 rec/s" entries were 100000/2s rounding). File uses the flat
> `passport` structure; Kafka and Database now use the nested `invoice` structure,
> so Kafka numbers are lower than the old passport-based Kafka runs.

---

## Executive Summary

SeedStream sustains **~32,000–39,000 records/second** for flat-record file generation and **~21,000–31,000 rec/s** for nested-record Kafka (E2E validated, ms timer). Database (nested invoice → 4 tables) is JDBC-bound at **~590–620 rec/s**. Performance comes from thread-local Faker caching, worker-side parallel serialization, and the `FieldRecord` flyweight (low GC).

> **July 2026 full re-run.** Every JMH and E2E benchmark was re-measured on 14 Jul 2026 against current
> `main`. End-to-end throughput is **unchanged** — but the component picture moved substantially:
>
> - **Datafaker generators are 7–65× faster** than the last recorded figures (`FakerCache`, commit `cf3492d`,
>   landed after the March benchmark run). Primitives and serializers are unchanged, confirming the effect
>   is real rather than a measurement artefact.
> - **Threading works fine; the E2E harness hides it.** `run_e2e_test.sh` times the whole CLI process, including
>   ~1.5–1.7 s of fixed JVM + locale startup — roughly half the wall clock of a 100K-record run — so speeding up
>   generation barely moves its number. On the engine's own clock at **1M records**, 8 threads vs 1 gives
>   **3.6×** (nested invoice → file), **2.1×** (passport → file), **2.1×** (primitives, hitting the ~1.5M rec/s
>   writer ceiling), **1.7×** (Kafka). Only generation and serialization are parallel; the writer thread is serial.
>   **Benchmark with 1M+ records** — at 100–200K, JIT warmup also distorts the picture downward.
> - **Kafka scales worst (1.7×)** because `KafkaProducer.send()` compresses each record into its batch buffer
>   *on the calling thread* — i.e. on the single writer thread. `compression: none` is **+45% at 4 threads**
>   (103.5K vs 71.6K rec/s) and scales 2.2×. `KafkaProducer` is thread-safe, so letting workers send directly
>   would likely recover most of the gap.
> - **NFR-1's 500 MB/s file target is not met on this hardware, and the limit is CPU.** The full pipeline
>   reaches **306 MB/s** (8 threads, 526-byte records, 1M records). Neither the disk (2.3 GB/s buffered) nor
>   the single writer thread (~1.85M rec/s ≈ 930 MB/s) binds first — generation + Jackson serialization
>   saturate the CPU. Both already run in parallel on the workers and already stream without an intermediate
>   `String`; there is no cheap fix left. NFR-1 is now recorded as **expected but unverified**, with a
>   falsifiable prediction (~10–12 cores) and a one-command reproduction for anyone with better hardware.
> - A 100K-record run writes ~24 MB and never leaves page cache: **no file benchmark in this project has ever
>   touched the disk.** Every file number here is a CPU measurement.
> - **Protobuf serialization was measured for the first time** and is the *slowest* format, ~2× slower than
>   JSON — the previous "~2.5M ops/s (est.)" figures were optimistic guesses.
>
> The practical consequence: **thread count pays in proportion to generation cost.** Generation-heavy nested
> structures scale ~3.6×; flatter records ~2.1×. For Kafka, drop compression before adding threads.

### Quick Performance Reference

| Workload | Current Performance | Status |
|----------|-------------------|--------|
| **E2E File Generation (passport, 1 thread)** | 33-37K rec/s | ✅ Validated |
| **E2E File Generation (passport, 4 threads)** | 32-39K rec/s | ✅ Validated |
| **E2E Kafka Generation (invoice, 1 thread)** | 21-26K rec/s | ✅ Validated |
| **E2E Kafka Generation (invoice, 4 threads)** | 25-32K rec/s | ✅ Validated |
| **E2E Database (invoice → 4 tables)** | 0.5-0.7K rec/s | ✅ Validated (JDBC-bound) |
| **Primitive Generation (in-memory)** | 4-252M ops/s | ✅ Exceeds target |
| **Datafaker Generation (isolated)** | 108K-1.1M ops/s | ✅ 7-65× faster since `FakerCache` |
| **Regex Types (isolated)** | 1.2-5.1M ops/s | ✅ Cheaper than a Datafaker name |
| **Thread Efficiency (8 threads, 1M records)** | 1.7×–3.6× (structure-dependent) | ✅ Only generation is parallel; writer is serial |
| **File write path (8T, 526 B, 1M records)** | 306 MB/s | ⚠️ NFR-1 target is 500 MB/s — CPU-bound; expected but unverified |
| **Single writer thread ceiling** | ~1.85M rec/s (~930 MB/s @ 526 B) | ✅ Not the binding constraint |
| **Disk ceiling (reference NVMe)** | 2.3 GB/s buffered / 1.1 GB/s sustained | ✅ Not the binding constraint |
| **Memory Usage** | 22-70MB typical | ✅ Efficient |
| **GC Overhead** | <2.63% | ✅ Healthy |

---

## Current Performance Metrics

### End-to-End Performance (June 14, 2026)

**Test Configuration:**
- Data Structure: Passport (11 flat fields, ~200 bytes/record) for File; Invoice (nested: invoices + issuer + recipient + line_items) for Kafka and Database
- Record Count: 100,000 per test
- Timing: millisecond wall clock (`records × 1000 / duration_ms`)
- Test Matrix: file (3 formats) + kafka (2 formats; csv N/A for nested) + database = 54 executed, 9 skipped
- Full per-config tables: [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md)

**Results Summary (representative /512M configurations):**

| Configuration | Throughput | Duration | GC Time % |
|---------------|------------|----------|-----------|
| **File/JSON/1T** | 33,658 rec/s | 3.0s | 0.74% |
| **File/CSV/4T** | 37,965 rec/s | 2.6s | 0.91% |
| **File/Protobuf/4T** | 36,496 rec/s | 2.7s | 1.35% |
| **File/JSON/8T** | 32,435 rec/s | 3.1s | 0.94% |
| **Kafka/JSON/1T** | 26,184 rec/s | 3.8s | 1.13% |
| **Kafka/Protobuf/4T** | 24,666 rec/s | 4.1s | 1.87% |
| **Kafka/JSON/8T** | 29,779 rec/s | 3.4s | 1.61% |
| **Database/8T/256M** | 538 rec/s | 185.8s | — |

**Key Observations:**
- File (flat passport): ~32–39K rec/s; formats converge and thread count has modest effect. Heap 28–78 MB, GC <5%.
- Kafka (nested invoice): ~21–32K rec/s; serialization is heavy enough that throughput **scales with threads** (1→4: ~26K→~32K).
- Database (nested invoice → 4 tables): ~530–676 **invoices**/s, but each invoice folds into ~13.5 physical rows (1 invoices + 1 issuer + 1 recipient + ~10.5 line_items), so a 100K run writes ~1.35M rows ≈ **~8,800 rows/s**. JDBC-bound; more threads do **not** help (single write path). Heap 18–27 MB. See [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md) (Database Destination scenario).
- Memory configuration has minimal impact (256MB sufficient, 512MB recommended).
- GC overhead healthy across all tests (<2.7%)

**Full E2E Results:** See [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md)

### Component Performance (JMH Benchmarks)

**Primitive Generators (Isolated):**
- Boolean: 252M ops/s ✅
- Enum: 142M ops/s ✅
- Integer: 63M ops/s ✅
- Date: 22M ops/s ✅ (was 2.4M — now clears NFR-1; cause not attributed)
- String (char): 12.4M ops/s ✅
- Decimal 4.3M / Timestamp 4.5M ops/s ⚠️ below the 10M NFR, but ~100× above any destination's drain rate

**Datafaker Generators (Isolated)** — re-measured 14 Jul 2026, high-fidelity config:
- Company Name: 1.10M ops/s (was 154K — 7.1×)
- City: 921K ops/s (was 14K — 64.7×)
- Name: 863K ops/s (was 23K — 37.2×)
- Email: 325K ops/s (was 24K — 13.5×)
- Address: 261K ops/s (was 17.6K — 14.8×)
- Phone: 108K ops/s (was 13K — 8.5×)

The `Name: 47K / Email: 34K` figures previously in this file disagreed with BENCHMARK-RESULTS.md
(23K / 24K). Both are now superseded; the numbers above are the reconciled, re-measured values.

**Regex Types (`registerRegex` → RgxGen), new:**
- `ORD-\d{8}`: 5.14M ops/s — `(INV|CRN|DBN)-[0-9]{6}`: 4.46M ops/s
- `[A-Z0-9]{10,35}`: 1.85M ops/s — `[a-z]+` (unbounded, 100-rep cap): 1.20M ops/s
- Pattern compile: 0.59–2.97 µs, once at `--faker-types` load

**Serializers (`SerializerBenchmark`):**
- JSON: 3.14M ops/s (simple), 1.07M (complex), 688K (nested)
- CSV: 2.58M ops/s (simple), 960K (complex), 240K (nested — double-serializes object→JSON→CSV)
- Protobuf: **1.48M ops/s (simple), 569K (complex), 307K (nested)** — measured for the first time.
  Protobuf is the **slowest** serializer, ~2× slower than JSON. Earlier estimates of ~2.5M were wrong.

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
   - Cross-instance caching is now addressed upstream: [datafaker-net/datafaker#1819](https://github.com/datafaker-net/datafaker/pull/1819) makes the expression-resolution and regex-compilation caches static/shared across Faker instances (open, not yet merged). Once merged + released, expect a further reduction here without any SeedStream code change.
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

1. **Datafaker Deep Caching (Upstream PR submitted)**
   - Add cross-instance expression-resolution + regex-compilation caching
   - Expected: 2-4× additional improvement on top of thread-local cache
   - Status: submitted upstream as [datafaker-net/datafaker#1819](https://github.com/datafaker-net/datafaker/pull/1819) — makes `FakeValuesService` caches static (two-level: static `RECIPE_MAP` L1 + per-instance L2, plus static `expression2generex` regex cache). No public API change; lands transparently on the next Datafaker bump. Currently **open, not merged**.
   - Benefit: Minimal for current needs — meaningful for seeded-per-record workloads that churn short-lived Fakers

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

**Benchmarks run on (June 14, 2026 baseline):**
- CPU: AMD Ryzen 5 PRO 4650U — 6 cores / 12 threads, max 2.1 GHz (low-power U-series)
- RAM: 30 GiB
- Disk: local SSD
- OS: Ubuntu 24.04.4 LTS (kernel 6.17.0-35-generic)
- JDK: OpenJDK 21.0.9 LTS; Docker 29.5.3 (Kafka + PostgreSQL on `localhost`)

The 2.1 GHz power-constrained mobile CPU caps absolute throughput; a 3.5+ GHz
desktop/server CPU reports proportionally higher rec/s. Treat these as a
relative baseline for the same host, not production targets.

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

**Operational:**
- [../benchmarks/README.md](../benchmarks/README.md) - How to run benchmarks
- [../benchmarks/PROFILING.md](../benchmarks/PROFILING.md) - How to profile
- [../benchmarks/KAFKA-BENCHMARK-GUIDE.md](../benchmarks/KAFKA-BENCHMARK-GUIDE.md) - Kafka benchmark setup

---

**Questions or Issues?** See [CONTRIBUTING.md](CONTRIBUTING.md) or open an issue.
