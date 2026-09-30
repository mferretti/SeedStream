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
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Reproducibility contract for the unique type: proves that the same seed produces identical
 * sequences of unique values across separate runs and thread counts. This guards against
 * algorithmic changes that would break existing users' reproducible data generation for the same
 * seed.
 */
class UniqueSequenceReproducibilityTest {
  private static final String OPT_JOB = "--job";
  private static final String OPT_COUNT = "--count";
  private static final String OPT_SEED = "--seed";
  private static final String OUTPUT_JSON = "output.json";
  private static final List<Long> ONE_TO_THOUSAND =
      LongStream.rangeClosed(1, 1000).boxed().toList();

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);

    // Define structure with three unique fields: one standalone, two in a group
    Files.writeString(
        structDir.resolve("seq.yaml"),
        """
        name: seq
        data:
          uid:
            datatype: "unique[1..1000]"
          a:
            datatype: "unique[pair, 1..20]"
          b:
            datatype: "unique[pair, 1..50]"
        """);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  private Path writeJobFile(String seed, String outPath) throws IOException {
    Path jobFile = tempDir.resolve("job.yaml");
    Files.writeString(
        jobFile,
        """
        source: seq.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: %s
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), seed, outPath));
    return jobFile;
  }

  private int execute(String... args) {
    return new CommandLine(new ExecuteCommand()).execute(args);
  }

  /** Parses a JSON NDJSON file and extracts a list of values for a given field. */
  private List<Long> extractFieldSequence(Path jsonFile, String fieldName) throws IOException {
    List<Long> sequence = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    for (String line : Files.readAllLines(jsonFile)) {
      JsonNode node = mapper.readTree(line);
      sequence.add(node.get(fieldName).asLong());
    }
    return sequence;
  }

  /** Parses a JSON NDJSON file and extracts (a, b) tuples as concatenated strings. */
  private List<String> extractTupleSequence(Path jsonFile, String fieldA, String fieldB)
      throws IOException {
    List<String> sequence = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    for (String line : Files.readAllLines(jsonFile)) {
      JsonNode node = mapper.readTree(line);
      long a = node.get(fieldA).asLong();
      long b = node.get(fieldB).asLong();
      sequence.add(a + "," + b);
    }
    return sequence;
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  @Test
  void shouldProduceIdenticalUniqueSequenceAcrossSeparateRunsWithSameSeed() throws Exception {
    // Run 1
    Path jobFile1 = writeJobFile("42", outDir.resolve("run1").toString());
    Path output1 = outDir.resolve("run1/output.json");
    int code1 = execute(OPT_JOB, jobFile1.toString(), OPT_COUNT, "1000");
    assertThat(code1).isZero();
    assertThat(output1).exists();

    List<Long> uidSeq1 = extractFieldSequence(output1, "uid");
    List<String> tupleSeq1 = extractTupleSequence(output1, "a", "b");

    // Run 2: same seed, separate invocation
    Path jobFile2 = writeJobFile("42", outDir.resolve("run2").toString());
    Path output2 = outDir.resolve("run2/output.json");
    int code2 = execute(OPT_JOB, jobFile2.toString(), OPT_COUNT, "1000");
    assertThat(code2).isZero();
    assertThat(output2).exists();

    List<Long> uidSeq2 = extractFieldSequence(output2, "uid");
    List<String> tupleSeq2 = extractTupleSequence(output2, "a", "b");

    // Order matters: the two runs must be byte-identical files, and the sequences equal element by
    // element (List.equals is positional — same values in a different order fail).
    assertThat(Files.mismatch(output1, output2))
        .as("run 1 and run 2 output must be byte-identical (-1 = no mismatching byte)")
        .isEqualTo(-1L);
    assertThat(uidSeq2).containsExactlyElementsOf(uidSeq1);
    assertThat(tupleSeq2).containsExactlyElementsOf(tupleSeq1);

    // uid must be a full permutation of 1..1000
    assertThat(uidSeq1).containsExactlyInAnyOrderElementsOf(ONE_TO_THOUSAND);

    // All (a, b) tuples must be distinct
    assertThat(tupleSeq1).as("all 1000 (a,b) tuples must be distinct").doesNotHaveDuplicates();
  }

  @Test
  void shouldProduceIdenticalUniqueSequenceRegardlessOfThreadCount() throws Exception {
    List<Long> referenceUid = null;
    List<String> referenceTuple = null;
    Path referenceOutput = null;

    for (int threads : new int[] {1, 4, 8}) {
      Path jobFile = writeJobFile("42", outDir.resolve("threads_" + threads).toString());
      Path output = outDir.resolve("threads_" + threads + "/output.json");
      int code =
          execute(
              OPT_JOB, jobFile.toString(), OPT_COUNT, "1000", "--threads", String.valueOf(threads));
      assertThat(code).isZero();

      List<Long> uidSeq = extractFieldSequence(output, "uid");
      List<String> tupleSeq = extractTupleSequence(output, "a", "b");

      if (referenceUid == null) {
        referenceUid = uidSeq;
        referenceTuple = tupleSeq;
        referenceOutput = output;
      } else {
        assertThat(Files.mismatch(referenceOutput, output))
            .as("output with threads=%d must be byte-identical to threads=1", threads)
            .isEqualTo(-1L);
        assertThat(uidSeq)
            .as("uid sequence with threads=%d must equal threads=1 reference", threads)
            .isEqualTo(referenceUid);
        assertThat(tupleSeq)
            .as("(a,b) tuple sequence with threads=%d must equal threads=1 reference", threads)
            .isEqualTo(referenceTuple);
      }
    }
  }

  @Test
  void shouldProduceDifferentSequenceWhenSeedDiffers() throws Exception {
    // Seed 42
    Path jobFile42 = writeJobFile("42", outDir.resolve("seed42").toString());
    Path output42 = outDir.resolve("seed42/output.json");
    int code42 = execute(OPT_JOB, jobFile42.toString(), OPT_COUNT, "1000");
    assertThat(code42).isZero();
    List<Long> uidSeq42 = extractFieldSequence(output42, "uid");

    // Seed 43
    Path jobFile43 = writeJobFile("43", outDir.resolve("seed43").toString());
    Path output43 = outDir.resolve("seed43/output.json");
    int code43 = execute(OPT_JOB, jobFile43.toString(), OPT_COUNT, "1000");
    assertThat(code43).isZero();
    List<Long> uidSeq43 = extractFieldSequence(output43, "uid");

    // Sequences must differ
    assertThat(uidSeq43).isNotEqualTo(uidSeq42);

    // But both must be valid permutations of 1..1000
    assertThat(uidSeq42).containsExactlyInAnyOrderElementsOf(ONE_TO_THOUSAND);
    assertThat(uidSeq43).containsExactlyInAnyOrderElementsOf(ONE_TO_THOUSAND);
  }

  @Test
  void shouldMatchPinnedGoldenSequenceForSeed42() throws Exception {
    // This test serves as the reproducibility contract: if the algorithm changes in a way that
    // affects the sequence order, this test will fail and alert that existing users' output will
    // differ for the same seed — which is a breaking change that must be noted in the CHANGELOG.
    Path jobFile = writeJobFile("42", outDir.resolve("golden").toString());
    Path output = outDir.resolve("golden/output.json");
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1000");
    assertThat(code).isZero();

    List<Long> uidSeq = extractFieldSequence(output, "uid");
    List<String> tupleSeq = extractTupleSequence(output, "a", "b");

    // REPRODUCIBILITY CONTRACT: First 20 uid values for seed 42, count 1000.
    // Changes to UniqueGenerator or FeistelPermutation will alter this sequence.
    // Any change is a breaking change that affects reproducibility for users depending on seed 42.
    List<Long> expectedFirstUids =
        List.of(
            635L, 811L, 460L, 290L, 222L, 890L, 459L, 668L, 916L, 735L, 318L, 30L, 354L, 232L, 283L,
            330L, 771L, 729L, 849L, 289L);

    List<String> expectedFirstTuples =
        List.of("18,45", "9,27", "19,6", "16,21", "12,7", "10,6", "1,2", "4,28", "9,32", "3,2");

    assertThat(uidSeq.subList(0, 20))
        .as("first 20 uid values for seed 42 must match pinned golden sequence")
        .isEqualTo(expectedFirstUids);

    assertThat(tupleSeq.subList(0, 10))
        .as("first 10 (a,b) tuples for seed 42 must match pinned golden sequence")
        .isEqualTo(expectedFirstTuples);
  }

  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  private List<Long> runSingleField(String structure, String datatype, String field)
      throws IOException {
    Files.writeString(
        structDir.resolve(structure + ".yaml"),
        """
        name: %s
        data:
          %s:
            datatype: "%s"
        """
            .formatted(structure, field, datatype));
    Path jobFile = tempDir.resolve(structure + "_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: %s.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(
                structure, structDir.toAbsolutePath(), outDir.resolve(structure).toString()));
    assertThat(execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1000")).isZero();
    return extractFieldSequence(outDir.resolve(structure + "/output.json"), field);
  }

  @Test
  void shouldMatchPinnedGoldenSequenceWhenMaxIsCount() throws Exception {
    List<Long> seq = runSingleField("counted", "unique[1..count]", "uid");
    assertThat(seq).containsExactlyInAnyOrderElementsOf(ONE_TO_THOUSAND);
    assertThat(seq.subList(0, 20))
        .as("first 20 unique[1..count] values for seed 42, count 1000")
        .containsExactly(
            357L, 693L, 747L, 665L, 736L, 921L, 984L, 390L, 617L, 891L, 536L, 138L, 542L, 854L, 78L,
            475L, 820L, 180L, 999L, 120L);
  }

  @Test
  void shouldProduceConsecutiveValuesWhenSerialWithMin() throws Exception {
    List<Long> seq = runSingleField("serial_min", "serial[1000]", "sid");
    assertThat(seq.subList(0, 5)).containsExactly(1000L, 1001L, 1002L, 1003L, 1004L);
  }
}
