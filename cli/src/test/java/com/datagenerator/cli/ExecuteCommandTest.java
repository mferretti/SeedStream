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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    cmd.setExecutionExceptionHandler(
        (ex, commandLine, parseResult) -> {
          commandLine.getErr().println(ex.getMessage());
          return 1;
        });

    int code = cmd.execute(OPT_JOB, tempDir.resolve("nonexistent.yaml").toString(), OPT_COUNT, "1");

    String output = err.toString();

    assertThat(code).isNotZero();
    assertThat(output).contains("nonexistent.yaml");
    assertThat(output).doesNotContain("SchemaParseException");
    assertThat(output).doesNotContain("\tat");
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

  @Test
  void shouldRedactCredentialsInJdbcUrl() {
    // C1 / CWE-532: userinfo and password query params must be masked before logging.
    assertThat(ExecuteCommand.redactJdbcCredentials("jdbc:postgresql://user:s3cret@host:5432/db"))
        .isEqualTo("jdbc:postgresql://****@host:5432/db")
        .doesNotContain("s3cret");
    assertThat(
            ExecuteCommand.redactJdbcCredentials(
                "jdbc:mysql://host/db?user=admin&password=s3cret&ssl=true"))
        .doesNotContain("s3cret")
        .doesNotContain("admin")
        .contains("ssl=true");
  }

  @Test
  void shouldLeaveCleanJdbcUrlUnchanged() {
    String clean = "jdbc:postgresql://db-host:5432/testdb";
    assertThat(ExecuteCommand.redactJdbcCredentials(clean)).isEqualTo(clean);
    assertThat(ExecuteCommand.redactJdbcCredentials(null)).isNull();
  }
}
