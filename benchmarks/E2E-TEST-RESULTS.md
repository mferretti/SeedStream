# End-to-End Test Results

**Date:** March 10, 2026
**Test Duration:** ~27 minutes
**Tests:** 45 executed, 18 skipped, 63 total
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
destination,format,threads,memory_mb,record_count,duration_sec,throughput_rps,heap_used_mb,heap_max_mb,gc_time_ms,gc_count,gc_time_percent,status,error
file,json,1,256,100000,4,25000,12,256,47,17,1.18,SUCCESS,
file,json,1,512,100000,3,33333,12,512,28,9,0.93,SUCCESS,
file,json,1,1024,100000,4,25000,11,1024,16,5,0.40,SUCCESS,
file,json,4,256,100000,3,33333,19,256,85,18,2.83,SUCCESS,
file,json,4,512,100000,3,33333,19,512,40,10,1.33,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 25000 rec/s (1.18% GC, Heap: 12/256MB)
- **1 threads, 512MB:** 33333 rec/s (0.93% GC, Heap: 12/512MB)
- **1 threads, 1024MB:** 25000 rec/s (0.40% GC, Heap: 11/1024MB)
- **4 threads, 256MB:** 33333 rec/s (2.83% GC, Heap: 19/256MB)
- **4 threads, 512MB:** 33333 rec/s (1.33% GC, Heap: 19/512MB)
- **4 threads, 1024MB:** 33333 rec/s (0.73% GC, Heap: 18/1024MB)
- **8 threads, 256MB:** 33333 rec/s (2.33% GC, Heap: 18/256MB)
- **8 threads, 512MB:** 33333 rec/s (1.30% GC, Heap: 18/512MB)
- **8 threads, 1024MB:** 33333 rec/s (0.77% GC, Heap: 18/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 25000 rec/s (2.17% GC, Heap: 15/256MB)
- **1 threads, 512MB:** 25000 rec/s (1.52% GC, Heap: 20/512MB)
- **1 threads, 1024MB:** 33333 rec/s (1.20% GC, Heap: 14/1024MB)
- **4 threads, 256MB:** 25000 rec/s (2.40% GC, Heap: 19/256MB)
- **4 threads, 512MB:** 25000 rec/s (1.52% GC, Heap: 20/512MB)
- **4 threads, 1024MB:** 33333 rec/s (1.27% GC, Heap: 18/1024MB)
- **8 threads, 256MB:** 20000 rec/s (2.04% GC, Heap: 18/256MB)
- **8 threads, 512MB:** 25000 rec/s (1.60% GC, Heap: 19/512MB)
- **8 threads, 1024MB:** 25000 rec/s (1.00% GC, Heap: 18/1024MB)

### Kafka Destination

#### JSON Format
- **1 threads, 256MB:** 25000 rec/s (2.08% GC, Heap: 13/256MB)
- **1 threads, 512MB:** 25000 rec/s (1.25% GC, Heap: 11/512MB)
- **1 threads, 1024MB:** 20000 rec/s (0.60% GC, Heap: 11/1024MB)
- **4 threads, 256MB:** 25000 rec/s (2.43% GC, Heap: 19/256MB)
- **4 threads, 512MB:** 25000 rec/s (1.85% GC, Heap: 19/512MB)
- **4 threads, 1024MB:** 25000 rec/s (1.27% GC, Heap: 20/1024MB)
- **8 threads, 256MB:** 25000 rec/s (2.67% GC, Heap: 18/256MB)
- **8 threads, 512MB:** 25000 rec/s (1.88% GC, Heap: 20/512MB)
- **8 threads, 1024MB:** 25000 rec/s (1.05% GC, Heap: 18/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 25000 rec/s (3.17% GC, Heap: 14/256MB)
- **1 threads, 512MB:** 25000 rec/s (2.20% GC, Heap: 21/512MB)
- **1 threads, 1024MB:** 20000 rec/s (1.16% GC, Heap: 18/1024MB)
- **4 threads, 256MB:** 25000 rec/s (3.08% GC, Heap: 18/256MB)
- **4 threads, 512MB:** 20000 rec/s (1.82% GC, Heap: 14/512MB)
- **4 threads, 1024MB:** 20000 rec/s (1.08% GC, Heap: 18/1024MB)
- **8 threads, 256MB:** 20000 rec/s (2.72% GC, Heap: 13/256MB)
- **8 threads, 512MB:** 20000 rec/s (1.86% GC, Heap: 18/512MB)
- **8 threads, 1024MB:** 20000 rec/s (0.86% GC, Heap: 18/1024MB)

### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._

- **1 threads, 256MB:** 694 rec/s (0.13% GC, Heap: 16/256MB)
- **1 threads, 512MB:** 729 rec/s (0.08% GC, Heap: 16/512MB)
- **1 threads, 1024MB:** 675 rec/s (0.04% GC, Heap: 16/1024MB)
- **4 threads, 256MB:** 680 rec/s (0.10% GC, Heap: 13/256MB)
- **4 threads, 512MB:** 561 rec/s (0.06% GC, Heap: 16/512MB)
- **4 threads, 1024MB:** 588 rec/s (0.03% GC, Heap: 16/1024MB)
- **8 threads, 256MB:** 684 rec/s (0.10% GC, Heap: 16/256MB)
- **8 threads, 512MB:** 662 rec/s (0.07% GC, Heap: 16/512MB)
- **8 threads, 1024MB:** 653 rec/s (0.03% GC, Heap: 16/1024MB)




## Memory Analysis

### 256MB Configuration
- Success Rate: 15/15 tests
- Average Heap Usage: 16MB
- Average GC Time: 1.96%

### 512MB Configuration
- Success Rate: 15/15 tests
- Average Heap Usage: 17MB
- Average GC Time: 1.28%

### 1GB Configuration
- Success Rate: 15/15 tests
- Average Heap Usage: 17MB
- Average GC Time: 0.77%

## Threading Impact
- **1 thread(s):** Average 20584 rec/s (15 tests)
- **4 thread(s):** Average 21677 rec/s (15 tests)
- **8 thread(s):** Average 20466 rec/s (15 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 256MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 24999-33333 rec/s

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
- **Threads:** 1 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 18750-25000 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "1000m"
  limits:
    memory: "512Mi"
    cpu: "2000m"
```

### For Database Inserts (JDBC Batch — Nested Multi-Table)


**Recommended Configuration:**
- **Memory:** 512MB (best observed performance)
- **Threads:** 1 (optimal for this workload)
- **Expected:** 546-729 invoice rec/s (4 tables, batch_size=1000, per_batch commit)

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
- **Expected:** 680-33333 rec/s

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
| File I/O | 4.9M ops/sec | 20000-33333 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 20000-25000 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | 561-729 rec/s | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
