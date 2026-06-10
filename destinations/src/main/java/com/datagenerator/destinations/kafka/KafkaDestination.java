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
import com.datagenerator.destinations.AbstractDestination;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.destinations.retry.RetryPolicy;
import com.datagenerator.formats.FormatSerializer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
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
public class KafkaDestination extends AbstractDestination {
  private final KafkaDestinationConfig config;
  private final FormatSerializer serializer;
  private final RetryPolicy retryPolicy;

  private Producer<String, byte[]> producer;
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
  public KafkaDestination(KafkaDestinationConfig cfg, FormatSerializer ser) {
    if (cfg.getBootstrap() == null || cfg.getBootstrap().isBlank()) {
      throw new DestinationException("Kafka bootstrap servers required");
    }
    if (cfg.getTopic() == null || cfg.getTopic().isBlank()) {
      throw new DestinationException("Kafka topic required");
    }

    this.config = cfg;
    this.serializer = ser;
    this.retryPolicy = RetryPolicy.of(cfg.getMaxRetries(), cfg.getRetryDelayMs());
  }

  /** Package-private: injects a pre-built producer for unit testing. */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification =
          "Fail-fast config validation is intentional; "
              + "KafkaDestination is not Serializable and not subject to finalizer attacks")
  KafkaDestination(
      KafkaDestinationConfig cfg, FormatSerializer ser, Producer<String, byte[]> prod) {
    this(cfg, ser);
    this.producer = prod;
    this.isOpen = true;
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

    // Idempotence requires acks=all; skip for acks=1/0 (test data generation)
    if ("all".equals(config.getAcks()) || "-1".equals(config.getAcks())) {
      props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    }

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
  public boolean supportsSerializedWrite() {
    // Each Kafka message is an independently-encoded payload, so serialization can run on the
    // worker threads; the producer send still happens on the single writer thread.
    return true;
  }

  @Override
  public void writeSerialized(byte[] payload) {
    requireOpen("Kafka");
    sendBytes(payload);
  }

  @Override
  public void write(Map<String, Object> data) {
    requireOpen("Kafka");
    sendBytes(serializer.serializeToBytes(data));
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void sendBytes(byte[] recordBytes) {
    try {
      // Create producer record (null key, uses default partitioning)
      ProducerRecord<String, byte[]> producerRecord =
          new ProducerRecord<>(config.getTopic(), null, recordBytes);

      if (config.isSync()) {
        retryPolicy.execute(
            "Kafka sync send to " + config.getTopic(), () -> producer.send(producerRecord).get());
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

    } catch (DestinationException e) {
      throw e;
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
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
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
