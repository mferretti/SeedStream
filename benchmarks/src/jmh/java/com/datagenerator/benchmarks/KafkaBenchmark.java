/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.benchmarks;

import com.datagenerator.destinations.kafka.KafkaDestination;
import com.datagenerator.destinations.kafka.KafkaDestinationConfig;
import com.datagenerator.formats.json.JsonSerializer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for Kafka producer throughput.
 *
 * <p><b>Goal:</b> Measure records/second throughput to Kafka under various configurations
 *
 * <p><b>Scenarios:</b>
 *
 * <ul>
 *   <li>Async vs Sync send modes
 *   <li>Different compression types (none, gzip, snappy, lz4, zstd)
 *   <li>Various batch sizes (1KB, 16KB, 64KB, 256KB)
 * </ul>
 *
 * <p><b>Prerequisites:</b>
 *
 * <p><b>Option 1: Local Kafka (Docker - Recommended)</b>
 *
 * <pre>
 * docker run -d --name kafka-benchmark \
 *   -p 9092:9092 \
 *   -e KAFKA_ENABLE_KRAFT=yes \
 *   -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
 *   -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
 *   -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
 *   -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
 *   -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
 *   -e KAFKA_BROKER_ID=1 \
 *   -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
 *   -e ALLOW_PLAINTEXT_LISTENER=yes \
 *   bitnami/kafka:latest
 * </pre>
 *
 * <p><b>Option 2: Manual Kafka Installation</b>
 *
 * <pre>
 * # Download Kafka 3.8+
 * wget https://downloads.apache.org/kafka/3.8.1/kafka_2.13-3.8.1.tgz
 * tar -xzf kafka_2.13-3.8.1.tgz
 * cd kafka_2.13-3.8.1
 *
 * # Start Kafka in KRaft mode (no ZooKeeper needed)
 * KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
 * bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/kraft/server.properties
 * bin/kafka-server-start.sh config/kraft/server.properties
 * </pre>
 *
 * <p><b>Create Benchmark Topic:</b>
 *
 * <pre>
 * # Using Docker
 * docker exec kafka-benchmark kafka-topics.sh --create \
 *   --topic benchmark-topic \
 *   --bootstrap-server localhost:9092 \
 *   --partitions 3 \
 *   --replication-factor 1
 *
 * # Or manual installation
 * bin/kafka-topics.sh --create \
 *   --topic benchmark-topic \
 *   --bootstrap-server localhost:9092 \
 *   --partitions 3 \
 *   --replication-factor 1
 * </pre>
 *
 * <p><b>Run Benchmarks:</b>
 *
 * <pre>
 * # All Kafka benchmarks
 * ./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*"
 *
 * # Specific scenario (e.g., async only)
 * ./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkAsyncProducer.*"
 * </pre>
 *
 * <p><b>Skip Kafka Benchmarks:</b>
 *
 * <p>To run benchmarks WITHOUT Kafka tests (useful when Kafka is not available):
 *
 * <pre>
 * ./gradlew :benchmarks:jmh -Pjmh.excludes=".*KafkaBenchmark.*"
 * </pre>
 *
 * <p><b>Monitor Kafka Throughput:</b>
 *
 * <pre>
 * # Watch consumer lag (in another terminal)
 * docker exec -it kafka-benchmark kafka-consumer-perf-test.sh \
 *   --topic benchmark-topic \
 *   --bootstrap-server localhost:9092 \
 *   --messages 1000000 \
 *   --threads 1
 * </pre>
 *
 * <p><b>Cleanup:</b>
 *
 * <pre>
 * # Stop and remove Docker container
 * docker stop kafka-benchmark && docker rm kafka-benchmark
 * </pre>
 *
 * <p><b>Expected Results:</b>
 *
 * <ul>
 *   <li>Async + no compression: 50,000+ records/sec
 *   <li>Async + gzip compression: 30,000+ records/sec
 *   <li>Sync + no compression: 5,000-10,000 records/sec
 *   <li>Batch size impact: Larger batches improve throughput but increase latency
 * </ul>
 *
 * <p><b>Note:</b> Results depend heavily on:
 *
 * <ul>
 *   <li>Kafka cluster performance (disk I/O, network)
 *   <li>Number of partitions (parallelism)
 *   <li>Replication factor (durability overhead)
 *   <li>Network latency to Kafka brokers
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
@State(Scope.Benchmark)
public class KafkaBenchmark {

  // Parameterized configurations
  @Param({"false", "true"})
  private boolean sync;

  @Param({"none", "gzip", "snappy", "lz4"})
  private String compression;

  @Param({"1024", "16384", "65536"})
  private int batchSize;

  // Kafka destination and test data
  private KafkaDestination kafkaDestination;
  private JsonSerializer jsonSerializer;
  private Map<String, Object> testRecord;

  // Kafka connection settings (can be overridden via system properties)
  private static final String KAFKA_BOOTSTRAP =
      System.getProperty("kafka.bootstrap", "localhost:9092");
  private static final String KAFKA_TOPIC = System.getProperty("kafka.topic", "benchmark-topic");

  @Setup(Level.Trial)
  public void setup() {
    // Create realistic test record (passport data)
    testRecord = new LinkedHashMap<>();
    testRecord.put("number", "AB1234567");
    testRecord.put("first_name", "Alexandra");
    testRecord.put("last_name", "Martinez");
    testRecord.put("full_name", "Alexandra Maria Martinez");
    testRecord.put("dob", LocalDate.of(1985, 8, 20));
    testRecord.put("nationality", "United States");
    testRecord.put("place_of_birth", "San Francisco");
    testRecord.put("issue_date", LocalDate.of(2020, 3, 15));
    testRecord.put("expiry_date", LocalDate.of(2030, 3, 14));
    testRecord.put("authority", "U.S. Department of State");
    testRecord.put("sex", "F");

    // Initialize serializer
    jsonSerializer = new JsonSerializer();

    // Build configuration based on parameters
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(KAFKA_BOOTSTRAP)
            .topic(KAFKA_TOPIC)
            .sync(sync)
            .batchSize(batchSize)
            .compression(compression)
            .lingerMs(sync ? 0 : 10) // Immediate send for sync, batch for async
            .acks(sync ? "all" : "1") // Strong acks for sync, faster for async
            .build();

    // Create and open Kafka destination
    kafkaDestination = new KafkaDestination(config, jsonSerializer);
    kafkaDestination.open();

    System.out.printf(
        "[KAFKA BENCHMARK] Setup complete: sync=%s, compression=%s, batchSize=%d bytes%n",
        sync, compression, batchSize);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (kafkaDestination != null) {
      try {
        kafkaDestination.flush();
        kafkaDestination.close();
        System.out.printf(
            "[KAFKA BENCHMARK] Teardown complete: sync=%s, compression=%s, batchSize=%d%n",
            sync, compression, batchSize);
      } catch (Exception e) {
        System.err.println("[KAFKA BENCHMARK] Error during teardown: " + e.getMessage());
      }
    }
  }

  /**
   * Benchmark: Write single record to Kafka
   *
   * <p>Measures end-to-end throughput including:
   *
   * <ul>
   *   <li>JSON serialization
   *   <li>Kafka producer send
   *   <li>Network transmission
   *   <li>Broker acknowledgment (if sync=true)
   * </ul>
   */
  @Benchmark
  public void benchmarkKafkaProducer() {
    kafkaDestination.write(testRecord);
  }

  /**
   * Benchmark: Async producer (non-blocking send)
   *
   * <p>Fast fire-and-forget mode with batching. Best throughput but no immediate durability
   * guarantee.
   */
  @Benchmark
  public void benchmarkAsyncProducer() {
    // Force async mode for this benchmark (ignores @Param)
    if (!sync) {
      kafkaDestination.write(testRecord);
    } else {
      // Skip if sync=true to avoid duplication with benchmarkKafkaProducer
      throw new RuntimeException("Skip: This benchmark runs only in async mode");
    }
  }

  /**
   * Benchmark: Sync producer (blocking send with ack wait)
   *
   * <p>Waits for broker acknowledgment before returning. Lower throughput but guaranteed
   * durability.
   */
  @Benchmark
  public void benchmarkSyncProducer() {
    // Force sync mode for this benchmark (ignores @Param)
    if (sync) {
      kafkaDestination.write(testRecord);
    } else {
      // Skip if sync=false to avoid duplication with benchmarkKafkaProducer
      throw new RuntimeException("Skip: This benchmark runs only in sync mode");
    }
  }
}
