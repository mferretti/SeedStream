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
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end contract of {@code cycle[...]} (#456) through the real {@code execute} command. */
class CycleTypeExecuteTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<String> ABC = List.of("A", "B", "C");

  @TempDir Path tempDir;

  private Path structDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    Files.createDirectories(structDir);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private void structure(String name, String dataYaml) throws IOException {
    Files.writeString(
        structDir.resolve(name + ".yaml"), "name: " + name + "\ndata:\n" + dataYaml + "\n");
  }

  private static String field(String name, String datatype) {
    return "  " + name + ":\n    datatype: \"" + datatype + "\"\n";
  }

  /** Runs the job through the real CLI; output lands in {@code <tempDir>/<outName>.json}. */
  private CliTestSupport.Result execute(String source, String outName, String... extraArgs)
      throws IOException {
    Path job = tempDir.resolve(outName + "-job.yaml");
    Files.writeString(
        job,
        "source: "
            + source
            + ".yaml\ntype: file\nstructures_path: "
            + structDir.toAbsolutePath()
            + "\nseed:\n  type: embedded\n  value: 42\nconf:\n  path: "
            + tempDir.toAbsolutePath()
            + "/"
            + outName
            + "\n");
    List<String> args = new ArrayList<>(List.of("execute", "--job", job.toString()));
    args.addAll(List.of(extraArgs));
    return CliTestSupport.run(args.toArray(String[]::new));
  }

  private Path output(String outName) {
    return tempDir.resolve(outName + ".json");
  }

  private <T> List<T> column(String outName, Function<JsonNode, T> extractor) throws IOException {
    List<T> out = new ArrayList<>();
    for (String line : Files.readAllLines(output(outName))) {
      out.add(extractor.apply(MAPPER.readTree(line)));
    }
    return out;
  }

  private static List<String> expectedCycle(List<String> values, int count) {
    return IntStream.range(0, count).mapToObj(i -> values.get(i % values.size())).toList();
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  @Test
  void shouldEmitValuesInOrderAndWrapWhenCountExceedsValueCount() throws IOException {
    structure("lookup", field("c", "cycle[A,B,C]") + field("x", "int[1..1000000]"));

    CliTestSupport.Result r = execute("lookup", "wrap", "--count", "7");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(column("wrap", n -> n.get("c").asText()))
        .containsExactly("A", "B", "C", "A", "B", "C", "A");
  }

  @Test
  void shouldEmitEachValueExactlyOnceWhenCountEqualsValueCount() throws IOException {
    structure(
        "categories",
        field("name", "cycle[Beverages,Condiments,Confections,Dairy,Grains,Meat,Produce,Seafood]"));

    CliTestSupport.Result r = execute("categories", "once", "--count", "8");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(column("once", n -> n.get("name").asText()))
        .containsExactly(
            "Beverages",
            "Condiments",
            "Confections",
            "Dairy",
            "Grains",
            "Meat",
            "Produce",
            "Seafood");
  }

  @Test
  void shouldAlwaysEmitTheValueWhenCycleHasASingleValue() throws IOException {
    structure("single", field("c", "cycle[only]"));

    CliTestSupport.Result r = execute("single", "single", "--count", "4");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(column("single", n -> n.get("c").asText())).containsOnly("only").hasSize(4);
  }

  @Test
  void shouldNotShiftOtherFieldsWhenCycleFieldIsAdded() throws IOException {
    // cycle first: a random draw by c would shift every x that follows it
    structure("with_c", field("c", "cycle[A,B,C]") + field("x", "int[1..1000000]"));
    structure("without_c", field("x", "int[1..1000000]"));

    assertThat(execute("with_c", "with", "--count", "1000").exit()).isZero();
    assertThat(execute("without_c", "without", "--count", "1000").exit()).isZero();

    List<Long> withC = column("with", n -> n.get("x").asLong());
    List<Long> withoutC = column("without", n -> n.get("x").asLong());
    assertThat(withC).hasSize(1000).containsExactlyElementsOf(withoutC);
    assertThat(withC.stream().distinct().count()).as("x really is random").isGreaterThan(900);
  }

  @Test
  void shouldProduceIdenticalCycleColumnRegardlessOfThreadCount() throws IOException {
    structure("lookup", field("c", "cycle[A,B,C]") + field("x", "int[1..1000000]"));
    List<String> expected = expectedCycle(ABC, 5000);

    for (int threads : new int[] {1, 4, 8}) {
      String out = "threads_" + threads;
      CliTestSupport.Result r =
          execute("lookup", out, "--count", "5000", "--threads", String.valueOf(threads));

      assertThat(r.exit()).as(r.err()).isZero();
      assertThat(column(out, n -> n.get("c").asText()))
          .as("c column with threads=%d", threads)
          .containsExactlyElementsOf(expected);
    }
  }

  @Test
  void shouldApplyIndexSemanticsInsideOneToOneObject() throws IOException {
    structure("inner", field("c", "cycle[A,B,C]"));
    structure("outer", field("id", "serial") + field("inner", "object[inner]"));

    CliTestSupport.Result r = execute("outer", "nested", "--count", "7");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(column("nested", n -> n.get("inner").get("c").asText()))
        .containsExactlyElementsOf(expectedCycle(ABC, 7));
  }

  @Test
  void shouldFailAtStartupWhenCycleIsInsideArray() throws IOException {
    structure("tagged", field("tags", "array[cycle[A,B], 1..3]"));

    CliTestSupport.Result r = execute("tagged", "bad", "--count", "5");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("cycle[...]").contains("tagged.tags").contains("array[...]");
    assertThat(output("bad")).doesNotExist();
  }

  @Test
  void shouldKeepEnumRandomAndUnchangedWhenCycleExistsInTheCodebase() throws IOException {
    // guard: enum stays a random draw, not an index-driven cycle
    structure("plain_enum", field("e", "enum[A,B,C]"));

    CliTestSupport.Result r = execute("plain_enum", "enum", "--count", "200");

    assertThat(r.exit()).as(r.err()).isZero();
    List<String> values = column("enum", n -> n.get("e").asText());
    assertThat(values).hasSize(200).isNotEqualTo(expectedCycle(ABC, 200));
    assertThat(values).containsOnly("A", "B", "C");
  }
}
