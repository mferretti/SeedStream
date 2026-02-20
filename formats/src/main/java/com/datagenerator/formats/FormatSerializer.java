package com.datagenerator.formats;

import java.util.Map;

/**
 * Interface for serializing generated data records to various output formats.
 *
 * <p>Implementations convert Map&lt;String, Object&gt; records to formatted strings (JSON, CSV,
 * Protobuf, etc.).
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe for concurrent generation workers.
 *
 * <p><b>Performance:</b> Serialize methods are on hot path - optimize for speed.
 */
public interface FormatSerializer {
  /**
   * Serialize a single record to formatted string.
   *
   * @param record the generated record with field names as keys
   * @return formatted string representation
   * @throws SerializationException if serialization fails
   */
  String serialize(Map<String, Object> record);

  /**
   * Get the format name (e.g., "json", "csv", "protobuf").
   *
   * @return format identifier
   */
  String getFormatName();
}
