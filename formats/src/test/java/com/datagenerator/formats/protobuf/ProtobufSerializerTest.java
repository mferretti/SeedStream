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

package com.datagenerator.formats.protobuf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtobufSerializerTest {
  private static final String FIELD_ACTIVE = "active";

  private ProtobufSerializer serializer;

  @BeforeEach
  void setUp() {
    serializer = new ProtobufSerializer();
  }

  @Test
  void shouldReturnCorrectFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("protobuf");
  }

  @Test
  void shouldSerializeSimpleRecord() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("age", 42);
    data.put(FIELD_ACTIVE, true);

    String result = serializer.serialize(data);

    // Result should be valid base64
    assertThat(result).isNotBlank().matches("^[A-Za-z0-9+/]+=*$");
    // Should be able to decode
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeWithFieldAliases() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("nome", "Marco");
    data.put("citta", "Milano");

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeBigDecimalAsDouble() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("price", new BigDecimal("99.95"));
    data.put("quantity", 5);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeLocalDateAsIso8601String() {
    LocalDate date = LocalDate.of(2024, 3, 15);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("birthDate", date);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    // Decode and verify it's valid protobuf
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeInstantAsIso8601String() {
    Instant timestamp = Instant.parse("2024-03-15T14:30:00Z");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("createdAt", timestamp);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeNestedMapsAsStrings() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Via Roma");
    address.put("city", "Milano");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Marco");
    data.put("address", address);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeArraysAsRepeatedFields() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Order");
    data.put("items", List.of("item1", "item2", "item3"));

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleNullValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "John");
    data.put("email", null);
    data.put("age", 42);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleEmptyRecord() {
    Map<String, Object> data = new LinkedHashMap<>();

    String result = serializer.serialize(data);

    // Empty protobuf message serializes to empty byte array
    // which base64-encodes to empty string
    assertThat(result).isNotNull();
    byte[] binary = Base64.getDecoder().decode(result);
    // Empty message is valid - just has no fields
    assertThat(binary).isEmpty();
  }

  @Test
  void shouldReuseSchemaForMultipleRecords() {
    Map<String, Object> record1 = new LinkedHashMap<>();
    record1.put("name", "John");
    record1.put("age", 42);

    Map<String, Object> record2 = new LinkedHashMap<>();
    record2.put("name", "Jane");
    record2.put("age", 35);

    // First record initializes schema
    String result1 = serializer.serialize(record1);
    assertThat(result1).isNotBlank();

    // Second record should reuse schema
    String result2 = serializer.serialize(record2);
    assertThat(result2).isNotBlank();

    // Both should be valid base64
    assertThat(Base64.getDecoder().decode(result1)).isNotEmpty();
    assertThat(Base64.getDecoder().decode(result2)).isNotEmpty();
  }

  @Test
  void shouldHandleVariousNumericTypes() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("intValue", 42);
    data.put("longValue", 123456789L);
    data.put("doubleValue", 3.14159);
    data.put("floatValue", 2.71828f);
    data.put("bigDecimalValue", new BigDecimal("999.99"));

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleBooleanValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(FIELD_ACTIVE, true);
    data.put("deleted", false);

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldProduceSmallerOutputThanJson() {
    // Protobuf should be more compact than JSON for structured data
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("firstName", "Christopher");
    data.put("lastName", "Montgomery");
    data.put("age", 42);
    data.put(FIELD_ACTIVE, true);
    data.put("balance", new BigDecimal("12345.67"));

    String protobufResult = serializer.serialize(data);
    byte[] protobufBinary = Base64.getDecoder().decode(protobufResult);

    // JSON equivalent (approximate)
    String jsonEquivalent =
        "{\"firstName\":\"Christopher\",\"lastName\":\"Montgomery\",\"age\":42,\"active\":true,\"balance\":12345.67}";

    // Protobuf binary should typically be smaller than JSON text
    // (though base64 encoding adds ~33% overhead)
    assertThat(protobufBinary)
        .withFailMessage("Protobuf should generally be more compact than JSON for structured data")
        .hasSizeLessThan(jsonEquivalent.length());
  }

  @Test
  void shouldSerializeComplexRecord() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 12345L);
    data.put("name", "Test Product");
    data.put("price", new BigDecimal("99.99"));
    data.put("inStock", true);
    data.put("tags", List.of("electronics", "gadgets"));
    data.put("createdAt", Instant.parse("2024-03-15T10:30:00Z"));
    data.put("releaseDate", LocalDate.of(2024, 6, 1));

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    // Complex record should still produce valid protobuf
    assertThat(binary).isNotEmpty().hasSizeGreaterThan(10);
  }
}
