# TASK-054: Formats Module — Avro + Confluent Schema Registry (Phase 2)

**Status:** ⏸️ Deferred (implement after TASK-053 complete)
**Priority:** P2
**Phase:** Phase 11 (Avro Support)
**Effort:** 8–12h
**Complexity:** High
**Dependencies:** TASK-053

---

## Goal

Integrate Confluent Schema Registry with the Avro serializer. Enables Kafka pipelines that use
Schema Registry for schema evolution and consumer compatibility.

---

## Acceptance Criteria

- [ ] `SchemaRegistryAvroSerializer` wraps `AvroSerializer`, registers schema on first use
- [ ] Job config accepts `schema_registry_url` in `conf` block
- [ ] Output uses Confluent wire format: `[0x00][4-byte schema ID][avro payload]` (single-object encoding)
- [ ] Schema ID cached after first registration (no re-registration per record)
- [ ] IT test: Testcontainers `cp-schema-registry` + `cp-kafka` containers
  - Produce Avro records → Kafka topic
  - Consume with Confluent Avro deserializer → verify field values
- [ ] `--format avro-registry` wired into `ExecuteCommand`
- [ ] Auth support: bearer token + basic auth for private Schema Registry instances

---

## Design Notes

### Confluent Wire Format
```
byte 0:    magic byte (0x00)
bytes 1-4: schema ID (big-endian int)
bytes 5+:  Avro binary payload (same as TASK-053 output, without Base64)
```

### Job Config
```yaml
conf:
  schema_registry_url: http://localhost:8081
  schema_registry_subject: my-topic-value   # optional, defaults to topic name + "-value"
  # Auth (optional):
  schema_registry_auth: bearer
  schema_registry_token: ${REGISTRY_TOKEN}
```

### Dependencies
```toml
confluent-avro-serializer = { module = "io.confluent:kafka-avro-serializer", version = "7.6.0" }
```
Or use the low-level `io.confluent:kafka-schema-registry-client` to avoid pulling the full
serializer stack.

### Testcontainers Setup
```java
@Container
static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

@Container
static GenericContainer<?> registry = new GenericContainer<>("confluentinc/cp-schema-registry:7.5.0")
    .dependsOn(kafka)
    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", kafka.getBootstrapServers())
    ...
```

Note: `cp-schema-registry` image is ~500MB. IT test will be slow — mark with `@Tag("slow")` and
exclude from default `integrationTest` run unless `--slow` flag passed.

---

## Out of Scope
- Avro schema evolution / compatibility checking
- Multiple subjects per job
- Protobuf or JSON Schema Registry (Avro only for this task)
