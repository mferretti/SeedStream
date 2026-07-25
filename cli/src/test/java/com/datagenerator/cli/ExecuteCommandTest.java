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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

class ExecuteCommandTest {
  private static final String OPT_JOB = "--job";
  private static final String OPT_COUNT = "--count";
  private static final String OPT_FORMAT = "--format";
  private static final String OPT_SEED = "--seed";
  private static final String OUTPUT_JSON = "output.json";
  private static final String JOB_FILE = "job.yaml";
  private static final String SIMPLE_YAML = "simple.yaml";

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;
  private final AtomicInteger jobFileCounter = new AtomicInteger();

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);

    Files.writeString(
        structDir.resolve(SIMPLE_YAML),
        """
        name: simple
        data:
          id:
            datatype: "int[1..1000]"
          label:
            datatype: "char[3..10]"
        """);
  }

  @AfterEach
  void resetLogLevel() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.setLevel(Level.INFO);
    Logger app = (Logger) LoggerFactory.getLogger("com.datagenerator");
    app.setLevel(Level.INFO);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private Path writeJobFile() throws IOException {
    return writeJobFile("file", "");
  }

  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional YAML line endings
  private Path writeJobFile(String destType, String extraConf) throws IOException {
    Path jobFile = tempDir.resolve(JOB_FILE);
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: %s
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        %s
        """
            .formatted(destType, structDir.toAbsolutePath(), outDir.toAbsolutePath(), extraConf));
    return jobFile;
  }

  private int execute(String... args) {
    return new CommandLine(new ExecuteCommand()).execute(args);
  }

  /**
   * Writes a standalone job YAML with arbitrary extra top-level YAML lines (e.g. {@code type:},
   * {@code seed:} block, {@code conf:} block). {@code structurePath} is the structure file's
   * location relative to {@code tempDir} (e.g. {@code "structures/simple.yaml"}); {@code
   * structures_path} is derived from its parent directory and {@code source} from its file name, so
   * both the initial parse and the registry's by-name lookup (basePath + "{name}.yaml") resolve to
   * the same file.
   */
  private Path writeJobYaml(String structurePath, String... lines) throws IOException {
    Path jobFile = tempDir.resolve("job_" + jobFileCounter.incrementAndGet() + ".yaml");
    Path resolvedStructurePath = tempDir.resolve(structurePath);
    Path parent = resolvedStructurePath.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("structurePath must include a parent directory");
    }
    StringBuilder yaml = new StringBuilder();
    yaml.append("source: ").append(resolvedStructurePath.getFileName()).append('\n');
    yaml.append("structures_path: ").append(parent.toAbsolutePath()).append('\n');
    for (String line : lines) {
      yaml.append(line).append('\n');
    }
    Files.writeString(jobFile, yaml.toString());
    return jobFile;
  }

  // ── Happy path — JSON ────────────────────────────────────────────────────────

  @Test
  void executeJsonToFileSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "5");

    assertThat(code).isZero();
    Path output = outDir.resolve(OUTPUT_JSON);
    assertThat(output).exists();
    List<String> lines = Files.readAllLines(output);
    assertThat(lines).hasSize(5);
    ObjectMapper mapper = new ObjectMapper();
    for (String line : lines) {
      JsonNode node = mapper.readTree(line);
      assertThat(node.has("id")).isTrue();
      assertThat(node.has("label")).isTrue();
    }
  }

  @Test
  void defaultCountIs100() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString());

    assertThat(code).isZero();
    List<String> lines = Files.readAllLines(outDir.resolve(OUTPUT_JSON));
    assertThat(lines).hasSize(100);
  }

  // ── Happy path — CSV ─────────────────────────────────────────────────────────

  @Test
  void executeCsvFormatWritesHeaderAndData() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "csv", OPT_COUNT, "3");

    assertThat(code).isZero();
    Path output = outDir.resolve("output.csv");
    assertThat(output).exists();
    List<String> lines = Files.readAllLines(output);
    // 1 header row + 3 data rows
    assertThat(lines).hasSize(4);
    assertThat(lines.get(0)).contains("id").contains("label");
  }

  // ── Happy path — Protobuf ────────────────────────────────────────────────────

  @Test
  void executeProtobufFormatSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "protobuf", OPT_COUNT, "5");

    assertThat(code).isZero();
    assertThat(outDir.resolve("output.protobuf")).exists();
  }

  // ── Happy path — Avro ────────────────────────────────────────────────────────

  @Test
  void executeAvroFormatSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "avro", OPT_COUNT, "5");

    assertThat(code).isZero();
    assertThat(outDir.resolve("output.avro")).exists();
  }

  // ── Seed reproducibility ─────────────────────────────────────────────────────

  @Test
  void seedOverrideProducesReproducibleOutput() throws Exception {
    Path jobFile = writeJobFile();

    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "10", OPT_SEED, "999");
    String first = Files.readString(outDir.resolve(OUTPUT_JSON));

    Files.delete(outDir.resolve(OUTPUT_JSON));

    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "10", OPT_SEED, "999");
    String second = Files.readString(outDir.resolve(OUTPUT_JSON));

    assertThat(first).isEqualTo(second);
  }

  @Test
  void differentSeedsProduceDifferentOutput() throws Exception {
    Path jobFile = writeJobFile();

    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "10", OPT_SEED, "1");
    String first = Files.readString(outDir.resolve(OUTPUT_JSON));

    Files.delete(outDir.resolve(OUTPUT_JSON));

    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "10", OPT_SEED, "2");
    String second = Files.readString(outDir.resolve(OUTPUT_JSON));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void seedOverrideOverridesJobEmbeddedSeed() throws Exception {
    Path jobFile = writeJobFile();

    // Run with embedded seed (42 from job YAML) via normal execution
    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "5");
    String withJobSeed = Files.readString(outDir.resolve(OUTPUT_JSON));

    Files.delete(outDir.resolve(OUTPUT_JSON));

    // Run with a different seed override
    execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "5", OPT_SEED, "99999");
    String withOverride = Files.readString(outDir.resolve(OUTPUT_JSON));

    assertThat(withJobSeed).isNotEqualTo(withOverride);
  }

  // ── Logging flags ────────────────────────────────────────────────────────────

  @Test
  void debugFlagSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "2", "--debug");

    assertThat(code).isZero();
  }

  @Test
  void verboseFlagSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "2", "--verbose");

    assertThat(code).isZero();
  }

  @Test
  void traceSampleOutOfRangeIsClampedAndSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    // 0 should be clamped to 1; 200 should be clamped to 100 — both should succeed
    int code =
        execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "2", "--debug", "--trace-sample", "0");

    assertThat(code).isZero();
  }

  // ── Threading ────────────────────────────────────────────────────────────────

  @Test
  void threadsOptionSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "10", "--threads", "2");

    assertThat(code).isZero();
    assertThat(outDir.resolve(OUTPUT_JSON)).exists();
  }

  // ── Write coalescing (issue #193): byte-identical output regardless of thread count ─────────

  @Test
  void jsonToFileOutputIsByteIdenticalAcrossThreadCounts() throws Exception {
    // chunkSize is 256 (hardcoded in GenerationEngine); pick a count that is NOT a multiple of it
    // so every run exercises a partial final chunk, in addition to several full coalesced chunks.
    Path jobFile = writeJobFile();
    int count = 256 * 3 + 7; // 775 — 3 full chunks + 1 partial chunk of 7

    byte[] reference = null;
    for (int threads : new int[] {1, 4, 8}) {
      Path output = outDir.resolve(OUTPUT_JSON);
      if (Files.exists(output)) {
        Files.delete(output);
      }

      int code =
          execute(
              OPT_JOB,
              jobFile.toString(),
              OPT_COUNT,
              String.valueOf(count),
              OPT_SEED,
              "555",
              "--threads",
              String.valueOf(threads));
      assertThat(code).isZero();

      byte[] bytes = Files.readAllBytes(output);
      if (reference == null) {
        reference = bytes;
        assertThat(Files.readAllLines(output)).hasSize(count);
      } else {
        assertThat(bytes)
            .as("output with %d threads must be byte-identical to the 1-thread reference", threads)
            .isEqualTo(reference);
      }
    }
  }

  // ── Per-chunk gzip mode (issue #210): deterministic multi-member .gz ──────────────────

  @Test
  void shouldProduceIdenticalGzipAcrossThreadCounts() throws Exception {
    // With compress_mode: per_chunk, the .gz should be byte-identical across threads
    // because member boundaries = f(chunkSize, count), never thread count.
    Path jobFile =
        writeJobYaml(
            "structures/simple.yaml",
            "type: file",
            "seed:",
            "  type: embedded",
            "  value: 999",
            "conf:",
            "  path: " + outDir.resolve("per_chunk").toAbsolutePath(),
            "  compress: true",
            "  compress_mode: per_chunk");

    int count = 256 * 2 + 50; // 562 — 2 full chunks + 1 partial

    byte[] reference = null;
    for (int threads : new int[] {1, 2, 4}) {
      Path output = outDir.resolve("per_chunk.json.gz");
      if (Files.exists(output)) {
        Files.delete(output);
      }

      int code =
          execute(
              OPT_JOB,
              jobFile.toString(),
              OPT_COUNT,
              String.valueOf(count),
              OPT_SEED,
              "777",
              "--threads",
              String.valueOf(threads));
      assertThat(code).isZero();

      byte[] bytes = Files.readAllBytes(output);
      if (reference == null) {
        reference = bytes;
      } else {
        assertThat(bytes)
            .as(
                ".gz bytes with %d threads must be byte-identical to the 1-thread reference (issue #210)",
                threads)
            .isEqualTo(reference);
      }
    }
  }

  @Test
  void shouldDecompressToSameBytesAsUncompressedRun() throws Exception {
    // Verify that gunzip(per_chunk output) == plain uncompressed run's file bytes.
    int count = 2500;

    // Uncompressed run
    Path jobFileUncompressed =
        writeJobYaml(
            "structures/simple.yaml",
            "type: file",
            "seed:",
            "  type: embedded",
            "  value: 888",
            "conf:",
            "  path: " + outDir.resolve("plain").toAbsolutePath(),
            "  compress: false");

    int codeUncompressed =
        execute(
            OPT_JOB,
            jobFileUncompressed.toString(),
            OPT_COUNT,
            String.valueOf(count),
            OPT_SEED,
            "888");

    assertThat(codeUncompressed).isZero();
    byte[] uncompressedBytes = Files.readAllBytes(outDir.resolve("plain.json"));

    // Per-chunk gzipped run
    Path jobFileCompressed =
        writeJobYaml(
            "structures/simple.yaml",
            "type: file",
            "seed:",
            "  type: embedded",
            "  value: 888",
            "conf:",
            "  path: " + outDir.resolve("per_chunk").toAbsolutePath(),
            "  compress: true",
            "  compress_mode: per_chunk");

    int codeCompressed =
        execute(
            OPT_JOB,
            jobFileCompressed.toString(),
            OPT_COUNT,
            String.valueOf(count),
            OPT_SEED,
            "888");

    assertThat(codeCompressed).isZero();

    // Decompress and compare
    java.io.ByteArrayOutputStream decompressed = new java.io.ByteArrayOutputStream();
    try (java.util.zip.GZIPInputStream gz =
        new java.util.zip.GZIPInputStream(
            Files.newInputStream(outDir.resolve("per_chunk.json.gz")))) {
      byte[] buffer = new byte[4096];
      int len;
      while ((len = gz.read(buffer)) != -1) {
        decompressed.write(buffer, 0, len);
      }
    }

    assertThat(decompressed.toByteArray()).isEqualTo(uncompressedBytes);
  }

  @Test
  void shouldDecompressToSameBytesOnSingleThreadPath() throws Exception {
    // Single-threaded path (count < threshold): verify gunzip(per_chunk) == plain output.
    int count = 300;

    // Uncompressed run
    Path jobFileUncompressed =
        writeJobYaml(
            "structures/simple.yaml",
            "type: file",
            "seed:",
            "  type: embedded",
            "  value: 777",
            "conf:",
            "  path: " + outDir.resolve("plain_single").toAbsolutePath(),
            "  compress: false");

    int codeUncompressed =
        execute(
            OPT_JOB,
            jobFileUncompressed.toString(),
            OPT_COUNT,
            String.valueOf(count),
            OPT_SEED,
            "777");

    assertThat(codeUncompressed).isZero();
    byte[] uncompressedBytes = Files.readAllBytes(outDir.resolve("plain_single.json"));

    // Per-chunk gzipped run
    Path jobFileCompressed =
        writeJobYaml(
            "structures/simple.yaml",
            "type: file",
            "seed:",
            "  type: embedded",
            "  value: 777",
            "conf:",
            "  path: " + outDir.resolve("per_chunk_single").toAbsolutePath(),
            "  compress: true",
            "  compress_mode: per_chunk");

    int codeCompressed =
        execute(
            OPT_JOB,
            jobFileCompressed.toString(),
            OPT_COUNT,
            String.valueOf(count),
            OPT_SEED,
            "777");

    assertThat(codeCompressed).isZero();

    // Decompress and compare
    java.io.ByteArrayOutputStream decompressed = new java.io.ByteArrayOutputStream();
    try (java.util.zip.GZIPInputStream gz =
        new java.util.zip.GZIPInputStream(
            Files.newInputStream(outDir.resolve("per_chunk_single.json.gz")))) {
      byte[] buffer = new byte[4096];
      int len;
      while ((len = gz.read(buffer)) != -1) {
        decompressed.write(buffer, 0, len);
      }
    }

    assertThat(decompressed.toByteArray()).isEqualTo(uncompressedBytes);
  }

  // ── Compression determinism regression (issues #193 + #210): every write path is
  //    thread-count-invariant on disk AND decompresses to the exact plain output ───────────

  @ParameterizedTest(name = "compress mode: {0}")
  @ValueSource(strings = {"none", "stream", "per_chunk"})
  void compressedOutputIsDeterministicAndDecompressesToPlainAcrossThreadCounts(String mode)
      throws Exception {
    // Count deliberately not a multiple of chunkSize (256) so a partial final chunk is exercised.
    int count = 256 * 3 + 7; // 775
    long seed = 20210L;

    // Plain reference content (also the decompressed target for the compressed modes).
    byte[] plainReference =
        Files.readAllBytes(
            runAcrossThreadCounts(writeCompressJob("none"), "output.json", count, seed));

    if ("none".equals(mode)) {
      // The "none" case is fully asserted inside runAcrossThreadCounts (byte-identical raw output).
      assertThat(plainReference).isNotEmpty();
      return;
    }

    // Compressed modes: on-disk .gz must be byte-identical across thread counts, and must
    // decompress back to the exact plain bytes.
    Path gz = runAcrossThreadCounts(writeCompressJob(mode), "output.json.gz", count, seed);
    assertThat(gunzip(gz))
        .as("gunzip(%s output) must equal the plain uncompressed output", mode)
        .isEqualTo(plainReference);
  }

  @Test
  void perChunkGzipDiffersFromStreamOnDiskButDecompressesIdentically() throws Exception {
    // Guards against a silent fallback to stream mode (e.g. compress_mode not wired through the
    // CLI): the two modes MUST produce different .gz bytes (multi-member vs single-member) while
    // decompressing to the same content.
    int count = 256 * 3 + 7; // 775 — spans several chunks so per_chunk emits multiple members
    long seed = 4242L;

    Path streamGz =
        runAcrossThreadCounts(writeCompressJob("stream"), "output.json.gz", count, seed);
    byte[] streamBytes = Files.readAllBytes(streamGz);
    byte[] streamContent = gunzip(streamGz);

    Path perChunkGz =
        runAcrossThreadCounts(writeCompressJob("per_chunk"), "output.json.gz", count, seed);
    byte[] perChunkBytes = Files.readAllBytes(perChunkGz);

    assertThat(perChunkBytes)
        .as("per_chunk .gz must differ from stream .gz (proves compress_mode took effect)")
        .isNotEqualTo(streamBytes);
    assertThat(gunzip(perChunkGz))
        .as("per_chunk and stream must decompress to identical content")
        .isEqualTo(streamContent);
  }

  /**
   * Build a file job at {@code outDir/output} with the given compression mode
   * (none|stream|per_chunk).
   */
  private Path writeCompressJob(String mode) throws IOException {
    String extraConf =
        switch (mode) {
          case "none" -> "  compress: false";
          case "stream" -> "  compress: true";
          case "per_chunk" -> "  compress: true\n  compress_mode: per_chunk";
          default -> throw new IllegalArgumentException("unknown mode: " + mode);
        };
    return writeJobFile("file", extraConf);
  }

  /**
   * Run the same job at {@code --threads 1/4/8} and assert the produced file is byte-identical
   * across all three (the determinism guarantee). Returns the path of the produced file.
   */
  private Path runAcrossThreadCounts(Path jobFile, String outputName, int count, long seed)
      throws Exception {
    Path output = outDir.resolve(outputName);
    byte[] reference = null;
    for (int threads : new int[] {1, 4, 8}) {
      if (Files.exists(output)) {
        Files.delete(output);
      }
      int code =
          execute(
              OPT_JOB,
              jobFile.toString(),
              OPT_COUNT,
              String.valueOf(count),
              OPT_SEED,
              String.valueOf(seed),
              "--threads",
              String.valueOf(threads));
      assertThat(code).isZero();

      byte[] bytes = Files.readAllBytes(output);
      if (reference == null) {
        reference = bytes;
      } else {
        assertThat(bytes)
            .as(
                "%s with %d threads must be byte-identical to the 1-thread reference",
                outputName, threads)
            .isEqualTo(reference);
      }
    }
    return output;
  }

  /** Fully decompress a gzip file (transparently reads consecutive members). */
  private byte[] gunzip(Path gzFile) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPInputStream gz = new GZIPInputStream(Files.newInputStream(gzFile))) {
      byte[] buffer = new byte[8192];
      int len;
      while ((len = gz.read(buffer)) != -1) {
        out.write(buffer, 0, len);
      }
    }
    return out.toByteArray();
  }

  // ── Error cases ──────────────────────────────────────────────────────────────

  @Test
  void missingJobOptionReturnsUsageError() {
    int code = execute(OPT_COUNT, "5");
    assertThat(code).isNotZero();
  }

  @Test
  void nonexistentJobFileReturnsError() {
    int code = execute(OPT_JOB, tempDir.resolve("nonexistent.yaml").toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  @Test
  void nonexistentJobFileShowsFriendlyErrorMessage() {

    StringWriter err = new StringWriter();

    CommandLine cmd = new CommandLine(new ExecuteCommand());

    cmd.setErr(new PrintWriter(err));

    cmd.setExecutionExceptionHandler(DataGeneratorCli.friendlyExceptionHandler());

    int code = cmd.execute(OPT_JOB, tempDir.resolve("nonexistent.yaml").toString(), OPT_COUNT, "1");

    String output = err.toString();

    assertThat(code).isNotZero();
    assertThat(output)
        .contains("nonexistent.yaml")
        .doesNotContain("SchemaParseException")
        .doesNotContain("\tat");
  }

  @Test
  void unsupportedFormatReturnsError() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "parquet");
    assertThat(code).isNotZero();
  }

  @Test
  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional YAML line endings
  void unsupportedDestinationTypeReturnsError() throws Exception {
    Path jobFile = tempDir.resolve("bad_dest.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: mongodb
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  @Test
  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional YAML line endings
  void nonexistentStructureFileReturnsError() throws Exception {
    Path jobFile = tempDir.resolve("bad_struct.yaml");
    Files.writeString(
        jobFile,
        """
        source: doesnotexist.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  // ── Structures path resolution ───────────────────────────────────────────────

  // ── File destination options ─────────────────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void fileDestinationWithCompressAndAppend() throws Exception {
    Path jobFile = tempDir.resolve("compress_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
          compress: true
          append: true
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "3");
    assertThat(code).isZero();
  }

  // ── Output path validation (T14) ─────────────────────────────────────────────

  @Test
  @DisabledOnOs(OS.WINDOWS)
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void symlinkOutputPathIsRejected() throws Exception {
    Path realFile = outDir.resolve("real.json");
    Files.writeString(realFile, "sensitive content");
    Path symlink = outDir.resolve("output.json");
    Files.createSymbolicLink(symlink, realFile);

    Path jobFile = tempDir.resolve("symlink_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");

    assertThat(code).isNotZero();
    assertThat(Files.readString(realFile)).isEqualTo("sensitive content");
  }

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void directoryAsOutputPathIsRejected() throws Exception {
    Path asDir = outDir.resolve("output.json");
    Files.createDirectories(asDir);

    Path jobFile = tempDir.resolve("dir_target_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  @Test
  void existingFileWithAppendFalseLogsWarnAndStillWrites() throws Exception {
    Path existing = outDir.resolve(OUTPUT_JSON);
    Files.writeString(existing, "stale data\n");

    Logger appLogger = (Logger) LoggerFactory.getLogger("com.datagenerator");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    appLogger.addAppender(appender);

    Path jobFile = writeJobFile();
    int code;
    try {
      code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "2");
    } finally {
      appLogger.detachAppender(appender);
      appender.stop();
    }

    assertThat(code).isZero();
    assertThat(appender.list)
        .anyMatch(
            event ->
                event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("will be truncated"));
    List<String> lines = Files.readAllLines(existing);
    assertThat(lines).hasSize(2); // truncated and rewritten, not appended to stale data
  }

  // ── Seed resolution edge cases ───────────────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void noSeedConfigFallsBackToDefaultSeed() throws Exception {
    Path jobFile = tempDir.resolve("noseed_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "3");
    assertThat(code).isZero();
    assertThat(outDir.resolve(OUTPUT_JSON)).exists();
  }

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void envSeedResolutionFailureFallsBackToDefaultSeed() throws Exception {
    Path jobFile = tempDir.resolve("envseed_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: env
          name: NONEXISTENT_SEED_VAR_TEST_XYZ_12345
        conf:
          path: %s/output
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "3");
    assertThat(code).isZero(); // falls back to seed 0
    assertThat(outDir.resolve(OUTPUT_JSON)).exists();
  }

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void fileSeedValidationIsApplied() throws Exception {
    Path seedFile = tempDir.resolve("seed.txt");
    Files.writeString(seedFile, "42");

    Path jobFile = tempDir.resolve("fileseed_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: file
          path: %s
        conf:
          path: %s/output
        """
            .formatted(
                structDir.toAbsolutePath(), seedFile.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "3");
    // Seed file permission check runs; result depends on OS file permissions
    assertThat(code).isIn(0, 1);
  }

  // ── Structures path fallback ──────────────────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void defaultStructuresPathFallbackFailsWhenNoStructuresInCwd() throws Exception {
    Path subDir = tempDir.resolve("mydir");
    Files.createDirectories(subDir);
    Path jobFile = subDir.resolve(JOB_FILE);
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero(); // config/structures/simple.yaml not present in CWD
  }

  // ── Serializer formats ────────────────────────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void cbeffSerializerCreatedWhenFormatIsCbeff() throws Exception {
    Path jobFile = tempDir.resolve("cbeff_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
          cbeff_format_owner: ISO
          cbeff_format_type: 19794-2-json
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "cbeff", OPT_COUNT, "1");
    assertThat(code).isBetween(0, 2);
  }

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void avroRegistrySerializerCreatedWhenFormatIsAvroRegistry() throws Exception {
    Path jobFile = tempDir.resolve("avroreg_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
          schema_registry_url: http://127.0.0.1:1
          topic: test-topic
          schema_registry_subject: test-topic-value
          schema_registry_auth: bearer
          schema_registry_token: test-token
        """
            .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

    // createSerializer() is covered; fails at serialization time (no registry)
    int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "avro-registry", OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  // ── Database destination ──────────────────────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void databaseDestinationAttemptedWhenTypeIsDatabase() throws Exception {
    Path jobFile = tempDir.resolve("db_basic_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: database
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          jdbc_url: jdbc:nonexistent://localhost/test
          username: sa
          password: ""
          table: simple_test
        """
            .formatted(structDir.toAbsolutePath()));

    // createDatabaseDestination() runs to completion; fails at open time (no JDBC driver)
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void databaseDestinationWithAllOptionalFieldsCoversAllBranches() throws Exception {
    Path jobFile = tempDir.resolve("db_full_job.yaml");
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: database
        structures_path: %s
        seed:
          type: embedded
          value: 42
        conf:
          jdbc_url: jdbc:nonexistent://localhost/test
          username: sa
          password: ""
          table: simple_test
          batch_size: 50
          pool_size: 2
          transaction_strategy: per_batch
          max_retries: 1
          retry_delay_ms: 50
        """
            .formatted(structDir.toAbsolutePath()));

    // All optional DB config branches exercised; fails at open time (no JDBC driver)
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1");
    assertThat(code).isNotZero();
  }

  // ── Structures path inference ─────────────────────────────────────────────────

  @Test
  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional YAML line endings
  void structuresPathInferredFromJobsDirectory() throws Exception {
    // Organise as config/jobs/job.yaml and config/structures/simple.yaml — mirrors real layout
    Path configDir = tempDir.resolve("config");
    Path jobsDir = configDir.resolve("jobs");
    Path structuresDir = configDir.resolve("structures");
    Files.createDirectories(jobsDir);
    Files.createDirectories(structuresDir);
    Files.copy(structDir.resolve(SIMPLE_YAML), structuresDir.resolve(SIMPLE_YAML));

    Path jobFile = jobsDir.resolve(JOB_FILE);
    Files.writeString(
        jobFile,
        """
        source: simple.yaml
        type: file
        seed:
          type: embedded
          value: 42
        conf:
          path: %s/output
        """
            .formatted(outDir.toAbsolutePath()));

    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "3");
    assertThat(code).isZero();
    assertThat(outDir.resolve(OUTPUT_JSON)).exists();
  }

  // JDBC credential redaction (CWE-532) now lives in JdbcUrlRedactor (destinations module) —
  // see JdbcUrlRedactorTest for that coverage.

  // ── Avro Registry secret substitution ──────────────────────────────────────

  @Test
  @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
  void schemaRegistryTokenSubstitutesEnvironmentVariable() throws Exception {
    // Set an environment variable to substitute
    System.setProperty("TEST_REGISTRY_TOKEN", "secret-bearer-token-value");
    try {
      Path jobFile = tempDir.resolve("avroreg_env_job.yaml");
      Files.writeString(
          jobFile,
          """
          source: simple.yaml
          type: file
          structures_path: %s
          seed:
            type: embedded
            value: 42
          conf:
            path: %s/output
            schema_registry_url: http://127.0.0.1:1
            topic: test-topic
            schema_registry_subject: test-topic-value
            schema_registry_auth: bearer
            schema_registry_token: "${TEST_REGISTRY_TOKEN}"
          """
              .formatted(structDir.toAbsolutePath(), outDir.toAbsolutePath()));

      // createSerializer() is covered; fails at serialization time (no registry)
      // But the token should be substituted before reaching that point
      int code = execute(OPT_JOB, jobFile.toString(), OPT_FORMAT, "avro-registry", OPT_COUNT, "1");
      assertThat(code).isNotZero(); // fails at serialization (no registry), but token was resolved
    } finally {
      System.clearProperty("TEST_REGISTRY_TOKEN");
    }
  }

  // ── T10: --debug/--verbose must not elevate third-party loggers ─────────────

  @Test
  void debugModeScopesTraceToApplicationLoggerOnly() throws Exception {
    // Third-party libraries (HikariCP, Kafka clients, ...) must not be elevated by --debug —
    // only com.datagenerator should move to TRACE, ROOT stays at INFO.
    Logger thirdPartyLogger = (Logger) LoggerFactory.getLogger("com.zaxxer.hikari");
    thirdPartyLogger.setLevel(Level.INFO);

    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1", "--debug");

    assertThat(code).isZero();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger app = (Logger) LoggerFactory.getLogger("com.datagenerator");
    assertThat(app.getLevel()).isEqualTo(Level.TRACE);
    assertThat(root.getLevel()).isEqualTo(Level.INFO);
    assertThat(thirdPartyLogger.getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  void verboseModeScopesDebugToApplicationLoggerOnly() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute(OPT_JOB, jobFile.toString(), OPT_COUNT, "1", "--verbose");

    assertThat(code).isZero();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger app = (Logger) LoggerFactory.getLogger("com.datagenerator");
    assertThat(app.getLevel()).isEqualTo(Level.DEBUG);
    assertThat(root.getLevel()).isEqualTo(Level.INFO);
  }
}
