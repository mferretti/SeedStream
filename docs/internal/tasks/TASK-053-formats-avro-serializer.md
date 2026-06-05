# TASK-053: Formats Module — Avro Serializer (Phase 1, no Schema Registry)

**Status:** 🔄 In Progress
**Priority:** P2
**Phase:** Phase 11 (Avro Support)
**Effort:** 4–6h
**Complexity:** Medium
**Dependencies:** TASK-013 (FormatSerializer interface)

---

## Goal

Add Apache Avro as an output format. Phase 1: dynamic schema inference from first record,
Base64-encoded binary output, no Schema Registry dependency. Plugs into the existing
`FormatSerializer` interface — zero changes to destinations or CLI structure.

---

## Acceptance Criteria

- [ ] `AvroSerializer` implements `FormatSerializer`
- [ ] `getFormatName()` returns `"avro"`
- [ ] Schema inferred from first record; subsequent records use same schema (thread-safe)
- [ ] Type mapping:
  - `String` → Avro `string`
  - `Integer` → Avro `int`
  - `Long` → Avro `long`
  - `Boolean` → Avro `boolean`
  - `Double`, `Float`, `BigDecimal` → Avro `double`
  - `LocalDate` → Avro `int` with `date` logical type (days since epoch)
  - `Instant` → Avro `long` with `timestamp-millis` logical type
  - `List` → Avro `array` of `string`
  - `Map` (nested object) → Avro `string` (JSON-encoded, same as Protobuf)
  - All fields nullable (`["null", <type>]` union, null default)
- [ ] Output: Base64-encoded binary (one line per record, same convention as Protobuf)
- [ ] `--format avro` wired into `ExecuteCommand`
- [ ] ≥ 12 unit tests passing (round-trip deserialization, type coverage, null safety)
- [ ] `./gradlew spotlessCheck` passes

---

## Implementation

### 1. Dependency (`gradle/libs.versions.toml` + `formats/build.gradle.kts`)

```toml
avro = "1.12.0"
avro-lib = { module = "org.apache.avro:avro", version.ref = "avro" }
```

```kotlin
implementation(libs.avro.lib)
```

### 2. `AvroSerializer`

Package: `com.datagenerator.formats.avro`

Key design:
- Double-checked locking for schema + `GenericDatumWriter` init
- `buildSchema(record)` iterates fields, calls `inferSchema(value)` per field
- Each field schema wrapped in `["null", type]` union with `NULL_DEFAULT_VALUE`
- `toGenericRecord(record)` populates a `GenericData.Record`
- `convertValue(value, unionSchema)` switches on `actual.getType()` (index 1 of union)
- Date: `(int) localDate.toEpochDay()`
- Timestamp: `instant.toEpochMilli()`
- Nested map: Jackson `ObjectMapper.writeValueAsString(map)`

### 3. CLI wiring (`ExecuteCommand.createSerializer`)

```java
case "avro" -> new AvroSerializer();
```

Update Javadoc and `@spec.arg` list.

---

## Testing

File: `formats/src/test/java/com/datagenerator/formats/avro/AvroSerializerTest.java`

Round-trip pattern: `serialize()` → Base64 decode → `GenericDatumReader` → assert field values.

Tests:
1. `shouldReturnCorrectFormatName`
2. `shouldSerializeSimpleStringRecord`
3. `shouldSerializeIntegerField`
4. `shouldSerializeLongField`
5. `shouldSerializeBooleanField`
6. `shouldSerializeBigDecimalAsDouble`
7. `shouldSerializeLocalDateAsDateLogicalType`
8. `shouldSerializeInstantAsTimestampMillis`
9. `shouldSerializeListAsArray`
10. `shouldSerializeNestedMapAsJsonString`
11. `shouldHandleNullValues`
12. `shouldProduceDeterministicOutputForSameInput`
13. `shouldSerializeMultipleRecordsWithSameSchema`
14. `shouldBeThreadSafe` (concurrent serialize from multiple threads)

---

## Notes

- `GenericDatumWriter` is thread-safe (stateless per-write); share the instance
- `BinaryEncoder` is NOT thread-safe; create one per `serialize()` call
- `ByteArrayOutputStream` needs no close (no external resources)
- Avro brings transitive Jackson deps — Gradle resolves to project version (2.22.0)
