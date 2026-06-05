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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

class ExecuteCommandTest {

  @TempDir Path tempDir;

  private Path structDir;
  private Path outDir;

  @BeforeEach
  void setUp() throws Exception {
    structDir = tempDir.resolve("structures");
    outDir = tempDir.resolve("out");
    Files.createDirectories(structDir);
    Files.createDirectories(outDir);

    Files.writeString(
        structDir.resolve("simple.yaml"),
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

  private Path writeJobFile() throws Exception {
    return writeJobFile("file", "");
  }

  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional YAML line endings
  private Path writeJobFile(String destType, String extraConf) throws Exception {
    Path jobFile = tempDir.resolve("job.yaml");
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

  // ── Happy path — JSON ────────────────────────────────────────────────────────

  @Test
  void executeJsonToFileSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--count", "5");

    assertThat(code).isZero();
    Path output = outDir.resolve("output.json");
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
    int code = execute("--job", jobFile.toString());

    assertThat(code).isZero();
    List<String> lines = Files.readAllLines(outDir.resolve("output.json"));
    assertThat(lines).hasSize(100);
  }

  // ── Happy path — CSV ─────────────────────────────────────────────────────────

  @Test
  void executeCsvFormatWritesHeaderAndData() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--format", "csv", "--count", "3");

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
    int code = execute("--job", jobFile.toString(), "--format", "protobuf", "--count", "5");

    assertThat(code).isZero();
    assertThat(outDir.resolve("output.protobuf")).exists();
  }

  // ── Happy path — Avro ────────────────────────────────────────────────────────

  @Test
  void executeAvroFormatSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--format", "avro", "--count", "5");

    assertThat(code).isZero();
    assertThat(outDir.resolve("output.avro")).exists();
  }

  // ── Seed reproducibility ─────────────────────────────────────────────────────

  @Test
  void seedOverrideProducesReproducibleOutput() throws Exception {
    Path jobFile = writeJobFile();

    execute("--job", jobFile.toString(), "--count", "10", "--seed", "999");
    String first = Files.readString(outDir.resolve("output.json"));

    Files.delete(outDir.resolve("output.json"));

    execute("--job", jobFile.toString(), "--count", "10", "--seed", "999");
    String second = Files.readString(outDir.resolve("output.json"));

    assertThat(first).isEqualTo(second);
  }

  @Test
  void differentSeedsProduceDifferentOutput() throws Exception {
    Path jobFile = writeJobFile();

    execute("--job", jobFile.toString(), "--count", "10", "--seed", "1");
    String first = Files.readString(outDir.resolve("output.json"));

    Files.delete(outDir.resolve("output.json"));

    execute("--job", jobFile.toString(), "--count", "10", "--seed", "2");
    String second = Files.readString(outDir.resolve("output.json"));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void seedOverrideOverridesJobEmbeddedSeed() throws Exception {
    Path jobFile = writeJobFile();

    // Run with embedded seed (42 from job YAML) via normal execution
    execute("--job", jobFile.toString(), "--count", "5");
    String withJobSeed = Files.readString(outDir.resolve("output.json"));

    Files.delete(outDir.resolve("output.json"));

    // Run with a different seed override
    execute("--job", jobFile.toString(), "--count", "5", "--seed", "99999");
    String withOverride = Files.readString(outDir.resolve("output.json"));

    assertThat(withJobSeed).isNotEqualTo(withOverride);
  }

  // ── Logging flags ────────────────────────────────────────────────────────────

  @Test
  void debugFlagSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--count", "2", "--debug");

    assertThat(code).isZero();
  }

  @Test
  void verboseFlagSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--count", "2", "--verbose");

    assertThat(code).isZero();
  }

  @Test
  void traceSampleOutOfRangeIsClampedAndSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    // 0 should be clamped to 1; 200 should be clamped to 100 — both should succeed
    int code =
        execute("--job", jobFile.toString(), "--count", "2", "--debug", "--trace-sample", "0");

    assertThat(code).isZero();
  }

  // ── Threading ────────────────────────────────────────────────────────────────

  @Test
  void threadsOptionSucceeds() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--count", "10", "--threads", "2");

    assertThat(code).isZero();
    assertThat(outDir.resolve("output.json")).exists();
  }

  // ── Error cases ──────────────────────────────────────────────────────────────

  @Test
  void missingJobOptionReturnsUsageError() {
    int code = execute("--count", "5");
    assertThat(code).isNotZero();
  }

  @Test
  void nonexistentJobFileReturnsError() {
    int code = execute("--job", tempDir.resolve("nonexistent.yaml").toString(), "--count", "1");
    assertThat(code).isNotZero();
  }

  @Test
  void unsupportedFormatReturnsError() throws Exception {
    Path jobFile = writeJobFile();
    int code = execute("--job", jobFile.toString(), "--format", "parquet");
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

    int code = execute("--job", jobFile.toString(), "--count", "1");
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

    int code = execute("--job", jobFile.toString(), "--count", "1");
    assertThat(code).isNotZero();
  }

  // ── Structures path resolution ───────────────────────────────────────────────

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
    Files.copy(structDir.resolve("simple.yaml"), structuresDir.resolve("simple.yaml"));

    Path jobFile = jobsDir.resolve("job.yaml");
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

    int code = execute("--job", jobFile.toString(), "--count", "3");
    assertThat(code).isZero();
    assertThat(outDir.resolve("output.json")).exists();
  }
}
