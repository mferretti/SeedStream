package com.datagenerator.formats.json;

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Serializes generated records to JSON format.
 *
 * <p>Produces newline-delimited JSON (NDJSON) for streaming compatibility. Each record is a single
 * JSON object on one line.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Compact JSON output (no pretty-printing for performance)
 *   <li>ISO-8601 datetime formatting
 *   <li>BigDecimal as numbers (not strings)
 *   <li>Field aliases preserved from generators
 * </ul>
 *
 * <p><b>Thread Safety:</b> ObjectMapper is thread-safe after configuration - can be shared across
 * workers.
 *
 * <p><b>Example Output:</b>
 *
 * <pre>
 * {"name":"John","age":42,"email":"john@example.com"}
 * {"name":"Jane","age":35,"email":"jane@example.com"}
 * </pre>
 */
@Slf4j
public class JsonSerializer implements FormatSerializer {
  private final ObjectMapper mapper;

  /** Create JSON serializer with default configuration. */
  public JsonSerializer() {
    this.mapper = createObjectMapper();
  }

  /**
   * Create JSON serializer with custom ObjectMapper.
   *
   * @param mapper custom configured ObjectMapper
   */
  public JsonSerializer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Register JSR-310 module for Java 8 date/time types (LocalDate, Instant, etc.)
    mapper.registerModule(new JavaTimeModule());

    // Disable pretty-printing for compact output (performance + smaller file size)
    mapper.disable(SerializationFeature.INDENT_OUTPUT);

    // Write dates as ISO-8601 strings, not timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return mapper;
  }

  @Override
  public String serialize(Map<String, Object> record) {
    try {
      return mapper.writeValueAsString(record);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize record to JSON: {}", record, e);
      throw new SerializationException("JSON serialization failed", e);
    }
  }

  @Override
  public String getFormatName() {
    return "json";
  }
}
