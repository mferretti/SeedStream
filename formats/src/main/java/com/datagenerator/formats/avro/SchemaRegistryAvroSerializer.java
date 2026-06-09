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

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;

/**
 * Serializes records to the Confluent Avro wire format for use with Schema Registry-aware Kafka
 * consumers.
 *
 * <p><b>Wire format (single-object encoding):</b>
 *
 * <pre>
 * byte 0:    magic byte (0x00)
 * bytes 1-4: schema ID (big-endian int)
 * bytes 5+:  Avro binary payload
 * </pre>
 *
 * <p><b>Schema lifecycle:</b> The Avro schema is inferred from the first record and registered with
 * the Schema Registry. The schema ID is cached; subsequent records reuse it without
 * re-registration.
 *
 * <p><b>Thread Safety:</b> Thread-safe. Schema and schema ID are initialized under double-checked
 * locking.
 *
 * <p><b>{@link #serialize}:</b> Returns Base64-encoded wire format bytes (suitable for file/console
 * output). {@link #serializeToBytes} returns raw bytes (for Kafka production).
 */
@Slf4j
public final class SchemaRegistryAvroSerializer implements FormatSerializer {

  static final byte MAGIC_BYTE = 0x00;

  private final AvroSerializer avroSerializer;
  private final SchemaRegistryClient registryClient;
  private final String subject;

  // schemaId and datumWriter are populated once via double-checked locking and treated
  // as immutable after publication; volatile guarantees safe publication. They are never
  // mutated post-init.
  @SuppressWarnings("java:S3077")
  private volatile Integer schemaId;

  @SuppressWarnings("java:S3077")
  private volatile GenericDatumWriter<GenericRecord> datumWriter;

  private final Object initLock = new Object();

  /**
   * @param avroSerializer the base Avro serializer (schema inference and record building)
   * @param registryClient Schema Registry client for schema registration
   * @param subject Schema Registry subject name (e.g. {@code my-topic-value})
   */
  public SchemaRegistryAvroSerializer(
      AvroSerializer avroSer, SchemaRegistryClient regClient, String subjectName) {
    this.avroSerializer = avroSer;
    this.registryClient = regClient;
    this.subject = subjectName;
  }

  /**
   * Convenience constructor. Creates an {@link HttpSchemaRegistryClient} from the given URL and
   * optional auth.
   *
   * @param registryUrl Schema Registry base URL (e.g. {@code http://localhost:8081})
   * @param subject subject name
   * @param authType {@code "bearer"}, {@code "basic"}, or {@code null}
   * @param token auth credential (bearer token or {@code user:password} for basic)
   */
  public SchemaRegistryAvroSerializer(
      String registryUrl, String subjectName, String authType, String token) {
    this(
        new AvroSerializer(),
        new HttpSchemaRegistryClient(registryUrl, authType, token),
        subjectName);
  }

  @Override
  public String serialize(Map<String, Object> data) {
    return Base64.getEncoder().encodeToString(serializeToBytes(data));
  }

  @Override
  public byte[] serializeToBytes(Map<String, Object> data) {
    ensureSchemaRegistered(data);
    byte[] avroPayload = buildAvroPayload(data);
    return buildWireFormat(schemaId, avroPayload);
  }

  @Override
  public String getFormatName() {
    return "avro-registry";
  }

  @SuppressWarnings("java:S3064")
  private void ensureSchemaRegistered(Map<String, Object> data) {
    if (schemaId == null) {
      synchronized (initLock) {
        if (schemaId == null) {
          avroSerializer.ensureInitialized(data);
          String schemaJson = avroSerializer.getSchema().toString();
          int registeredId = registryClient.registerSchema(subject, schemaJson);
          datumWriter = new GenericDatumWriter<>(avroSerializer.getSchema());
          schemaId = registeredId;
          log.debug("Schema registered for subject '{}', id={}", subject, schemaId);
        }
      }
    }
  }

  private byte[] buildAvroPayload(Map<String, Object> data) {
    try {
      GenericRecord avroRecord = avroSerializer.buildGenericRecord(data);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
      datumWriter.write(avroRecord, encoder);
      encoder.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new SerializationException("Avro payload serialization failed", e);
    }
  }

  static byte[] buildWireFormat(int schemaId, byte[] avroPayload) {
    ByteBuffer buf = ByteBuffer.allocate(5 + avroPayload.length);
    buf.put(MAGIC_BYTE);
    buf.putInt(schemaId);
    buf.put(avroPayload);
    return buf.array();
  }
}
