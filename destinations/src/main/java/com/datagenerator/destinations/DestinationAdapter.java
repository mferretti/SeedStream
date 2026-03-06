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

package com.datagenerator.destinations;

import java.util.Map;

/**
 * Interface for sending generated data to various destinations (file, Kafka, database, etc.).
 *
 * <p>Implementations write records to target systems with batching, compression, and error
 * handling.
 *
 * <p><b>Lifecycle:</b>
 *
 * <ol>
 *   <li>open() - Initialize connection/resources
 *   <li>write() - Write records (may batch internally)
 *   <li>flush() - Force pending writes to destination
 *   <li>close() - Release resources
 * </ol>
 *
 * <p><b>Thread Safety:</b> Implementations should be thread-safe for concurrent writes from
 * multiple generator workers.
 */
public interface DestinationAdapter extends AutoCloseable {
  /**
   * Open connection to destination and initialize resources.
   *
   * @throws DestinationException if connection fails
   */
  void open();

  /**
   * Write a single record to destination. May batch records internally for performance.
   *
   * @param record the generated record
   * @throws DestinationException if write fails
   */
  void write(Map<String, Object> record);

  /**
   * Flush any buffered records to destination.
   *
   * @throws DestinationException if flush fails
   */
  void flush();

  /** Close connection and release resources. Should call flush() before closing. */
  @Override
  void close();

  /**
   * Get destination type identifier (e.g., "file", "kafka", "database").
   *
   * @return destination type
   */
  String getDestinationType();
}
