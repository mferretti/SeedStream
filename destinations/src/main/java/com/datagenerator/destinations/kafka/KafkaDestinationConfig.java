package com.datagenerator.destinations.kafka;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for Kafka destination.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Kafka broker connection (bootstrap servers)
 *   <li>Topic configuration
 *   <li>Async/sync send modes
 *   <li>Configurable batching and compression
 *   <li>SASL/SSL authentication
 * </ul>
 */
@Value
@Builder
public class KafkaDestinationConfig {
  /** Kafka bootstrap servers (e.g., "localhost:9092" or "broker1:9092,broker2:9092"). */
  String bootstrap;

  /** Target Kafka topic. */
  String topic;

  /** Whether to send records synchronously (wait for ack). Default: false (async). */
  @Builder.Default boolean sync = false;

  /** Kafka producer batch size in bytes. Default: 16384 (16KB). */
  @Builder.Default int batchSize = 16384;

  /** Time to wait before sending batch (milliseconds). Default: 10ms. */
  @Builder.Default int lingerMs = 10;

  /** Compression type: none, gzip, snappy, lz4, zstd. Default: none. */
  @Builder.Default String compression = "none";

  /** Acknowledgment mode: 0, 1, or all. Default: "1". */
  @Builder.Default String acks = "1";

  // Authentication fields
  /** Security protocol: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL. Optional. */
  String securityProtocol;

  /** SASL mechanism: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI. Optional. */
  String saslMechanism;

  /** SASL JAAS configuration string. Optional. */
  String saslJaasConfig;

  // SSL fields
  /** Path to SSL truststore file. Optional. */
  String sslTruststoreLocation;

  /** Password for SSL truststore. Optional. */
  String sslTruststorePassword;

  /** Path to SSL keystore file. Optional. */
  String sslKeystoreLocation;

  /** Password for SSL keystore. Optional. */
  String sslKeystorePassword;
}
