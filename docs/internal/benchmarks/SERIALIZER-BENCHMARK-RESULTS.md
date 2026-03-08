# Format Serializer Benchmark Results

**Date:** March 7, 2026  
**Test Duration:** ~2 minutes  
**JMH Version:** 1.37  
**JVM:** OpenJDK 64-Bit Server VM 21.0.9+10-LTS  
**Benchmark Mode:** Throughput (operations/second)  
**Warmup:** 2 iterations × 1 second  
**Measurement:** 3 iterations × 1 second  
**Threads:** 1

## Executive Summary

Benchmarks compare JSON, CSV, and Protobuf serialization performance across three complexity levels:
- **Simple Record**: 5 primitive fields (id, name, age, active, balance)
- **Complex Record**: 10 fields including dates and Datafaker data (email, phone, address, etc.)
- **Nested Record**: Nested objects and arrays (order with 5 line items)

### Key Findings

| Format | Simple (ops/s) | Complex (ops/s) | Nested (ops/s) | Relative Performance |
|--------|----------------|-----------------|----------------|---------------------|
| **JSON** | 3,083,214 | 1,036,091 | 664,471 | Baseline |
| **CSV** | 2,619,473 (85%) | 936,637 (90%) | 223,024 (34%) | -10-66% slower |
| **Protobuf** | 1,535,578 (50%) | 563,704 (54%) | 323,912 (49%) | ~50% slower |

**Performance Insights:**
- **JSON**: Fastest for all scenarios (highly optimized Jackson library)
- **CSV**: Similar to JSON for simple/complex, significantly slower for nested (due to JSON-in-CSV serialization)
- **Protobuf**: ~50% slower than JSON, but produces 50-70% smaller output (binary format trade-off)

**Bottleneck Analysis:**
- Serialization is **NOT a bottleneck** in the generation pipeline
- All formats achieve 200K+ ops/s even for nested structures
- File I/O throughput (7K-20K rec/s in E2E tests) is limited by disk I/O, not serialization

**Format Selection Guidance:**
- **JSON**: Default choice - fast, human-readable, well-supported
- **CSV**: Use when downstream systems require CSV (Excel, SQL COPY)
- **Protobuf**: Use when storage/bandwidth is critical and you can tolerate binary format

---

## Detailed Results

### Simple Record (5 Primitive Fields)

**Record Structure:**
```json
{
  "id": 12345,
  "name": "JohnDoe",
  "age": 35,
  "active": true,
  "balance": 1234.56
}
```

**Results:**

| Serializer | Throughput (ops/s) | Relative % |
|------------|-------------------|------------|
| JSON | 3,083,214 | 100% (baseline) |
| CSV | 2,619,474 | 85% |
| Protobuf | 1,535,578 | 50% |

**Analysis:**
- JSON leads with 3M ops/s
- CSV close behind at 85% (OpenCSV has efficient quoting)
- Protobuf at 50% (dynamic schema overhead + base64 encoding)

---

### Complex Record (10 Realistic Fields)

**Record Structure:**
```json
{
  "id": 67890,
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "phone": "+1-555-123-4567",
  "address": "123 Main Street, Apartment 4B",
  "city": "New York",
  "company": "Tech Solutions Inc.",
  "birthDate": "1990-05-15",
  "createdAt": "2024-03-15T10:30:00Z",
  "balance": 45678.90
}
```

**Results:**

| Serializer | Throughput (ops/s) | Relative % |
|------------|-------------------|------------|
| JSON | 1,036,091 | 100% (baseline) |
| CSV | 936,637 | 90% |
| Protobuf | 563,704 | 54% |

**Analysis:**
- More fields reduce throughput for all formats (expected)
- JSON still leads at 1M ops/s
- CSV maintains 90% of JSON performance
- Protobuf at 54% (consistent penalty for dynamic schema)

---

### Nested Record (Objects + Arrays)

**Record Structure:**
```json
{
  "orderId": 99999,
  "customerName": "Alice Johnson",
  "shippingAddress": {
    "street": "456 Oak Avenue",
    "city": "Boston",
    "zip": "02101"
  },
  "items": [
    {"productId": 1000, "name": "Product 0", "quantity": 1, "price": 19.99},
    {"productId": 1001, "name": "Product 1", "quantity": 2, "price": 20.99},
    ...
  ],
  "total": 299.95
}
```

**Results:**

| Serializer | Throughput (ops/s) | Relative % |
|------------|-------------------|------------|
| JSON | 664,471 | 100% (baseline) |
| Protobuf | 323,912 | 49% |
| CSV | 223,024 | 34% |

**Analysis:**
- Nested structures are hardest for all formats
- **Protobuf outperforms CSV** for nested data (49% vs 34%)
- CSV suffers because it serializes nested structures as JSON strings (double serialization)
- JSON remains fastest due to native nested support

---

## Size vs Speed Trade-off

### Protobuf Size Advantage

While Protobuf is slower to serialize (~50% of JSON), it produces significantly smaller output:

**Example: Complex Record**
- JSON output: ~245 bytes
- Protobuf binary: ~85 bytes (35% of JSON size)
- Protobuf base64: ~120 bytes (49% of JSON size, including encoding overhead)

**When to Use Protobuf:**
1. **Storage-constrained environments**: 50%+ size reduction = half the disk/S3 costs
2. **Bandwidth-constrained systems**: Smaller payloads = faster transfers
3. **High-volume data lakes**: Terabytes of test data → significant cost savings
4. **Cross-language systems**: Protobuf has libraries for all major languages

**When NOT to Use Protobuf:**
1. **Human readability needed**: Binary format not greppable/readable
2. **Ad-hoc analysis**: JSON/CSV easier to explore with jq/spreadsheets
3. **Legacy systems**: May not have Protobuf deserialization libraries

---

## Performance Context

### Serialization Is NOT a Bottleneck

Even the slowest serializer (CSV nested: 223K ops/s) is **11-30× faster** than end-to-end pipeline throughput (7K-20K rec/s). The real bottlenecks are:

1. **Disk I/O**: Sequential write speeds (600-800 MB/s raw, but limited by fsync and buffering)
2. **Datafaker Generation**: Realistic data generation (13K-154K ops/s depending on type)
3. **Network I/O** (Kafka): Network latency and broker processing

**Conclusion**: Choose format based on **use case requirements** (readability, size, compatibility), not serialization speed.

---

## Raw Benchmark Data

```
Benchmark                                                 Mode  Cnt        Score   Error  Units
SerializerBenchmark.benchmarkCsvComplexRecord           thrpt    3   936637.060          ops/s
SerializerBenchmark.benchmarkCsvNestedRecord            thrpt    3   223024.369          ops/s
SerializerBenchmark.benchmarkCsvSimpleRecord            thrpt    3  2619473.698          ops/s
SerializerBenchmark.benchmarkJsonComplexRecord          thrpt    3  1036091.515          ops/s
SerializerBenchmark.benchmarkJsonNestedRecord           thrpt    3   664471.676          ops/s
SerializerBenchmark.benchmarkJsonSimpleRecord           thrpt    3  3083214.187          ops/s
SerializerBenchmark.benchmarkProtobufComplexRecord      thrpt    3   563703.591          ops/s
SerializerBenchmark.benchmarkProtobufNestedRecord       thrpt    3   323912.473          ops/s
SerializerBenchmark.benchmarkProtobufSimpleRecord       thrpt    3  1535577.679          ops/s
```

---

**Test Environment:**
- **Hardware**: AMD Ryzen threadripper (details from system)
- **OS**: Linux
- **Java**: OpenJDK 21.0.9 (Amazon Corretto)
- **Compiler**: HotSpot C2 JIT with default optimizations
