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

package com.datagenerator.destinations.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.datagenerator.destinations.IntegrationTest;
import com.datagenerator.formats.json.JsonSerializer;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for KafkaDestination using Testcontainers.
 *
 * <p>These tests require Docker to be running.
 *
 * <p>Run with: ./gradlew :destinations:integrationTest
 */
class KafkaDestinationIT extends IntegrationTest {

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

  private KafkaDestination destination;
  private KafkaConsumer<String, String> consumer;

  @BeforeEach
  void setUp() {
    // Create consumer to verify messages
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    consumer = new KafkaConsumer<>(props);
  }

  @AfterEach
  void tearDown() {
    if (destination != null) {
      destination.close();
    }
    if (consumer != null) {
      consumer.close();
    }
  }

  @Test
  void shouldWriteRecordsToKafka() throws Exception {
    // Given: Kafka destination configured with real Kafka container
    String topic = "test-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .batchSize(1) // Flush immediately for testing
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    // Subscribe consumer to topic
    consumer.subscribe(Collections.singletonList(topic));

    // When: Write 3 records
    Map<String, Object> record1 = Map.of("id", 1, "name", "Alice");
    Map<String, Object> record2 = Map.of("id", 2, "name", "Bob");
    Map<String, Object> record3 = Map.of("id", 3, "name", "Charlie");

    destination.write(record1);
    destination.write(record2);
    destination.write(record3);
    destination.flush();

    // Then: Poll and verify records
    CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(record -> messages.add(record.value()));
              return messages.size() >= 3;
            });

    assertThat(messages).hasSize(3);
    assertThat(messages.get(0)).contains("\"id\":1", "\"name\":\"Alice\"");
    assertThat(messages.get(1)).contains("\"id\":2", "\"name\":\"Bob\"");
    assertThat(messages.get(2)).contains("\"id\":3", "\"name\":\"Charlie\"");
  }

  @Test
  void shouldHandleLargeNumberOfRecords() throws Exception {
    // Given: Kafka destination with batching
    String topic = "test-large-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .batchSize(100)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write 1000 records
    int recordCount = 1000;
    for (int i = 0; i < recordCount; i++) {
      Map<String, Object> record = Map.of("id", i, "value", "Record-" + i);
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= recordCount;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(recordCount);
  }

  @Test
  void shouldWriteRecordsWithSyncMode() throws Exception {
    // Given: Kafka destination in sync mode
    String topic = "test-sync-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .sync(true) // Synchronous mode
            .batchSize(1)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write record in sync mode
    Map<String, Object> record = new HashMap<>();
    record.put("userId", "user-123");
    record.put("name", "Alice");
    record.put("email", "alice@example.com");

    destination.write(record);
    destination.flush();

    // Then: Verify record received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return !records.isEmpty();
            });

    assertThat(records).isNotEmpty();
    ConsumerRecord<String, String> kafkaRecord = records.get(0);
    assertThat(kafkaRecord.value())
        .contains(
            "\"userId\":\"user-123\"", "\"name\":\"Alice\"", "\"email\":\"alice@example.com\"");
  }

  @Test
  void shouldHandleCompressionMode() throws Exception {
    // Given: Kafka destination with compression
    String topic = "test-compression-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .sync(false) // Async mode (default)
            .compression("gzip") // Enable compression
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with compression
    for (int i = 0; i < 50; i++) {
      Map<String, Object> record = Map.of("id", i, "data", "x".repeat(100)); // Compressible data
      destination.write(record);
    }
    destination.flush();

    // Then: All records should eventually arrive
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 50;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(50);
  }

  @Test
  void shouldHandleSnappyCompression() throws Exception {
    // Given: Kafka destination with Snappy compression
    String topic = "test-snappy-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .compression("snappy")
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with Snappy compression
    for (int i = 0; i < 20; i++) {
      Map<String, Object> record =
          Map.of("id", i, "data", "Snappy compression test data: " + "x".repeat(50));
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 20;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(20);
  }

  @Test
  void shouldHandleLz4Compression() throws Exception {
    // Given: Kafka destination with LZ4 compression
    String topic = "test-lz4-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .compression("lz4")
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with LZ4 compression
    for (int i = 0; i < 20; i++) {
      Map<String, Object> record =
          Map.of("id", i, "data", "LZ4 compression test data: " + "y".repeat(50));
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 20;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(20);
  }

  @Test
  void shouldHandleZstdCompression() throws Exception {
    // Given: Kafka destination with Zstandard compression
    String topic = "test-zstd-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .compression("zstd")
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with Zstandard compression
    for (int i = 0; i < 20; i++) {
      Map<String, Object> record =
          Map.of("id", i, "data", "Zstandard compression test data: " + "z".repeat(50));
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 20;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(20);
  }

  @Test
  void shouldHandleNoCompression() throws Exception {
    // Given: Kafka destination with no compression (explicit)
    String topic = "test-no-compression-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .compression("none")
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with no compression
    for (int i = 0; i < 15; i++) {
      Map<String, Object> record = Map.of("id", i, "data", "No compression data: " + i);
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 15;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(15);
  }

  @Test
  void shouldHandleCustomBatchSizeAndLinger() throws Exception {
    // Given: Kafka destination with custom batch size and linger
    String topic = "test-custom-batch-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .batchSize(32768) // 32KB
            .lingerMs(50) // 50ms linger time
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with custom batching
    for (int i = 0; i < 30; i++) {
      Map<String, Object> record = Map.of("id", i, "data", "Batch test data " + i);
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 30;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(30);
  }

  @Test
  void shouldHandleDifferentAcksSettings() throws Exception {
    // Given: Kafka destination with acks="all" (idempotent producer requirement)
    String topic = "test-acks-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .acks("all") // Explicit all acks for durability
            .batchSize(10)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write records with all acks
    for (int i = 0; i < 10; i++) {
      Map<String, Object> record = Map.of("id", i, "data", "Acks test " + i);
      destination.write(record);
    }
    destination.flush();

    // Then: Verify all records received (with high durability)
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return records.size() >= 10;
            });

    assertThat(records).hasSizeGreaterThanOrEqualTo(10);
  }

  @Test
  void shouldAcceptSecurityProtocolConfiguration() {
    // Given: Kafka destination with PLAINTEXT security protocol (testcontainers default)
    String topic = "test-security-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .securityProtocol("PLAINTEXT") // Explicit PLAINTEXT
            .build();

    // When: Open destination
    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    // Then: Should open successfully (no exception)
    assertThat(destination).isNotNull();
  }

  @Test
  void shouldAcceptConfigurationWithoutOptionalFields() {
    // Given: Minimal Kafka config (only required fields)
    String topic = "test-minimal-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .build();

    // When: Open destination with minimal config
    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    // Then: Should use defaults successfully
    assertThat(destination).isNotNull();
  }

  @Test
  void shouldHandleInvalidBrokerAddress() {
    // Given: Kafka destination with malformed broker address
    String topic = "test-error-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap("not-a-valid-hostname!!!") // Invalid hostname format
            .topic(topic)
            .build();

    // When/Then: Should fail on open due to invalid bootstrap servers
    destination = new KafkaDestination(config, new JsonSerializer());
    assertThatThrownBy(() -> destination.open())
        .isInstanceOf(Exception.class); // KafkaException: Failed to construct kafka producer
  }

  @Test
  void shouldHandleWriteAfterClose() throws Exception {
    // Given: Kafka destination that has been closed
    String topic = "test-close-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();
    destination.close(); // Close the destination

    // When: Try to write after closing
    Map<String, Object> record = Map.of("id", 1, "data", "test");

    // Then: Should throw exception
    assertThatThrownBy(() -> destination.write(record))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("not open");
  }

  @Test
  void shouldHandleSerializationError() throws Exception {
    // Given: Kafka destination with records that might have serialization issues
    String topic = "test-serialization-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write record with special characters that JSON can handle
    Map<String, Object> record =
        Map.of(
            "id", 1,
            "data", "Special chars: \n\t\r\"'\\",
            "unicode", "日本語 中文 한국어 العربية");

    destination.write(record);
    destination.flush();

    // Then: Record should be serialized and published successfully
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return !records.isEmpty();
            });

    assertThat(records).isNotEmpty();
    assertThat(records.get(0).value()).contains("Special chars");
  }

  @Test
  void shouldHandleEmptyRecords() throws Exception {
    // Given: Kafka destination
    String topic = "test-empty-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write empty record
    Map<String, Object> emptyRecord = Map.of();
    destination.write(emptyRecord);
    destination.flush();

    // Then: Empty record should be published successfully
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return !records.isEmpty();
            });

    assertThat(records).isNotEmpty();
    assertThat(records.get(0).value()).isEqualTo("{}");
  }

  @Test
  void shouldHandleLargeRecords() throws Exception {
    // Given: Kafka destination with reasonably large messages
    String topic = "test-large-record-topic";
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(kafka.getBootstrapServers())
            .topic(topic)
            .build();

    destination = new KafkaDestination(config, new JsonSerializer());
    destination.open();

    consumer.subscribe(Collections.singletonList(topic));

    // When: Write a large record (100KB of data - within default Kafka limits)
    String largeData = "x".repeat(100 * 1024); // 100KB string
    Map<String, Object> largeRecord = Map.of("id", 1, "data", largeData);

    destination.write(largeRecord);
    destination.flush();

    // Then: Large record should be published successfully
    CopyOnWriteArrayList<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100)).forEach(records::add);
              return !records.isEmpty();
            });

    assertThat(records).isNotEmpty();
    assertThat(records.get(0).value()).contains("\"id\":1");
  }
}
