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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End to end for {@code nullable: <rate>} (#492). The central contract is no drift: adding the key
 * to a field must change nothing but that field, and that field is either null or exactly the value
 * it had without the key. Both runs use the SAME structure name (the file is rewritten in between)
 * because unique permutations are keyed by structure name.
 */
class NullableFieldsExecuteTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String NAME = "rec";
  private static final String FIELD = "name";
  private static final List<String> COLUMNS =
      List.of("key", "uid", "id", "name", "amount", "born", "flag", "tag");

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;
  private int runCounter;

  /** One structure field; {@code nullable} is the raw YAML value or {@code null} for no key. */
  private record F(String name, String type, String nullable) {}

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static String type(String field) {
    return switch (field) {
      case "key" -> "serial";
      case "uid" -> "unique[1..1000000]";
      case "id" -> "int[1..1000000]";
      case "name" -> "char[3..12]";
      case "amount" -> "decimal[0.0..1000.0]";
      case "born" -> "date[2020-01-01..2025-12-31]";
      case "flag" -> "boolean";
      case "tag" -> "enum[A,B,C]";
      default -> throw new IllegalArgumentException(field);
    };
  }

  /** The standard record with {@code nullable} set on {@code target} only (null = no key). */
  private static List<F> standard(String target, String nullable) {
    List<F> fields = new ArrayList<>();
    for (String c : COLUMNS) {
      fields.add(new F(c, type(c), c.equals(target) ? nullable : null));
    }
    return fields;
  }

  private void writeStructure(String name, List<F> fields) throws IOException {
    StringBuilder sb = new StringBuilder("name: " + name + "\ndata:\n");
    for (F f : fields) {
      sb.append("  ")
          .append(f.name())
          .append(":\n    datatype: \"")
          .append(f.type())
          .append("\"\n");
      if (f.nullable() != null) {
        sb.append("    nullable: ").append(f.nullable()).append('\n');
      }
    }
    Files.writeString(structDir.resolve(name + ".yaml"), sb.toString());
  }

  private Path job(String source, String outSub) throws IOException {
    Path jobFile = tempDir.resolve("job_" + outSub + ".yaml");
    Files.writeString(
        jobFile,
        String.join(
            "\n",
            "source: " + source + ".yaml",
            "type: file",
            "structures_path: " + structDir.toAbsolutePath(),
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outDir.toAbsolutePath() + "/" + outSub + "/output",
            ""));
    return jobFile;
  }

  private CliTestSupport.Result exec(String source, String format, int count, int threads)
      throws IOException {
    String sub = "run" + (++runCounter);
    return CliTestSupport.run(
        "execute",
        "--job",
        job(source, sub).toString(),
        "--format",
        format,
        "--count",
        String.valueOf(count),
        "--threads",
        String.valueOf(threads),
        "--seed",
        "42");
  }

  /** Runs the structure and returns the output lines (CSV: header included). */
  private List<String> run(String source, String format, int count, int threads)
      throws IOException {
    CliTestSupport.Result r = exec(source, format, count, threads);
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllLines(outDir.resolve("run" + runCounter + "/output." + format));
  }

  private List<String> json(List<F> fields, int count, int threads) throws IOException {
    writeStructure(NAME, fields);
    return run(NAME, "json", count, threads);
  }

  /** Per record: all fields but {@code target} identical; target null or exactly the baseline. */
  private static int assertDriftFree(List<String> base, List<String> got, String target)
      throws IOException {
    assertThat(got).hasSameSizeAs(base);
    int nulls = 0;
    for (int i = 0; i < base.size(); i++) {
      JsonNode b = JSON.readTree(base.get(i));
      JsonNode g = JSON.readTree(got.get(i));
      assertThat(g.size()).as("record %d field count", i).isEqualTo(b.size());
      for (String f : (Iterable<String>) b::fieldNames) {
        assertThat(g.has(f)).as("record %d has '%s'", i, f).isTrue();
        if (f.equals(target) && g.get(f).isNull()) {
          nulls++;
        } else {
          assertThat(g.get(f)).as("record %d field '%s'", i, f).isEqualTo(b.get(f));
        }
      }
    }
    return nulls;
  }

  // ── no-drift contract ─────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"name,1", "name,4", "id,1", "amount,4", "born,1", "flag,4", "tag,1"})
  void shouldChangeOnlyTheNullableFieldWhenRateIsBetweenZeroAndOne(String target, int threads)
      throws IOException {
    List<String> base = json(standard(target, null), 400, threads);

    List<String> got = json(standard(target, "0.3"), 400, threads);

    int nulls = assertDriftFree(base, got, target);
    assertThat(nulls).as("null count for rate 0.3 over 400").isBetween(60, 180);
  }

  @ParameterizedTest
  @CsvSource({"0,1", "0,4", "0.0,1", "0.0,4"})
  void shouldProduceIdenticalBytesWhenRateIsZero(String rate, int threads) throws IOException {
    List<String> base = json(standard(FIELD, null), 300, threads);

    assertThat(json(standard(FIELD, rate), 300, threads)).isEqualTo(base);
  }

  @ParameterizedTest
  @CsvSource({"1,1", "1,4", "1.0,1", "1.0,4"})
  void shouldNullEveryValueAndNothingElseWhenRateIsOne(String rate, int threads)
      throws IOException {
    List<String> base = json(standard(FIELD, null), 300, threads);

    List<String> got = json(standard(FIELD, rate), 300, threads);

    assertThat(assertDriftFree(base, got, FIELD)).isEqualTo(300);
  }

  @Test
  void shouldNotChangeValuesWhenSeveralFieldsAreNullableAtOnce() throws IOException {
    List<String> base = json(standard(null, null), 300, 4);
    List<F> fields = new ArrayList<>();
    for (F f : standard(null, null)) {
      boolean nullable = List.of("id", "name", "amount", "born").contains(f.name());
      fields.add(new F(f.name(), f.type(), nullable ? "0.4" : null));
    }

    List<String> got = json(fields, 300, 4);

    int total = 0;
    for (String f : List.of("id", "name", "amount", "born")) {
      total += assertDriftFreeOnly(base, got, f);
    }
    assertThat(total).isPositive();
    for (String f : List.of("key", "uid", "flag", "tag")) {
      assertThat(assertDriftFreeOnly(base, got, f)).isZero();
    }
  }

  /** Like assertDriftFree but only checks {@code field}: null or equal; returns the null count. */
  private static int assertDriftFreeOnly(List<String> base, List<String> got, String field)
      throws IOException {
    int nulls = 0;
    for (int i = 0; i < base.size(); i++) {
      JsonNode g = JSON.readTree(got.get(i)).get(field);
      if (g.isNull()) {
        nulls++;
      } else {
        assertThat(g)
            .as("record %d '%s'", i, field)
            .isEqualTo(JSON.readTree(base.get(i)).get(field));
      }
    }
    return nulls;
  }

  @Test
  void shouldAcceptZeroRateOnKeyTypesAndStayIdentical() throws IOException {
    List<String> base = json(standard(null, null), 200, 4);

    assertThat(json(standard("key", "0"), 200, 4)).isEqualTo(base);
    assertThat(json(standard("uid", "0.0"), 200, 4)).isEqualTo(base);
  }

  @Test
  void shouldNullRefFieldAndLeaveOtherFieldsIdentical() throws IOException {
    Files.writeString(
        structDir.resolve("parent.yaml"), "name: parent\ndata:\n  id:\n    datatype: serial\n");
    List<F> plain =
        List.of(new F("fk", "ref[parent.id, 1..50]", null), new F("x", "int[1..1000]", null));
    List<F> nullable =
        List.of(new F("fk", "ref[parent.id, 1..50]", "0.3"), new F("x", "int[1..1000]", null));
    writeStructure("child", plain);
    List<String> base = run("child", "json", 400, 4);

    writeStructure("child", nullable);
    List<String> got = run("child", "json", 400, 4);

    assertThat(assertDriftFree(base, got, "fk")).isBetween(60, 180);
  }

  @Test
  void shouldChangeOnlyNestedFieldWhenNullableIsInsideObjectStructure() throws IOException {
    writeStructure(
        "addr", List.of(new F("street", "char[3..9]", null), new F("zip", "int[1..99999]", null)));
    writeStructure(
        "person", List.of(new F("id", "serial", null), new F("home", "object[addr]", null)));
    List<String> base = run("person", "json", 300, 4);

    writeStructure(
        "addr", List.of(new F("street", "char[3..9]", null), new F("zip", "int[1..99999]", "0.5")));
    List<String> got = run("person", "json", 300, 4);

    int nulls = 0;
    for (int i = 0; i < base.size(); i++) {
      JsonNode b = JSON.readTree(base.get(i));
      JsonNode g = JSON.readTree(got.get(i));
      assertThat(g.get("id")).isEqualTo(b.get("id"));
      assertThat(g.get("home").get("street")).isEqualTo(b.get("home").get("street"));
      if (g.get("home").get("zip").isNull()) {
        nulls++;
      } else {
        assertThat(g.get("home").get("zip")).isEqualTo(b.get("home").get("zip"));
      }
    }
    assertThat(nulls).isBetween(90, 210);
  }

  @Test
  void shouldNullWholeObjectAndArrayWhenTheyAreNullable() throws IOException {
    writeStructure(
        "addr", List.of(new F("street", "char[3..9]", null), new F("zip", "int[1..99999]", null)));
    List<F> plain =
        List.of(
            new F("id", "serial", null),
            new F("home", "object[addr]", null),
            new F("tags", "array[char[2..4], 1..3]", null),
            new F("after", "int[1..1000]", null));
    List<F> nullable =
        List.of(
            new F("id", "serial", null),
            new F("home", "object[addr]", "0.5"),
            new F("tags", "array[char[2..4], 1..3]", "0.5"),
            new F("after", "int[1..1000]", null));
    writeStructure("order", plain);
    List<String> base = run("order", "json", 300, 4);

    writeStructure("order", nullable);
    List<String> got = run("order", "json", 300, 4);

    int homeNull = 0;
    int tagsNull = 0;
    for (int i = 0; i < base.size(); i++) {
      JsonNode b = JSON.readTree(base.get(i));
      JsonNode g = JSON.readTree(got.get(i));
      assertThat(g.get("id")).isEqualTo(b.get("id"));
      assertThat(g.get("after")).as("record %d after", i).isEqualTo(b.get("after"));
      if (g.get("home").isNull()) {
        homeNull++;
      } else {
        assertThat(g.get("home")).isEqualTo(b.get("home"));
      }
      if (g.get("tags").isNull()) {
        tagsNull++;
      } else {
        assertThat(g.get("tags")).isEqualTo(b.get("tags"));
      }
    }
    assertThat(homeNull).isBetween(90, 210);
    assertThat(tagsNull).isBetween(90, 210);
  }

  // ── rate sanity, determinism, prefix stability ────────────────────────────────

  @Test
  void shouldHitTheRateWithinATightBandAndStayDeterministicAcrossThreads() throws IOException {
    List<F> fields = standard(FIELD, "0.05");
    List<String> single = json(fields, 20_000, 1);

    long nulls = 0;
    for (String l : single) {
      if (JSON.readTree(l).get(FIELD).isNull()) {
        nulls++;
      }
    }
    assertThat(nulls / 20_000.0).as("null share").isBetween(0.04, 0.06);
    assertThat(json(fields, 20_000, 4)).isEqualTo(single);
    assertThat(json(fields, 20_000, 1)).isEqualTo(single);
  }

  @Test
  void shouldKeepSmallerRunAsPrefixOfLargerRun() throws IOException {
    List<F> fields = standard(FIELD, "0.3");
    List<String> small = json(fields, 1000, 4);
    List<String> large = json(fields, 2000, 4);

    assertThat(large.subList(0, 1000)).isEqualTo(small);
  }

  // ── formats ───────────────────────────────────────────────────────────────────

  @Test
  void shouldWriteJsonNullNotTheTextNullWhenFieldIsNull() throws IOException {
    List<String> lines = json(standard(FIELD, "1"), 20, 1);

    for (String l : lines) {
      assertThat(JSON.readTree(l).get(FIELD).isNull()).as(l).isTrue();
      assertThat(JSON.readTree(l).get(FIELD).isTextual()).as(l).isFalse();
    }
  }

  @Test
  void shouldWriteEmptyCsvCellWhenFieldIsNullAndKeepOtherCellsIdentical() throws IOException {
    writeStructure(NAME, standard(null, null));
    List<String> base = run(NAME, "csv", 200, 4);
    writeStructure(NAME, standard(FIELD, "0.3"));
    List<String> got = run(NAME, "csv", 200, 4);

    // CsvSerializer quotes every cell (header too); a null is written as an empty cell ""
    int col = List.of(base.get(0).split(",")).indexOf("\"" + FIELD + "\"");
    assertThat(col).isEqualTo(COLUMNS.indexOf(FIELD));
    assertThat(got.get(0)).isEqualTo(base.get(0));
    int empty = 0;
    for (int i = 1; i < base.size(); i++) {
      String[] b = base.get(i).split(",", -1);
      String[] g = got.get(i).split(",", -1);
      assertThat(g).hasSameSizeAs(b);
      for (int c = 0; c < b.length; c++) {
        if (c == col && "\"\"".equals(g[c])) {
          empty++;
        } else {
          assertThat(g[c]).as("row %d col %d", i, c).isEqualTo(b[c]);
        }
      }
    }
    assertThat(empty).isBetween(30, 90);
  }

  // ── validation ────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.0", "0.05", "1", "1.0"})
  void shouldAcceptRateInZeroToOne(String rate) throws IOException {
    writeStructure(NAME, standard(FIELD, rate));

    CliTestSupport.Result r = exec(NAME, "json", 10, 1);

    assertThat(r.exit()).as(r.err()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.1", "1.5", "\"abc\"", "true"})
  void shouldRejectInvalidRateNamingTheField(String rate) throws IOException {
    writeStructure(NAME, standard(FIELD, rate));

    CliTestSupport.Result r = exec(NAME, "json", 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("name").contains("nullable").doesNotContain("Unrecognized");
    assertThat(outDir.resolve("run" + runCounter + "/output.json")).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "uid"})
  void shouldRejectPositiveRateOnKeyTypes(String keyField) throws IOException {
    writeStructure(NAME, standard(keyField, "0.1"));

    CliTestSupport.Result r = exec(NAME, "json", 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains(keyField).contains("nullable").doesNotContain("Unrecognized");
    assertThat(outDir.resolve("run" + runCounter + "/output.json")).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ref[parent.id, 1..count, unique]", "ref[parent.id, 1..count, unique=g]"})
  void shouldRejectPositiveRateOnUniqueRef(String type) throws IOException {
    Files.writeString(
        structDir.resolve("parent.yaml"), "name: parent\ndata:\n  id:\n    datatype: serial\n");
    writeStructure("child", List.of(new F("fk", type, "0.1")));

    CliTestSupport.Result r = exec("child", "json", 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("fk").contains("nullable").doesNotContain("Unrecognized");
  }
}
