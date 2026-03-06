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

/**
 * Kafka destination support for streaming generated data to Kafka topics.
 *
 * <p>This package provides high-performance Kafka producer integration with batching, compression,
 * and error handling.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.destinations.kafka.KafkaDestination} - Kafka producer wrapper
 *   <li>{@link com.datagenerator.destinations.kafka.KafkaDestinationConfig} - Configuration
 *       (bootstrap, topic, security)
 * </ul>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Batching:</b> Configurable batch size for high throughput
 *   <li><b>Compression:</b> Optional compression (gzip, snappy, lz4, zstd)
 *   <li><b>Security:</b> SASL/SSL authentication support
 *   <li><b>Error Handling:</b> Retry logic with exponential backoff
 *   <li><b>Metrics:</b> Track send success/failure rates
 * </ul>
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * type: kafka
 * conf:
 *   bootstrap: localhost:9092        # Kafka bootstrap servers
 *   topic: test-data                 # Target topic
 *   batch_size: 1000                 # Records per batch
 *   compression: lz4                 # Compression type
 *   security:                        # Optional SASL/SSL
 *     protocol: SASL_SSL
 *     mechanism: PLAIN
 *     username: ${KAFKA_USER}
 *     password: ${KAFKA_PASS}
 * </pre>
 *
 * <p><b>Performance Tips:</b>
 *
 * <ul>
 *   <li>Use batching (batch_size: 500-1000) for maximum throughput
 *   <li>Enable compression (lz4 recommended) to reduce network traffic
 *   <li>Tune linger.ms and batch.size for latency vs throughput tradeoff
 *   <li>Use async sends with callbacks for highest performance
 * </ul>
 *
 * <p><b>Thread Safety:</b> KafkaProducer is thread-safe, but this implementation uses
 * single-threaded writes for ordered delivery within partitions.
 */
package com.datagenerator.destinations.kafka;
