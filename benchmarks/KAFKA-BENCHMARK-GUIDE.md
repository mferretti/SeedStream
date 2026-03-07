# Kafka Producer Benchmark - Quick Start Guide

This guide shows you how to run the Kafka producer benchmarks to measure throughput under various configurations.

## TL;DR - Fastest Way to Run

```bash
# Automated script (starts Kafka in Docker if needed)
./benchmarks/run_kafka_benchmark.sh
```

That's it! The script will:
1. Check if Kafka is running
2. Start Kafka in Docker if needed
3. Create the benchmark topic
4. Run all benchmarks (~15-20 minutes)
5. Show you where results are saved

## Prerequisites

### Option 1: Docker (Recommended)

Just have Docker installed. The script will handle everything else.

```bash
docker --version  # Verify Docker is installed
```

### Option 2: Manual Kafka Setup

If you prefer to run Kafka manually:

```bash
# Start Kafka (KRaft mode, no ZooKeeper needed)
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

# Wait for Kafka to be ready
sleep 15

# Create benchmark topic (3 partitions for parallelism)
docker exec kafka-benchmark kafka-topics.sh --create \
  --topic benchmark-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

## Running the Benchmarks

### Method 1: Automated Script (Easiest)

```bash
./benchmarks/run_kafka_benchmark.sh
```

**Customization:**

```bash
# Use custom Kafka address
KAFKA_BOOTSTRAP=broker1:9092 ./benchmarks/run_kafka_benchmark.sh

# Use custom topic name
KAFKA_TOPIC=my-benchmark-topic ./benchmarks/run_kafka_benchmark.sh
```

### Method 2: Manual Gradle Command

```bash
# Run all Kafka benchmarks
./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*"

# Run only async producer tests
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkAsyncProducer.*"

# Run only sync producer tests
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkSyncProducer.*"

# Custom Kafka settings
./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*" \
  -Dkafka.bootstrap=broker1:9092 \
  -Dkafka.topic=my-topic
```

### Method 3: Run WITHOUT Kafka Benchmarks

If you want to run other benchmarks but skip Kafka (when Kafka is not available):

```bash
./gradlew :benchmarks:jmh -Pjmh.excludes=".*KafkaBenchmark.*"
```

## What Gets Tested

The benchmark runs **24 different configurations** combining:

### Parameters

- **Sync mode**: 
  - `false` - Async fire-and-forget (fastest)
  - `true` - Wait for broker acknowledgment (slower, more durable)

- **Compression**:
  - `none` - No compression (fastest)
  - `gzip` - Good compression ratio, CPU intensive
  - `snappy` - Balanced compression/speed
  - `lz4` - Fast compression, good for high throughput

- **Batch size**:
  - `1024` bytes (1KB) - Low latency, lower throughput
  - `16384` bytes (16KB) - Default, balanced
  - `65536` bytes (64KB) - Higher throughput, higher latency

### Test Record

Each benchmark sends realistic customer records (~300 bytes JSON):

```json
{
  "customer_id": "uuid",
  "first_name": "string",
  "last_name": "string",
  "email": "string",
  "phone": "string",
  "billing_address": "string",
  "city": "string",
  "state": "string",
  "postal_code": "string",
  "country": "string",
  "birthDate": "date",
  "createdAt": "timestamp",
  "accountBalance": "number",
  "accountStatus": "string",
  "loyaltyPoints": "number"
}
```

## Expected Results

### Typical Throughput (records per second)

| Configuration | Expected Throughput |
|--------------|---------------------|
| Async + no compression | **50,000+** |
| Async + gzip | **30,000+** |
| Async + snappy | **40,000+** |
| Async + lz4 | **45,000+** |
| Sync + no compression | **5,000-10,000** |
| Sync + gzip | **3,000-5,000** |

**Note**: Results vary based on:
- Network latency to Kafka brokers
- Kafka cluster performance (disk I/O, CPU)
- Number of topic partitions
- Replication factor
- Hardware (CPU, memory, network)

### Understanding Results

**High Throughput = Good**
- More records per second = better performance
- Look for configurations that fit your use case

**Async vs Sync Trade-offs**
- **Async**: 5-10x faster, risk of data loss if producer crashes
- **Sync**: Slower, but guaranteed durability (records confirmed on disk)

**Compression Trade-offs**
- **None**: Fastest, uses more network bandwidth
- **Gzip**: Best compression ratio, highest CPU usage
- **Snappy/LZ4**: Balanced, good for most use cases

**Batch Size Trade-offs**
- **Small (1KB)**: Low latency, lower throughput
- **Large (64KB)**: Higher throughput, higher latency
- **Default (16KB)**: Usually optimal for balanced workloads

## Viewing Results

Results are saved in JSON format:

```bash
# Location
benchmarks/build/reports/jmh/results.json

# View raw results
cat benchmarks/build/reports/jmh/results.json

# Format results (if you have format_results.py)
python3 benchmarks/format_results.py > KAFKA-BENCHMARK-RESULTS.md
```

### Example Output

```
Benchmark                                   (sync)  (compression)  (batchSize)   Score    Error  Units
KafkaBenchmark.benchmarkKafkaProducer       false         none         16384  52341.2 ± 1234.5  ops/s
KafkaBenchmark.benchmarkKafkaProducer       false         gzip         16384  31245.6 ±  892.3  ops/s
KafkaBenchmark.benchmarkKafkaProducer        true         none         16384   8756.3 ±  421.2  ops/s
```

**Reading the Results:**
- **Score**: Average throughput (operations/records per second)
- **Error**: Margin of error (95% confidence interval)
- **Units**: ops/s = operations per second = records per second

## Cleanup

### Stop and Remove Kafka Container

```bash
docker stop kafka-benchmark && docker rm kafka-benchmark
```

### Delete Benchmark Data

```bash
# Remove benchmark results
rm -rf benchmarks/build/reports/jmh/

# Remove Kafka data (if you need to start fresh)
docker volume prune  # Careful: removes all unused volumes
```

## Troubleshooting

### "Cannot connect to Kafka"

**Check Kafka is running:**
```bash
docker ps | grep kafka-benchmark

# Should show a running container
```

**Check Kafka logs:**
```bash
docker logs kafka-benchmark
```

**Test connectivity:**
```bash
nc -zv localhost 9092
# Should show: Connection to localhost 9092 port [tcp/*] succeeded!
```

### "Topic not found"

**List topics:**
```bash
docker exec kafka-benchmark kafka-topics.sh --list --bootstrap-server localhost:9092
```

**Recreate topic:**
```bash
docker exec kafka-benchmark kafka-topics.sh --create \
  --topic benchmark-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

### "Benchmarks taking too long"

Benchmarks run 24 configurations with:
- 2 warmup iterations (2 seconds each)
- 5 measurement iterations (5 seconds each)

Total time: ~15-20 minutes for all configurations.

**To speed up (less accurate):**
Edit `benchmarks/src/jmh/java/com/datagenerator/benchmarks/KafkaBenchmark.java`:
```java
@Warmup(iterations = 1, time = 1)  // Reduce warmup
@Measurement(iterations = 2, time = 2)  // Fewer measurements
```

Then rebuild:
```bash
./gradlew :benchmarks:compileJmhJava
```

### "OutOfMemoryError"

Increase JVM heap size:
```bash
export GRADLE_OPTS="-Xmx4g"
./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*"
```

## Advanced Usage

### Run Specific Configuration Only

Avoid running all 24 combinations by filtering:

```bash
# Only test async mode
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkAsyncProducer.*"

# Only test no compression
# (This requires editing the @Param annotation in KafkaBenchmark.java)
```

### Custom Kafka Configuration

Override via system properties:

```bash
./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*" \
  -Dkafka.bootstrap=production-broker1:9092,production-broker2:9092 \
  -Dkafka.topic=production-benchmark
```

### Profile with JFR (Java Flight Recorder)

```bash
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkAsyncProducer.*" \
  -Pjmh.jvmArgs="-XX:StartFlightRecording=filename=kafka-benchmark.jfr,duration=60s"
```

Analyze with JDK Mission Control:
```bash
jmc kafka-benchmark.jfr
```

## Next Steps

- Analyze which configuration works best for your use case
- Test with your actual data structure (modify test record in benchmark)
- Run benchmarks on production-like hardware
- Compare results with different topic partition counts
- Experiment with Kafka broker settings (log.segment.bytes, etc.)

## Questions?

See the main benchmark documentation:
- `benchmarks/README.md` - Full benchmark suite documentation
- `benchmarks/src/jmh/java/com/datagenerator/benchmarks/KafkaBenchmark.java` - Source code with detailed comments
