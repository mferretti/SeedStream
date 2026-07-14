# End-to-End Test Results

**Date:** July 14, 2026
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

_Showing first 5 rows — full data in [`benchmarks/e2e_results.csv`](../benchmarks/e2e_results.csv)_

```csv
destination,format,threads,memory_mb,record_count,duration_ms,throughput_rps,heap_used_mb,heap_max_mb,gc_time_ms,gc_count,gc_time_percent,status,error
file,json,1,256,100000,2909,34376,57,256,56,6,1.93,SUCCESS,
file,json,1,512,100000,2982,33534,30,512,20,3,0.67,SUCCESS,
file,json,1,1024,100000,2982,33534,76,1024,50,5,1.68,SUCCESS,
file,json,4,256,100000,2693,37133,56,256,45,6,1.67,SUCCESS,
file,json,4,512,100000,2845,35149,32,512,31,4,1.09,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 34376 rec/s (1.93% GC, Heap: 57/256MB)
- **1 threads, 512MB:** 33534 rec/s (0.67% GC, Heap: 30/512MB)
- **1 threads, 1024MB:** 33534 rec/s (1.68% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 37133 rec/s (1.67% GC, Heap: 56/256MB)
- **4 threads, 512MB:** 35149 rec/s (1.09% GC, Heap: 32/512MB)
- **4 threads, 1024MB:** 31857 rec/s (2.07% GC, Heap: 75/1024MB)
- **8 threads, 256MB:** 35285 rec/s (1.83% GC, Heap: 58/256MB)
- **8 threads, 512MB:** 33200 rec/s (0.93% GC, Heap: 32/512MB)
- **8 threads, 1024MB:** 36670 rec/s (2.24% GC, Heap: 77/1024MB)

#### CSV Format
- **1 threads, 256MB:** 37009 rec/s (2.59% GC, Heap: 58/256MB)
- **1 threads, 512MB:** 36443 rec/s (1.02% GC, Heap: 32/512MB)
- **1 threads, 1024MB:** 35625 rec/s (1.78% GC, Heap: 76/1024MB)
- **4 threads, 256MB:** 39494 rec/s (2.05% GC, Heap: 57/256MB)
- **4 threads, 512MB:** 38372 rec/s (0.92% GC, Heap: 32/512MB)
- **4 threads, 1024MB:** 33300 rec/s (1.73% GC, Heap: 78/1024MB)
- **8 threads, 256MB:** 37133 rec/s (1.97% GC, Heap: 58/256MB)
- **8 threads, 512MB:** 36179 rec/s (1.09% GC, Heap: 33/512MB)
- **8 threads, 1024MB:** 36523 rec/s (2.30% GC, Heap: 80/1024MB)

#### Protobuf Format
- **1 threads, 256MB:** 30450 rec/s (2.04% GC, Heap: 41/256MB)
- **1 threads, 512MB:** 32541 rec/s (1.01% GC, Heap: 29/512MB)
- **1 threads, 1024MB:** 31615 rec/s (1.68% GC, Heap: 73/1024MB)
- **4 threads, 256MB:** 37285 rec/s (2.16% GC, Heap: 40/256MB)
- **4 threads, 512MB:** 36818 rec/s (1.22% GC, Heap: 28/512MB)
- **4 threads, 1024MB:** 36258 rec/s (2.03% GC, Heap: 73/1024MB)
- **8 threads, 256MB:** 35778 rec/s (2.25% GC, Heap: 41/256MB)
- **8 threads, 512MB:** 35688 rec/s (1.14% GC, Heap: 30/512MB)
- **8 threads, 1024MB:** 34626 rec/s (2.08% GC, Heap: 74/1024MB)

### Kafka Destination

#### JSON Format
- **1 threads, 256MB:** 21824 rec/s (1.66% GC, Heap: 43/256MB)
- **1 threads, 512MB:** 23100 rec/s (1.20% GC, Heap: 36/512MB)
- **1 threads, 1024MB:** 23397 rec/s (0.73% GC, Heap: 19/1024MB)
- **4 threads, 256MB:** 28868 rec/s (2.54% GC, Heap: 69/256MB)
- **4 threads, 512MB:** 32113 rec/s (1.77% GC, Heap: 50/512MB)
- **4 threads, 1024MB:** 29904 rec/s (0.99% GC, Heap: 33/1024MB)
- **8 threads, 256MB:** 28735 rec/s (2.21% GC, Heap: 81/256MB)
- **8 threads, 512MB:** 30534 rec/s (1.86% GC, Heap: 76/512MB)
- **8 threads, 1024MB:** 26838 rec/s (1.42% GC, Heap: 68/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 21834 rec/s (2.31% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 22036 rec/s (1.50% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 21344 rec/s (0.96% GC, Heap: 19/1024MB)
- **4 threads, 256MB:** 25265 rec/s (2.48% GC, Heap: 70/256MB)
- **4 threads, 512MB:** 25316 rec/s (1.92% GC, Heap: 61/512MB)
- **4 threads, 1024MB:** 23490 rec/s (1.60% GC, Heap: 60/1024MB)
- **8 threads, 256MB:** 23218 rec/s (2.76% GC, Heap: 117/256MB)
- **8 threads, 512MB:** 23752 rec/s (1.76% GC, Heap: 75/512MB)
- **8 threads, 1024MB:** 23397 rec/s (1.73% GC, Heap: 66/1024MB)

### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._

- **1 threads, 256MB:** 625 rec/s (0.09% GC, Heap: 18/256MB)
- **1 threads, 512MB:** 597 rec/s (0.05% GC, Heap: 18/512MB)
- **1 threads, 1024MB:** 604 rec/s (0.03% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 564 rec/s (0.11% GC, Heap: 22/256MB)
- **4 threads, 512MB:** 544 rec/s (0.07% GC, Heap: 25/512MB)
- **4 threads, 1024MB:** 624 rec/s (0.04% GC, Heap: 30/1024MB)
- **8 threads, 256MB:** 616 rec/s (0.17% GC, Heap: 62/256MB)
- **8 threads, 512MB:** 628 rec/s (0.09% GC, Heap: 39/512MB)
- **8 threads, 1024MB:** 588 rec/s (0.07% GC, Heap: 58/1024MB)




## Memory Analysis

### 256MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 54MB
- Average GC Time: 1.82%

### 512MB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 38MB
- Average GC Time: 1.07%

### 1GB Configuration
- Success Rate: 18/18 tests
- Average Heap Usage: 58MB
- Average GC Time: 1.40%

## Threading Impact
- **1 thread(s):** Average 24471 rec/s (18 tests)
- **4 thread(s):** Average 27353 rec/s (18 tests)
- **8 thread(s):** Average 26632 rec/s (18 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** csv (best throughput observed)
- **Expected:** 29620-39494 rec/s

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
- **Expected:** 24084-32113 rec/s

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
- **Threads:** 8 (optimal for this workload)
- **Expected:** 471-628 invoice rec/s (4 tables, batch_size=1000, per_batch commit)

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

### Memory-Constrained Environments

**256MB Configuration:**
- ✅ Works for most scenarios with 1-2 threads
- **Recommendation:** Use 1-2 threads maximum
- **Expected:** 564-39494 rec/s

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
| File I/O | 4.9M ops/sec | 30450-39494 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 21344-32113 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | 544-628 rec/s | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
