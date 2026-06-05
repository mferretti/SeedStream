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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for {@link SchemaRegistryAvroSerializer} against a real Confluent Schema
 * Registry.
 *
 * <p>Verifies that the Confluent wire format is produced correctly: magic byte, 4-byte schema ID,
 * and valid Avro binary payload.
 *
 * <p>Requires Docker. Run with: {@code ./gradlew :formats:integrationTest}
 */
@Tag("integration")
@Tag("slow")
@Testcontainers
class SchemaRegistryAvroSerializerIT {

  private static final Network NETWORK = Network.newNetwork();

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
          .withNetwork(NETWORK)
          .withNetworkAliases("kafka");

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> schemaRegistry =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
          .withNetwork(NETWORK)
          .dependsOn(kafka)
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withExposedPorts(8081)
          .waitingFor(new HttpWaitStrategy().forPath("/subjects").forPort(8081));

  private String registryUrl;
  private SchemaRegistryAvroSerializer serializer;
  private AvroSerializer avroSerializer;

  @BeforeEach
  void setUp() {
    registryUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    avroSerializer = new AvroSerializer();
    HttpSchemaRegistryClient client =
        new HttpSchemaRegistryClient(registryUrl, (String) null, (String) null);
    serializer = new SchemaRegistryAvroSerializer(avroSerializer, client, "orders-value");
  }

  @Test
  void producesConfluentWireFormatWithValidSchemaId() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("orderId", "ORD-001");
    record.put("amount", 49.99);
    record.put("quantity", 3);

    byte[] bytes = serializer.serializeToBytes(record);

    // magic byte
    assertThat(bytes[0]).isEqualTo(SchemaRegistryAvroSerializer.MAGIC_BYTE);

    // schema ID must be a positive integer assigned by the registry
    int schemaId = ByteBuffer.wrap(bytes, 1, 4).getInt();
    assertThat(schemaId).isGreaterThan(0);
  }

  @Test
  void avroPayloadDecodesToExpectedValues() throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", "Alice");
    record.put("score", 42);

    byte[] wireBytes = serializer.serializeToBytes(record);

    // strip 5-byte header, decode remaining as Avro binary
    byte[] avroPayload = new byte[wireBytes.length - 5];
    System.arraycopy(wireBytes, 5, avroPayload, 0, avroPayload.length);

    Schema schema = avroSerializer.getSchema();
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(avroPayload, null);
    GenericRecord decoded = reader.read(null, decoder);

    assertThat(decoded.get("name").toString()).isEqualTo("Alice");
    assertThat(decoded.get("score")).isEqualTo(42);
  }

  @Test
  void differentSchemasGetDifferentIds() {
    HttpSchemaRegistryClient client =
        new HttpSchemaRegistryClient(registryUrl, (String) null, (String) null);

    SchemaRegistryAvroSerializer serializerA =
        new SchemaRegistryAvroSerializer(new AvroSerializer(), client, "schema-a-value");
    SchemaRegistryAvroSerializer serializerB =
        new SchemaRegistryAvroSerializer(new AvroSerializer(), client, "schema-b-value");

    Map<String, Object> recA = new LinkedHashMap<>();
    recA.put("fieldA", "value");
    Map<String, Object> recB = new LinkedHashMap<>();
    recB.put("fieldB", 99);

    byte[] bytesA = serializerA.serializeToBytes(recA);
    byte[] bytesB = serializerB.serializeToBytes(recB);

    int idA = ByteBuffer.wrap(bytesA, 1, 4).getInt();
    int idB = ByteBuffer.wrap(bytesB, 1, 4).getInt();

    assertThat(idA).isGreaterThan(0);
    assertThat(idB).isGreaterThan(0);
    assertThat(idA).isNotEqualTo(idB);
  }
}
