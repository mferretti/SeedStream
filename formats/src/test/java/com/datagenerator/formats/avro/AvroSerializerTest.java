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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AvroSerializerTest {

  private static final String ALICE = "Alice";

  private AvroSerializer serializer;

  @BeforeEach
  void setUp() {
    serializer = new AvroSerializer();
  }

  @Test
  void shouldReturnCorrectFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("avro");
  }

  @Test
  void shouldSerializeSimpleStringRecord() {
    Map<String, Object> data = Map.of("name", ALICE, "city", "Rome");

    String result = serializer.serialize(data);

    assertThat(result).isNotBlank().matches("^[A-Za-z0-9+/]+=*$");
    byte[] binary = Base64.getDecoder().decode(result);
    assertThat(binary).isNotEmpty();
  }

  @Test
  void shouldRoundTripStringField() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", ALICE);

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("name")).hasToString(ALICE);
  }

  @Test
  void shouldRoundTripIntegerField() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("count", 42);

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("count")).isEqualTo(42);
  }

  @Test
  void shouldRoundTripLongField() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 9_999_999_999L);

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("id")).isEqualTo(9_999_999_999L);
  }

  @Test
  void shouldRoundTripBooleanField() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("active", true);

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("active")).isEqualTo(true);
  }

  @Test
  void shouldRoundTripBigDecimalAsDouble() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("price", new BigDecimal("99.95"));

    GenericRecord decoded = roundTrip(data);

    assertThat((double) decoded.get("price"))
        .isCloseTo(99.95, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void shouldRoundTripLocalDateAsDateLogicalType() throws Exception {
    LocalDate date = LocalDate.of(2024, Month.MARCH, 15);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dob", date);

    GenericRecord decoded = roundTrip(data);

    // date logical type stored as int (days since epoch)
    int daysSinceEpoch = (int) decoded.get("dob");
    assertThat(daysSinceEpoch).isEqualTo((int) date.toEpochDay());
  }

  @Test
  void shouldRoundTripInstantAsTimestampMillis() throws Exception {
    Instant ts = Instant.ofEpochMilli(1_700_000_000_000L);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("created_at", ts);

    GenericRecord decoded = roundTrip(data);

    long millis = (long) decoded.get("created_at");
    assertThat(millis).isEqualTo(ts.toEpochMilli());
  }

  @Test
  void shouldSerializeListAsAvroArray() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tags", List.of("a", "b", "c"));

    GenericRecord decoded = roundTrip(data);

    Object tagsObj = decoded.get("tags");
    assertThat(tagsObj).isNotNull();
    assertThat(tagsObj.toString()).contains("a").contains("b").contains("c");
  }

  @Test
  void shouldSerializeNestedMapAsJsonString() throws Exception {
    Map<String, Object> nested = Map.of("street", "Via Roma", "number", 1);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("address", nested);

    GenericRecord decoded = roundTrip(data);

    String addressStr = decoded.get("address").toString();
    assertThat(addressStr).contains("Via Roma");
  }

  @Test
  void shouldHandleNullValues() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Bob");
    data.put("middle_name", null);

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("name")).hasToString("Bob");
    assertThat(decoded.get("middle_name")).isNull();
  }

  @Test
  void shouldSanitizeInvalidFieldNames() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("first-name", ALICE);
    data.put("123id", "XYZ");
    data.put("valid_field", "ok");

    GenericRecord decoded = roundTrip(data);

    assertThat(decoded.get("first_name")).hasToString(ALICE);
    assertThat(decoded.get("_123id")).hasToString("XYZ");
    assertThat(decoded.get("valid_field")).hasToString("ok");
  }

  @Test
  void shouldProduceDeterministicOutputForSameInput() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 1);
    data.put("name", ALICE);

    String first = serializer.serialize(data);
    String second = serializer.serialize(data);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldRoundTripMultipleRecordsPreservingFieldValues() throws Exception {
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("name", ALICE);
    r1.put("age", 30);

    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("name", "Bob");
    r2.put("age", 25);

    Map<String, Object> r3 = new LinkedHashMap<>();
    r3.put("name", "Carol");
    r3.put("age", 42);

    List<GenericRecord> results = roundTripAll(List.of(r1, r2, r3));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).get("name")).hasToString(ALICE);
    assertThat(results.get(0).get("age")).isEqualTo(30);
    assertThat(results.get(1).get("name")).hasToString("Bob");
    assertThat(results.get(1).get("age")).isEqualTo(25);
    assertThat(results.get(2).get("name")).hasToString("Carol");
    assertThat(results.get(2).get("age")).isEqualTo(42);
  }

  @Test
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void shouldBeThreadSafe() throws InterruptedException {
    int threads = 8;
    int recordsPerThread = 100;
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger errors = new AtomicInteger();

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      pool.submit(
          () -> {
            try {
              start.await();
              for (int i = 0; i < recordsPerThread; i++) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("thread", threadId);
                data.put("seq", i);
                data.put("name", "worker-" + threadId);
                String result = serializer.serialize(data);
                if (result == null || result.isBlank()) {
                  errors.incrementAndGet();
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              errors.incrementAndGet();
            } catch (Exception e) {
              errors.incrementAndGet();
            }
          });
    }

    start.countDown();
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);

    assertThat(errors.get()).isZero();
  }

  @Test
  void shouldSerializeObjectArrayElementsAsParseableJson() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("line_items", List.of(Map.of("sku", "ABC", "qty", 3), Map.of("sku", "XYZ", "qty", 1)));

    GenericRecord decoded = roundTrip(data);

    List<?> items = (List<?>) decoded.get("line_items");
    assertThat(items).hasSize(2);
    ObjectMapper mapper = new ObjectMapper();
    for (Object item : items) {
      // issue #284: elements must be parseable JSON, not Java Map#toString() syntax such as
      // "{qty=3, sku=ABC}" (which is not valid JSON and would fail to parse below).
      JsonNode node = mapper.readTree(item.toString());
      assertThat(node.has("sku")).isTrue();
      assertThat(node.has("qty")).isTrue();
    }
    assertThat(items.get(0).toString()).doesNotContain("=").contains("\"sku\"");
  }

  @Test
  void shouldNotStringifyNumericFieldThatWasNullInFirstRecord() throws Exception {
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("score", null);
    r1.put("name", ALICE);

    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("score", 42);
    r2.put("name", "Bob");

    GenericRecord decoded1 = roundTrip(r1);
    assertThat(decoded1.get("score")).isNull();

    GenericRecord decoded2 = roundTrip(r2);
    // issue #285: a field that was null in the schema-defining first record must not lock to
    // STRING forever; a later Integer value must be preserved as a numeric Avro type (here Long,
    // per the widened null-first union), not silently stringified via toString().
    assertThat(decoded2.get("score")).isInstanceOf(Long.class).isEqualTo(42L);
  }

  @Test
  void shouldThrowSerializationExceptionWhenLaterRecordHasIncompatibleType() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("age", 30);
    serializer.serialize(first);

    Map<String, Object> drifted = new LinkedHashMap<>();
    drifted.put("age", "thirty");

    assertThatThrownBy(() -> serializer.serialize(drifted))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("age");
  }

  @Test
  void shouldRoundTripNullElementsWhenListContainsNulls() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tags", Arrays.asList("a", null, "c"));

    GenericRecord decoded = roundTrip(data);

    List<?> tags = (List<?>) decoded.get("tags");
    assertThat(tags).hasSize(3);
    assertThat(tags.get(0)).hasToString("a");
    assertThat(tags.get(1)).isNull();
    assertThat(tags.get(2)).hasToString("c");
  }

  @Test
  void shouldRoundTripNullElementsWhenFirstRecordListHadNone() throws Exception {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("tags", List.of("x"));
    roundTrip(first);

    Map<String, Object> second = new LinkedHashMap<>();
    second.put("tags", Arrays.asList(null, "y"));

    List<?> tags = (List<?>) roundTrip(second).get("tags");
    assertThat(tags.get(0)).isNull();
    assertThat(tags.get(1)).hasToString("y");
  }

  static Stream<Arguments> incompatibleLaterValues() {
    return Stream.of(
        Arguments.of(30, "thirty"), // INT
        Arguments.of(30L, "thirty"), // LONG
        Arguments.of(1.5d, "one and a half"), // DOUBLE
        Arguments.of(new BigDecimal("1.50"), "x"), // DECIMAL -> DOUBLE
        Arguments.of(true, "yes"), // BOOLEAN
        Arguments.of(LocalDate.of(2024, Month.JANUARY, 1), "2024-01-01"), // date
        Arguments.of(Instant.EPOCH, "1970-01-01T00:00:00Z")); // timestamp-millis
  }

  @ParameterizedTest
  @MethodSource("incompatibleLaterValues")
  void shouldThrowSerializationExceptionNamingFieldWhenLaterValueDoesNotMatchSchema(
      Object first, Object drifted) {
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("field", first);
    serializer.serialize(r1);

    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("field", drifted);

    assertThatThrownBy(() -> serializer.serialize(r2))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("'field'")
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldKeepSerializingWhenPreviousRecordFailedOnTypeDrift() throws Exception {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("age", 30);
    roundTrip(first);
    Map<String, Object> drifted = new LinkedHashMap<>();
    drifted.put("age", "thirty");
    assertThatThrownBy(() -> serializer.serialize(drifted))
        .isInstanceOf(SerializationException.class);

    Map<String, Object> next = new LinkedHashMap<>();
    next.put("age", 31);
    assertThat(roundTrip(next).get("age")).isEqualTo(31);
  }

  @Test
  void shouldRoundTripEmptyListAsEmptyArray() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tags", List.of());

    assertThat((List<?>) roundTrip(data).get("tags")).isEmpty();
  }

  @Test
  void shouldRoundTripWholeListFieldAsNullWhenLaterRecordHasNoList() throws Exception {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("tags", List.of("a"));
    roundTrip(first);

    Map<String, Object> second = new LinkedHashMap<>();
    second.put("tags", null);

    assertThat(roundTrip(second).get("tags")).isNull();
  }

  @Test
  void shouldRoundTripOnlyNullElementsWhenListHasNoValues() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tags", Arrays.asList(null, null));

    assertThat((List<?>) roundTrip(data).get("tags")).containsExactly(null, null);
  }

  @Test
  void shouldStringifyNumericListElements() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("nums", List.of(1, 22, 333));

    List<?> nums = (List<?>) roundTrip(data).get("nums");
    assertThat(nums).extracting(Object::toString).containsExactly("1", "22", "333");
  }

  @Test
  void shouldDeclareNullableStringElementsInArraySchema() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tags", List.of("a"));
    serializer.serialize(data);

    Schema array = serializer.getSchema().getField("tags").schema().getTypes().get(1);
    assertThat(array.getType()).isEqualTo(Schema.Type.ARRAY);
    assertThat(array.getElementType().getTypes())
        .extracting(Schema::getType)
        .containsExactly(Schema.Type.NULL, Schema.Type.STRING);
  }

  @Test
  void shouldSerializeMissingKeyInLaterRecordAsNull() throws Exception {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("name", ALICE);
    first.put("age", 30);
    roundTrip(first);

    Map<String, Object> second = new LinkedHashMap<>();
    second.put("name", "Bob");

    GenericRecord decoded = roundTrip(second);
    assertThat(decoded.get("name")).hasToString("Bob");
    assertThat(decoded.get("age")).isNull();
  }

  @Test
  void shouldTruncateInstantToMillisWhenEncodingTimestamp() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ts", Instant.parse("2024-05-06T07:08:09.123456789Z"));

    assertThat(roundTrip(data).get("ts"))
        .isEqualTo(Instant.parse("2024-05-06T07:08:09.123Z").toEpochMilli());
  }

  @Test
  void shouldRoundTripPreEpochDateAndInstant() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("d", LocalDate.of(1969, Month.DECEMBER, 31));
    data.put("ts", Instant.parse("1969-12-31T23:59:59Z"));

    GenericRecord decoded = roundTrip(data);
    assertThat(decoded.get("d")).isEqualTo(-1);
    assertThat(decoded.get("ts")).isEqualTo(-1000L);
  }

  @Test
  void shouldRoundTripUnicodeStrings() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Zoë 北京 🚀");

    assertThat(roundTrip(data).get("name")).hasToString("Zoë 北京 🚀");
  }

  // --- helpers ---

  private GenericRecord roundTrip(Map<String, Object> data) throws Exception {
    String base64 = serializer.serialize(data);
    byte[] binary = Base64.getDecoder().decode(base64);

    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(serializer.getSchema());
    return reader.read(
        null, DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(binary), null));
  }

  private List<GenericRecord> roundTripAll(List<Map<String, Object>> records) throws Exception {
    List<GenericRecord> result = new java.util.ArrayList<>();
    for (Map<String, Object> data : records) {
      result.add(roundTrip(data));
    }
    return result;
  }
}
