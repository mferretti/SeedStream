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

package com.datagenerator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.cli.CliTestSupport.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Output-content tests for the non-JSON formats driven through {@code execute}. The JSON run of the
 * same job and seed is the oracle: every other format must decode to the same records in the same
 * order. Binary decoders are hand-written (Avro OCF, protobuf wire format) because the {@code cli}
 * test classpath deliberately has no Avro/protobuf compile dependency; this also means the tests
 * verify the on-disk bytes independently of the serializers' own libraries.
 */
@SuppressFBWarnings(
    value = "VA_FORMAT_STRING_USES_NEWLINE",
    justification = "YAML fixtures need a literal \\n, not the platform separator")
class ExecuteOutputFormatsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String OUTPUT = "output";

  @TempDir Path tempDir;

  private Path structDir;
  private int runCounter;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    Files.createDirectories(structDir);
    Files.writeString(
        structDir.resolve("item.yaml"),
        """
        name: item
        data:
          id:
            datatype: "int[1..1000]"
          label:
            datatype: "char[3..10]"
          amount:
            datatype: "decimal[0.0..100.0]"
          active:
            datatype: "boolean"
          born:
            datatype: "date[2020-01-01..2025-12-31]"
        """);
  }

  /** Runs execute for the given format/seed/count/threads and returns the output file bytes. */
  private byte[] generate(String format, long seed, int count, int threads) throws IOException {
    Path out = Files.createDirectories(tempDir.resolve("out-" + (++runCounter)));
    Path job = tempDir.resolve("job-" + runCounter + ".yaml");
    Files.writeString(
        job,
        "source: item.yaml\ntype: file\nstructures_path: %s\nseed:\n  type: embedded\n  value: %d\n"
                .formatted(structDir.toAbsolutePath(), seed)
            + "conf:\n  path: %s/%s\n".formatted(out.toAbsolutePath(), OUTPUT));
    Result r =
        CliTestSupport.run(
            "execute",
            "--job",
            job.toString(),
            "--format",
            format,
            "--count",
            String.valueOf(count),
            "--threads",
            String.valueOf(threads));
    assertThat(r.exit()).as("%s run stderr: %s", format, r.err()).isZero();
    return Files.readAllBytes(out.resolve(OUTPUT + "." + format));
  }

  private List<JsonNode> jsonRecords(long seed, int count) throws IOException {
    List<JsonNode> records = new ArrayList<>();
    for (String line : lines(generate("json", seed, count, 1))) {
      records.add(MAPPER.readTree(line));
    }
    return records;
  }

  private static List<String> lines(byte[] bytes) {
    return Arrays.asList(new String(bytes, StandardCharsets.UTF_8).strip().split("\n"));
  }

  // ── CSV ─────────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(ints = {25, 1500})
  void shouldWriteHeaderAndExactRowCountAndSameValuesAsJsonWhenFormatIsCsv(int count)
      throws IOException {
    List<String> csv = lines(generate("csv", 9, count, 4));
    List<JsonNode> expected = jsonRecords(9, count);

    assertThat(csv).hasSize(count + 1);
    assertThat(csvCells(csv.get(0)))
        .containsExactlyInAnyOrder("id", "label", "amount", "active", "born");
    List<String> header = csvCells(csv.get(0));
    for (int i = 0; i < count; i++) {
      List<String> cells = csvCells(csv.get(i + 1));
      assertThat(cells).as("row %d column count", i).hasSameSizeAs(header);
      Map<String, String> row = new HashMap<>();
      for (int c = 0; c < header.size(); c++) {
        row.put(header.get(c), cells.get(c));
      }
      JsonNode want = expected.get(i);
      assertThat(row.get("id")).as("row %d id", i).isEqualTo(want.get("id").asText());
      assertThat(row.get("label")).as("row %d label", i).isEqualTo(want.get("label").asText());
      assertThat(Double.parseDouble(row.get("amount")))
          .as("row %d amount", i)
          .isEqualTo(want.get("amount").asDouble());
      assertThat(row.get("active")).as("row %d active", i).isEqualTo(want.get("active").asText());
      assertThat(row.get("born")).as("row %d born", i).isEqualTo(want.get("born").asText());
    }
  }

  private static List<String> csvCells(String line) {
    List<String> cells = new ArrayList<>();
    for (String raw : line.split(",", -1)) {
      cells.add(
          raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")
              ? raw.substring(1, raw.length() - 1)
              : raw);
    }
    return cells;
  }

  // ── Avro ────────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(ints = {25, 1500})
  void shouldDecodeToSameRecordsAsJsonWhenFormatIsAvro(int count) throws IOException {
    AvroFile avro = AvroFile.parse(generate("avro", 9, count, 4));
    List<JsonNode> expected = jsonRecords(9, count);

    assertThat(avro.schema().path("type").asText()).isEqualTo("record");
    assertThat(avro.records()).hasSize(count);
    for (int i = 0; i < count; i++) {
      Map<String, Object> got = avro.records().get(i);
      JsonNode want = expected.get(i);
      assertThat(got.get("id")).as("record %d id", i).isEqualTo(want.get("id").asLong());
      assertThat(got.get("label")).as("record %d label", i).isEqualTo(want.get("label").asText());
      assertThat((Double) got.get("amount"))
          .as("record %d amount", i)
          .isEqualTo(want.get("amount").asDouble());
      assertThat(got.get("active"))
          .as("record %d active", i)
          .isEqualTo(want.get("active").asBoolean());
      assertThat(got.get("born")).as("record %d born", i).isEqualTo(want.get("born").asText());
    }
  }

  @Test
  void shouldWriteValidAvroContainerHeaderWhenFormatIsAvro() throws IOException {
    byte[] bytes = generate("avro", 9, 5, 1);

    assertThat(Arrays.copyOf(bytes, 4)).containsExactly('O', 'b', 'j', 1);
    AvroFile avro = AvroFile.parse(bytes);
    assertThat(avro.schema().get("fields")).hasSize(5);
  }

  // ── Protobuf ────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(ints = {25, 1500})
  void shouldDecodeToSameRecordsAsJsonWhenFormatIsProtobuf(int count) throws IOException {
    List<String> lines = lines(generate("protobuf", 9, count, 4));
    List<JsonNode> expected = jsonRecords(9, count);
    List<Map<Integer, Object>> decoded = new ArrayList<>();
    for (String line : lines) {
      decoded.add(decodeProtobuf(Base64.getDecoder().decode(line)));
    }

    assertThat(decoded).hasSize(count);
    // The wire format carries no names and the schema is inferred, so recover the field numbers
    // from the wire types: double = amount, ISO date string = born, other string = label,
    // varint holding values > 1 = id; the remaining field per record is the boolean.
    Map<String, Integer> number = new HashMap<>();
    for (Map<Integer, Object> record : decoded) {
      record.forEach(
          (n, v) -> {
            if (v instanceof Double) {
              number.put("amount", n);
            } else if (v instanceof String str) {
              number.put(str.matches("\\d{4}-\\d{2}-\\d{2}") ? "born" : "label", n);
            } else if ((Long) v > 1) {
              number.put("id", n);
            }
          });
    }
    assertThat(number).containsOnlyKeys("amount", "born", "label", "id");
    for (int i = 0; i < count; i++) {
      Map<Integer, Object> fields = decoded.get(i);
      JsonNode want = expected.get(i);
      assertThat(fields.get(number.get("id")))
          .as("record %d id", i)
          .isEqualTo(want.get("id").asLong());
      assertThat(fields.get(number.get("label")))
          .as("record %d label", i)
          .isEqualTo(want.get("label").asText());
      assertThat((Double) fields.get(number.get("amount")))
          .as("record %d amount", i)
          .isEqualTo(want.get("amount").asDouble());
      assertThat(fields.get(number.get("born")))
          .as("record %d born", i)
          .isEqualTo(want.get("born").asText());
      Set<Integer> boolFields = new HashSet<>(fields.keySet());
      boolFields.removeAll(number.values());
      assertThat(boolFields)
          .as("record %d has exactly one remaining (boolean) field", i)
          .hasSize(1);
      assertThat((Long) fields.get(boolFields.iterator().next()) != 0L)
          .as("record %d active", i)
          .isEqualTo(want.get("active").asBoolean());
    }
  }

  // ── Column order ────────────────────────────────────────────────────────────

  @Test
  void shouldProduceIdenticalBytesWhenSameSeedIsRunTwiceForAvro() throws IOException {
    byte[] first = generate("avro", 5, 1500, 4);
    byte[] second = generate("avro", 5, 1500, 4);

    assertThat(first).isNotEmpty().isEqualTo(second);
  }

  @Test
  void shouldProduceIdenticalAvroRecordsWhenSameSeedIsRunTwice() throws IOException {
    // Content-level reproducibility, independent of the container's sync marker.
    AvroFile first = AvroFile.parse(generate("avro", 5, 1500, 4));
    AvroFile second = AvroFile.parse(generate("avro", 5, 1500, 1));

    assertThat(first.records()).isEqualTo(second.records());
    assertThat(first.records())
        .isNotEqualTo(AvroFile.parse(generate("avro", 6, 1500, 4)).records());
  }

  // ── Minimal binary decoders ─────────────────────────────────────────────────

  /** Reads Avro/protobuf style varints and primitives from a byte array. */
  private static final class Reader {
    private final byte[] data;
    private int pos;

    Reader(byte[] data, int pos) {
      this.data = data;
      this.pos = pos;
    }

    boolean hasMore() {
      return pos < data.length;
    }

    long varint() {
      long result = 0;
      int shift = 0;
      while (true) {
        byte b = data[pos++];
        result |= (long) (b & 0x7f) << shift;
        if ((b & 0x80) == 0) {
          return result;
        }
        shift += 7;
      }
    }

    long zigzag() {
      long n = varint();
      return (n >>> 1) ^ -(n & 1);
    }

    byte[] bytes(int len) {
      byte[] out = Arrays.copyOfRange(data, pos, pos + len);
      pos += len;
      return out;
    }

    String string(int len) {
      return new String(bytes(len), StandardCharsets.UTF_8);
    }

    double doubleLe() {
      return ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }
  }

  /** Decodes a flat protobuf message into field number to Long / Double / String. */
  private static Map<Integer, Object> decodeProtobuf(byte[] message) {
    Map<Integer, Object> fields = new LinkedHashMap<>();
    Reader in = new Reader(message, 0);
    while (in.hasMore()) {
      long tag = in.varint();
      int number = (int) (tag >>> 3);
      switch ((int) (tag & 7)) {
        case 0 -> fields.put(number, in.varint());
        case 1 -> fields.put(number, in.doubleLe());
        case 2 -> fields.put(number, in.string((int) in.varint()));
        default -> throw new IllegalStateException("unexpected wire type in tag " + tag);
      }
    }
    return fields;
  }

  /** Avro Object Container File, null codec, record of {@code [null, T]} unions. */
  private record AvroFile(JsonNode schema, List<Map<String, Object>> records) {

    static AvroFile parse(byte[] bytes) throws IOException {
      Reader in = new Reader(bytes, 0);
      assertThat(in.bytes(4)).as("OCF magic").containsExactly('O', 'b', 'j', 1);
      Map<String, byte[]> meta = new HashMap<>();
      long blockCount = in.zigzag();
      while (blockCount != 0) {
        if (blockCount < 0) {
          in.zigzag(); // block byte size
          blockCount = -blockCount;
        }
        for (long i = 0; i < blockCount; i++) {
          String key = in.string((int) in.zigzag());
          meta.put(key, in.bytes((int) in.zigzag()));
        }
        blockCount = in.zigzag();
      }
      assertThat(new String(meta.getOrDefault("avro.codec", new byte[0]), StandardCharsets.UTF_8))
          .as("codec")
          .isIn("", "null");
      JsonNode schema = MAPPER.readTree(meta.get("avro.schema"));
      byte[] sync = in.bytes(16);

      List<Map<String, Object>> records = new ArrayList<>();
      while (in.hasMore()) {
        long count = in.zigzag();
        long size = in.zigzag();
        Reader block = new Reader(in.bytes((int) size), 0);
        for (long i = 0; i < count; i++) {
          records.add(readRecord(block, schema));
        }
        assertThat(in.bytes(16)).as("block sync marker").isEqualTo(sync);
      }
      return new AvroFile(schema, records);
    }

    private static Map<String, Object> readRecord(Reader in, JsonNode schema) {
      Map<String, Object> record = new LinkedHashMap<>();
      for (JsonNode field : schema.get("fields")) {
        record.put(field.get("name").asText(), readValue(in, field.get("type")));
      }
      return record;
    }

    private static Object readValue(Reader in, JsonNode type) {
      if (type.isArray()) {
        JsonNode branch = type.get((int) in.zigzag());
        return readValue(in, branch);
      }
      String name = type.isObject() ? type.get("type").asText() : type.asText();
      String logical = type.isObject() ? type.path("logicalType").asText() : "";
      return switch (name) {
        case "null" -> null;
        case "boolean" -> in.bytes(1)[0] != 0;
        case "int" ->
            "date".equals(logical)
                ? LocalDate.ofEpochDay(in.zigzag()).toString()
                : (Object) in.zigzag();
        case "long" -> in.zigzag();
        case "double" -> in.doubleLe();
        case "string" -> in.string((int) in.zigzag());
        default -> throw new IllegalStateException("unsupported avro type " + type);
      };
    }
  }
}
