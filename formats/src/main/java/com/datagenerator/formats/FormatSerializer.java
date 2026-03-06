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
