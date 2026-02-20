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
