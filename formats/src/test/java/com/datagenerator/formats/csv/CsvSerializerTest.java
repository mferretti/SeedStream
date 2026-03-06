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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvSerializerTest {
  private CsvSerializer serializer;

  @BeforeEach
  void setUp() {
    serializer = new CsvSerializer();
  }

  @Test
  void shouldSerializeHeader() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("age", 42);
    record.put("city", "NYC");

    String header = serializer.serializeHeader(record);

    assertThat(header).isEqualTo("\"name\",\"age\",\"city\"");
  }

  @Test
  void shouldSerializeSimpleRow() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("age", 42);
    record.put("city", "NYC");

    String csv = serializer.serialize(record);

    assertThat(csv).isEqualTo("\"John\",\"42\",\"NYC\"");
  }

  @Test
  void shouldSerializeWithFieldAliases() {
    // Simulate generator output with aliases
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("nome", "Marco");
    record.put("citta", "Milano");

    String header = serializer.serializeHeader(record);
    String csv = serializer.serialize(record);

    assertThat(header).isEqualTo("\"nome\",\"citta\"");
    assertThat(csv).isEqualTo("\"Marco\",\"Milano\"");
  }

  @Test
  void shouldEscapeQuotesInValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("description", "Product \"Premium\" Edition");

    String csv = serializer.serialize(record);

    // OpenCSV doubles quotes for escaping
    assertThat(csv).contains("\"Product \"\"Premium\"\" Edition\"");
  }

  @Test
  void shouldEscapeCommasInValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("address", "123 Main St, Apt 4B");

    String csv = serializer.serialize(record);

    // Values with commas should be quoted
    assertThat(csv).isEqualTo("\"123 Main St, Apt 4B\"");
  }

  @Test
  void shouldEscapeNewlinesInValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("notes", "Line 1\nLine 2\nLine 3");

    String csv = serializer.serialize(record);

    // Newlines should be preserved within quotes
    assertThat(csv).contains("Line 1\nLine 2\nLine 3");
  }

  @Test
  void shouldSerializeBigDecimal() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("price", new BigDecimal("99.95"));
    record.put("quantity", 5);

    String csv = serializer.serialize(record);

    assertThat(csv).isEqualTo("\"99.95\",\"5\"");
  }

  @Test
  void shouldSerializeLocalDateAsIso8601() {
    LocalDate date = LocalDate.of(2024, 3, 15);
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("birthDate", date);

    String csv = serializer.serialize(record);

    assertThat(csv).isEqualTo("\"2024-03-15\"");
  }

  @Test
  void shouldSerializeInstantAsIso8601() {
    Instant timestamp = Instant.parse("2024-03-15T14:30:00Z");
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("createdAt", timestamp);

    String csv = serializer.serialize(record);

    assertThat(csv).isEqualTo("\"2024-03-15T14:30:00Z\"");
  }

  @Test
  void shouldSerializeNestedObjectAsJson() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Via Roma");
    address.put("city", "Milano");

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Marco");
    record.put("address", address);

    String csv = serializer.serialize(record);

    // Nested object should be JSON-serialized and quoted (CSV escapes inner quotes by doubling)
    assertThat(csv).contains("Marco");
    assertThat(csv).contains("Via Roma");
    assertThat(csv).contains("Milano");
    // The JSON structure is preserved but quotes are escaped
    assertThat(csv).matches(".*\\{.*street.*Via Roma.*city.*Milano.*\\}.*");
  }

  @Test
  void shouldSerializeArrayAsJson() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Order");
    record.put("items", List.of("item1", "item2", "item3"));

    String csv = serializer.serialize(record);

    assertThat(csv).contains("Order");
    assertThat(csv).contains("item1");
    assertThat(csv).contains("item2");
    assertThat(csv).contains("item3");
    // Array structure is preserved
    assertThat(csv).contains("[");
    assertThat(csv).contains("]");
  }

  @Test
  void shouldSerializeComplexNestedStructure() {
    Map<String, Object> lineItem1 = Map.of("product", "Widget", "quantity", 5, "price", 10.50);
    Map<String, Object> lineItem2 = Map.of("product", "Gadget", "quantity", 2, "price", 25.00);

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("invoiceNumber", "INV-001");
    record.put("date", LocalDate.of(2024, 3, 15));
    record.put("lineItems", List.of(lineItem1, lineItem2));

    String csv = serializer.serialize(record);

    assertThat(csv).contains("INV-001");
    assertThat(csv).contains("2024-03-15");
    // Nested array should be JSON-serialized
    assertThat(csv).contains("Widget");
    assertThat(csv).contains("Gadget");
  }

  @Test
  void shouldHandleNullValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("middleName", null);
    record.put("age", 42);

    String csv = serializer.serialize(record);

    // Null should be empty string
    assertThat(csv).isEqualTo("\"John\",\"\",\"42\"");
  }

  @Test
  void shouldHandleEmptyRecord() {
    Map<String, Object> record = Map.of();

    String header = serializer.serializeHeader(record);
    String csv = serializer.serialize(record);

    assertThat(header).isEmpty();
    assertThat(csv).isEmpty();
  }

  @Test
  void shouldPreserveFieldOrder() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("field1", "A");
    record.put("field2", "B");
    record.put("field3", "C");

    String header = serializer.serializeHeader(record);
    String csv = serializer.serialize(record);

    assertThat(header).isEqualTo("\"field1\",\"field2\",\"field3\"");
    assertThat(csv).isEqualTo("\"A\",\"B\",\"C\"");
  }

  @Test
  void shouldHandleBooleanValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("active", true);
    record.put("verified", false);

    String csv = serializer.serialize(record);

    assertThat(csv).isEqualTo("\"true\",\"false\"");
  }

  @Test
  void shouldReturnCorrectFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("csv");
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
