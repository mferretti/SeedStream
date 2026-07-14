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

import java.util.List;
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
  void write(Map<String, Object> data);

  /**
   * Whether this destination can accept records that have already been serialized to bytes by a
   * worker thread via {@link #writeSerialized}. Defaults to {@code false}; destinations that can
   * append an independently-encoded record payload (e.g. newline-delimited file formats, Kafka
   * messages) override this to return {@code true}. Container formats that must serialize on the
   * writer thread (e.g. Avro OCF) must leave it {@code false}.
   *
   * @return true if {@link #writeSerialized} is supported
   */
  default boolean supportsSerializedWrite() {
    return false;
  }

  /**
   * Write a record that a worker thread already serialized to its raw payload bytes. The
   * destination applies its own framing (e.g. a trailing newline for newline-delimited file
   * formats). Called only on the single writer thread, so implementations need not be thread-safe.
   *
   * @param payload raw serialized record bytes (no framing)
   * @throws DestinationException if write fails
   */
  default void writeSerialized(byte[] payload) {
    throw new UnsupportedOperationException(
        getDestinationType() + " does not support serialized writes");
  }

  /**
   * Whether this destination can further coalesce multiple {@link #writeSerialized} payloads within
   * a chunk into a single write, via {@link #coalesce} and {@link #writeSerializedChunk}. Defaults
   * to {@code false}. Only meaningful when {@link #supportsSerializedWrite()} is also {@code true};
   * ignored otherwise.
   *
   * <p>Enable this for destinations where independently-serialized record payloads can be
   * concatenated (with framing, e.g. a newline delimiter) into one blob and written in a single
   * call — collapsing what would be {@code chunkSize} writer-thread {@code write()} calls into one.
   * Leave this {@code false} (the default) for destinations where each payload must remain an
   * independent write unit — e.g. Kafka, where one payload is one message; coalescing there would
   * merge multiple records into a single message, corrupting the output.
   *
   * @return true if {@link #coalesce} and {@link #writeSerializedChunk} are supported
   */
  default boolean supportsWriteCoalescing() {
    return false;
  }

  /**
   * Fold an ordered, non-empty list of independently-serialized record payloads into a single
   * combined payload, applying whatever framing this destination needs between records (e.g. a
   * trailing newline per record for NDJSON). Runs on a worker thread — the writer thread later
   * writes the result via {@link #writeSerializedChunk}. Only invoked when {@link
   * #supportsWriteCoalescing()} returns {@code true}.
   *
   * @param payloads ordered, non-empty per-record serialized payloads
   * @return single payload combining all inputs, framed as this destination requires
   */
  default byte[] coalesce(List<byte[]> payloads) {
    throw new UnsupportedOperationException(
        getDestinationType() + " does not support write coalescing");
  }

  /**
   * Write a payload already produced by {@link #coalesce} — one or more record payloads already
   * concatenated and framed. Called only on the single writer thread, so implementations need not
   * be thread-safe. Only invoked when {@link #supportsWriteCoalescing()} returns {@code true}.
   * Unlike {@link #writeSerialized}, implementations must NOT apply any further per-record framing
   * here: {@link #coalesce} already did.
   *
   * @param coalescedPayload the already-framed, concatenated payload
   * @throws DestinationException if write fails
   */
  default void writeSerializedChunk(byte[] coalescedPayload) {
    throw new UnsupportedOperationException(
        getDestinationType() + " does not support write coalescing");
  }

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
