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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.datagenerator.formats.FormatSerializer;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvSerializerTest {
  private static final String MARCO = "Marco";
  private static final String MILANO = "Milano";
  private static final String FIELD_PRICE = "price";
  private static final String FIELD_QUANTITY = "quantity";

  private CsvSerializer serializer;

  @BeforeEach
  void setUp() {
    serializer = new CsvSerializer();
  }

  @Test
  void shouldSerializeHeader() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("age", 42);
    data.put("city", "NYC");

    String header = serializer.serializeHeader(data);

    assertThat(header).isEqualTo("\"name\",\"age\",\"city\"");
  }

  @Test
  void shouldSerializeSimpleRow() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("age", 42);
    data.put("city", "NYC");

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"John\",\"42\",\"NYC\"");
  }

  @Test
  void shouldNeutralizeFormulaInjectionInValues() {
    // CSV formula injection (F1): leading = + - @ TAB CR get a single-quote prefix.
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("formula", "=cmd|'/c calc'!A1");
    data.put("phone", "+39061234567");

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"'=cmd|'/c calc'!A1\",\"'+39061234567\"");
  }

  @Test
  void shouldNeutralizeFormulaInjectionInHeader() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("=evil", "x");

    assertThat(serializer.serializeHeader(data)).isEqualTo("\"'=evil\"");
  }

  @Test
  void shouldNotAlterNormalValues() {
    // Typed numbers (incl. negative) and ordinary strings are untouched.
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("balance", -42);

    assertThat(serializer.serialize(data)).isEqualTo("\"John\",\"-42\"");
  }

  @Test
  void shouldSerializeWithFieldAliases() {
    // Simulate generator output with aliases
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("nome", MARCO);
    data.put("citta", MILANO);

    String header = serializer.serializeHeader(data);
    String csv = serializer.serialize(data);

    assertThat(header).isEqualTo("\"nome\",\"citta\"");
    assertThat(csv).isEqualTo("\"Marco\",\"Milano\"");
  }

  @Test
  void shouldEscapeQuotesInValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("description", "Product \"Premium\" Edition");

    String csv = serializer.serialize(data);

    // OpenCSV doubles quotes for escaping
    assertThat(csv).contains("\"Product \"\"Premium\"\" Edition\"");
  }

  @Test
  void shouldEscapeCommasInValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("address", "123 Main St, Apt 4B");

    String csv = serializer.serialize(data);

    // Values with commas should be quoted
    assertThat(csv).isEqualTo("\"123 Main St, Apt 4B\"");
  }

  @Test
  void shouldEscapeNewlinesInValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("notes", "Line 1\nLine 2\nLine 3");

    String csv = serializer.serialize(data);

    // Newlines should be preserved within quotes
    assertThat(csv).contains("Line 1\nLine 2\nLine 3");
  }

  @Test
  void shouldSerializeBigDecimal() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(FIELD_PRICE, new BigDecimal("99.95"));
    data.put(FIELD_QUANTITY, 5);

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"99.95\",\"5\"");
  }

  @Test
  void shouldSerializeLocalDateAsIso8601() {
    LocalDate date = LocalDate.of(2024, 3, 15);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("birthDate", date);

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"2024-03-15\"");
  }

  @Test
  void shouldSerializeInstantAsIso8601() {
    Instant timestamp = Instant.parse("2024-03-15T14:30:00Z");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("createdAt", timestamp);

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"2024-03-15T14:30:00Z\"");
  }

  @Test
  void shouldSerializeNestedObjectAsJson() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Via Roma");
    address.put("city", MILANO);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", MARCO);
    data.put("address", address);

    String csv = serializer.serialize(data);

    // Nested object should be JSON-serialized and quoted (CSV escapes inner quotes by doubling)
    // The JSON structure is preserved but quotes are escaped
    assertThat(csv)
        .contains(MARCO)
        .contains("Via Roma")
        .contains(MILANO)
        .matches(".*\\{.*street.*Via Roma.*city.*Milano.*\\}.*");
  }

  @Test
  void shouldSerializeArrayAsJson() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Order");
    data.put("items", List.of("item1", "item2", "item3"));

    String csv = serializer.serialize(data);

    // Array structure is preserved
    assertThat(csv)
        .contains("Order")
        .contains("item1")
        .contains("item2")
        .contains("item3")
        .contains("[")
        .contains("]");
  }

  @Test
  void shouldSerializeComplexNestedStructure() {
    Map<String, Object> lineItem1 =
        Map.of("product", "Widget", FIELD_QUANTITY, 5, FIELD_PRICE, 10.50);
    Map<String, Object> lineItem2 =
        Map.of("product", "Gadget", FIELD_QUANTITY, 2, FIELD_PRICE, 25.00);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("invoiceNumber", "INV-001");
    data.put("date", LocalDate.of(2024, 3, 15));
    data.put("lineItems", List.of(lineItem1, lineItem2));

    String csv = serializer.serialize(data);

    // Nested array should be JSON-serialized
    assertThat(csv)
        .contains("INV-001")
        .contains("2024-03-15")
        .contains("Widget")
        .contains("Gadget");
  }

  @Test
  void shouldHandleNullValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("middleName", null);
    data.put("age", 42);

    String csv = serializer.serialize(data);

    // Null should be empty string
    assertThat(csv).isEqualTo("\"John\",\"\",\"42\"");
  }

  @Test
  void shouldHandleEmptyRecord() {
    Map<String, Object> data = Map.of();

    String header = serializer.serializeHeader(data);
    String csv = serializer.serialize(data);

    assertThat(header).isEmpty();
    assertThat(csv).isEmpty();
  }

  @Test
  void shouldPreserveFieldOrder() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("field1", "A");
    data.put("field2", "B");
    data.put("field3", "C");

    String header = serializer.serializeHeader(data);
    String csv = serializer.serialize(data);

    assertThat(header).isEqualTo("\"field1\",\"field2\",\"field3\"");
    assertThat(csv).isEqualTo("\"A\",\"B\",\"C\"");
  }

  @Test
  void shouldHandleBooleanValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("active", true);
    data.put("verified", false);

    String csv = serializer.serialize(data);

    assertThat(csv).isEqualTo("\"true\",\"false\"");
  }

  @Test
  void shouldReturnCorrectFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("csv");
  }

  // ── FormatSerializer default methods (exercised via CsvSerializer) ──────────

  @Test
  void serializeToBytesDefaultReturnsUtf8Encoding() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", MARCO);

    byte[] bytes = serializer.serializeToBytes(row);
    String expected = serializer.serialize(row);

    assertThat(bytes).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void createStreamWriterDefaultWritesRecordWithNewline() throws Exception {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("city", "Rome");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (FormatSerializer.StreamWriter writer = serializer.createStreamWriter(out)) {
      writer.writeRecord(row);
    }

    String output = out.toString(StandardCharsets.UTF_8);
    assertThat(output).startsWith(serializer.serialize(row)).endsWith("\n");
  }

  @Test
  void streamWriterDefaultCloseDoesNotThrow() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FormatSerializer.StreamWriter writer = serializer.createStreamWriter(out);

    assertThatCode(writer::close).doesNotThrowAnyException();
  }

  @Test
  void shouldSerializeMultipleRowsWithSameStructure() {
    Map<String, Object> record1 = new LinkedHashMap<>();
    record1.put("name", "John");
    record1.put("age", 42);

    Map<String, Object> record2 = new LinkedHashMap<>();
    record2.put("name", "Jane");
    record2.put("age", 35);

    String header = serializer.serializeHeader(record1);
    String row1 = serializer.serialize(record1);
    String row2 = serializer.serialize(record2);

    assertThat(header).isEqualTo("\"name\",\"age\"");
    assertThat(row1).isEqualTo("\"John\",\"42\"");
    assertThat(row2).isEqualTo("\"Jane\",\"35\"");
  }
}
