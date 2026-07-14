# End-to-End Test Results

> ### ⚠️ What these numbers actually measure
>
> This harness times the **whole CLI process** (`date +%s%3N` around `cli execute`), so every figure below
> includes ~1.5–1.7 s of fixed JVM startup, class loading, and Datafaker locale initialisation. At the 100K
> records/scenario used here, that overhead is roughly **half the wall clock**.
>
> That makes these numbers a fair answer to *"what does one 100K-record CLI run deliver?"* — and a poor one
> for *"what is the engine's throughput?"* or *"does adding threads help?"*. The fixed cost is identical at
> 1, 4, and 8 threads, so it flattens the thread-scaling columns below almost to noise.
>
> Engine-only rates for the same jobs (from the CLI's own `Time elapsed` line) are 2–3× higher, and threading
> clearly does help there: nested invoice → file goes **40K → 90K rec/s** from 1 to 8 threads (2.3×).
> See [PERFORMANCE.md §4 Thread Count](PERFORMANCE.md#4-thread-count).

**Date:** July 14, 2026
**Test Duration:** ~35 minutes
**Tests:** 54 executed, 9 skipped, 63 total
**Data Structures:** Invoice nested (invoices → issuer, recipient, line_items) for database; Passport (11 fields) for file/kafka
**Record Count:** 100000 per test
**Test Matrix:** 2 file/kafka destinations × 3 formats × 3 thread counts × 3 memory limits + 9 database tests = 63 tests

**⚠️ LOCAL TESTING ENVIRONMENT:**
All tests execute on a **single machine** with:
- Kafka broker in Docker container (`localhost:9092`)
- File destination on local SSD
- Zero network latency (loopback interface)

**Production environments** with real network infrastructure will experience:
- Network round-trip latency (1-100ms typical)
- Bandwidth constraints
- Lower Kafka throughput (expect 30-50% reduction)

**Test environment (auto-captured baseline hardware):**
- CPU: AMD Ryzen 5 PRO 4650U with Radeon Graphics (6 cores / 12 threads)
- RAM: 30Gi
- OS: Ubuntu 24.04.4 LTS (kernel 6.17.0-35-generic)
- JDK: OpenJDK 21.0.9; Docker version 29.6.1

Absolute throughput is hardware-bound — treat these figures as a **relative baseline** for this host, not production targets.

**Registry Refactoring Impact:** Tests run after implementing DatafakerRegistry pattern (commits fe83bd3, c299834). Performance remains **stable** - registry lookup overhead is negligible (<1% difference vs enum-based pre-refactoring baseline).

## Executive Summary

This benchmark measures **real-world, end-to-end performance** using the complete CLI pipeline:
1. Parse data structure YAML
2. Load job configuration
3. Initialize generators (Datafaker + primitives via DatafakerRegistry)
4. Generate records in parallel (1/4/8 threads)
5. Serialize to JSON/CSV/Protobuf
6. Write to File/Kafka destination

## Complete Results

_Showing first 5 rows — full data in [`benchmarks/e2e_results.csv`](e2e_results.csv)_

```csv
destination,format,threads,memory_mb,record_count,duration_ms,throughput_rps,heap_used_mb,heap_max_mb,gc_time_ms,gc_count,gc_time_percent,status,error
file,json,1,256,100000,2899,34494,56,256,59,6,2.04,SUCCESS,
file,json,1,512,100000,2932,34106,30,512,20,3,0.68,SUCCESS,
file,json,1,1024,100000,3017,33145,76,1024,56,5,1.86,SUCCESS,
file,json,4,256,100000,2877,34758,59,256,51,7,1.77,SUCCESS,
file,json,4,512,100000,2984,33512,31,512,25,3,0.84,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 34494 rec/s (2.04% GC, Heap: 56/256MB)
- **1 threads, 512MB:** 34106 rec/s (0.68% GC, Heap: 30/512MB)
- **1 threads, 1024MB:** 33145 rec/s (1.86% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 34758 rec/s (1.77% GC, Heap: 59/256MB)
- **4 threads, 512MB:** 33512 rec/s (0.84% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 35498 rec/s (2.09% GC, Heap: 77/1024MB)
- **8 threads, 256MB:** 34364 rec/s (1.92% GC, Heap: 56/256MB)
- **8 threads, 512MB:** 35612 rec/s (0.78% GC, Heap: 31/512MB)
- **8 threads, 1024MB:** 33978 rec/s (1.77% GC, Heap: 78/1024MB)

#### CSV Format
- **1 threads, 256MB:** 35919 rec/s (2.19% GC, Heap: 58/256MB)
- **1 threads, 512MB:** 35561 rec/s (0.96% GC, Heap: 31/512MB)
- **1 threads, 1024MB:** 33200 rec/s (2.12% GC, Heap: 77/1024MB)
- **4 threads, 256MB:** 38971 rec/s (1.99% GC, Heap: 57/256MB)
- **4 threads, 512MB:** 38955 rec/s (1.13% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 36805 rec/s (2.13% GC, Heap: 79/1024MB)
- **8 threads, 256MB:** 33704 rec/s (1.75% GC, Heap: 60/256MB)
- **8 threads, 512MB:** 37495 rec/s (1.01% GC, Heap: 33/512MB)
- **8 threads, 1024MB:** 33145 rec/s (1.69% GC, Heap: 79/1024MB)

#### Protobuf Format
- **1 threads, 256MB:** 32341 rec/s (2.46% GC, Heap: 40/256MB)
- **1 threads, 512MB:** 32970 rec/s (1.09% GC, Heap: 29/512MB)
- **1 threads, 1024MB:** 32647 rec/s (1.86% GC, Heap: 73/1024MB)
- **4 threads, 256MB:** 35997 rec/s (2.05% GC, Heap: 41/256MB)
- **4 threads, 512MB:** 36563 rec/s (1.06% GC, Heap: 29/512MB)
- **4 threads, 1024MB:** 35075 rec/s (1.86% GC, Heap: 73/1024MB)
- **8 threads, 256MB:** 35398 rec/s (2.05% GC, Heap: 41/256MB)
- **8 threads, 512MB:** 37397 rec/s (1.20% GC, Heap: 30/512MB)
- **8 threads, 1024MB:** 35790 rec/s (2.15% GC, Heap: 74/1024MB)

### Kafka Destination

#### JSON Format
- **1 threads, 256MB:** 26075 rec/s (2.24% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 25529 rec/s (1.10% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 24606 rec/s (0.89% GC, Heap: 19/1024MB)
- **4 threads, 256MB:** 30165 rec/s (2.17% GC, Heap: 70/256MB)
- **4 threads, 512MB:** 30703 rec/s (1.50% GC, Heap: 53/512MB)
- **4 threads, 1024MB:** 29403 rec/s (1.35% GC, Heap: 53/1024MB)
- **8 threads, 256MB:** 27847 rec/s (2.17% GC, Heap: 79/256MB)
- **8 threads, 512MB:** 28710 rec/s (1.52% GC, Heap: 74/512MB)
- **8 threads, 1024MB:** 30441 rec/s (1.55% GC, Heap: 70/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 22593 rec/s (2.26% GC, Heap: 19/256MB)
- **1 threads, 512MB:** 23020 rec/s (1.66% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 21862 rec/s (1.01% GC, Heap: 19/1024MB)
- **4 threads, 256MB:** 25926 rec/s (2.85% GC, Heap: 68/256MB)
- **4 threads, 512MB:** 26178 rec/s (1.86% GC, Heap: 61/512MB)
- **4 threads, 1024MB:** 25549 rec/s (1.46% GC, Heap: 54/1024MB)
- **8 threads, 256MB:** 22993 rec/s (2.48% GC, Heap: 112/256MB)
- **8 threads, 512MB:** 23551 rec/s (1.77% GC, Heap: 73/512MB)
- **8 threads, 1024MB:** 23843 rec/s (1.69% GC, Heap: 68/1024MB)

### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._

- **1 threads, 256MB:** 550 rec/s (0.09% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 617 rec/s (0.06% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 605 rec/s (0.03% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 615 rec/s (0.11% GC, Heap: 21/256MB)
- **4 threads, 512MB:** 589 rec/s (0.07% GC, Heap: 25/512MB)
- **4 threads, 1024MB:** 485 rec/s (0.04% GC, Heap: 30/1024MB)
- **8 threads, 256MB:** 562 rec/s (0.15% GC, Heap: 59/256MB)
- **8 threads, 512MB:** 584 rec/s (0.09% GC, Heap: 40/512MB)
- **8 threads, 1024MB:** 616 rec/s (0.07% GC, Heap: 59/1024MB)




## Memory Analysis

### 256MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 52MB
- Average GC Time: 1.82%

### 512MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 36MB
- Average GC Time: 1.02%

### 1GB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 60MB
- Average GC Time: 1.42%

## Threading Impact
- **1 thread(s):** Average 24991 rec/s (18 tests)
- **4 thread(s):** Average 27541 rec/s (18 tests)
- **8 thread(s):** Average 26446 rec/s (18 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** csv (best throughput observed)
- **Expected:** 29228-38971 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "4000m"
  limits:
    memory: "512Mi"
    cpu: "8000m"
```

### For Kafka Streaming (Network-Bound)

**Recommended Configuration:**
- **Memory:** 512MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 23027-30703 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "4000m"
  limits:
    memory: "1024Mi"
    cpu: "8000m"
```

### For Database Inserts (JDBC Batch — Nested Multi-Table)


**Recommended Configuration:**
- **Memory:** 512MB (best observed performance)
- **Threads:** 1 (optimal for this workload)
- **Expected:** 462-617 invoice rec/s (4 tables, batch_size=1000, per_batch commit)

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "1000m"
  limits:
    memory: "1024Mi"
    cpu: "2000m"
```

### Memory-Constrained Environments

**256MB Configuration:**
- ✅ Works for most scenarios with 1-2 threads
- **Recommendation:** Use 1-2 threads maximum
- **Expected:** 550-38971 rec/s

## Known Limitations

1. **Async Kafka Mode:** Not tested (config issue with idempotence)
2. **Database Destination:** Requires a running `postgres-benchmark` Docker container — tests skipped automatically if unavailable
3. **Local Testing:** All Kafka and database tests use Docker on localhost - production network latency not reflected (see warning at top)
4. **Disk Speed:** File throughput depends on storage type (SSD vs HDD)

## Comparison with Component Benchmarks

_Isolated benchmarks are from JMH runs (static). End-to-End columns are computed from this run's results._

| Component | Isolated Benchmark | End-to-End (this run) | Notes |
|-----------|-------------------|----------------------|-------|
| Primitive Generators | 259M ops/sec | - | Baseline (JMH) |
| Datafaker Generators | 12K-154K ops/sec | - | 1,680× slower (JMH) |
| JSON Serializer | 2.9M ops/sec | - | 89× slower (JMH) |
| CSV Serializer | Same as JSON | - | 89× slower (JMH) |
| File I/O | 4.9M ops/sec | 32341-38971 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 21862-30703 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | 485-617 rec/s | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
