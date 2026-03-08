# Benchmark Suite - Operational Guide

This guide explains **how to run** SeedStream benchmarks to measure performance.

**For performance results, analysis, and tuning guidance**, see:
- **[../docs/PERFORMANCE-STATUS.md](../docs/PERFORMANCE-STATUS.md)** - Current performance status
- **[../docs/E2E-TEST-RESULTS.md](../docs/E2E-TEST-RESULTS.md)** - Full E2E test results
- **[../docs/BENCHMARK-RESULTS.md](../docs/BENCHMARK-RESULTS.md)** - JMH component results
- **[../docs/PERFORMANCE.md](../docs/PERFORMANCE.md)** - Performance guide and tuning

---

## Overview

This module contains two types of benchmarks:

1. **E2E Benchmarks** (bash scripts) - Measure complete pipeline with real CLI execution
2. **Component Benchmarks** (JMH) - Measure isolated components (generators, serializers)

**Note**: Benchmarks are excluded from regular test suite (`./gradlew test`) because they are time-consuming. Run explicitly using commands below.

---

## E2E Benchmarks (Recommended)

**Purpose:** Measure complete generation pipeline with realistic workloads

### Quick Start

Run full E2E benchmark suite (~15-20 minutes):

```bash
cd benchmarks && ./run_e2e_test.sh
```

```

JFR profiles saved to: `build/jfr/*.jfr`

**Analyzing profiles:** See [PROFILING.md](PROFILING.md) for details

**Results:** See [../docs/E2E-TEST-RESULTS.md](../docs/E2E-TEST-RESULTS.md)

---

## Component Benchmarks (JMH)

**Purpose:** Measure isolated component performance (generators, serializers)

### Run All Components

```bash
./gradlew :benchmarks:jmh
```

**Duration:** ~10-15 minutes  
**Output:** `build/reports/jmh/results.json`

### Run Specific Components

```bash
# Primitive generators only
./gradlew :benchmarks:jmh -Pjmh.includes=".*PrimitiveGenerators.*"

# Datafaker generators only
./gradlew :benchmarks:jmh -Pjmh.includes=".*DatafakerGenerators.*"

# Specific method
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkIntegerGenerator"
```

### Generate Report

```bash
# Run benchmarks and format results
./gradlew :benchmarks:jmh && python3 benchmarks/format_results.py > ../docs/BENCHMARK-RESULTS.md
```

**Results:** See [../docs/BENCHMARK-RESULTS.md](../docs/BENCHMARK-RESULTS.md)

---

## Kafka Benchmarks

**Prerequisites:** Kafka must be running on `localhost:9092`

### Quick Kafka Setup (Docker)

```bash
# Start Kafka container
docker run -d --name kafka-benchmark \
  -p 9092:9092 \
  -e KAFKA_ENABLE_KRAFT=yes \
  -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e ALLOW_PLAINTEXT_LISTENER=yes \
  bitnami/kafka:latest

# Wait for startup (~15 seconds)
sleep 15

# Create benchmark topic
docker exec kafka-benchmark kafka-topics.sh --create \
  --topic benchmark-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

### Run Kafka Benchmarks

**E2E Kafka tests:**
```bash
cd benchmarks && ./run_e2e_test.sh
# Kafka tests run automatically if Kafka is available
```

**Component Kafka benchmark:**
```bash
./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*"
```

**Cleanup:**
```bash
docker stop kafka-benchmark && docker rm kafka-benchmark
```

**Detailed Guide:** See [KAFKA-BENCHMARK-GUIDE.md](KAFKA-BENCHMARK-GUIDE.md)

---

## Configuration

### JMH Parameters

Edit `benchmarks/build.gradle.kts` to adjust JMH settings:

```kotlin
jmh {
    warmupIterations.set(2)      // Default: 2
    iterations.set(3)             // Default: 3
    fork.set(1)                   // Default: 1
    threads.set(1)                // Default: 1 (component benchmarks)
}
```

### E2E Test Parameters

Edit scripts or set environment variables:

```bash
# Override test configuration
RECORD_COUNT=50000 ./run_e2e_test.sh      # Default: 100000
THREADS="1 4" ./run_e2e_test.sh           # Default: 1 4 8
MEMORY_CONFIGS="512m" ./run_e2e_test.sh   # Default: 256m 512m 1024m
```

---

## File Descriptions

**Scripts:**
- `run_e2e_test.sh` - Run full E2E benchmark suite
- `run_benchmarks.sh` - Run JMH component benchmarks (legacy wrapper)
- `run_kafka_benchmark.sh` - Kafka-specific benchmarks (standalone)
- `format_results.py` - Generate markdown report from JMH JSON results
- `hardware_io_test.sh` - Test raw disk I/O baseline

**Operational Guides:**
- `README.md` - This file (how to run benchmarks)
- `PROFILING.md` - How to use JFR profiling
- `KAFKA-BENCHMARK-GUIDE.md` - Detailed Kafka setup and benchmarking

**Output:**
- `e2e_results.csv` - Latest E2E test results (CSV format)
- `build/jfr/*.jfr` - JFR profiling data (when --profile enabled)
- `build/reports/jmh/results.json` - JMH component results (JSON format)

**Analysis & Results:** See `../docs/` directory
- [PERFORMANCE-STATUS.md](../docs/PERFORMANCE-STATUS.md) - Current performance summary
- [E2E-TEST-RESULTS.md](../docs/E2E-TEST-RESULTS.md) - Full E2E results
- [BENCHMARK-RESULTS.md](../docs/BENCHMARK-RESULTS.md) - JMH component results
- [BASELINE-ANALYSIS.md](../docs/BASELINE-ANALYSIS.md) - Performance baseline analysis

---

## Troubleshooting

**JMH benchmarks fail to compile:**
```bash
./gradlew clean :benchmarks:clean
./gradlew :benchmarks:jmhClasses
```

**OutOfMemoryError during benchmarks:**
```bash
# Increase Gradle heap
export GRADLE_OPTS="-Xmx4g"
./gradlew :benchmarks:jmh
```

**Kafka connection errors:**
```bash
# Verify Kafka is running
docker ps | grep kafka-benchmark

# Check Kafka logs
docker logs kafka-benchmark

# Test connection
docker exec kafka-benchmark kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092
```

**E2E tests hang:**
- Check if destination directory has write permissions
- Verify sufficient disk space (>1GB recommended)
- Check system resources (CPU/memory not exhausted)

---

## Related Documentation

**Performance:**
- [../docs/PERFORMANCE-STATUS.md](../docs/PERFORMANCE-STATUS.md) - Current status & key metrics
- [../docs/PERFORMANCE.md](../docs/PERFORMANCE.md) - Performance guide & tuning
- [../docs/E2E-TEST-RESULTS.md](../docs/E2E-TEST-RESULTS.md) - Full E2E results
- [../docs/BENCHMARK-RESULTS.md](../docs/BENCHMARK-RESULTS.md) - Component results

**Operational:**
- [PROFILING.md](PROFILING.md) - JFR profiling guide
- [KAFKA-BENCHMARK-GUIDE.md](KAFKA-BENCHMARK-GUIDE.md) - Kafka benchmarking

**Analysis (Internal):**
- [../docs/internal/benchmarks/](../docs/internal/benchmarks/) - Historical analysis & methodology

---

**Questions?** See [../docs/CONTRIBUTING.md](../docs/CONTRIBUTING.md) or open an issue.

```

