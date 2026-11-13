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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End to end for the field cap {@code max_length} / {@code length_unit} (#440). Central contract: a
 * cap that never triggers changes nothing (no drift); a cap that triggers keeps every value within
 * the limit, cuts only at code point boundaries, and stays deterministic across thread counts.
 */
class MaxLengthExecuteTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String NAME = "rec";
  private static final String EMOJIS = "(😀){10}"; // 10 code points, 40 UTF-8 bytes

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;
  private int runCounter;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  /** A field block: {@code extra} is raw YAML lines (already indented 4 spaces) or empty. */
  private static String field(String name, String datatype, String... extraLines) {
    StringBuilder sb = new StringBuilder("  " + name + ":\n    datatype: \"" + datatype + "\"\n");
    for (String l : extraLines) {
      sb.append("    ").append(l).append('\n');
    }
    return sb.toString();
  }

  private void structure(String name, String geolocation, String... fields) throws IOException {
    String geo = geolocation == null ? "" : "geolocation: " + geolocation + "\n";
    Files.writeString(
        structDir.resolve(name + ".yaml"),
        "name: " + name + "\n" + geo + "data:\n" + String.join("", fields));
  }

  private Path job(String source, String sub) throws IOException {
    Path jobFile = tempDir.resolve("job_" + sub + ".yaml");
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
            "  path: " + outDir.toAbsolutePath() + "/" + sub + "/output",
            ""));
    return jobFile;
  }

  private CliTestSupport.Result exec(String source, int count, int threads, String... extra)
      throws IOException {
    String sub = "run" + (++runCounter);
    String[] base = {
      "execute",
      "--job",
      job(source, sub).toString(),
      "--format",
      "json",
      "--count",
      String.valueOf(count),
      "--threads",
      String.valueOf(threads),
      "--seed",
      "42"
    };
    String[] all = new String[base.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(extra, 0, all, base.length, extra.length);
    return CliTestSupport.run(all);
  }

  private byte[] bytes(String source, int count, int threads, String... extra) throws IOException {
    CliTestSupport.Result r = exec(source, count, threads, extra);
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllBytes(outDir.resolve("run" + runCounter + "/output.json"));
  }

  private List<String> values(String field, String source, int count, int threads, String... extra)
      throws IOException {
    String text = new String(bytes(source, count, threads, extra), StandardCharsets.UTF_8);
    return text.lines()
        .map(
            l -> {
              try {
                JsonNode n = JSON.readTree(l).get(field);
                return n.isNull() ? null : n.asText();
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  private Path fakerTypes(String yaml) throws IOException {
    return Files.writeString(tempDir.resolve("types.yaml"), yaml);
  }

  private static int codePoints(String s) {
    return s.codePointCount(0, s.length());
  }

  // ── no drift ──────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"1", "4"})
  void shouldProduceIdenticalBytesWhenCapNeverTriggers(int threads) throws IOException {
    String[] rest = {
      field("id", "serial"),
      field("name", "char[3..12]"),
      field("n", "int[1..1000000]"),
      field("tag", "enum[A,B,C]")
    };
    structure(NAME, "italy", join(field("city", "city"), rest));
    byte[] base = bytes(NAME, 3000, threads);

    structure(NAME, "italy", join(field("city", "city", "max_length: 1000"), rest));
    byte[] capped = bytes(NAME, 3000, threads);

    assertThat(capped).isEqualTo(base);
  }

  @Test
  void shouldProduceIdenticalBytesWhenCharCapIsAtOrAboveDeclaredMax() throws IOException {
    structure(NAME, null, field("name", "char[3..12]"), field("n", "int[1..1000]"));
    byte[] base = bytes(NAME, 1000, 4);

    structure(
        NAME, null, field("name", "char[3..12]", "max_length: 12"), field("n", "int[1..1000]"));

    assertThat(bytes(NAME, 1000, 4)).isEqualTo(base);
  }

  @Test
  void shouldProduceIdenticalBytesWhenBytesCapNeverTriggers() throws IOException {
    structure(NAME, "germany", field("city", "city"), field("n", "int[1..1000]"));
    byte[] base = bytes(NAME, 2000, 4);

    structure(
        NAME,
        "germany",
        field("city", "city", "max_length: 1000", "length_unit: bytes"),
        field("n", "int[1..1000]"));

    assertThat(bytes(NAME, 2000, 4)).isEqualTo(base);
  }

  private static String join(String first, String... rest) {
    return first + String.join("", rest);
  }

  // ── cap triggers ──────────────────────────────────────────────────────────────

  @Test
  void shouldKeepEveryValueWithinCapAndMostValuesUntruncatedWhenCountryIsCapped()
      throws IOException {
    structure(NAME, null, field("country", "country"));
    List<String> uncapped = values("country", NAME, 5000, 4);
    Set<String> names = new HashSet<>(uncapped);

    structure(NAME, null, field("country", "country", "max_length: 8"));
    List<String> capped = values("country", NAME, 5000, 4);

    assertThat(capped).hasSize(5000);
    assertThat(capped).allSatisfy(v -> assertThat(codePoints(v)).isLessThanOrEqualTo(8));
    long real = capped.stream().filter(names::contains).count();
    assertThat(real).as("values that are an untruncated country name").isGreaterThan(2500);
    // attempt 1 is untouched: a value that already fitted is exactly the uncapped value
    for (int i = 0; i < uncapped.size(); i++) {
      if (codePoints(uncapped.get(i)) <= 8) {
        assertThat(capped.get(i)).as("record %d", i).isEqualTo(uncapped.get(i));
      }
    }
  }

  @Test
  void shouldBeDeterministicAcrossRunsAndThreadCountsWhenCapTriggers() throws IOException {
    structure(
        NAME,
        null,
        field("country", "country", "max_length: 8"),
        field("city", "city", "max_length: 6"),
        field("n", "int[1..1000]"));

    byte[] one = bytes(NAME, 3000, 1);
    byte[] oneAgain = bytes(NAME, 3000, 1);
    byte[] four = bytes(NAME, 3000, 4);
    byte[] fourAgain = bytes(NAME, 3000, 4);

    assertThat(oneAgain).isEqualTo(one);
    assertThat(fourAgain).isEqualTo(four);
    assertThat(four).isEqualTo(one);
  }

  @Test
  void shouldKeepUtf8LengthWithinCapAndValidTextWhenUnitIsBytes() throws IOException {
    structure(NAME, "germany", field("city", "city", "max_length: 8", "length_unit: bytes"));
    structure("plain", "germany", field("city", "city"));
    byte[] raw = bytes(NAME, 5000, 4);
    boolean hasNonAscii =
        values("city", "plain", 5000, 4).stream()
            .anyMatch(v -> v.length() != v.getBytes(StandardCharsets.UTF_8).length);
    assertThat(hasNonAscii).as("sanity: germany cities contain non-ASCII").isTrue();

    List<String> capped = values("city", NAME, 5000, 4);

    assertThat(capped).hasSize(5000);
    for (String v : capped) {
      byte[] utf8 = v.getBytes(StandardCharsets.UTF_8);
      assertThat(utf8.length).as(v).isLessThanOrEqualTo(8);
      assertThat(new String(utf8, StandardCharsets.UTF_8)).as("round trip").isEqualTo(v);
      assertThat(v).as("no lone surrogate / replacement char").doesNotContain("�");
    }
    // the raw output must itself be well-formed UTF-8
    assertThat(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString())
        .doesNotContain("�");
  }

  @Test
  void shouldCountCodePointsNotUtf16UnitsWhenUnitIsChars() throws IOException {
    structure(NAME, null, field("e", "cli_emoji", "max_length: 4"));
    Path types = fakerTypes("types:\n  cli_emoji: \"regex:" + EMOJIS + "\"\n");

    List<String> got = values("e", NAME, 20, 1, "--faker-types", types.toString());

    assertThat(got).hasSize(20).allSatisfy(v -> assertThat(v).isEqualTo("😀😀😀😀"));
  }

  @Test
  void shouldNeverSplitSupplementaryCharacterWhenUnitIsBytes() throws IOException {
    // 10 emojis = 40 bytes; cap 10 bytes -> 2 whole emojis (8 bytes), never 2.5
    structure(NAME, null, field("e", "cli_emoji", "max_length: 10", "length_unit: bytes"));
    Path types = fakerTypes("types:\n  cli_emoji: \"regex:" + EMOJIS + "\"\n");

    List<String> got = values("e", NAME, 20, 1, "--faker-types", types.toString());

    assertThat(got).hasSize(20).allSatisfy(v -> assertThat(v).isEqualTo("😀😀"));
  }

  @Test
  void shouldStripTrailingWhitespaceAfterTruncation() throws IOException {
    // "ab" + 8 spaces + "cd", cap 6 -> "ab    " -> "ab"
    structure(NAME, null, field("t", "cli_gap", "max_length: 6"));
    Path types = fakerTypes("types:\n  cli_gap: \"regex:ab {8}cd\"\n");

    List<String> got = values("t", NAME, 10, 1, "--faker-types", types.toString());

    assertThat(got).hasSize(10).allSatisfy(v -> assertThat(v).isEqualTo("ab"));
  }

  @Test
  void shouldKeepWhitespaceOnlyTruncationInsteadOfReturningEmpty() throws IOException {
    // 6 spaces + "x", cap 4 -> "    " (all whitespace): stripping would leave empty, so keep it
    structure(NAME, null, field("t", "cli_pad", "max_length: 4"));
    Path types = fakerTypes("types:\n  cli_pad: \"regex:[ ]{6}x\"\n");

    List<String> got = values("t", NAME, 10, 1, "--faker-types", types.toString());

    assertThat(got).hasSize(10).allSatisfy(v -> assertThat(v).isEqualTo("    "));
  }

  @Test
  void shouldCapNonNullValuesAndKeepNullRateWhenNullableIsCombined() throws IOException {
    structure(NAME, null, field("country", "country", "max_length: 8", "nullable: 0.3"));

    List<String> got = values("country", NAME, 2000, 4);

    long nulls = got.stream().filter(v -> v == null).count();
    assertThat(nulls).as("null count for rate 0.3 over 2000").isBetween(480L, 720L);
    assertThat(got.stream().filter(v -> v != null))
        .isNotEmpty()
        .allSatisfy(v -> assertThat(codePoints(v)).isLessThanOrEqualTo(8));
  }

  @Test
  void shouldCapNestedObjectFieldLikeTopLevelOne() throws IOException {
    structure("addr", null, field("country", "country", "max_length: 8"), field("n", "serial"));
    structure(NAME, null, field("id", "serial"), field("home", "object[addr]"));

    String text = new String(bytes(NAME, 1000, 4), StandardCharsets.UTF_8);

    assertThat(text.lines().toList())
        .hasSize(1000)
        .allSatisfy(
            l -> {
              String c = JSON.readTree(l).get("home").get("country").asText();
              assertThat(codePoints(c)).isLessThanOrEqualTo(8);
            });
  }

  // ── validation ────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.5", "\"abc\"", "true"})
  void shouldRejectInvalidMaxLengthNamingTheField(String value) throws IOException {
    structure(NAME, null, field("city", "city", "max_length: " + value));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("city").contains("max_length").doesNotContain("Unrecognized");
    assertThat(outDir.resolve("run" + runCounter + "/output.json")).doesNotExist();
  }

  @Test
  void shouldRejectLengthUnitWithoutMaxLength() throws IOException {
    structure(NAME, null, field("city", "city", "length_unit: bytes"));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("city").contains("length_unit").doesNotContain("Unrecognized");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bits", "BYTES", "\"\"", "3", "true"})
  void shouldRejectUnknownLengthUnit(String unit) throws IOException {
    structure(NAME, null, field("city", "city", "max_length: 9", "length_unit: " + unit));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("city").contains("length_unit").doesNotContain("Unrecognized");
  }

  @ParameterizedTest
  @ValueSource(strings = {"chars", "bytes"})
  void shouldAcceptBothLengthUnits(String unit) throws IOException {
    structure(NAME, null, field("city", "city", "max_length: 9", "length_unit: " + unit));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).as(r.err()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "int[1..9]",
        "decimal[0.0..9.0]",
        "date[2020-01-01..2025-12-31]",
        "timestamp[2024-01-01T00:00:00..2024-12-31T23:59:59]",
        "boolean",
        "enum[A,B,C]",
        "cycle[A,B,C]",
        "serial",
        "unique[1..1000000]",
        "ref[parent.id, 1..50]",
        "object[parent]",
        "array[char[2..4], 1..3]"
      })
  void shouldRejectMaxLengthOnNonTextTypesNamingFieldAndType(String type) throws IOException {
    structure("parent", null, field("id", "serial"));
    structure(NAME, null, field("fld", type, "max_length: 5"));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("fld").contains("max_length").doesNotContain("Unrecognized");
    assertThat(outDir.resolve("run" + runCounter + "/output.json")).doesNotExist();
  }

  @Test
  void shouldRejectCharMinAboveMaxLength() throws IOException {
    structure(NAME, null, field("name", "char[10..20]", "max_length: 5"));

    CliTestSupport.Result r = exec(NAME, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("name").contains("max_length").doesNotContain("Unrecognized");
  }

  @Test
  void shouldAcceptCharRangeWhenMaxLengthIsBetweenMinAndMax() throws IOException {
    structure(NAME, null, field("name", "char[3..12]", "max_length: 5"));

    List<String> got = values("name", NAME, 500, 1);

    assertThat(got).allSatisfy(v -> assertThat(codePoints(v)).isBetween(3, 5));
  }

  @Test
  void shouldAcceptCharMinEqualToMaxLength() throws IOException {
    structure(NAME, null, field("name", "char[5..9]", "max_length: 5"));

    List<String> got = values("name", NAME, 200, 1);

    assertThat(got).allSatisfy(v -> assertThat(codePoints(v)).isEqualTo(5));
  }
}
