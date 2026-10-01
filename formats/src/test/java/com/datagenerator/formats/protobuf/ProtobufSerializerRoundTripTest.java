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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Decodes serializer output with an independently built descriptor and asserts field values. */
class ProtobufSerializerRoundTripTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProtobufSerializer serializer = new ProtobufSerializer();

  /** Field spec: name, type, repeated. Field numbers are assigned in order starting at 1. */
  private record F(String name, Type type, boolean repeated) {
    static F of(String name, Type type) {
      return new F(name, type, false);
    }

    static F rep(String name, Type type) {
      return new F(name, type, true);
    }
  }

  private static Descriptor descriptor(F... fields) throws Exception {
    DescriptorProto.Builder msg = DescriptorProto.newBuilder().setName("Record");
    int n = 1;
    for (F f : fields) {
      msg.addField(
          FieldDescriptorProto.newBuilder()
              .setName(f.name())
              .setNumber(n++)
              .setType(f.type())
              .setLabel(f.repeated() ? Label.LABEL_REPEATED : Label.LABEL_OPTIONAL));
    }
    FileDescriptor fd =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder().setName("t.proto").addMessageType(msg).build(),
            new FileDescriptor[0]);
    return fd.getMessageTypes().get(0);
  }

  private static DynamicMessage decode(String base64, Descriptor d)
      throws InvalidProtocolBufferException {
    return DynamicMessage.parseFrom(d, Base64.getDecoder().decode(base64));
  }

  private static Object get(DynamicMessage m, String field) {
    return m.getField(m.getDescriptorForType().findFieldByName(field));
  }

  @SuppressWarnings("unchecked")
  private static List<String> strings(DynamicMessage m, String field) {
    return (List<String>) get(m, field);
  }

  private static boolean has(DynamicMessage m, String field) {
    FieldDescriptor fd = m.getDescriptorForType().findFieldByName(field);
    return fd.isRepeated() ? m.getRepeatedFieldCount(fd) > 0 : m.hasField(fd);
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void shouldRoundTripScalarsWithExactValuesAndTypes() throws Exception {
    Descriptor d =
        descriptor(
            F.of("s", Type.TYPE_STRING),
            F.of("i", Type.TYPE_INT64),
            F.of("l", Type.TYPE_INT64),
            F.of("d", Type.TYPE_DOUBLE),
            F.of("bd", Type.TYPE_DOUBLE),
            F.of("fl", Type.TYPE_DOUBLE),
            F.of("b", Type.TYPE_BOOL));
    String out =
        serializer.serialize(
            map(
                "s",
                "hello",
                "i",
                -7,
                "l",
                Long.MAX_VALUE,
                "d",
                3.14159,
                "bd",
                new BigDecimal("99.95"),
                "fl",
                1.5f,
                "b",
                true));

    DynamicMessage m = decode(out, d);

    assertThat(get(m, "s")).isEqualTo("hello");
    assertThat(get(m, "i")).isEqualTo(-7L);
    assertThat(get(m, "l")).isEqualTo(Long.MAX_VALUE);
    assertThat(get(m, "d")).isEqualTo(3.14159);
    assertThat(get(m, "bd")).isEqualTo(99.95);
    assertThat(get(m, "fl")).isEqualTo(1.5d);
    assertThat(get(m, "b")).isEqualTo(true);
  }

  @Test
  void shouldEncodeDatesAsIsoStrings() throws Exception {
    Descriptor d = descriptor(F.of("day", Type.TYPE_STRING), F.of("ts", Type.TYPE_STRING));
    String out =
        serializer.serialize(
            map(
                "day",
                LocalDate.of(2024, Month.FEBRUARY, 29),
                "ts",
                Instant.parse("2024-02-29T10:15:30Z")));

    DynamicMessage m = decode(out, d);

    assertThat(get(m, "day")).isEqualTo("2024-02-29");
    assertThat(get(m, "ts")).isEqualTo("2024-02-29T10:15:30Z");
  }

  @Test
  void shouldLeaveNullFieldAbsentAndOthersIntact() throws Exception {
    Descriptor d = descriptor(F.of("a", Type.TYPE_STRING), F.of("b", Type.TYPE_STRING));

    DynamicMessage m = decode(serializer.serialize(map("a", null, "b", "x")), d);

    assertThat(has(m, "a")).isFalse();
    assertThat(get(m, "b")).isEqualTo("x");
  }

  @Test
  void shouldEncodeNestedMapAsJsonString() throws Exception {
    Descriptor d = descriptor(F.of("addr", Type.TYPE_STRING));
    String out = serializer.serialize(map("addr", map("city", "Milano", "zip", 20100)));

    assertThat(get(decode(out, d), "addr")).isEqualTo("{\"city\":\"Milano\",\"zip\":20100}");
  }

  @Test
  void shouldEncodeListOfScalarsAsRepeatedStrings() throws Exception {
    Descriptor d = descriptor(F.rep("tags", Type.TYPE_STRING));
    String out = serializer.serialize(map("tags", Arrays.asList("a", 2, null, true)));

    assertThat(strings(decode(out, d), "tags")).containsExactly("a", "2", "", "true");
  }

  @Test
  void shouldEncodeListOfObjectsAsRepeatedStrings() throws Exception {
    Descriptor d = descriptor(F.rep("items", Type.TYPE_STRING));
    String out =
        serializer.serialize(map("items", List.of(map("sku", "A", "q", 1), map("sku", "B"))));

    // Object elements are JSON (like nested-map fields and Avro, #284), never Map.toString().
    List<String> items = strings(decode(out, d), "items");
    assertThat(items).hasSize(2);
    assertThat(JSON.readTree(items.get(0))).isEqualTo(JSON.readTree("{\"sku\":\"A\",\"q\":1}"));
    assertThat(JSON.readTree(items.get(1))).isEqualTo(JSON.readTree("{\"sku\":\"B\"}"));
  }

  @Test
  void shouldEncodeNestedListElementsAsJson() throws Exception {
    Descriptor d = descriptor(F.rep("matrix", Type.TYPE_STRING));
    String out = serializer.serialize(map("matrix", List.of(List.of(1, 2), List.of("x"))));

    assertThat(strings(decode(out, d), "matrix")).containsExactly("[1,2]", "[\"x\"]");
  }

  @Test
  void shouldEncodeEmptyListAsNoElements() throws Exception {
    Descriptor d = descriptor(F.rep("tags", Type.TYPE_STRING), F.of("n", Type.TYPE_INT64));

    DynamicMessage m = decode(serializer.serialize(map("tags", List.of(), "n", 5)), d);

    assertThat(strings(m, "tags")).isEmpty();
    assertThat(get(m, "n")).isEqualTo(5L);
  }

  @Test
  void shouldRoundTripUnicodeAndEmoji() throws Exception {
    Descriptor d = descriptor(F.of("s", Type.TYPE_STRING));
    String text = "caffè 日本語 🚀 \u0000 end";

    assertThat(get(decode(serializer.serialize(map("s", text)), d), "s")).isEqualTo(text);
  }

  @Test
  void shouldThrowSerializationExceptionWhenStringReplacesInt() {
    serializer.serialize(map("n", 1));
    Map<String, Object> data = map("n", "oops");

    assertThatThrownBy(() -> serializer.serialize(data))
        .isExactlyInstanceOf(SerializationException.class)
        .hasMessageContaining("n");
  }

  @Test
  void shouldThrowSerializationExceptionWhenDoubleReplacesInt() {
    serializer.serialize(map("n", 1));
    Map<String, Object> data = map("n", 1.5);

    assertThatThrownBy(() -> serializer.serialize(data))
        .isExactlyInstanceOf(SerializationException.class);
  }

  @Test
  void shouldThrowSerializationExceptionWhenBooleanReplacesInt() {
    serializer.serialize(map("n", 1));
    Map<String, Object> data = map("n", true);

    assertThatThrownBy(() -> serializer.serialize(data))
        .isExactlyInstanceOf(SerializationException.class);
  }

  @Test
  void shouldThrowSerializationExceptionWhenScalarReplacesList() {
    serializer.serialize(map("tags", List.of("a")));
    Map<String, Object> data = map("tags", "single");

    assertThatThrownBy(() -> serializer.serialize(data))
        .isExactlyInstanceOf(SerializationException.class);
  }

  @Test
  void shouldStringifyIntWhenFieldWasInferredAsString() throws Exception {
    Descriptor d = descriptor(F.of("n", Type.TYPE_STRING));
    serializer.serialize(map("n", "x"));

    assertThat(get(decode(serializer.serialize(map("n", 42)), d), "n")).isEqualTo("42");
  }

  @Test
  void shouldInferStringWhenFirstRecordHasNullAndStringifyLaterNumber() throws Exception {
    Descriptor d = descriptor(F.of("n", Type.TYPE_STRING));
    serializer.serialize(map("n", null));

    assertThat(get(decode(serializer.serialize(map("n", 42)), d), "n")).isEqualTo("42");
  }

  @Test
  void shouldSkipExtraKeysAndLeaveMissingKeysUnset() throws Exception {
    Descriptor d = descriptor(F.of("a", Type.TYPE_STRING), F.of("b", Type.TYPE_INT64));
    serializer.serialize(map("a", "first", "b", 1));

    DynamicMessage extra = decode(serializer.serialize(map("a", "x", "b", 2, "c", "extra")), d);
    DynamicMessage missing = decode(serializer.serialize(map("a", "only")), d);

    assertThat(get(extra, "a")).isEqualTo("x");
    assertThat(get(extra, "b")).isEqualTo(2L);
    assertThat(extra.getUnknownFields().asMap()).isEmpty();
    assertThat(get(missing, "a")).isEqualTo("only");
    assertThat(has(missing, "b")).isFalse();
  }

  @Test
  void shouldKeepFirstRecordSchemaWhenKeyOrderDiffers() throws Exception {
    Descriptor d = descriptor(F.of("a", Type.TYPE_STRING), F.of("b", Type.TYPE_INT64));
    serializer.serialize(map("a", "x", "b", 1));

    DynamicMessage m = decode(serializer.serialize(map("b", 9, "a", "y")), d);

    assertThat(get(m, "a")).isEqualTo("y");
    assertThat(get(m, "b")).isEqualTo(9L);
  }

  @Test
  void shouldRoundTripConcurrentlyFromManyThreads() throws Exception {
    int threads = 16;
    int perThread = 200;
    Descriptor d =
        descriptor(
            F.of("id", Type.TYPE_INT64),
            F.of("name", Type.TYPE_STRING),
            F.of("score", Type.TYPE_DOUBLE),
            F.rep("tags", Type.TYPE_STRING));
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<List<String>>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        long base = (long) t * perThread;
        Callable<List<String>> task =
            () -> {
              start.await();
              List<String> outs = new ArrayList<>();
              for (int i = 0; i < perThread; i++) {
                long id = base + i;
                outs.add(
                    serializer.serialize(
                        map(
                            "id",
                            id,
                            "name",
                            "n" + id,
                            "score",
                            id / 4.0,
                            "tags",
                            List.of("t" + id))));
              }
              return outs;
            };
        futures.add(pool.submit(task));
      }
      start.countDown();
      for (int t = 0; t < threads; t++) {
        List<String> outs = futures.get(t).get();
        for (int i = 0; i < perThread; i++) {
          long id = (long) t * perThread + i;
          DynamicMessage m = decode(outs.get(i), d);
          assertThat(get(m, "id")).isEqualTo(id);
          assertThat(get(m, "name")).isEqualTo("n" + id);
          assertThat(get(m, "score")).isEqualTo(id / 4.0);
          assertThat(strings(m, "tags")).containsExactly("t" + id);
        }
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void shouldReportProtobufFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("protobuf");
  }

  @Test
  void shouldEncodeEmptyRecordAsEmptyMessage() {
    assertThat(Base64.getDecoder().decode(serializer.serialize(new LinkedHashMap<>()))).isEmpty();
  }

  @Test
  void shouldBeMoreCompactThanEquivalentJson() throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("firstName", "Christopher");
    data.put("lastName", "Montgomery");
    data.put("age", 42);
    data.put("balance", new BigDecimal("12345.67"));

    byte[] binary = Base64.getDecoder().decode(serializer.serialize(data));

    assertThat(binary).hasSizeLessThan(JSON.writeValueAsBytes(data).length);
  }
}
