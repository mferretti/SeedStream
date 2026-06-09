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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    LocalDate date = LocalDate.of(2024, 3, 15);
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
