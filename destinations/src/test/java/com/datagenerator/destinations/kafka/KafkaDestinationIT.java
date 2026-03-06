package com.datagenerator.destinations.kafka;

import static org.assertj.core.api.Assertions.assertThat;
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
}
