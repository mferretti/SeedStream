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

package com.datagenerator.formats.json;

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.OutputStream;
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
 * <p><b>Thread Safety:</b> ObjectMapper and JsonFactory are thread-safe after configuration and can
 * be shared across workers. {@link StreamWriter} instances are NOT thread-safe.
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
  private final JsonFactory jsonFactory;

  /** Create JSON serializer with default configuration. */
  public JsonSerializer() {
    this.mapper = createObjectMapper();
    this.jsonFactory = mapper.getFactory();
  }

  /**
   * Create JSON serializer with custom ObjectMapper.
   *
   * @param mapper custom configured ObjectMapper
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ObjectMapper is a thread-safe, shared serialization service; storing reference is intentional")
  public JsonSerializer(ObjectMapper objectMapper) {
    this.mapper = objectMapper;
    this.jsonFactory = objectMapper.getFactory();
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

  /**
   * Open a streaming writer that holds one {@link JsonGenerator} open for all records, eliminating
   * per-record String allocation. The generator is closed (but the stream is not) when the writer
   * is closed.
   *
   * <p>The generator is wired to a flush-suppressing proxy so that per-record {@code gen.flush()}
   * only drains the generator's internal byte buffer into {@code out}'s buffer — it does NOT
   * propagate a system-level flush on every record, preserving the {@link
   * java.io.BufferedOutputStream} batching behaviour.
   */
  @Override
  public StreamWriter createStreamWriter(OutputStream out) throws IOException {
    // Proxy that forwards writes but swallows flush() — prevents gen.flush() from
    // triggering a BufferedOutputStream drain on every single record.
    OutputStream genOut =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            out.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
          }

          @Override
          public void flush() {} // suppress: real flush only on FileDestination.flush()
        };

    JsonGenerator gen = jsonFactory.createGenerator(genOut);
    gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    gen.setCodec(mapper);
    return new StreamWriter() {
      @Override
      public void writeRecord(Map<String, Object> record) throws IOException {
        gen.writeObject(record);
        gen.flush(); // drains generator's internal buffer → out's buffer; no OS call
        out.write('\n');
      }

      @Override
      public void close() throws IOException {
        gen.close();
      }
    };
  }

  @Override
  public String getFormatName() {
    return "json";
  }
}
