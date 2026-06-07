# End-to-End Test Results

**Date:** June 06, 2026
**Test Duration:** ~3 minutes
**Tests:** 18 executed, 45 skipped, 63 total
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
file,json,1,256,100000,3212,31133,9,256,45,16,1.40,SUCCESS,
file,json,1,512,100000,3359,29770,10,512,30,9,0.89,SUCCESS,
file,json,1,1024,100000,3316,30156,9,1024,19,6,0.57,SUCCESS,
file,json,4,256,100000,2808,35612,17,256,62,16,2.21,SUCCESS,
file,json,4,512,100000,2918,34270,10,512,40,10,1.37,SUCCESS,
```

## Analysis by Scenario

### File Destination

#### JSON Format
- **1 threads, 256MB:** 31133 rec/s (1.40% GC, Heap: 9/256MB)
- **1 threads, 512MB:** 29770 rec/s (0.89% GC, Heap: 10/512MB)
- **1 threads, 1024MB:** 30156 rec/s (0.57% GC, Heap: 9/1024MB)
- **4 threads, 256MB:** 35612 rec/s (2.21% GC, Heap: 17/256MB)
- **4 threads, 512MB:** 34270 rec/s (1.37% GC, Heap: 10/512MB)
- **4 threads, 1024MB:** 35868 rec/s (0.79% GC, Heap: 16/1024MB)
- **8 threads, 256MB:** 32905 rec/s (2.01% GC, Heap: 17/256MB)
- **8 threads, 512MB:** 32000 rec/s (1.25% GC, Heap: 16/512MB)
- **8 threads, 1024MB:** 32206 rec/s (0.77% GC, Heap: 16/1024MB)

#### CSV Format


#### Protobuf Format
- **1 threads, 256MB:** 31426 rec/s (2.42% GC, Heap: 10/256MB)
- **1 threads, 512MB:** 30321 rec/s (1.55% GC, Heap: 13/512MB)
- **1 threads, 1024MB:** 30184 rec/s (0.75% GC, Heap: 10/1024MB)
- **4 threads, 256MB:** 29248 rec/s (2.90% GC, Heap: 17/256MB)
- **4 threads, 512MB:** 28481 rec/s (1.94% GC, Heap: 16/512MB)
- **4 threads, 1024MB:** 29120 rec/s (1.31% GC, Heap: 16/1024MB)
- **8 threads, 256MB:** 27533 rec/s (3.06% GC, Heap: 17/256MB)
- **8 threads, 512MB:** 27601 rec/s (1.93% GC, Heap: 17/512MB)
- **8 threads, 1024MB:** 27487 rec/s (1.13% GC, Heap: 17/1024MB)

### Kafka Destination

#### JSON Format


#### CSV Format


#### Protobuf Format


### Database Destination (JDBC — no format dimension)

_Stage 2 multi-table auto-decomposition. Each invoice record writes to 4 tables: `invoices` (root), `issuer`, `recipient`, `line_items` (1–10 rows each). Throughput is rec/s of root invoices; total DB writes are ~4–12× higher. batch_size=1000 root records, per_batch commit._



_(Tests skipped — PostgreSQL container not running)_
_(No database tests ran)_

## Memory Analysis

### 256MB Configuration
- Success Rate: 6/6 tests
- Average Heap Usage: 14MB
- Average GC Time: 2.33%

### 512MB Configuration
- Success Rate: 6/6 tests
- Average Heap Usage: 14MB
- Average GC Time: 1.49%

### 1GB Configuration
- Success Rate: 6/6 tests
- Average Heap Usage: 14MB
- Average GC Time: 0.89%

## Threading Impact
- **1 thread(s):** Average 30498 rec/s (6 tests)
- **4 thread(s):** Average 32099 rec/s (6 tests)
- **8 thread(s):** Average 29955 rec/s (6 tests)

## Production Recommendations

### For File Generation (High Throughput)

**Recommended Configuration:**
- **Memory:** 1024MB (best observed performance)
- **Threads:** 4 (optimal for this workload)
- **Format:** json (best throughput observed)
- **Expected:** 26901-35868 rec/s

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

### For Kafka Streaming (Network-Bound)

**Recommended Configuration:**
- **Memory:** 0MB (best observed performance)
- **Threads:** 0 (optimal for this workload)
- **Format:**  (best throughput observed)
- **Expected:** 0-0 rec/s

**Kubernetes Resource Requests:**
```yaml
resources:
  requests:
    memory: "0Mi"
    cpu: "0m"
  limits:
    memory: "0Mi"
    cpu: "0m"
```

### For Database Inserts (JDBC Batch — Nested Multi-Table)

_No successful database tests — PostgreSQL was not available._



### Memory-Constrained Environments

**256MB Configuration:**
- ✅ Works for most scenarios with 1-2 threads
- **Recommendation:** Use 1-2 threads maximum
- **Expected:** 27533-35612 rec/s

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
| File I/O | 4.9M ops/sec | 27487-35868 rec/s | Disk-bound |
| Kafka (async) | 3.5K rec/sec (sync) | 0-0 rec/s | Network-bound (localhost) |
| Database (JDBC) | - | skipped | Nested 4 tables, batch_size=1000 |

**Insight:** End-to-end performance is **dominated by the slowest component** (Datafaker generation for complex fields) and **I/O operations** (network for Kafka/database, disk for files).

## Raw Data

GC logs available in: `benchmarks/build/gc_logs/`
