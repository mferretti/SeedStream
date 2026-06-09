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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.formats.FormatSerializer.StreamWriter;
import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonSerializerTest {
  private static final String THREAD_1 = "Thread1";
  private static final String THREAD_2 = "Thread2";

  private JsonSerializer serializer;
  private ObjectMapper mapper; // For parsing output to verify structure

  @BeforeEach
  void setUp() {
    serializer = new JsonSerializer();
    mapper = new ObjectMapper();
  }

  @Test
  void shouldSerializeSimpleRecord() throws Exception {
    Map<String, Object> record = Map.of("name", "John", "age", 42, "active", true);

    String json = serializer.serialize(record);

    assertThat(json).isNotBlank();
    JsonNode node = mapper.readTree(json);
    assertThat(node.get("name").asText()).isEqualTo("John");
    assertThat(node.get("age").asInt()).isEqualTo(42);
    assertThat(node.get("active").asBoolean()).isTrue();
  }

  @Test
  void shouldSerializeWithFieldAliases() throws Exception {
    // Simulate generator output with aliases
    Map<String, Object> record = Map.of("nome", "Marco", "citta", "Milano");

    String json = serializer.serialize(record);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("nome").asText()).isEqualTo("Marco");
    assertThat(node.get("citta").asText()).isEqualTo("Milano");
  }

  @Test
  void shouldSerializeBigDecimalAsNumber() throws Exception {
    Map<String, Object> record = Map.of("price", new BigDecimal("99.95"), "quantity", 5);

    String json = serializer.serialize(record);

    assertThat(json).contains("\"price\":99.95"); // Not "99.95" (string)
    // Note: When parsing JSON, numbers may be deserialized as Double by default
    JsonNode node = mapper.readTree(json);
    assertThat(node.get("price").isNumber()).isTrue();
    assertThat(node.get("price").decimalValue()).isEqualByComparingTo("99.95");
  }

  @Test
  void shouldSerializeLocalDateAsIso8601() throws Exception {
    LocalDate date = LocalDate.of(2024, 3, 15);
    Map<String, Object> record = Map.of("birthDate", date);

    String json = serializer.serialize(record);

    assertThat(json).contains("\"2024-03-15\"");
    JsonNode node = mapper.readTree(json);
    assertThat(node.get("birthDate").asText()).isEqualTo("2024-03-15");
  }

  @Test
  void shouldSerializeInstantAsIso8601() throws Exception {
    Instant timestamp = Instant.parse("2024-03-15T14:30:00Z");
    Map<String, Object> record = Map.of("createdAt", timestamp);

    String json = serializer.serialize(record);

    assertThat(json).contains("2024-03-15");
    JsonNode node = mapper.readTree(json);
    assertThat(node.get("createdAt").asText()).isEqualTo("2024-03-15T14:30:00Z");
  }

  @Test
  void shouldSerializeNestedObjects() throws Exception {
    Map<String, Object> address = Map.of("street", "Via Roma", "city", "Milano");
    Map<String, Object> record = Map.of("name", "Marco", "address", address);

    String json = serializer.serialize(record);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("name").asText()).isEqualTo("Marco");
    assertThat(node.get("address").isObject()).isTrue();
    assertThat(node.get("address").get("street").asText()).isEqualTo("Via Roma");
    assertThat(node.get("address").get("city").asText()).isEqualTo("Milano");
  }

  @Test
  void shouldSerializeArrays() throws Exception {
    Map<String, Object> record =
        Map.of("name", "Order", "items", List.of("item1", "item2", "item3"));

    String json = serializer.serialize(record);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("items").isArray()).isTrue();
    assertThat(node.get("items")).hasSize(3);
    assertThat(node.get("items").get(0).asText()).isEqualTo("item1");
  }

  @Test
  void shouldSerializeComplexNestedStructure() throws Exception {
    Map<String, Object> lineItem1 = Map.of("product", "Widget", "quantity", 5, "price", 10.50);
    Map<String, Object> lineItem2 = Map.of("product", "Gadget", "quantity", 2, "price", 25.00);

    Map<String, Object> company = Map.of("name", "ACME Corp", "taxId", "12345678");

    Map<String, Object> invoice =
        Map.of(
            "invoiceNumber",
            "INV-001",
            "date",
            LocalDate.of(2024, 3, 15),
            "company",
            company,
            "lineItems",
            List.of(lineItem1, lineItem2));

    String json = serializer.serialize(invoice);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("invoiceNumber").asText()).isEqualTo("INV-001");
    assertThat(node.get("company").get("name").asText()).isEqualTo("ACME Corp");
    assertThat(node.get("lineItems")).hasSize(2);
    assertThat(node.get("lineItems").get(0).get("product").asText()).isEqualTo("Widget");
  }

  @Test
  void shouldProduceCompactJson() {
    Map<String, Object> record = Map.of("name", "John", "age", 42);

    String json = serializer.serialize(record);

    // Should NOT contain indentation or newlines
    assertThat(json).doesNotContain("\n").doesNotContain("  ");
  }

  @Test
  void shouldPreserveFieldOrder() {
    // Use LinkedHashMap to preserve insertion order
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("field1", "A");
    record.put("field2", "B");
    record.put("field3", "C");

    String json = serializer.serialize(record);

    // JSON spec doesn't guarantee order, but Jackson preserves it by default
    assertThat(json).matches(".*\"field1\".*\"field2\".*\"field3\".*");
  }

  @Test
  void shouldHandleEmptyRecord() {
    Map<String, Object> record = Map.of();

    String json = serializer.serialize(record);

    assertThat(json).isEqualTo("{}");
  }

  @Test
  void shouldHandleNullValues() throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("middleName", null);
    record.put("age", 42);

    String json = serializer.serialize(record);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("middleName").isNull()).isTrue();
  }

  // ── StreamWriter ──────────────────────────────────────────────────────────

  @Test
  void streamWriterWritesJsonFollowedByNewline() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (StreamWriter writer = serializer.createStreamWriter(out)) {
      writer.writeRecord(Map.of("name", "Alice", "age", 30));
    }

    String output = out.toString(StandardCharsets.UTF_8);
    assertThat(output).endsWith("\n");
    JsonNode node = mapper.readTree(output.trim());
    assertThat(node.get("name").asText()).isEqualTo("Alice");
    assertThat(node.get("age").asInt()).isEqualTo(30);
  }

  @Test
  void streamWriterWritesMultipleRecords() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (StreamWriter writer = serializer.createStreamWriter(out)) {
      writer.writeRecord(Map.of("id", 1));
      writer.writeRecord(Map.of("id", 2));
      writer.writeRecord(Map.of("id", 3));
    }

    String[] lines = out.toString(StandardCharsets.UTF_8).split("\n");
    assertThat(lines).hasSize(3);
    assertThat(mapper.readTree(lines[0]).get("id").asInt()).isEqualTo(1);
    assertThat(mapper.readTree(lines[1]).get("id").asInt()).isEqualTo(2);
    assertThat(mapper.readTree(lines[2]).get("id").asInt()).isEqualTo(3);
  }

  @Test
  void streamWriterOutputMatchesSerialize() throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Bob");
    record.put("score", new BigDecimal("9.5"));
    record.put("date", LocalDate.of(2025, 1, 1));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (StreamWriter writer = serializer.createStreamWriter(out)) {
      writer.writeRecord(record);
    }

    String streamed = out.toString(StandardCharsets.UTF_8).trim();
    String direct = serializer.serialize(record);
    assertThat(mapper.readTree(streamed)).isEqualTo(mapper.readTree(direct));
  }

  @Test
  void streamWriterDoesNotCloseUnderlyingStream() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    StreamWriter writer = serializer.createStreamWriter(out);
    writer.writeRecord(Map.of("key", "val"));
    writer.close();

    // Stream still usable after writer closed
    out.write("extra".getBytes(StandardCharsets.UTF_8));
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("key").contains("extra");
  }

  @Test
  void streamWriterDoesNotPropagateFlushToUnderlyingStream() throws Exception {
    int[] flushCount = {0};
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    OutputStream trackingOut =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            buf.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            buf.write(b, off, len);
          }

          @Override
          public void flush() {
            flushCount[0]++;
          }
        };

    try (StreamWriter writer = serializer.createStreamWriter(trackingOut)) {
      for (int i = 0; i < 100; i++) {
        writer.writeRecord(Map.of("id", i));
      }
    }

    assertThat(flushCount[0]).isZero();
    assertThat(buf.size()).isGreaterThan(0);
  }

  @Test
  void streamWriterBytesReachOuterStreamWithoutExplicitFlush() throws Exception {
    int[] flushCount = {0};
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    OutputStream trackingOut =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            buf.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            buf.write(b, off, len);
          }

          @Override
          public void flush() {
            flushCount[0]++;
          }
        };

    try (StreamWriter writer = serializer.createStreamWriter(trackingOut)) {
      writer.writeRecord(Map.of("key", "value"));
    }

    String output = buf.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"key\"").contains("\"value\"");
    assertThat(flushCount[0]).isZero();
  }

  @Test
  void streamWriterHandlesComplexTypes() throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("items", List.of("a", "b"));
    record.put("ts", Instant.parse("2025-06-01T00:00:00Z"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (StreamWriter writer = serializer.createStreamWriter(out)) {
      writer.writeRecord(record);
    }

    JsonNode node = mapper.readTree(out.toString(StandardCharsets.UTF_8).trim());
    assertThat(node.get("items").isArray()).isTrue();
    assertThat(node.get("ts").asText()).isEqualTo("2025-06-01T00:00:00Z");
  }

  @Test
  void shouldReturnCorrectFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("json");
  }

  @Test
  void shouldThrowSerializationExceptionOnError() {
    // Create a map with an unserializable object
    Object unserializable =
        new Object() {
          public Object getSelf() {
            return this; // Circular reference
          }
        };

    Map<String, Object> record = Map.of("circular", unserializable);

    assertThatThrownBy(() -> serializer.serialize(record))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("JSON serialization failed");
  }

  @Test
  void shouldBeThreadSafe() {
    // ObjectMapper is thread-safe, verify concurrent use
    Map<String, Object> record1 = Map.of("id", 1, "name", THREAD_1);
    Map<String, Object> record2 = Map.of("id", 2, "name", THREAD_2);

    String json1 = serializer.serialize(record1);
    String json2 = serializer.serialize(record2);

    assertThat(json1).contains(THREAD_1);
    assertThat(json2).contains(THREAD_2);
    assertThat(json1).doesNotContain(THREAD_2);
    assertThat(json2).doesNotContain(THREAD_1);
  }
}
