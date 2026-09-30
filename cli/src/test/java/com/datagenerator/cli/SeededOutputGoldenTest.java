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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Reproducibility contract for seeded (non-{@code serial}/{@code unique}) fields: pins the exact
 * records produced for seed 42 across every primitive kind, end to end through {@code execute}.
 *
 * <p>Any change to seed derivation, the engine's per-record reseeding, or a primitive generator's
 * use of randomness changes these values. That must be a deliberate, CHANGELOG-noted break (as in
 * #343), never a side effect. Records are compared parsed, so JSON key order is not pinned.
 */
class SeededOutputGoldenTest {

  private static final List<String> GOLDEN_SEED_42 =
      List.of(
          "{\"dt\":\"2024-09-08\",\"b\":false,\"c\":\"OshJZ\",\"d\":62.6,\"e\":\"GREEN\",\"i\":718,\"ts\":\"2024-06-21T10:06:09Z\",\"arr\":[6]}",
          "{\"dt\":\"2023-12-02\",\"b\":true,\"c\":\"DvPHy\",\"d\":98.5,\"e\":\"GREEN\",\"i\":963,\"ts\":\"2024-06-24T01:18:37Z\",\"arr\":[1]}",
          "{\"dt\":\"2022-04-24\",\"b\":true,\"c\":\"nFV\",\"d\":33.2,\"e\":\"RED\",\"i\":401,\"ts\":\"2024-03-19T07:48:06Z\",\"arr\":[6,4,3]}",
          "{\"dt\":\"2020-11-10\",\"b\":true,\"c\":\"EfHaYc\",\"d\":28.6,\"e\":\"BLUE\",\"i\":645,\"ts\":\"2024-10-08T00:36:54Z\",\"arr\":[4,1]}",
          "{\"dt\":\"2025-01-19\",\"b\":true,\"c\":\"QBVj\",\"d\":42.6,\"e\":\"RED\",\"i\":167,\"ts\":\"2024-12-18T01:27:28Z\",\"arr\":[3,3]}");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir Path tempDir;

  private Path structDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    Files.createDirectories(structDir);
    Files.writeString(
        structDir.resolve("prims.yaml"),
        """
        name: prims
        data:
          i:
            datatype: "int[1..1000]"
          d:
            datatype: "decimal[0.0..100.0]"
          c:
            datatype: "char[3..8]"
          b:
            datatype: "boolean"
          dt:
            datatype: "date[2020-01-01..2025-12-31]"
          ts:
            datatype: "timestamp[2024-01-01T00:00:00..2024-12-31T23:59:59]"
          e:
            datatype: "enum[RED,GREEN,BLUE]"
          arr:
            datatype: "array[int[1..9], 1..3]"
        """);
  }

  @Test
  void shouldMatchPinnedRecordsWhenSeedIs42OnOneThread() throws IOException {
    assertThat(run("42", "1")).containsExactlyElementsOf(parse(String.join("\n", GOLDEN_SEED_42)));
  }

  @Test
  void shouldMatchPinnedRecordsWhenSeedIs42OnFourThreads() throws IOException {
    assertThat(run("42", "4")).containsExactlyElementsOf(parse(String.join("\n", GOLDEN_SEED_42)));
  }

  @Test
  void shouldShareNoRecordWithSeed42WhenSeedIs43() throws IOException {
    // Adjacent seeds must yield unrelated data, not a permutation of the same records (#343).
    // Compared against a live seed-42 run, not the golden, so it holds whatever the algorithm.
    assertThat(run("43", "1")).doesNotContainAnyElementsOf(run("42", "1"));
  }

  private List<JsonNode> run(String seed, String threads) throws IOException {
    Path out = tempDir.resolve("out-" + seed + "-" + threads);
    Path job = writeJob(seed, out);
    int exit =
        new CommandLine(new ExecuteCommand())
            .execute("--job", job.toString(), "--count", "5", "--threads", threads);
    assertThat(exit).isZero();
    return parse(Files.readString(out.resolve("output.json")));
  }

  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  private Path writeJob(String seed, Path out) throws IOException {
    Path job = tempDir.resolve("job-" + out.getFileName() + ".yaml");
    Files.writeString(
        job,
        """
        source: prims.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: %s
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), seed, out.toAbsolutePath()));
    return job;
  }

  private static List<JsonNode> parse(String ndjson) throws IOException {
    List<JsonNode> records = new ArrayList<>();
    for (String line : ndjson.strip().split("\n")) {
      records.add(MAPPER.readTree(line));
    }
    return records;
  }
}
