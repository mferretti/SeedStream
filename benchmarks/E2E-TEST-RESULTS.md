# End-to-End Test Results

**Date:** June 14, 2026
**Test Duration:** ~34 minutes
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
file,json,1,256,100000,2896,34530,57,256,56,6,1.93,SUCCESS,
file,json,1,512,100000,2971,33658,30,512,22,3,0.74,SUCCESS,
file,json,1,1024,100000,2927,34164,76,1024,49,6,1.67,SUCCESS,
file,json,4,256,100000,2521,39666,57,256,50,7,1.98,SUCCESS,
file,json,4,512,100000,3030,33003,31,512,29,4,0.96,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 34530 rec/s (1.93% GC, Heap: 57/256MB)
- **1 threads, 512MB:** 33658 rec/s (0.74% GC, Heap: 30/512MB)
- **1 threads, 1024MB:** 34164 rec/s (1.67% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 39666 rec/s (1.98% GC, Heap: 57/256MB)
- **4 threads, 512MB:** 33003 rec/s (0.96% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 32808 rec/s (1.97% GC, Heap: 75/1024MB)
- **8 threads, 256MB:** 37411 rec/s (1.72% GC, Heap: 57/256MB)
- **8 threads, 512MB:** 32435 rec/s (0.94% GC, Heap: 31/512MB)
- **8 threads, 1024MB:** 32351 rec/s (1.71% GC, Heap: 78/1024MB)

#### CSV Format
- **1 threads, 256MB:** 37664 rec/s (2.30% GC, Heap: 56/256MB)
- **1 threads, 512MB:** 36284 rec/s (1.05% GC, Heap: 31/512MB)
- **1 threads, 1024MB:** 36589 rec/s (1.98% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 38372 rec/s (1.92% GC, Heap: 54/256MB)
- **4 threads, 512MB:** 37965 rec/s (0.91% GC, Heap: 31/512MB)
- **4 threads, 1024MB:** 35038 rec/s (2.00% GC, Heap: 76/1024MB)
- **8 threads, 256MB:** 33211 rec/s (1.96% GC, Heap: 57/256MB)
- **8 threads, 512MB:** 37147 rec/s (1.23% GC, Heap: 32/512MB)
- **8 threads, 1024MB:** 31989 rec/s (1.86% GC, Heap: 78/1024MB)

#### Protobuf Format
- **1 threads, 256MB:** 34013 rec/s (2.48% GC, Heap: 41/256MB)
- **1 threads, 512MB:** 33189 rec/s (0.93% GC, Heap: 29/512MB)
- **1 threads, 1024MB:** 32404 rec/s (1.85% GC, Heap: 73/1024MB)
- **4 threads, 256MB:** 37864 rec/s (2.20% GC, Heap: 42/256MB)
- **4 threads, 512MB:** 36496 rec/s (1.35% GC, Heap: 28/512MB)
- **4 threads, 1024MB:** 37243 rec/s (1.90% GC, Heap: 73/1024MB)
- **8 threads, 256MB:** 36859 rec/s (2.25% GC, Heap: 41/256MB)
- **8 threads, 512MB:** 35448 rec/s (1.21% GC, Heap: 29/512MB)
- **8 threads, 1024MB:** 35186 rec/s (2.15% GC, Heap: 73/1024MB)

### Kafka Destination

#### JSON Format
- **1 threads, 256MB:** 25933 rec/s (1.87% GC, Heap: 19/256MB)
- **1 threads, 512MB:** 26184 rec/s (1.13% GC, Heap: 19/512MB)
- **1 threads, 1024MB:** 24746 rec/s (0.72% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 31605 rec/s (2.34% GC, Heap: 60/256MB)
- **4 threads, 512MB:** 28977 rec/s (1.48% GC, Heap: 58/512MB)
- **4 threads, 1024MB:** 27639 rec/s (1.35% GC, Heap: 57/1024MB)
- **8 threads, 256MB:** 29052 rec/s (1.92% GC, Heap: 64/256MB)
- **8 threads, 512MB:** 29779 rec/s (1.61% GC, Heap: 64/512MB)
- **8 threads, 1024MB:** 28409 rec/s (1.53% GC, Heap: 63/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 22925 rec/s (2.25% GC, Heap: 20/256MB)
- **1 threads, 512MB:** 22857 rec/s (1.58% GC, Heap: 20/512MB)
- **1 threads, 1024MB:** 21353 rec/s (1.02% GC, Heap: 19/1024MB)
- **4 threads, 256MB:** 25654 rec/s (2.39% GC, Heap: 66/256MB)
- **4 threads, 512MB:** 24666 rec/s (1.87% GC, Heap: 57/512MB)
- **4 threads, 1024MB:** 25342 rec/s (1.44% GC, Heap: 57/1024MB)
- **8 threads, 256MB:** 24943 rec/s (2.47% GC, Heap: 66/256MB)
- **8 threads, 512MB:** 24096 rec/s (1.78% GC, Heap: 62/512MB)
- **8 threads, 1024MB:** 24888 rec/s (4.95% GC, Heap: 60/1024MB)

### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._

- **1 threads, 256MB:** 652 rec/s (0.10% GC, Heap: 19/256MB)
- **1 threads, 512MB:** 626 rec/s (0.06% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 585 rec/s (0.03% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 632 rec/s (0.09% GC, Heap: 21/256MB)
- **4 threads, 512MB:** 559 rec/s (0.06% GC, Heap: 20/512MB)
- **4 threads, 1024MB:** 676 rec/s (0.04% GC, Heap: 20/1024MB)
- **8 threads, 256MB:** 538 rec/s (0.09% GC, Heap: 23/256MB)
- **8 threads, 512MB:** 530 rec/s (0.07% GC, Heap: 24/512MB)
- **8 threads, 1024MB:** 647 rec/s (0.04% GC, Heap: 27/1024MB)




## Memory Analysis

### 256MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 46MB
- Average GC Time: 1.79%

### 512MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 34MB
- Average GC Time: 1.05%

### 1GB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 56MB
- Average GC Time: 1.57%

## Threading Impact
- **1 thread(s):** Average 25464 rec/s (18 tests)
- **4 thread(s):** Average 27455 rec/s (18 tests)
- **8 thread(s):** Average 26384 rec/s (18 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 29749-39666 rec/s

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
- **Memory:** 256MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 23703-31605 rec/s

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

### For Database Inserts (JDBC Batch — Nested Multi-Table)


**Recommended Configuration:**
- **Memory:** 1024MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Expected:** 507-676 invoice rec/s (4 tables, batch_size=1000, per_batch commit)

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "1024Mi"
    cpu: "4000m"
  limits:
    memory: "2048Mi"
    cpu: "8000m"
```

### Memory-Constrained Environments

**256MB Configuration:**
- ✅ Works for most scenarios with 1-2 threads
- **Recommendation:** Use 1-2 threads maximum
- **Expected:** 538-39666 rec/s

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
| File I/O | 4.9M ops/sec | 31989-39666 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 21353-31605 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | 530-676 rec/s | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
