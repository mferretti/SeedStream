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

package com.datagenerator.formats.avro;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaRegistryAvroSerializerTest {

  @Mock private SchemaRegistryClient registryClient;

  private AvroSerializer avroSerializer;
  private SchemaRegistryAvroSerializer serializer;

  @BeforeEach
  void setUp() {
    avroSerializer = new AvroSerializer();
    serializer =
        new SchemaRegistryAvroSerializer(avroSerializer, registryClient, "test-topic-value");
  }

  private Map<String, Object> record(Object... keysAndValues) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      m.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return m;
  }

  // ── Wire format ───────────────────────────────────────────────────────────

  @Test
  void wireFormatStartsWithMagicByte() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(42);

    byte[] bytes = serializer.serializeToBytes(record("name", "Alice"));

    assertThat(bytes[0]).isEqualTo(SchemaRegistryAvroSerializer.MAGIC_BYTE);
  }

  @Test
  void wireFormatContainsSchemaIdInBytes1To4() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(7);

    byte[] bytes = serializer.serializeToBytes(record("name", "Alice"));

    ByteBuffer buf = ByteBuffer.wrap(bytes);
    buf.get(); // skip magic byte
    assertThat(buf.getInt()).isEqualTo(7);
  }

  @Test
  void wireFormatPayloadIsAvroBytes() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(1);

    byte[] bytes = serializer.serializeToBytes(record("name", "Alice"));

    assertThat(bytes.length).isGreaterThan(5); // magic + id + at least 1 avro byte
  }

  @Test
  void buildWireFormatStaticHelper() {
    byte[] payload = new byte[] {0x01, 0x02, 0x03};
    byte[] wire = SchemaRegistryAvroSerializer.buildWireFormat(99, payload);

    assertThat(wire).hasSize(8); // 1 + 4 + 3
    assertThat(wire[0]).isEqualTo((byte) 0x00);
    ByteBuffer buf = ByteBuffer.wrap(wire, 1, 4);
    assertThat(buf.getInt()).isEqualTo(99);
    assertThat(wire[5]).isEqualTo((byte) 0x01);
    assertThat(wire[6]).isEqualTo((byte) 0x02);
    assertThat(wire[7]).isEqualTo((byte) 0x03);
  }

  // ── Schema caching ────────────────────────────────────────────────────────

  @Test
  void schemaRegisteredOnlyOnFirstRecord() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(1);

    Map<String, Object> rec = record("x", "v");
    serializer.serializeToBytes(rec);
    serializer.serializeToBytes(rec);
    serializer.serializeToBytes(rec);

    verify(registryClient, times(1)).registerSchema(anyString(), anyString());
  }

  @Test
  void correctSubjectPassedToRegistry() {
    when(registryClient.registerSchema(eq("test-topic-value"), anyString())).thenReturn(5);

    serializer.serializeToBytes(record("key", "val"));

    verify(registryClient).registerSchema(eq("test-topic-value"), anyString());
  }

  @Test
  void schemaIdUsedConsistentlyAcrossRecords() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(123);

    byte[] first = serializer.serializeToBytes(record("a", "1"));
    byte[] second = serializer.serializeToBytes(record("a", "2"));

    assertThat(ByteBuffer.wrap(first, 1, 4).getInt()).isEqualTo(123);
    assertThat(ByteBuffer.wrap(second, 1, 4).getInt()).isEqualTo(123);
  }

  // ── serialize() returns Base64 ────────────────────────────────────────────

  @Test
  void serializeReturnsBase64OfWireFormat() {
    when(registryClient.registerSchema(anyString(), anyString())).thenReturn(3);

    String result = serializer.serialize(record("x", "y"));
    byte[] decoded = Base64.getDecoder().decode(result);

    assertThat(decoded[0]).isEqualTo(SchemaRegistryAvroSerializer.MAGIC_BYTE);
  }

  // ── Format name ───────────────────────────────────────────────────────────

  @Test
  void formatNameIsAvroRegistry() {
    assertThat(serializer.getFormatName()).isEqualTo("avro-registry");
  }

  // ── Error propagation ─────────────────────────────────────────────────────

  @Test
  void propagatesSchemaRegistryException() {
    when(registryClient.registerSchema(anyString(), anyString()))
        .thenThrow(new SchemaRegistryException("registry down"));

    assertThatThrownBy(() -> serializer.serializeToBytes(record("f", "v")))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("registry down");
  }
}
