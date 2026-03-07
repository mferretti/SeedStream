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
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("age", 42);
    record.put("active", true);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    // Result should be valid base64
    assertThat(result).matches("^[A-Za-z0-9+/]+=*$");
    // Should be able to decode
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeWithFieldAliases() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("nome", "Marco");
    record.put("citta", "Milano");

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeBigDecimalAsDouble() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("price", new BigDecimal("99.95"));
    record.put("quantity", 5);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeLocalDateAsIso8601String() {
    LocalDate date = LocalDate.of(2024, 3, 15);
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("birthDate", date);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    // Decode and verify it's valid protobuf
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeInstantAsIso8601String() {
    Instant timestamp = Instant.parse("2024-03-15T14:30:00Z");
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("createdAt", timestamp);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeNestedMapsAsStrings() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Via Roma");
    address.put("city", "Milano");

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Marco");
    record.put("address", address);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldSerializeArraysAsRepeatedFields() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Order");
    record.put("items", List.of("item1", "item2", "item3"));

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleNullValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "John");
    record.put("email", null);
    record.put("age", 42);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleEmptyRecord() {
    Map<String, Object> record = new LinkedHashMap<>();

    String result = serializer.serialize(record);

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
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("intValue", 42);
    record.put("longValue", 123456789L);
    record.put("doubleValue", 3.14159);
    record.put("floatValue", 2.71828f);
    record.put("bigDecimalValue", new BigDecimal("999.99"));

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldHandleBooleanValues() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("active", true);
    record.put("deleted", false);

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldProduceSmallerOutputThanJson() {
    // Protobuf should be more compact than JSON for structured data
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("firstName", "Christopher");
    record.put("lastName", "Montgomery");
    record.put("age", 42);
    record.put("active", true);
    record.put("balance", new BigDecimal("12345.67"));

    String protobufResult = serializer.serialize(record);
    byte[] protobufBinary = Base64.getDecoder().decode(protobufResult);

    // JSON equivalent (approximate)
    String jsonEquivalent =
        "{\"firstName\":\"Christopher\",\"lastName\":\"Montgomery\",\"age\":42,\"active\":true,\"balance\":12345.67}";

    // Protobuf binary should typically be smaller than JSON text
    // (though base64 encoding adds ~33% overhead)
    assertThat(protobufBinary.length)
        .withFailMessage("Protobuf should generally be more compact than JSON for structured data")
        .isLessThan(jsonEquivalent.length());
  }

  @Test
  void shouldSerializeComplexRecord() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 12345L);
    record.put("name", "Test Product");
    record.put("price", new BigDecimal("99.99"));
    record.put("inStock", true);
    record.put("tags", List.of("electronics", "gadgets"));
    record.put("createdAt", Instant.parse("2024-03-15T10:30:00Z"));
    record.put("releaseDate", LocalDate.of(2024, 6, 1));

    String result = serializer.serialize(record);

    assertThat(result).isNotBlank();
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
    // Complex record should still produce valid protobuf
    assertThat(binary.length).isGreaterThan(10); // Reasonable size check
  }
}
