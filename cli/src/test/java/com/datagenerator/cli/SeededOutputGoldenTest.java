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
 * #343, #374), never a side effect. Records are compared parsed; key order is pinned by
 * ExecuteOutputFormatsTest.
 */
class SeededOutputGoldenTest {

  private static final List<String> GOLDEN_SEED_42 =
      List.of(
          "{\"i\":333,\"d\":74.0,\"c\":\"OshJZ\",\"b\":true,\"dt\":\"2024-09-24\",\"ts\":\"2024-03-17T18:46:41Z\",\"e\":\"BLUE\",\"arr\":[6]}",
          "{\"i\":365,\"d\":77.6,\"c\":\"DvPHy\",\"b\":false,\"dt\":\"2022-10-18\",\"ts\":\"2024-09-14T05:14:57Z\",\"e\":\"RED\",\"arr\":[1]}",
          "{\"i\":338,\"d\":70.7,\"c\":\"nFV\",\"b\":false,\"dt\":\"2023-07-08\",\"ts\":\"2024-12-15T01:35:04Z\",\"e\":\"RED\",\"arr\":[6,4,3]}",
          "{\"i\":950,\"d\":56.4,\"c\":\"EfHaYc\",\"b\":false,\"dt\":\"2022-06-27\",\"ts\":\"2024-03-03T18:19:58Z\",\"e\":\"GREEN\",\"arr\":[4,1]}",
          "{\"i\":776,\"d\":38.0,\"c\":\"QBVj\",\"b\":false,\"dt\":\"2022-01-17\",\"ts\":\"2024-04-05T09:34:54Z\",\"e\":\"GREEN\",\"arr\":[3,3]}");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Raw-byte fingerprint of the file/NDJSON write path (serialize + coalesce) for seed 42, 1000
  // records across 4 worker threads. 1000 >= the 1000 single-threaded threshold, so this exercises
  // the multi-threaded, multi-chunk path with worker-side folding. Pins the EXACT output bytes —
  // JSON encoding, newline framing and global record order — the invariant the per-chunk serializer
  // per-chunk serializer must preserve. Change only on a deliberate, CHANGELOG-noted break.
  private static final String GOLDEN_SHA_SEED_42_1000 =
      "07c11380dbd12219b0b8f8a853f7ef48d9725039c8c7268b10e121abaa3a9f7e";

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

  @Test
  void shouldMatchRawByteShaForSeed42MultiChunkMultiThread() throws IOException {
    // Byte-exact characterization of the coalescing file path across multiple chunks and threads.
    // Parsed-record equality (above) does not pin newline framing or key order; this does.
    Path out = tempDir.resolve("out-sha");
    Path job = writeJob("42", out);
    int exit =
        new CommandLine(new ExecuteCommand())
            .execute("--job", job.toString(), "--count", "1000", "--threads", "4");
    assertThat(exit).isZero();
    byte[] bytes = Files.readAllBytes(out.resolve("output.json"));
    assertThat(sha256(bytes)).isEqualTo(GOLDEN_SHA_SEED_42_1000);
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
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
