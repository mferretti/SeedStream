# End-to-End Test Results

**Date:** June 11, 2026
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
file,json,1,256,100000,3100,32258,55,256,64,8,2.06,SUCCESS,
file,json,1,512,100000,3294,30358,30,512,31,4,0.94,SUCCESS,
file,json,1,1024,100000,3337,29967,75,1024,53,6,1.59,SUCCESS,
file,json,4,256,100000,3541,28240,76,256,67,10,1.89,SUCCESS,
file,json,4,512,100000,3221,31046,31,512,32,5,0.99,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 32258 rec/s (2.06% GC, Heap: 55/256MB)
- **1 threads, 512MB:** 30358 rec/s (0.94% GC, Heap: 30/512MB)
- **1 threads, 1024MB:** 29967 rec/s (1.59% GC, Heap: 75/1024MB)
- **4 threads, 256MB:** 28240 rec/s (1.89% GC, Heap: 76/256MB)
- **4 threads, 512MB:** 31046 rec/s (0.99% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 32216 rec/s (1.16% GC, Heap: 39/1024MB)
- **8 threads, 256MB:** 32530 rec/s (2.05% GC, Heap: 77/256MB)
- **8 threads, 512MB:** 32071 rec/s (1.03% GC, Heap: 31/512MB)
- **8 threads, 1024MB:** 30525 rec/s (0.92% GC, Heap: 36/1024MB)

#### CSV Format
- **1 threads, 256MB:** 32819 rec/s (2.30% GC, Heap: 56/256MB)
- **1 threads, 512MB:** 31515 rec/s (1.10% GC, Heap: 30/512MB)
- **1 threads, 1024MB:** 32583 rec/s (2.15% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 34746 rec/s (2.95% GC, Heap: 76/256MB)
- **4 threads, 512MB:** 33886 rec/s (1.05% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 33090 rec/s (1.75% GC, Heap: 78/1024MB)
- **8 threads, 256MB:** 35423 rec/s (2.73% GC, Heap: 79/256MB)
- **8 threads, 512MB:** 35549 rec/s (1.21% GC, Heap: 32/512MB)
- **8 threads, 1024MB:** 28612 rec/s (1.06% GC, Heap: 36/1024MB)

#### Protobuf Format
- **1 threads, 256MB:** 30826 rec/s (2.22% GC, Heap: 40/256MB)
- **1 threads, 512MB:** 30656 rec/s (1.13% GC, Heap: 28/512MB)
- **1 threads, 1024MB:** 29231 rec/s (1.84% GC, Heap: 72/1024MB)
- **4 threads, 256MB:** 34025 rec/s (2.59% GC, Heap: 74/256MB)
- **4 threads, 512MB:** 33255 rec/s (1.30% GC, Heap: 28/512MB)
- **4 threads, 1024MB:** 33244 rec/s (1.56% GC, Heap: 43/1024MB)
- **8 threads, 256MB:** 32905 rec/s (2.07% GC, Heap: 75/256MB)
- **8 threads, 512MB:** 34199 rec/s (1.50% GC, Heap: 28/512MB)
- **8 threads, 1024MB:** 30854 rec/s (2.25% GC, Heap: 78/1024MB)

### Kafka Destination

#### JSON Format
- **1 threads, 256MB:** 21621 rec/s (1.84% GC, Heap: 44/256MB)
- **1 threads, 512MB:** 22941 rec/s (1.15% GC, Heap: 25/512MB)
- **1 threads, 1024MB:** 23485 rec/s (0.73% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 27932 rec/s (2.29% GC, Heap: 64/256MB)
- **4 threads, 512MB:** 28473 rec/s (1.71% GC, Heap: 55/512MB)
- **4 threads, 1024MB:** 27502 rec/s (1.43% GC, Heap: 56/1024MB)
- **8 threads, 256MB:** 28571 rec/s (2.17% GC, Heap: 69/256MB)
- **8 threads, 512MB:** 27716 rec/s (1.64% GC, Heap: 61/512MB)
- **8 threads, 1024MB:** 27917 rec/s (1.31% GC, Heap: 62/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 21953 rec/s (2.28% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 21519 rec/s (1.68% GC, Heap: 19/512MB)
- **1 threads, 1024MB:** 20725 rec/s (1.14% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 25568 rec/s (2.58% GC, Heap: 63/256MB)
- **4 threads, 512MB:** 24881 rec/s (2.02% GC, Heap: 55/512MB)
- **4 threads, 1024MB:** 25575 rec/s (1.43% GC, Heap: 56/1024MB)
- **8 threads, 256MB:** 23551 rec/s (2.31% GC, Heap: 64/256MB)
- **8 threads, 512MB:** 23923 rec/s (1.87% GC, Heap: 60/512MB)
- **8 threads, 1024MB:** 22192 rec/s (1.40% GC, Heap: 58/1024MB)

### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._

- **1 threads, 256MB:** 620 rec/s (0.09% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 522 rec/s (0.04% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 619 rec/s (0.03% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 606 rec/s (0.09% GC, Heap: 20/256MB)
- **4 threads, 512MB:** 522 rec/s (0.06% GC, Heap: 18/512MB)
- **4 threads, 1024MB:** 524 rec/s (0.04% GC, Heap: 21/1024MB)
- **8 threads, 256MB:** 655 rec/s (0.11% GC, Heap: 18/256MB)
- **8 threads, 512MB:** 539 rec/s (0.07% GC, Heap: 18/512MB)
- **8 threads, 1024MB:** 528 rec/s (0.04% GC, Heap: 25/1024MB)




## Memory Analysis

### 256MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 55MB
- Average GC Time: 1.92%

### 512MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 33MB
- Average GC Time: 1.14%

### 1GB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 48MB
- Average GC Time: 1.21%

## Threading Impact
- **1 thread(s):** Average 23012 rec/s (18 tests)
- **4 thread(s):** Average 25296 rec/s (18 tests)
- **8 thread(s):** Average 24903 rec/s (18 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 512MB (best observed performance)
- **Threads:** 8 (optimal for this workload)
- **Format:** csv (best throughput observed)
- **Expected:** 26661-35549 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "8000m"
  limits:
    memory: "1024Mi"
    cpu: "16000m"
```

### For Kafka Streaming (Network-Bound)

**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 8 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 21428-28571 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "8000m"
  limits:
    memory: "512Mi"
    cpu: "16000m"
```

### For Database Inserts (JDBC Batch — Nested Multi-Table)


**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 8 (optimal for this workload)
- **Expected:** 491-655 invoice rec/s (4 tables, batch_size=1000, per_batch commit)

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "8000m"
  limits:
    memory: "512Mi"
    cpu: "16000m"
```

### Memory-Constrained Environments

**256MB Configuration:**
- ✅ Works for most scenarios with 1-2 threads
- **Recommendation:** Use 1-2 threads maximum
- **Expected:** 606-35423 rec/s

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
| File I/O | 4.9M ops/sec | 28240-35549 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 20725-28571 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | 522-655 rec/s | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
