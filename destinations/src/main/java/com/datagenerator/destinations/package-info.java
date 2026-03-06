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
 * Destination adapters for writing generated data to various targets.
 *
 * <p>This package provides the infrastructure for sending generated records to different
 * destinations: files, Kafka topics, databases, and more. Each destination adapter handles
 * connection management, batching, error handling, and resource cleanup.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.destinations.DestinationAdapter} - Base interface for all
 *       destinations
 *   <li>{@link com.datagenerator.destinations.file.FileDestination} - Write to local files with NIO
 *   <li>{@link com.datagenerator.destinations.kafka.KafkaDestination} - Stream to Kafka topics
 * </ul>
 *
 * <p><b>Lifecycle:</b> All destinations follow a standard lifecycle:
 *
 * <ol>
 *   <li><b>open()</b> - Initialize connection/resources
 *   <li><b>write(record)</b> - Write records (may batch internally)
 *   <li><b>flush()</b> - Force pending writes to destination
 *   <li><b>close()</b> - Release resources (AutoCloseable)
 * </ol>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * FileDestinationConfig config = FileDestinationConfig.builder()
 *     .filePath(Paths.get("output/data.json"))
 *     .compress(true)
 *     .batchSize(1000)
 *     .build();
 *
 * try (FileDestination dest = new FileDestination(config, new JsonSerializer())) {
 *     dest.open();
 *     for (Map&lt;String, Object&gt; record : records) {
 *         dest.write(record);
 *     }
 *     dest.flush();
 * } // Auto-close releases resources
 * </pre>
 *
 * <p><b>Performance Optimizations:</b>
 *
 * <ul>
 *   <li><b>Batching:</b> Write multiple records in a single I/O operation (2-3x faster)
 *   <li><b>Buffering:</b> Use large buffers to amortize system call overhead
 *   <li><b>Compression:</b> Optional gzip compression for file destinations
 *   <li><b>Connection Pooling:</b> Kafka producer pooling for reuse across jobs
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>Connection failures throw {@link com.datagenerator.destinations.DestinationException}
 *   <li>Partial batch failures logged and retried (Kafka)
 *   <li>Resource cleanup guaranteed via AutoCloseable
 * </ul>
 *
 * <p><b>Thread Safety:</b> Destination adapters are NOT thread-safe by design. Use one instance per
 * writer thread (typically single writer in GenerationEngine).
 *
 * @see com.datagenerator.formats.FormatSerializer
 * @see com.datagenerator.core.engine.GenerationEngine
 */
package com.datagenerator.destinations;
