# Performance Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for measuring and validating the performance of SeedStream's data generation pipeline.

## Overview

The benchmarks validate the NFR-1 requirement of 10M records/second for in-memory primitive generation and measure performance across all critical paths:

- **Primitive Generators**: char, int, decimal, boolean, date, timestamp
- **Datafaker Generators**: Realistic data (names, emails, addresses, etc.)
- **Composite Generators**: Objects and arrays
- **Serializers**: JSON and CSV formatting
- **Destinations**: File I/O throughput

**Note**: Benchmarks are excluded from the regular test suite (`./gradlew test`) because they are time-consuming (~10-15 minutes). They must be run explicitly using the commands below.

## Running Benchmarks

### Quick Start (One Command)

Run benchmarks and generate report in one step:

```bash
./benchmarks/run_benchmarks.sh
```

This script will:
1. Run all JMH benchmarks (~10-15 minutes)
2. Generate formatted `BENCHMARK-RESULTS.md` report
3. Display completion summary

### Manual Steps

#### Run All Benchmarks

```bash
./gradlew :benchmarks:jmh
```

This runs all benchmarks with default configuration:
- 2 warmup iterations (1 second each)
- 3 measurement iterations (1 second each)
- 1 fork
- Single thread

Results are saved to: `benchmarks/build/reports/jmh/results.json`

### Run Specific Benchmark

```bash
# Run only primitive generators
./gradlew :benchmarks:jmh -Pjmh.includes=".*PrimitiveGenerators.*"

# Run only Datafaker generators
./gradlew :benchmarks:jmh -Pjmh.includes=".*DatafakerGenerators.*"

# Run only a specific method
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkIntegerGenerator"
```

### Custom Configuration

Edit `benchmarks/build.gradle.kts` to adjust JMH parameters:

```kotlin
jmh {
    warmupIterations.set(5)      // Increase warmup
    iterations.set(10)            // More measurement iterations
    fork.set(3)                   // More forks for stability
    threads.set(4)                // Multi-threaded benchmarks
}
```

## Processing Results

### Automated Report Generation (Recommended)

Use the provided `format_results.py` script to generate a formatted markdown report:

```bash
# Run benchmarks
./gradlew :benchmarks:jmh

# Generate formatted report
python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md
```

The script:
- Reads `benchmarks/build/reports/jmh/results.json`
- Groups results by category (Primitives, Datafaker, Composites, Serializers, Destinations)
- Validates NFR-1 compliance (10M ops/s for primitives)
- Calculates summary statistics
- Outputs formatted markdown to stdout

**One-liner to run benchmarks and generate report:**
```bash
./gradlew :benchmarks:jmh && python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md
```

### Manual Analysis

1. **Locate results**: `benchmarks/build/reports/jmh/results.json`

2. **Extract key metrics** from each benchmark entry:
   ```json
   {
     "benchmark": "com.datagenerator.benchmarks.PrimitiveGeneratorsBenchmark.benchmarkIntegerGenerator",
     "primaryMetric": {
       "score": 56807284.0,
       "scoreError": 2674722.0,
       "scoreUnit": "ops/s"
     }
   }
   ```

3. **Group by category**: Primitives, Datafaker, Composites, Serializers, Destinations

4. **Compare against targets**:
   - Primitives: Must exceed 10M ops/s (NFR-1 requirement)
   - Datafaker: Expected ~10-50K ops/s (realistic data overhead)
   - File I/O: Should enable 100+ MB/s throughput

## Benchmark Descriptions

### PrimitiveGeneratorsBenchmark

Tests raw generation speed for primitive types:
- `benchmarkCharGenerator`: Random strings [3..15] chars
- `benchmarkIntegerGenerator`: Random integers [1..999]
- `benchmarkDecimalGenerator`: Random BigDecimal [0.0..100.0]
- `benchmarkBooleanGenerator`: Random boolean (50/50)
- `benchmarkDateGenerator`: Random LocalDate [2020-01-01..2025-12-31]
- `benchmarkTimestampGenerator`: Random Instant [now-30d..now]

**Target**: 10M ops/s minimum for NFR-1 compliance

### DatafakerGeneratorsBenchmark

Tests realistic data generation via Datafaker library:
- `benchmarkNameGeneration`: Full names
- `benchmarkEmailGeneration`: Email addresses
- `benchmarkPhoneGeneration`: Phone numbers
- `benchmarkAddressGeneration`: Street addresses
- `benchmarkCityGeneration`: City names
- `benchmarkCompanyGeneration`: Company names

**Expected**: ~10K-50K ops/s (significantly slower than primitives due to realistic data generation)

### CompositeGeneratorsBenchmark

Tests nested structure generation:
- `benchmarkSmallArray`: Arrays of 5-10 integers
- `benchmarkLargeArray`: Arrays of 50-100 integers
- `benchmarkSimpleObject`: Objects with 3 fields (id, name, active)

**Expected**: Lower than primitives due to recursive generation overhead

### SerializerBenchmark

Tests formatting performance:
- `benchmarkJsonSimpleRecord`: Simple flat record (5 fields)
- `benchmarkJsonComplexRecord`: Complex record (10 fields, dates)
- `benchmarkJsonNestedRecord`: Nested objects and arrays
- `benchmarkCsvSimpleRecord`: CSV with simple fields
- `benchmarkCsvComplexRecord`: CSV with complex fields
- `benchmarkCsvNestedRecord`: CSV with nested data (JSON strings)

**Goal**: Identify serialization bottlenecks in file I/O pipeline

### DestinationBenchmark

Tests I/O throughput:
- `benchmarkRawFileWrite`: Pure write throughput (baseline)
- `benchmarkFileDestinationWrite`: End-to-end serialize + write

**Goal**: Compare raw I/O vs. full pipeline overhead

## Interpreting Results

### NFR-1 Compliance Check

```
✓ Fastest primitive generator: 258,483,682 ops/s
  ✓ PASSED NFR-1 requirement (10M ops/s)
```

If any primitive generator shows < 10M ops/s, investigate:
- Hardware limitations (CPU speed, memory bandwidth)
- JVM warm-up issues (increase warmup iterations)
- Resource contention (close other applications)

### Datafaker Performance

Expected range: 10K-50K ops/s

If significantly lower:
- Locale loading overhead (first run is slower)
- Consider caching Faker instances (currently recreated per call)

### File I/O Analysis

Compare `benchmarkRawFileWrite` vs `benchmarkFileDestinationWrite`:
- Large gap → Serialization bottleneck
- Small gap → I/O bound (disk throughput limit)

Target: Enable 100+ MB/s file writes for production use

## CI/CD Integration

Add benchmark step to GitHub Actions (optional, long-running):

```yaml
- name: Run Performance Benchmarks
  run: ./gradlew :benchmarks:jmh
  
- name: Upload Benchmark Results
  uses: actions/upload-artifact@v3
  with:
    name: jmh-results
    path: benchmarks/build/reports/jmh/results.json
```

**Note**: Benchmarks take ~10-15 minutes. Consider running only on release branches or manually triggered.

## Troubleshooting

### Issue: "OutOfMemoryError" during benchmarks

**Solution**: Increase JMH JVM heap size in `build.gradle.kts`:

```kotlin
jmh {
    jvmArgs.set(listOf("-Xmx4g", "-Xms2g"))
}
```

### Issue: Results vary significantly between runs

**Solution**: Increase forks and iterations for stability:

```kotlin
jmh {
    fork.set(3)
    iterations.set(10)
}
```

### Issue: Benchmarks too slow

**Solution**: Reduce iterations or run specific benchmarks:

```bash
./gradlew :benchmarks:jmh -Pjmh.includes=".*Primitive.*"
```

## Performance Baseline (March 2026)

Reference results on development hardware:

| Category | Best Performance | Notes |
|----------|-----------------|-------|
| Primitives | 258M ops/s | BooleanGenerator, exceeds NFR-1 by 25× |
| Datafaker | 55K ops/s | CompanyGenerator, realistic data |
| Composites | 5.8M ops/s | Small array generation |
| JSON Serialization | 2.6M ops/s | Simple records |
| File I/O | 4.7M ops/s | Raw writes (baseline) |

**Hardware**: Development machine (specific configuration may vary)
**JVM**: OpenJDK 21.0.9, Amazon Corretto
