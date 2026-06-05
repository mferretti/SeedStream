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

package com.datagenerator.formats;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Interface for serializing generated data records to various output formats.
 *
 * <p>Implementations convert Map&lt;String, Object&gt; records to formatted strings (JSON, CSV,
 * Protobuf, etc.).
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe for concurrent generation workers.
 * {@link StreamWriter} instances are NOT thread-safe — each destination gets its own.
 *
 * <p><b>Performance:</b> Serialize methods are on hot path - optimize for speed.
 */
public interface FormatSerializer {

  /**
   * Stateful record writer bound to a single OutputStream for its lifetime. Eliminates intermediate
   * String allocation by writing bytes directly to the stream.
   *
   * <p>Not thread-safe. Obtain one instance per destination via {@link
   * FormatSerializer#createStreamWriter}.
   */
  interface StreamWriter extends AutoCloseable {
    /**
     * Write one record followed by a newline byte to the bound stream.
     *
     * @throws IOException if writing fails
     */
    void writeRecord(Map<String, Object> record) throws IOException;

    /**
     * Release any resources held by this writer. Does NOT close the underlying stream.
     *
     * @throws IOException if closing fails
     */
    @Override
    default void close() throws IOException {}
  }

  /**
   * Serialize a single record to formatted string.
   *
   * @param record the generated record with field names as keys
   * @return formatted string representation
   * @throws SerializationException if serialization fails
   */
  String serialize(Map<String, Object> record);

  /**
   * Serialize a single record to raw bytes. Default implementation encodes {@link #serialize}
   * output as UTF-8. Binary serializers (e.g. Confluent Avro wire format) should override this to
   * return the true binary payload.
   *
   * @param record the generated record with field names as keys
   * @return serialized bytes
   * @throws SerializationException if serialization fails
   */
  default byte[] serializeToBytes(Map<String, Object> record) {
    return serialize(record).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Open a {@link StreamWriter} that streams records directly to {@code out} without intermediate
   * String allocation. The stream is NOT closed when the writer is closed.
   *
   * <p>Default implementation encodes {@link #serialize} output as UTF-8. Override to write bytes
   * directly (e.g. via {@code JsonGenerator}) for higher throughput.
   *
   * @param out destination stream — not closed by the returned writer
   * @return stateful writer; caller must close it when done
   * @throws IOException if the underlying stream cannot be initialized
   */
  default StreamWriter createStreamWriter(OutputStream out) throws IOException {
    return record -> {
      out.write(serialize(record).getBytes(StandardCharsets.UTF_8));
      out.write('\n');
    };
  }

  /**
   * Get the format name (e.g., "json", "csv", "protobuf").
   *
   * @return format identifier
   */
  String getFormatName();
}
