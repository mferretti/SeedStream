package com.datagenerator.formats.csv;

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVWriter;
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
    this.jsonMapper = createJsonMapper();
  }

  /**
   * Create CSV serializer with custom JSON mapper for complex fields.
   *
   * @param jsonMapper mapper for serializing nested objects/arrays
   */
  public CsvSerializer(ObjectMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  private static ObjectMapper createJsonMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.INDENT_OUTPUT);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  /**
   * Serialize header row with column names.
   *
   * @param record sample record to extract column names (uses key order from LinkedHashMap)
   * @return CSV header row
   */
  public String serializeHeader(Map<String, Object> record) {
    if (record.isEmpty()) {
      return "";
    }

    StringWriter stringWriter = new StringWriter();
    try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
      String[] headers = record.keySet().toArray(new String[0]);
      csvWriter.writeNext(headers, true); // true = always quote
    } catch (Exception e) {
      log.error("Failed to serialize CSV header: {}", record.keySet(), e);
      throw new SerializationException("CSV header serialization failed", e);
    }

    return stringWriter.toString().trim();
  }

  @Override
  public String serialize(Map<String, Object> record) {
    if (record.isEmpty()) {
      return "";
    }

    StringWriter stringWriter = new StringWriter();
    try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
      String[] values = new String[record.size()];
      int i = 0;

      for (Object value : record.values()) {
        values[i++] = convertToString(value);
      }

      csvWriter.writeNext(values, true); // true = always quote
    } catch (Exception e) {
      log.error("Failed to serialize record to CSV: {}", record, e);
      throw new SerializationException("CSV serialization failed", e);
    }

    return stringWriter.toString().trim();
  }

  /**
   * Convert field value to CSV-compatible string.
   *
   * @param value the field value
   * @return string representation
   */
  private String convertToString(Object value) {
    if (value == null) {
      return "";
    }

    // Primitive types and strings
    if (value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character) {
      return value.toString();
    }

    // Dates and timestamps (ISO-8601)
    if (value instanceof LocalDate || value instanceof Instant) {
      return value.toString();
    }

    // Complex types (nested objects, arrays) -> serialize to JSON
    if (value instanceof Map || value instanceof List) {
      try {
        return jsonMapper.writeValueAsString(value);
      } catch (JsonProcessingException e) {
        log.warn("Failed to serialize complex value to JSON, using toString(): {}", value, e);
        return value.toString();
      }
    }

    // Fallback
    return value.toString();
  }

  @Override
  public String getFormatName() {
    return "csv";
  }
}
