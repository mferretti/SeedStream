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
import com.datagenerator.cli.CliTestSupport.Result;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Value-level and contract tests for {@code execute}: seed precedence, option validation and {@code
 * --faker-types}. Every run goes through the real root command with the production friendly
 * exception handler, so exit codes and stderr are what a user sees (0 ok, 1 runtime, 2 usage).
 */
@SuppressFBWarnings(
    value = "VA_FORMAT_STRING_USES_NEWLINE",
    justification = "YAML fixtures need a literal \\n, not the platform separator")
class ExecuteCommandOptionsTest {

  private static final String EXECUTE = "execute";
  private static final String JOB = "--job";
  private static final String COUNT = "--count";
  private static final String SEED = "--seed";
  private static final String OUTPUT_JSON = "output.json";
  private static final int USAGE_ERROR = 2;
  private static final int RUNTIME_ERROR = 1;

  @TempDir Path tempDir;

  private Path structDir;
  private int runCounter;

  @BeforeEach
  void setUp() throws IOException {
    structDir = tempDir.resolve("structures");
    Files.createDirectories(structDir);
    Files.writeString(
        structDir.resolve("rec.yaml"),
        """
        name: rec
        data:
          id:
            datatype: "int[1..1000000]"
          label:
            datatype: "char[8..12]"
        """);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  /** Writes a file-destination job; {@code seedBlock} is the YAML seed section ("" for none). */
  private Path writeJob(String source, String seedBlock, Path outDir) throws IOException {
    Path job = tempDir.resolve("job-" + (++runCounter) + ".yaml");
    Files.writeString(
        job,
        "source: %s\ntype: file\nstructures_path: %s\n%sconf:\n  path: %s/output\n"
            .formatted(source, structDir.toAbsolutePath(), seedBlock, outDir.toAbsolutePath()));
    return job;
  }

  private static String embedded(long value) {
    return "seed:\n  type: embedded\n  value: %d\n".formatted(value);
  }

  private Path newOutDir() throws IOException {
    return Files.createDirectories(tempDir.resolve("out-" + (++runCounter)));
  }

  /** Runs {@code execute} on a fresh job; returns the bytes written to the output file. */
  private byte[] generate(String seedBlock, String... extraArgs) throws IOException {
    Path out = newOutDir();
    Path job = writeJob("rec.yaml", seedBlock, out);
    Result r = run(job, extraArgs);
    assertThat(r.exit()).as("stderr: %s", r.err()).isZero();
    return Files.readAllBytes(out.resolve(OUTPUT_JSON));
  }

  private static Result run(Path job, String... extraArgs) {
    String[] args = new String[3 + extraArgs.length];
    args[0] = EXECUTE;
    args[1] = JOB;
    args[2] = job.toString();
    System.arraycopy(extraArgs, 0, args, 3, extraArgs.length);
    return CliTestSupport.run(args);
  }

  // ── 1. Seed precedence ──────────────────────────────────────────────────────

  @Test
  void shouldUseCliSeedWhenYamlSeedIsAlsoPresent() throws IOException {
    byte[] cliOverride = generate(embedded(42), SEED, "7", COUNT, "50");
    byte[] referenceSeed7 = generate(embedded(7), COUNT, "50");
    byte[] referenceSeed42 = generate(embedded(42), COUNT, "50");

    assertThat(cliOverride).isEqualTo(referenceSeed7).isNotEqualTo(referenceSeed42);
  }

  @Test
  void shouldUseYamlSeedWhenNoCliSeedIsGiven() throws IOException {
    byte[] yamlSeed = generate(embedded(42), COUNT, "50");
    byte[] referenceSeed42 = generate(embedded(42), SEED, "42", COUNT, "50");
    byte[] referenceSeed7 = generate(embedded(7), COUNT, "50");

    assertThat(yamlSeed).isEqualTo(referenceSeed42).isNotEqualTo(referenceSeed7);
  }

  @Test
  void shouldHonourExplicitCliSeedZeroWhenYamlSeedIsNonZero() throws IOException {
    // 0 is the default seed; an override of 0 must still win over YAML (null-check, not != 0).
    byte[] cliZero = generate(embedded(42), SEED, "0", COUNT, "50");
    byte[] referenceZero = generate(embedded(0), COUNT, "50");

    assertThat(cliZero).isEqualTo(referenceZero).isNotEqualTo(generate(embedded(42), COUNT, "50"));
  }

  @Test
  void shouldHonourNegativeCliSeed() throws IOException {
    byte[] cliNegative = generate(embedded(42), SEED, "-5", COUNT, "50");
    byte[] referenceNegative = generate(embedded(-5), COUNT, "50");

    assertThat(cliNegative).isEqualTo(referenceNegative);
  }

  @Test
  void shouldDefaultToSeedZeroAndWarnWhenNoSeedAnywhere() throws IOException {
    Path out = newOutDir();
    Result r = run(writeJob("rec.yaml", "", out), COUNT, "50");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(r.logged(Level.WARN, "No seed configuration found, using default: 0")).isTrue();
    assertThat(r.logged(Level.INFO, "Using seed: 0")).isTrue();
    assertThat(Files.readAllBytes(out.resolve(OUTPUT_JSON)))
        .isEqualTo(generate(embedded(0), COUNT, "50"))
        .isNotEqualTo(generate(embedded(42), COUNT, "50"));
  }

  @Test
  void shouldNotWarnAboutMissingSeedWhenSeedIsConfigured() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(42), newOutDir()), COUNT, "5");

    assertThat(r.exit()).isZero();
    assertThat(r.logged(Level.WARN, "No seed configuration")).isFalse();
    assertThat(r.logged(Level.INFO, "Using seed: 42")).isTrue();
  }

  @Test
  void shouldLogCliSeedAsEffectiveSeedWhenOverridingYaml() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(42), newOutDir()), SEED, "7", COUNT, "5");

    assertThat(r.exit()).isZero();
    assertThat(r.logged(Level.INFO, "Using seed: 7")).isTrue();
    assertThat(r.logged(Level.INFO, "Using seed: 42")).isFalse();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldUseSeedFromFileWhenSeedTypeIsFile() throws IOException {
    Path seedFile = tempDir.resolve("seed.txt");
    Files.writeString(seedFile, "7\n");
    Files.setPosixFilePermissions(seedFile, PosixFilePermissions.fromString("rw-------"));
    String fileSeed = "seed:\n  type: file\n  path: %s\n".formatted(seedFile.toAbsolutePath());

    byte[] fromFile = generate(fileSeed, COUNT, "50");

    assertThat(fromFile)
        .isEqualTo(generate(embedded(7), COUNT, "50"))
        .isNotEqualTo(generate(embedded(0), COUNT, "50"));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectSeedFileReadableByOthersWhenSeedTypeIsFile() throws IOException {
    Path seedFile = tempDir.resolve("seed-open.txt");
    Files.writeString(seedFile, "7");
    // Deliberately insecure, proves the check rejects it
    Files.setPosixFilePermissions( // nosemgrep
        seedFile, PosixFilePermissions.fromString("rw-r--r--"));
    String fileSeed = "seed:\n  type: file\n  path: %s\n".formatted(seedFile.toAbsolutePath());
    Path out = newOutDir();

    Result r = run(writeJob("rec.yaml", fileSeed, out), COUNT, "5");

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Seed file has insecure permissions").contains("chmod 600");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldFailWhenConfiguredSeedFileIsNotANumber() throws IOException {
    Path seedFile = tempDir.resolve("seed-bad.txt");
    Files.writeString(seedFile, "not-a-number");
    Files.setPosixFilePermissions(seedFile, PosixFilePermissions.fromString("rw-------"));
    String fileSeed = "seed:\n  type: file\n  path: %s\n".formatted(seedFile.toAbsolutePath());
    Path out = newOutDir();

    Result r = run(writeJob("rec.yaml", fileSeed, out), COUNT, "50");

    // A configured seed source that cannot be read is an error, never a silent seed 0.
    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Cannot resolve the configured seed").contains("--seed");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldFailWhenConfiguredSeedFileIsMissing() throws IOException {
    String fileSeed =
        "seed:\n  type: file\n  path: %s\n".formatted(tempDir.resolve("no-such-seed.txt"));
    Path out = newOutDir();

    Result r = run(writeJob("rec.yaml", fileSeed, out), COUNT, "5");

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Cannot resolve the configured seed").contains("no-such-seed.txt");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldUseCliSeedWhenConfiguredSeedSourceIsBroken() throws IOException {
    // --seed overrides the job's seed config, so a broken source is never consulted.
    String envSeed = "seed:\n  type: env\n  name: SEEDSTREAM_TEST_DEFINITELY_UNSET_VAR_8f3a\n";
    Path out = newOutDir();

    Result r = run(writeJob("rec.yaml", envSeed, out), COUNT, "20", SEED, "7");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(Files.readAllBytes(out.resolve(OUTPUT_JSON)))
        .isEqualTo(generate(embedded(7), COUNT, "20"));
  }

  @Test
  void shouldFailWhenConfiguredSeedEnvVarIsUnset() throws IOException {
    String envSeed = "seed:\n  type: env\n  name: SEEDSTREAM_TEST_DEFINITELY_UNSET_VAR_8f3a\n";
    Path out = newOutDir();

    Result r = run(writeJob("rec.yaml", envSeed, out), COUNT, "50");

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err())
        .contains("Cannot resolve the configured seed")
        .contains("SEEDSTREAM_TEST_DEFINITELY_UNSET_VAR_8f3a");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  // ── 2. Option validation ────────────────────────────────────────────────────

  @Test
  void shouldFailWithUsageErrorWhenJobOptionIsMissing() {
    Result r = CliTestSupport.run(EXECUTE, COUNT, "5");

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("Missing required option: '--job=<jobFile>'");
  }

  @Test
  void shouldFailWithUsageErrorWhenCountIsNotANumber() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(1), newOutDir()), COUNT, "abc");

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("Invalid value for option '--count'").contains("'abc'");
  }

  @Test
  void shouldFailWithUsageErrorWhenThreadsIsNotANumber() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(1), newOutDir()), "--threads", "many");

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("Invalid value for option '--threads'").contains("'many'");
  }

  @Test
  void shouldFailWithUsageErrorWhenSeedIsNotANumber() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(1), newOutDir()), SEED, "xyz");

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("Invalid value for option '--seed'").contains("'xyz'");
  }

  @Test
  void shouldFailWithUsageErrorWhenUnknownOptionIsGiven() throws IOException {
    Result r = run(writeJob("rec.yaml", embedded(1), newOutDir()), "--bogus");

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("Unknown option: '--bogus'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "-100"})
  void shouldRejectNonPositiveCountAsUsageErrorAndLeaveOutputUntouched(String count)
      throws IOException {
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");

    Result r = run(writeJob("rec.yaml", embedded(1), out), COUNT, count);

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("--count must be >= 1, got " + count);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  @Test
  void shouldAcceptCountOfOne() throws IOException {
    byte[] bytes = generate(embedded(1), COUNT, "1");

    assertThat(new String(bytes, StandardCharsets.UTF_8).strip().split("\n")).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({"0, 2000", "-1, 2000", "0, 10", "-1, 10"})
  @Timeout(30)
  void shouldRejectNonPositiveThreadCountAsUsageError(String threads, String count)
      throws IOException {
    // Rejected for every job size (small jobs never consult the thread count, but a nonsensical
    // value is still a usage error) and before the output is touched.
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");

    Result r = run(writeJob("rec.yaml", embedded(1), out), COUNT, count, "--threads", threads);

    assertThat(r.exit()).isEqualTo(USAGE_ERROR);
    assertThat(r.err()).contains("--threads must be >= 1, got " + threads);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  @Test
  void shouldFailWithMessageWhenFormatIsUnknown() throws IOException {
    Path out = newOutDir();
    Result r = run(writeJob("rec.yaml", embedded(1), out), "--format", "parquet");

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Unsupported format: parquet");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldAcceptFormatCaseInsensitively() throws IOException {
    byte[] upper = generate(embedded(5), COUNT, "20", "--format", "JSON");
    byte[] lower = generate(embedded(5), COUNT, "20", "--format", "json");

    assertThat(upper).isNotEmpty().isEqualTo(lower);
  }

  @Test
  void shouldAcceptShortOptionForms() throws IOException {
    Path out = newOutDir();
    Path job = writeJob("rec.yaml", embedded(42), out);

    Result r =
        CliTestSupport.run(
            EXECUTE, "-j", job.toString(), "-c", "30", "-s", "7", "-f", "csv", "-t", "1");

    assertThat(r.exit()).as(r.err()).isZero();
    List<String> lines = Files.readAllLines(out.resolve("output.csv"));
    assertThat(lines).hasSize(31); // header + 30 rows: -c honoured, -f honoured
    // -s 7 honoured: identical to the long-form run with seed 7
    Path ref = newOutDir();
    Result r2 =
        run(
            writeJob("rec.yaml", embedded(42), ref),
            SEED,
            "7",
            COUNT,
            "30",
            "--format",
            "csv",
            "--threads",
            "1");
    assertThat(r2.exit()).isZero();
    assertThat(Files.readAllBytes(out.resolve("output.csv")))
        .isEqualTo(Files.readAllBytes(ref.resolve("output.csv")));
  }

  @Test
  void shouldFailWhenJobFileDoesNotExist() {
    Path missing = tempDir.resolve("nope.yaml");
    Result r = CliTestSupport.run(EXECUTE, JOB, missing.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("job config file not found: " + missing);
  }

  @Test
  void shouldFailWhenJobYamlIsMalformed() throws IOException {
    Path job = tempDir.resolve("broken.yaml");
    Files.writeString(job, "source: [unterminated\ntype: file\n");

    Result r = CliTestSupport.run(EXECUTE, JOB, job.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Failed to read job config file").contains(job.toString());
  }

  @Test
  void shouldFailWhenJobYamlHasUnknownProperty() throws IOException {
    Path job = tempDir.resolve("typo.yaml");
    Files.writeString(
        job, "source: rec.yaml\ntype: file\nsede: 1\nconf:\n  path: %s/o\n".formatted(tempDir));

    Result r = CliTestSupport.run(EXECUTE, JOB, job.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Failed to read job config file").contains("sede");
  }

  @Test
  void shouldFailWhenJobYamlMissesRequiredField() throws IOException {
    Path job = tempDir.resolve("nosource.yaml");
    Files.writeString(job, "type: file\nconf:\n  path: %s/o\n".formatted(tempDir));

    Result r = CliTestSupport.run(EXECUTE, JOB, job.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Validation failed for").contains("source: must not be null");
  }

  @Test
  void shouldFailWhenStructureFileDoesNotExist() throws IOException {
    Path out = newOutDir();
    Result r = run(writeJob("ghost.yaml", embedded(1), out));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("data structure file not found:").contains("ghost.yaml");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldNameOffendingTypeWhenStructureHasInvalidDatatype() throws IOException {
    writeBadTypeStructure();
    Result r = run(writeJob("bad.yaml", embedded(1), newOutDir()));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Failed to load structure: bad").contains("quaternion");
  }

  @Test
  void shouldNotTouchOutputWhenStructureHasInvalidDatatype() throws IOException {
    writeBadTypeStructure();
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");
    Result r = run(writeJob("bad.yaml", embedded(1), out));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  private void writeBadTypeStructure() throws IOException {
    Files.writeString(
        structDir.resolve("bad.yaml"),
        """
        name: bad
        data:
          id:
            datatype: "int[1..10]"
          oops:
            datatype: "quaternion[1..2]"
        """);
  }

  @Test
  void shouldFailWhenIntRangeIsInverted() throws IOException {
    writeInvertedRangeStructure();
    Result r = run(writeJob("inv.yaml", embedded(1), newOutDir()));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("min (10) > max (1)");
  }

  @Test
  void shouldNotTouchOutputWhenIntRangeIsInverted() throws IOException {
    writeInvertedRangeStructure();
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");
    Result r = run(writeJob("inv.yaml", embedded(1), out));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "decimal[5.0..1.0]",
        "char[9..2]",
        "date[2025-01-01..2020-01-01]",
        "timestamp[2025-01-01T00:00:00..2020-01-01T00:00:00]",
        "array[int[1..2], 5..1]",
        "array[int[9..1], 1..2]"
      })
  void shouldNotTouchOutputWhenAnyFieldConstraintIsInvalid(String datatype) throws IOException {
    Files.writeString(
        structDir.resolve("inv.yaml"),
        "name: inv\ndata:\n  ok:\n    datatype: \"int[1..5]\"\n  bad:\n    datatype: \""
            + datatype
            + "\"\n");
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");

    Result r = run(writeJob("inv.yaml", embedded(1), out));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  @Test
  void shouldNotTouchOutputWhenNestedStructureHasInvalidRange() throws IOException {
    Files.writeString(
        structDir.resolve("child.yaml"),
        "name: child\ndata:\n  n:\n    datatype: \"int[10..1]\"\n");
    Files.writeString(
        structDir.resolve("parent.yaml"),
        "name: parent\ndata:\n  kids:\n    datatype: \"array[object[child], 1..2]\"\n");
    Path out = newOutDir();
    Files.writeString(out.resolve(OUTPUT_JSON), "precious\n");

    Result r = run(writeJob("parent.yaml", embedded(1), out));

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(out.resolve(OUTPUT_JSON)).hasContent("precious");
  }

  private void writeInvertedRangeStructure() throws IOException {
    Files.writeString(
        structDir.resolve("inv.yaml"),
        """
        name: inv
        data:
          id:
            datatype: "int[10..1]"
        """);
  }

  // ── 3. --faker-types ────────────────────────────────────────────────────────

  private static final Pattern SKU = Pattern.compile("SKU-[A-Z]{3}-[0-9]{4}");

  private void writeSkuStructure() throws IOException {
    Files.writeString(
        structDir.resolve("sku.yaml"),
        """
        name: sku
        data:
          code:
            datatype: "cli_test_sku"
        """);
  }

  private Path writeTypes(String name, String content) throws IOException {
    Path types = tempDir.resolve(name);
    Files.writeString(types, content);
    return types;
  }

  @Test
  void shouldGenerateValuesFromRegisteredTypeWhenFakerTypesIsGiven() throws IOException {
    writeSkuStructure();
    Path types =
        writeTypes("types.yaml", "types:\n  cli_test_sku: \"regex:SKU-[A-Z]{3}-[0-9]{4}\"\n");
    Path out = newOutDir();

    Result r =
        run(writeJob("sku.yaml", embedded(3), out), COUNT, "40", "--faker-types", types.toString());

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(r.logged(Level.INFO, "Registered 1 custom Datafaker type(s)")).isTrue();
    List<String> lines = Files.readAllLines(out.resolve(OUTPUT_JSON));
    assertThat(lines).hasSize(40);
    assertThat(lines)
        .allSatisfy(
            line -> {
              String value = line.replaceAll(".*\"code\":\"([^\"]*)\".*", "$1");
              assertThat(value).matches(SKU);
            });
    assertThat(lines).doesNotHaveDuplicates();
  }

  @Test
  void shouldProduceSameValuesAcrossRunsWhenSeedAndFakerTypesAreSame() throws IOException {
    writeSkuStructure();
    Path types =
        writeTypes("types.yaml", "types:\n  cli_test_sku: \"regex:SKU-[A-Z]{3}-[0-9]{4}\"\n");
    byte[] first = runSku(types, 11);
    byte[] second = runSku(types, 11);
    byte[] other = runSku(types, 12);

    assertThat(first).isEqualTo(second).isNotEqualTo(other);
  }

  private byte[] runSku(Path types, long seed) throws IOException {
    Path out = newOutDir();
    Result r =
        run(
            writeJob("sku.yaml", embedded(seed), out),
            COUNT,
            "25",
            "--faker-types",
            types.toString());
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllBytes(out.resolve(OUTPUT_JSON));
  }

  @Test
  void shouldResolveAliasDeclaredInFakerTypes() throws IOException {
    Files.writeString(
        structDir.resolve("skualias.yaml"),
        """
        name: skualias
        data:
          code:
            datatype: "cli_test_sku_alias"
        """);
    Path types =
        writeTypes(
            "alias.yaml",
            """
            types:
              cli_test_sku_base: "regex:AL-[0-9]{6}"
            aliases:
              cli_test_sku_alias: cli_test_sku_base
            """);
    Path out = newOutDir();

    Result r =
        run(
            writeJob("skualias.yaml", embedded(3), out),
            COUNT,
            "10",
            "--faker-types",
            types.toString());

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(Files.readAllLines(out.resolve(OUTPUT_JSON)))
        .hasSize(10)
        .allSatisfy(line -> assertThat(line).containsPattern("\"code\":\"AL-[0-9]{6}\""));
  }

  @Test
  void shouldFailWhenCustomTypeIsUsedWithoutFakerTypes() throws IOException {
    Files.writeString(
        structDir.resolve("unreg.yaml"),
        """
        name: unreg
        data:
          code:
            datatype: "cli_test_never_registered_type"
        """);
    Path out = newOutDir();

    Result r = run(writeJob("unreg.yaml", embedded(3), out), COUNT, "5");

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("cli_test_never_registered_type");
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldFailBeforeWritingWhenFakerTypesFileIsMissing() throws IOException {
    writeSkuStructure();
    Path missing = tempDir.resolve("no-such-types.yaml");
    Path out = newOutDir();

    Result r = run(writeJob("sku.yaml", embedded(3), out), "--faker-types", missing.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err())
        .contains("Failed to read Datafaker types config")
        .contains(missing.toString());
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldFailWhenFakerTypesRegexIsInvalid() throws IOException {
    writeSkuStructure();
    Path types = writeTypes("badregex.yaml", "types:\n  cli_test_badregex: \"regex:[A-Z\"\n");
    Path out = newOutDir();

    Result r = run(writeJob("sku.yaml", embedded(3), out), "--faker-types", types.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err())
        .contains("Invalid Datafaker type 'cli_test_badregex'")
        .contains(types.toString());
    assertThat(out.resolve(OUTPUT_JSON)).doesNotExist();
  }

  @Test
  void shouldFailWhenFakerTypesExpressionDoesNotExist() throws IOException {
    writeSkuStructure();
    Path types = writeTypes("badexpr.yaml", "types:\n  cli_test_badexpr: beer.noSuchMethod\n");

    Result r =
        run(writeJob("sku.yaml", embedded(3), newOutDir()), "--faker-types", types.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Invalid Datafaker type 'cli_test_badexpr'");
  }

  @Test
  void shouldFailWhenFakerTypesYamlIsNotAMapping() throws IOException {
    writeSkuStructure();
    Path types = writeTypes("list.yaml", "- just\n- a list\n");

    Result r =
        run(writeJob("sku.yaml", embedded(3), newOutDir()), "--faker-types", types.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err())
        .contains("Empty or invalid Datafaker types config")
        .contains(types.toString());
  }

  @Test
  void shouldFailWhenFakerTypesYamlIsUnparseable() throws IOException {
    writeSkuStructure();
    Path types = writeTypes("garbage.yaml", "types: [unterminated\n  : :\n");

    Result r =
        run(writeJob("sku.yaml", embedded(3), newOutDir()), "--faker-types", types.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err())
        .contains("Failed to read Datafaker types config")
        .contains(types.toString());
  }

  @Test
  void shouldFailWhenFakerTypesFileIsEmpty() throws IOException {
    writeSkuStructure();
    Path types = writeTypes("empty.yaml", "");

    Result r =
        run(writeJob("sku.yaml", embedded(3), newOutDir()), "--faker-types", types.toString());

    assertThat(r.exit()).isEqualTo(RUNTIME_ERROR);
    assertThat(r.err()).contains("Empty or invalid Datafaker types config");
  }
}
