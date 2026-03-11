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

import com.datagenerator.core.util.LogUtils;
import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.FormatSerializer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Writes generated records to Kafka topics.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Async and sync send modes
 *   <li>Configurable batching and compression
 *   <li>SASL/SSL authentication
 *   <li>Automatic producer configuration
 *   <li>Error handling and retry logic
 * </ul>
 *
 * <p><b>Format Support:</b> JSON, CSV, or any custom {@link FormatSerializer}. Records are
 * serialized to bytes before sending.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * KafkaDestinationConfig config = KafkaDestinationConfig.builder()
 *     .bootstrap("localhost:9092")
 *     .topic("test-topic")
 *     .compression("gzip")
 *     .build();
 *
 * try (KafkaDestination dest = new KafkaDestination(config, new JsonSerializer())) {
 *     dest.open();
 *     dest.write(record1);
 *     dest.write(record2);
 *     dest.flush();
 * }
 * </pre>
 *
 * <p><b>Thread Safety:</b> Kafka producer is thread-safe, so this destination can be used
 * concurrently by multiple generator workers.
 */
@Slf4j
public class KafkaDestination implements DestinationAdapter {
  private final KafkaDestinationConfig config;
  private final FormatSerializer serializer;

  private KafkaProducer<String, byte[]> producer;
  private boolean isOpen = false;
  private long recordCount = 0;

  /**
   * Create Kafka destination with configuration and serializer.
   *
   * @param config Kafka connection and producer configuration
   * @param serializer format serializer (JSON, CSV, etc.)
   */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification =
          "Fail-fast config validation in constructor is intentional; "
              + "KafkaDestination is not Serializable and not subject to finalizer attacks")
  public KafkaDestination(KafkaDestinationConfig config, FormatSerializer serializer) {
    if (config.getBootstrap() == null || config.getBootstrap().isBlank()) {
      throw new DestinationException("Kafka bootstrap servers required");
    }
    if (config.getTopic() == null || config.getTopic().isBlank()) {
      throw new DestinationException("Kafka topic required");
    }

    this.config = config;
    this.serializer = serializer;
  }

  @Override
  public void open() {
    if (isOpen) {
      log.warn("Kafka destination already open: topic={}", config.getTopic());
      return;
    }

    Properties props = buildProducerProperties();
    producer = new KafkaProducer<>(props);
    isOpen = true;

    log.info(
        "Opened Kafka destination: topic={}, bootstrap={}, sync={}, compression={}",
        config.getTopic(),
        config.getBootstrap(),
        config.isSync(),
        config.getCompression());
  }

  private Properties buildProducerProperties() {
    Properties props = new Properties();

    // Required properties
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrap());

    // Serializers
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

    // Performance tuning
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
    props.put(ProducerConfig.LINGER_MS_CONFIG, config.getLingerMs());
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getCompression());
    props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());

    // Enable idempotence for exactly-once semantics
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

    // Authentication (SASL/SSL)
    if (config.getSecurityProtocol() != null) {
      props.put("security.protocol", config.getSecurityProtocol());
    }
    if (config.getSaslMechanism() != null) {
      props.put("sasl.mechanism", config.getSaslMechanism());
    }
    if (config.getSaslJaasConfig() != null) {
      props.put("sasl.jaas.config", config.getSaslJaasConfig());
    }

    // SSL properties
    if (config.getSslTruststoreLocation() != null) {
      props.put("ssl.truststore.location", config.getSslTruststoreLocation());
      props.put("ssl.truststore.password", config.getSslTruststorePassword());
    }
    if (config.getSslKeystoreLocation() != null) {
      props.put("ssl.keystore.location", config.getSslKeystoreLocation());
      props.put("ssl.keystore.password", config.getSslKeystorePassword());
    }

    return props;
  }

  @Override
  public void write(Map<String, Object> record) {
    if (!isOpen) {
      throw new DestinationException("Kafka destination not open. Call open() first.");
    }

    try {
      // Serialize record to bytes
      String serializedRecord = serializer.serialize(record);
      byte[] recordBytes = serializedRecord.getBytes(StandardCharsets.UTF_8);

      // Create producer record (null key, uses default partitioning)
      ProducerRecord<String, byte[]> producerRecord =
          new ProducerRecord<>(config.getTopic(), null, recordBytes);

      if (config.isSync()) {
        // Synchronous send - wait for acknowledgment
        producer.send(producerRecord).get();
      } else {
        // Asynchronous send - fire and forget with callback
        producer.send(
            producerRecord,
            (metadata, exception) -> {
              if (exception != null) {
                log.error("Failed to send record to Kafka topic: {}", config.getTopic(), exception);
              } else {
                // TRACE log successful send (sampled)
                if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
                  log.trace(
                      "Sent record to partition {} offset {}",
                      metadata.partition(),
                      metadata.offset());
                }
              }
            });
      }

      recordCount++;

      // Progress logging every 10,000 records
      if (recordCount % 10000 == 0) {
        log.info("Sent {} records to Kafka topic: {}", recordCount, config.getTopic());
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DestinationException("Interrupted while sending record to Kafka", e);
    } catch (ExecutionException e) {
      throw new DestinationException("Failed to send record to Kafka", e.getCause());
    } catch (Exception e) {
      throw new DestinationException("Error writing to Kafka", e);
    }
  }

  @Override
  public void flush() {
    if (!isOpen) {
      log.warn("Cannot flush - Kafka destination not open");
      return;
    }

    log.debug("Flushing Kafka producer for topic: {}", config.getTopic());
    producer.flush();
  }

  @Override
  public void close() {
    if (!isOpen) {
      log.warn("Kafka destination already closed");
      return;
    }

    try {
      flush();
      producer.close();
      isOpen = false;
      log.info("Closed Kafka destination - total records sent: {}", recordCount);
    } catch (Exception e) {
      log.error("Error closing Kafka producer", e);
      throw new DestinationException("Failed to close Kafka producer", e);
    }
  }

  @Override
  public String getDestinationType() {
    return "kafka";
  }
}
