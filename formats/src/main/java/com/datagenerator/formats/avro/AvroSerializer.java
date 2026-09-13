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
import com.datagenerator.formats.SerializerMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;

/**
 * Serializes generated records to Apache Avro binary format.
 *
 * <p>Schema is inferred dynamically from the first record and cached; all subsequent records use
 * the same schema. Output is Base64-encoded binary (one line per record), consistent with {@link
 * com.datagenerator.formats.protobuf.ProtobufSerializer}.
 *
 * <p><b>Type Mapping:</b>
 *
 * <ul>
 *   <li>String → Avro {@code string}
 *   <li>Integer → Avro {@code int}
 *   <li>Long → Avro {@code long}
 *   <li>Boolean → Avro {@code boolean}
 *   <li>Double, Float, BigDecimal → Avro {@code double}
 *   <li>LocalDate → Avro {@code int} with {@code date} logical type (days since epoch)
 *   <li>Instant → Avro {@code long} with {@code timestamp-millis} logical type
 *   <li>List → Avro {@code array} of {@code string}; object/Map elements are JSON-encoded (not
 *       {@link Object#toString()})
 *   <li>Map (nested object) → Avro {@code string} (JSON-encoded)
 * </ul>
 *
 * <p>All fields are nullable ({@code ["null", type]} union with null default).
 *
 * <p><b>Null-first fields:</b> the schema is inferred from the first record only. If a field's
 * first value is {@code null}, its true type is unknown at schema-build time; rather than locking
 * the field to {@code STRING} forever (which would silently stringify later non-null numeric values
 * via {@code toString()}), such fields get a widened union of {@code [null, long, double, boolean,
 * string]}. The value's runtime type is resolved per-record to pick the matching branch. This loses
 * date/timestamp logical-type precision for fields whose type is only revealed later as {@link
 * LocalDate}/{@link Instant} (they fall back to {@code string}/{@code long} respectively), but
 * never corrupts a numeric/boolean value into a string.
 *
 * <p><b>Thread Safety:</b> Thread-safe. Schema and writer are initialized once via double-checked
 * locking. {@code GenericDatumWriter} is stateless per write and safe to share across threads.
 */
@Slf4j
public class AvroSerializer implements FormatSerializer {

  private static final ObjectMapper JSON_MAPPER = SerializerMapper.INSTANCE;

  // Schema and datumWriter are populated once via double-checked locking and treated
  // as immutable after publication; volatile guarantees safe publication of the fully
  // initialised objects to other threads. They are never mutated post-init.
  @SuppressWarnings("java:S3077")
  private volatile Schema schema;

  @SuppressWarnings("java:S3077")
  private volatile GenericDatumWriter<GenericRecord> datumWriter;

  private final Object initLock = new Object();

  // Reused across records on the same worker thread for throughput; never remove()d because the
  // reuse is the point and generation worker threads are short-lived (terminate at job end, like
  // FakerCache). Holds no per-record state beyond the bound stream.
  @SuppressWarnings("java:S5164")
  private static final ThreadLocal<BinaryEncoder> ENCODER = new ThreadLocal<>();

  @Override
  public String serialize(Map<String, Object> data) {
    ensureInitialized(data);
    try {
      GenericRecord avroRecord = buildGenericRecord(data);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, ENCODER.get());
      ENCODER.set(encoder);
      datumWriter.write(avroRecord, encoder);
      encoder.flush();
      return Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (IOException e) {
      throw new SerializationException("Avro serialization failed", e);
    }
  }

  /**
   * Initializes schema from the given record if not already initialized. Safe to call multiple
   * times; subsequent calls are no-ops.
   */
  public void ensureInitialized(Map<String, Object> data) {
    if (schema == null) {
      synchronized (initLock) {
        if (schema == null) {
          schema = buildSchema(data);
          datumWriter = new GenericDatumWriter<>(schema);
          log.debug("Avro schema initialized with {} fields", data.size());
        }
      }
    }
  }

  @Override
  public String getFormatName() {
    return "avro";
  }

  /**
   * Returns the Avro schema inferred from the first serialized record, or {@code null} if no record
   * has been serialized yet.
   */
  public Schema getSchema() {
    return schema;
  }

  private Schema buildSchema(Map<String, Object> data) {
    List<Schema.Field> fields = new ArrayList<>();
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String fieldName = sanitizeFieldName(entry.getKey());
      Schema nullable = buildFieldSchema(entry.getValue());
      fields.add(new Schema.Field(fieldName, nullable, null, Schema.Field.NULL_DEFAULT_VALUE));
    }
    return Schema.createRecord("Record", null, "com.datagenerator", false, fields);
  }

  /**
   * Builds the nullable union schema for one field from its first-record value.
   *
   * <p>When {@code firstValue} is {@code null} we have no runtime type to infer from (issue #285).
   * Locking such a field to {@code STRING} would silently stringify later non-null
   * Integer/Long/Instant/etc. values for the whole run, corrupting both their Avro type and their
   * encoded representation. Instead we widen the union to the common scalar kinds so the true value
   * can be encoded natively once it appears; {@link #resolveBranch} picks the matching branch per
   * record.
   */
  private Schema buildFieldSchema(Object firstValue) {
    Schema nullSchema = Schema.create(Schema.Type.NULL);
    if (firstValue == null) {
      return Schema.createUnion(
          nullSchema,
          Schema.create(Schema.Type.LONG),
          Schema.create(Schema.Type.DOUBLE),
          Schema.create(Schema.Type.BOOLEAN),
          Schema.create(Schema.Type.STRING));
    }
    return Schema.createUnion(nullSchema, inferSchema(firstValue));
  }

  private static String sanitizeFieldName(String name) {
    if (name == null || name.isEmpty()) return "_field";
    String sanitized = name.replaceAll("\\W", "_");
    if (sanitized.isEmpty()) sanitized = "_field";
    return Character.isDigit(sanitized.charAt(0)) ? "_" + sanitized : sanitized;
  }

  /**
   * Infers the Avro type for a known, non-null value. Callers must not pass {@code null}; a null
   * first-record value is handled separately by {@link #buildFieldSchema} since its true type is
   * unknown (see class javadoc, issue #285).
   */
  private Schema inferSchema(Object value) {
    if (value instanceof String) return Schema.create(Schema.Type.STRING);
    if (value instanceof Long) return Schema.create(Schema.Type.LONG);
    if (value instanceof Integer) return Schema.create(Schema.Type.INT);
    if (value instanceof Boolean) return Schema.create(Schema.Type.BOOLEAN);
    if (value instanceof Double || value instanceof Float) return Schema.create(Schema.Type.DOUBLE);
    if (value instanceof BigDecimal) return Schema.create(Schema.Type.DOUBLE);
    if (value instanceof LocalDate) {
      return LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
    }
    if (value instanceof Instant) {
      return LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
    }
    if (value instanceof List) {
      return Schema.createArray(Schema.create(Schema.Type.STRING));
    }
    // Map (nested object) → JSON string
    return Schema.create(Schema.Type.STRING);
  }

  /**
   * Converts a record map to a {@link GenericRecord} using the cached schema. Schema must be
   * initialized via {@link #ensureInitialized} or {@link #serialize} before calling.
   *
   * @throws IllegalStateException if called before schema initialization
   */
  public GenericRecord buildGenericRecord(Map<String, Object> data) {
    if (schema == null) {
      throw new IllegalStateException(
          "Avro schema not initialized; call ensureInitialized() or serialize() first");
    }
    GenericRecord avroRecord = new GenericData.Record(schema);
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String fieldName = sanitizeFieldName(entry.getKey());
      Schema.Field field = schema.getField(fieldName);
      if (field != null) {
        avroRecord.put(fieldName, convertValue(entry.getValue(), field.schema()));
      }
    }
    return avroRecord;
  }

  /**
   * Converts a Java value to its Avro representation. The field schema is a {@code ["null", ...]}
   * union; for ordinary fields the non-null type is fixed at index 1, but a field whose first
   * record value was {@code null} carries a widened multi-branch union (see {@link
   * #buildFieldSchema}), so the matching branch is resolved per-value via {@link #resolveBranch}.
   */
  private Object convertValue(Object value, Schema unionSchema) {
    if (value == null) return null;
    Schema actual = resolveBranch(value, unionSchema);
    return switch (actual.getType()) {
      case INT ->
          value instanceof LocalDate ld ? (int) ld.toEpochDay() : ((Number) value).intValue();
      case LONG ->
          value instanceof Instant inst ? inst.toEpochMilli() : ((Number) value).longValue();
      case DOUBLE ->
          value instanceof BigDecimal bd ? bd.doubleValue() : ((Number) value).doubleValue();
      case BOOLEAN -> value;
      case STRING -> convertToString(value);
      case ARRAY -> convertToAvroArray(value, actual);
      default -> value.toString();
    };
  }

  /**
   * Picks the union branch matching {@code value}'s runtime type. Ordinary fields have a fixed
   * two-branch {@code [null, type]} union, so the non-null branch at index 1 is always correct.
   * Null-first fields (issue #285) carry a widened union; the branch is chosen by the value's Java
   * type so it is encoded natively instead of falling through to {@code STRING}.
   */
  private Schema resolveBranch(Object value, Schema unionSchema) {
    List<Schema> branches = unionSchema.getTypes();
    if (branches.size() == 2) {
      return branches.get(1);
    }
    Schema.Type target = resolvePolymorphicType(value);
    for (Schema branch : branches) {
      if (branch.getType() == target) {
        return branch;
      }
    }
    // STRING is always present as the catch-all branch built in buildFieldSchema.
    return branches.get(branches.size() - 1);
  }

  private Schema.Type resolvePolymorphicType(Object value) {
    if (value instanceof Long || value instanceof Integer || value instanceof Instant) {
      return Schema.Type.LONG;
    }
    if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
      return Schema.Type.DOUBLE;
    }
    if (value instanceof Boolean) {
      return Schema.Type.BOOLEAN;
    }
    return Schema.Type.STRING;
  }

  private String convertToString(Object value) {
    if (value instanceof Map<?, ?> || value instanceof List<?>) {
      try {
        return JSON_MAPPER.writeValueAsString(value);
      } catch (JsonProcessingException e) {
        return value.toString();
      }
    }
    return value.toString();
  }

  private GenericData.Array<String> convertToAvroArray(Object value, Schema arraySchema) {
    if (!(value instanceof List<?> list)) {
      return new GenericData.Array<>(0, arraySchema);
    }
    GenericData.Array<String> avroArray = new GenericData.Array<>(list.size(), arraySchema);
    for (Object item : list) {
      // issue #284: object/Map elements must be JSON-encoded via convertToString (same mechanism
      // as top-level Map fields), not Object#toString(), which would emit non-parseable Java map
      // syntax like "{qty=3, sku=ABC}".
      avroArray.add(item == null ? null : convertToString(item));
    }
    return avroArray;
  }
}
