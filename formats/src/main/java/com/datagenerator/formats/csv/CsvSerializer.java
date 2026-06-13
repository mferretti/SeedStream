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

package com.datagenerator.formats.csv;

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import com.datagenerator.formats.SerializerMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Serializes generated records to CSV format.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Header row with column names (call {@link #serializeHeader(Map)} once, then {@link
 *       #serialize(Map)} for rows)
 *   <li>Proper CSV escaping (quotes, commas, newlines)
 *   <li>Nested objects/arrays serialized as JSON strings
 *   <li>ISO-8601 date formatting
 * </ul>
 *
 * <p><b>Nested Data Handling:</b> Maps and Lists are serialized as JSON strings within CSV fields.
 * For truly flat CSV, design structures with primitive fields only.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * CsvSerializer serializer = new CsvSerializer();
 * String header = serializer.serializeHeader(firstRecord); // Name,Age,City
 * String row1 = serializer.serialize(firstRecord);         // John,42,NYC
 * String row2 = serializer.serialize(secondRecord);        // Jane,35,LA
 * </pre>
 *
 * <p><b>Thread Safety:</b> Thread-safe after construction (immutable configuration)
 */
@Slf4j
public class CsvSerializer implements FormatSerializer {
  private final ObjectMapper jsonMapper;

  /** Create CSV serializer with default configuration. */
  public CsvSerializer() {
    this.jsonMapper = SerializerMapper.INSTANCE;
  }

  /**
   * Create CSV serializer with custom JSON mapper for complex fields.
   *
   * @param jsonMapper mapper for serializing nested objects/arrays
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ObjectMapper is a thread-safe, shared serialization service; storing reference is intentional")
  public CsvSerializer(ObjectMapper objectMapper) {
    this.jsonMapper = objectMapper;
  }

  /**
   * Serialize header row with column names.
   *
   * @param record sample record to extract column names (uses key order from LinkedHashMap)
   * @return CSV header row
   */
  public String serializeHeader(Map<String, Object> data) {
    if (data.isEmpty()) return "";
    String[] headers = data.keySet().toArray(new String[0]);
    for (int i = 0; i < headers.length; i++) {
      headers[i] = neutralizeFormula(headers[i]);
    }
    return writeCsv(headers);
  }

  @Override
  public String serialize(Map<String, Object> data) {
    if (data.isEmpty()) return "";
    String[] values = new String[data.size()];
    int i = 0;
    for (Object value : data.values()) {
      values[i++] = convertToString(value);
    }
    return writeCsv(values);
  }

  private String writeCsv(String[] values) {
    StringWriter sw = new StringWriter();
    try (CSVWriter csvWriter = new CSVWriter(sw)) {
      csvWriter.writeNext(values, true);
    } catch (IOException e) {
      throw new SerializationException("CSV serialization failed", e);
    }
    return sw.toString().trim();
  }

  private String convertToString(Object value) {
    return switch (value) {
      case null -> "";
      case String s -> neutralizeFormula(s);
      case Number n -> n.toString();
      case Boolean b -> b.toString();
      case Character c -> c.toString();
      case LocalDate d -> d.toString();
      case Instant ts -> ts.toString();
      case Map<?, ?> map -> toJson(map);
      case List<?> list -> toJson(list);
      default -> value.toString();
    };
  }

  /**
   * Neutralize CSV formula injection (CWE-1236). A cell whose first character is a spreadsheet
   * formula trigger ({@code = + - @}, TAB or CR) is executed as a formula by
   * Excel/LibreOffice/Sheets even when quoted, so prefix it with a single quote per OWASP guidance.
   * Only string cells pass through here; typed numbers/dates and nested JSON are not formula
   * vectors.
   *
   * @param value raw string cell value
   * @return the value unchanged, or prefixed with {@code '} if it starts with a formula trigger
   */
  private static String neutralizeFormula(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    if (first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r') {
      return "'" + value;
    }
    return value;
  }

  private String toJson(Object value) {
    try {
      return jsonMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize complex value to JSON, using toString(): {}", value, e);
      return value.toString();
    }
  }

  @Override
  public String getFormatName() {
    return "csv";
  }
}
