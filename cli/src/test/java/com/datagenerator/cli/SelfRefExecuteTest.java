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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End to end for a self-reference {@code ref[t.c]} inside structure {@code t} (#213): record i
 * references the key of a uniformly random earlier record; record 0 references itself, or is NULL
 * when the field is nullable.
 */
class SelfRefExecuteTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String STRUCT = "t";

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  /** Writes structure {@code name}; fields are (name, datatype, nullable-or-null) triples. */
  private void structure(String name, String... fields) throws IOException {
    StringBuilder sb = new StringBuilder("name: " + name + "\ndata:\n");
    for (int i = 0; i < fields.length; i += 3) {
      sb.append("  ")
          .append(fields[i])
          .append(":\n    datatype: \"")
          .append(fields[i + 1])
          .append("\"\n");
      if (fields[i + 2] != null) {
        sb.append("    nullable: ").append(fields[i + 2]).append('\n');
      }
    }
    Files.writeString(structDir.resolve(name + ".yaml"), sb.toString());
  }

  private Path job(String source, String out) throws IOException {
    Path jobFile = tempDir.resolve("job_" + out + ".yaml");
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
            "  path: " + outDir.toAbsolutePath() + "/" + out + "/output",
            ""));
    return jobFile;
  }

  private CliTestSupport.Result exec(Path jobFile, int count, int threads) {
    return CliTestSupport.run(
        "execute",
        "--job",
        jobFile.toString(),
        "--count",
        String.valueOf(count),
        "--threads",
        String.valueOf(threads));
  }

  private List<String> lines(String out, int count, int threads) throws IOException {
    CliTestSupport.Result r = exec(job(STRUCT, out), count, threads);
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllLines(outDir.resolve(out + "/output.json"));
  }

  private List<JsonNode> records(List<String> jsonLines) throws IOException {
    List<JsonNode> out = new ArrayList<>();
    for (String l : jsonLines) {
      out.add(JSON.readTree(l));
    }
    return out;
  }

  private static boolean isNull(JsonNode rec, String field) {
    return rec.get(field) == null || rec.get(field).isNull();
  }

  /**
   * Checks the self-reference contract on {@code out}: record 0 is its own key (or null when {@code
   * nullableRoot}); every other non-null value is the key of a strictly earlier record.
   */
  private void assertSelfRefs(List<JsonNode> out, boolean nullableRoot) {
    Map<Long, Integer> indexOfKey = new HashMap<>();
    for (int i = 0; i < out.size(); i++) {
      indexOfKey.put(out.get(i).get("id").asLong(), i);
    }
    assertThat(indexOfKey).as("keys are distinct").hasSameSizeAs(out);
    JsonNode first = out.get(0);
    if (nullableRoot) {
      assertThat(isNull(first, "p")).as("record 0 is NULL").isTrue();
    } else {
      assertThat(first.get("p").asLong())
          .as("record 0 references itself")
          .isEqualTo(first.get("id").asLong());
    }
    for (int i = 1; i < out.size(); i++) {
      if (isNull(out.get(i), "p")) {
        continue;
      }
      Integer j = indexOfKey.get(out.get(i).get("p").asLong());
      assertThat(j).as("record %d references an existing key", i).isNotNull();
      assertThat(j).as("record %d references an earlier record", i).isLessThan(i);
    }
  }

  // ── contract ─────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
    "serial,1",
    "serial,2",
    "serial,1000",
    "unique[1..count],1",
    "unique[1..count],2",
    "unique[1..count],1000"
  })
  void shouldReferenceEarlierKeyAndRootItselfWhenNotNullable(String keyType, int count)
      throws Exception {
    structure(STRUCT, "id", keyType, null, "p", "ref[t.id]", null);

    List<String> single = lines("one", count, 1);
    List<String> multi = lines("four", count, 4);

    assertThat(single).hasSize(count);
    assertThat(multi).as("thread count independent").isEqualTo(single);
    assertSelfRefs(records(single), false);
  }

  @ParameterizedTest
  @CsvSource({"serial,0.05", "serial,1", "unique[1..count],0.05", "unique[1..count],1"})
  void shouldMakeRootNullAndKeepEarlierRuleWhenNullable(String keyType, String rate)
      throws Exception {
    structure(STRUCT, "id", keyType, null, "p", "ref[t.id]", rate);

    List<String> single = lines("one", 1000, 1);
    List<String> multi = lines("four", 1000, 4);

    assertThat(multi).isEqualTo(single);
    List<JsonNode> out = records(single);
    assertSelfRefs(out, true);
    long nulls = out.stream().filter(r -> isNull(r, "p")).count();
    if ("1".equals(rate)) {
      assertThat(nulls).as("rate 1 nulls everything").isEqualTo(1000);
    } else {
      assertThat(nulls).as("nullable rate applies on top").isBetween(10L, 120L);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void shouldMakeRootNullEvenWithTinyCountWhenNullable(int count) throws Exception {
    structure(STRUCT, "id", "serial", null, "p", "ref[t.id]", "0.05");

    List<JsonNode> out = records(lines("one", count, 1));

    assertThat(out).hasSize(count);
    assertThat(isNull(out.get(0), "p")).isTrue();
  }

  @Test
  void shouldStayWithinHundredAndRecordIndexWhenSerialStartsAtHundred() throws Exception {
    structure(STRUCT, "id", "serial[100]", null, "p", "ref[t.id]", null);

    List<JsonNode> out = records(lines("one", 1000, 4));

    assertThat(out.get(0).get("p").asLong()).isEqualTo(100);
    for (int i = 1; i < out.size(); i++) {
      assertThat(out.get(i).get("p").asLong()).as("record %d", i).isBetween(100L, 99L + i);
    }
    assertThat(out.stream().map(r -> r.get("p").asLong()).distinct().count())
        .as("spread, not a constant")
        .isGreaterThan(100);
  }

  @Test
  void shouldStayWithinBoundedSerialRange() throws Exception {
    structure(STRUCT, "id", "serial[10..5000]", null, "p", "ref[t.id]", null);

    List<JsonNode> out = records(lines("one", 1000, 1));

    for (int i = 1; i < out.size(); i++) {
      assertThat(out.get(i).get("p").asLong()).as("record %d", i).isBetween(10L, 9L + i);
    }
  }

  @ParameterizedTest
  @CsvSource({"serial,", "unique[1..count],", "serial,0.05"})
  void shouldBePrefixStableWhenCountGrows(String keyType, String rate) throws Exception {
    structure(STRUCT, "id", keyType, null, "p", "ref[t.id]", rate);

    List<String> half = lines("half", 500, 4);
    List<String> full = lines("full", 1000, 1);

    // unique[1..count] is dynamic (not prefix-stable) by design, only fixed forms must prefix
    if (!keyType.contains("count")) {
      assertThat(full.subList(0, 500)).isEqualTo(half);
    } else {
      assertThat(half).hasSize(500);
    }
  }

  @Test
  void shouldBePrefixStableWhenUniqueKeyHasFixedRange() throws Exception {
    structure(STRUCT, "id", "unique[1..100000]", null, "p", "ref[t.id]", null);

    List<String> half = lines("half", 500, 4);
    List<String> full = lines("full", 1000, 1);

    assertThat(full.subList(0, 500)).isEqualTo(half);
    assertSelfRefs(records(half), false);
  }

  @ParameterizedTest
  @ValueSource(strings = {"serial", "unique[1..count]", "unique[1..100000]"})
  void shouldNotChangeOtherFieldsWhenSelfRefFieldIsAdded(String keyType) throws Exception {
    structure(STRUCT, "id", keyType, null, "x", "int[1..1000000]", null, "n", "char[3..8]", null);
    List<JsonNode> without = records(lines("without", 1000, 1));
    structure(
        STRUCT,
        "id",
        keyType,
        null,
        "x",
        "int[1..1000000]",
        null,
        "n",
        "char[3..8]",
        null,
        "p",
        "ref[t.id]",
        null);
    List<JsonNode> with = records(lines("with", 1000, 4));

    assertThat(with).hasSameSizeAs(without);
    for (int i = 0; i < with.size(); i++) {
      for (String f : List.of("id", "x", "n")) {
        assertThat(with.get(i).get(f))
            .as("record %d field %s", i, f)
            .isEqualTo(without.get(i).get(f));
      }
    }
  }

  @Test
  void shouldNotChangeOtherFieldsWhenSelfRefFieldIsAddedAndNullable() throws Exception {
    structure(STRUCT, "id", "serial", null, "x", "int[1..1000000]", null);
    List<JsonNode> without = records(lines("without", 1000, 1));
    structure(STRUCT, "id", "serial", null, "x", "int[1..1000000]", null, "p", "ref[t.id]", "0.05");
    List<JsonNode> with = records(lines("with", 1000, 1));

    for (int i = 0; i < with.size(); i++) {
      assertThat(with.get(i).get("x")).isEqualTo(without.get(i).get("x"));
    }
  }

  // ── startup rejections ───────────────────────────────────────────────────────

  private CliTestSupport.Result failing(String source) throws IOException {
    CliTestSupport.Result r = exec(job(source, source), 10, 1);
    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).as("not the old placeholder rejection").doesNotContain("#213");
    assertThat(outDir.resolve(source + "/output.json")).doesNotExist();
    return r;
  }

  @ParameterizedTest
  @ValueSource(strings = {"ref[t.id, unique]", "ref[t.id, unique=g]"})
  void shouldRejectUniqueSelfReference(String type) throws Exception {
    structure(STRUCT, "id", "serial", null, "p", type, null);

    assertThat(failing(STRUCT).err())
        .contains(type + " in t.p")
        .containsIgnoringCase("self-reference")
        .contains("unique");
  }

  @Test
  void shouldRejectSelfReferenceInsideArray() throws Exception {
    structure(STRUCT, "id", "serial", null, "p", "array[ref[t.id], 1..3]", null);

    assertThat(failing(STRUCT).err())
        .contains("in t.p")
        .containsIgnoringCase("self-reference")
        .containsIgnoringCase("array");
  }

  @Test
  void shouldRejectSelfReferenceInStructureReachedThroughObject() throws Exception {
    structure("inner", "id", "serial", null, "p", "ref[inner.id]", null);
    structure("root", "n", "object[inner]", null);

    assertThat(failing("root").err())
        .contains("ref[inner.id] in inner.p")
        .containsIgnoringCase("nested");
  }

  @Test
  void shouldRejectSelfReferenceInStructureReachedThroughArrayOfObjects() throws Exception {
    structure("inner", "id", "serial", null, "p", "ref[inner.id]", null);
    structure("root", "n", "array[object[inner], 1..3]", null);

    assertThat(failing("root").err())
        .contains("ref[inner.id] in inner.p")
        .containsIgnoringCase("nested");
  }

  @Test
  void shouldRejectWhenKeyFieldIsMissing() throws Exception {
    structure(STRUCT, "p", "ref[t.id]", null);

    assertThat(failing(STRUCT).err()).contains("ref[t.id] in t.p").contains("'t.id' not found");
  }

  @Test
  void shouldRejectWhenKeyFieldIsNullable() throws Exception {
    structure(STRUCT, "id", "serial", "0.1", "p", "ref[t.id]", null);

    assertThat(failing(STRUCT).err())
        .contains("ref[t.id] in t.p")
        .contains("'t.id' is nullable")
        .contains("never be null");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "int[1..1000]|int",
        "char[3..5]|char",
        "cycle[1,2,3]|cycle",
        "unique[g, 1..count]|unique"
      })
  void shouldRejectWhenKeyFieldIsNotReferenceable(String keyType, String named) throws Exception {
    structure(STRUCT, "id", keyType, null, "p", "ref[t.id]", null);

    assertThat(failing(STRUCT).err())
        .contains("ref[t.id] in t.p")
        .contains("t.id")
        .contains("cannot be referenced")
        .contains(named);
  }

  @Test
  void shouldNotNeedCountsEntryForSelfReference() throws Exception {
    structure(STRUCT, "id", "serial", null, "p", "ref[t.id]", null);

    CliTestSupport.Result r = exec(job(STRUCT, "one"), 10, 1);

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(r.err()).doesNotContain("no row count");
  }
}
