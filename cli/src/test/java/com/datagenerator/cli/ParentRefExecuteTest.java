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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * End to end for {@code ref[t.c]} resolved from the parent's key and a row count (#442): the bare
 * form must be byte-for-byte the explicit-range form, and every misuse fails at startup.
 */
class ParentRefExecuteTest {
  private static final String OPT_JOB = "--job";
  private static final String OPT_COUNT = "--count";
  private static final String OPT_COUNTS = "--counts";
  private static final ObjectMapper JSON = new ObjectMapper();

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

  private void structure(String name, String... fieldAndType) throws IOException {
    StringBuilder sb = new StringBuilder("name: " + name + "\ndata:\n");
    for (int i = 0; i < fieldAndType.length; i += 2) {
      sb.append("  ")
          .append(fieldAndType[i])
          .append(":\n    datatype: \"")
          .append(fieldAndType[i + 1])
          .append("\"\n");
    }
    Files.writeString(structDir.resolve(name + ".yaml"), sb.toString());
  }

  /** Writes a child {@code name} with a key-ish field {@code fk} and a free field {@code x}. */
  private void child(String name, String fkType) throws IOException {
    structure(name, "fk", fkType, "x", "int[1..1000000]");
  }

  private Path job(String name, String... extraLines) throws IOException {
    Path jobFile = tempDir.resolve("job_" + name + ".yaml");
    List<String> lines = new ArrayList<>();
    lines.add("source: " + name + ".yaml");
    lines.add("type: file");
    lines.add("structures_path: " + structDir.toAbsolutePath());
    lines.addAll(List.of(extraLines));
    lines.addAll(
        List.of(
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outDir.toAbsolutePath() + "/" + name + "/output",
            ""));
    Files.writeString(jobFile, String.join("\n", lines));
    return jobFile;
  }

  private Path countsFile(String fileName, String content) throws IOException {
    Path f = tempDir.resolve(fileName);
    Files.writeString(f, content);
    return f;
  }

  private CliTestSupport.Result exec(Path jobFile, int count, int threads, String... extra) {
    List<String> args = new ArrayList<>();
    args.addAll(
        List.of(
            "execute",
            OPT_JOB,
            jobFile.toString(),
            OPT_COUNT,
            String.valueOf(count),
            "--threads",
            String.valueOf(threads)));
    args.addAll(List.of(extra));
    return CliTestSupport.run(args.toArray(String[]::new));
  }

  private List<String> lines(String name, Path jobFile, int count, int threads, String... extra)
      throws IOException {
    CliTestSupport.Result r = exec(jobFile, count, threads, extra);
    assertThat(r.exit()).as(r.err()).isZero();
    return Files.readAllLines(outDir.resolve(name + "/output.json"));
  }

  private Set<Long> longs(List<String> jsonLines, String field) throws IOException {
    Set<Long> set = new HashSet<>();
    for (String l : jsonLines) {
      set.add(JSON.readTree(l).get(field).asLong());
    }
    return set;
  }

  /** Asserts bare child output equals explicit child output, for 1 and 4 threads. */
  private void assertBareEqualsExplicit(
      String bareType, String explicitType, int count, String counts, String... cliCounts)
      throws IOException {
    child("bare", bareType);
    child("expl", explicitType);
    Path bareJob = job("bare", "counts: counts.yaml");
    countsFile("counts.yaml", counts);
    Path explJob = job("expl");
    for (int threads : new int[] {1, 4}) {
      List<String> expected = lines("expl", explJob, count, threads);
      assertThat(expected).hasSize(count);
      assertThat(lines("bare", bareJob, count, threads, cliCounts))
          .as("%s vs %s with %d threads", bareType, explicitType, threads)
          .isEqualTo(expected);
    }
  }

  // ── byte identity ────────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "serial|1..500",
        "serial[100]|100..599",
        "serial[1..500]|1..500",
        "unique[1..count]|1..500",
        "unique[1..500]|1..500"
      })
  void shouldMatchExplicitRangeWhenBareRefPointsAtParentKey(String parentType, String range)
      throws Exception {
    structure("p", "id", parentType, "y", "int[1..9]");

    assertBareEqualsExplicit("ref[p.id]", "ref[p.id, " + range + "]", 2000, "p: 500\n");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "serial|1..500",
        "serial[100]|100..599",
        "serial[1..500]|1..500",
        "unique[1..count]|1..500",
        "unique[1..500]|1..500"
      })
  void shouldMatchExplicitUniqueWhenBareRefIsUnique(String parentType, String range)
      throws Exception {
    structure("p", "id", parentType, "y", "int[1..9]");

    assertBareEqualsExplicit(
        "ref[p.id, unique]", "ref[p.id, " + range + ", unique]", 500, "p: 500\n");
  }

  @Test
  void shouldMatchExplicitCompositeUniqueWhenTwoParentsShareGroup() throws Exception {
    structure("p1", "id", "serial");
    structure("p2", "id", "unique[1..count]");
    structure("bare", "a", "ref[p1.id, unique=pk]", "b", "ref[p2.id, unique=pk]");
    structure("expl", "a", "ref[p1.id, 1..20, unique=pk]", "b", "ref[p2.id, 1..30, unique=pk]");
    countsFile("counts.yaml", "p1: 20\np2: 30\n");
    Path bareJob = job("bare", "counts: counts.yaml");
    Path explJob = job("expl");

    for (int threads : new int[] {1, 4}) {
      List<String> expected = lines("expl", explJob, 100, threads);
      assertThat(lines("bare", bareJob, 100, threads)).isEqualTo(expected);
    }
  }

  @Test
  void shouldMatchExplicitRangeWhenBareRefIsInNestedObject() throws Exception {
    structure("p", "id", "serial");
    structure("ship_bare", "uid", "ref[p.id]");
    structure("ship_expl", "uid", "ref[p.id, 1..500]");
    structure("bare", "s", "object[ship_bare]", "x", "int[1..99]");
    structure("expl", "s", "object[ship_expl]", "x", "int[1..99]");
    countsFile("counts.yaml", "p: 500\n");

    for (int threads : new int[] {1, 4}) {
      assertThat(lines("bare", job("bare", "counts: counts.yaml"), 300, threads))
          .isEqualTo(lines("expl", job("expl"), 300, threads));
    }
  }

  @Test
  void shouldMatchExplicitRangeWhenBareRefIsInArrayOfObjects() throws Exception {
    structure("p", "id", "serial");
    structure("line_bare", "uid", "ref[p.id]");
    structure("line_expl", "uid", "ref[p.id, 1..500]");
    structure("bare", "lines", "array[object[line_bare], 1..3]");
    structure("expl", "lines", "array[object[line_expl], 1..3]");
    countsFile("counts.yaml", "p: 500\n");

    for (int threads : new int[] {1, 4}) {
      assertThat(lines("bare", job("bare", "counts: counts.yaml"), 300, threads))
          .isEqualTo(lines("expl", job("expl"), 300, threads));
    }
  }

  // ── membership ───────────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"serial", "serial[100]", "serial[1..500]", "unique[1..count]", "unique[1..500]"})
  void shouldReferenceOnlyExistingParentValuesWhenParentGeneratedWithCountN(String parentType)
      throws Exception {
    structure("p", "id", parentType);
    child("kid", "ref[p.id]");
    countsFile("counts.yaml", "p: 500\n");

    Set<Long> parentIds = longs(lines("p", job("p"), 500, 1), "id");
    Set<Long> childIds = longs(lines("kid", job("kid", "counts: counts.yaml"), 2000, 4), "fk");

    assertThat(parentIds).hasSize(500);
    assertThat(childIds).isNotEmpty().isSubsetOf(parentIds);
    assertThat(childIds.size()).as("pool is actually spread, not a constant").isGreaterThan(100);
  }

  @Test
  void shouldCoverWholeParentWhenBareUniqueRefIsOneToOne() throws Exception {
    structure("p", "id", "serial");
    child("kid", "ref[p.id, unique]");
    countsFile("counts.yaml", "p: 500\n");

    Set<Long> parentIds = longs(lines("p", job("p"), 500, 1), "id");
    List<String> kid = lines("kid", job("kid", "counts: counts.yaml"), 500, 4);

    assertThat(longs(kid, "fk")).isEqualTo(parentIds);
  }

  // ── errors ───────────────────────────────────────────────────────────────────

  @Test
  void shouldFailWhenParentHasNoCount() throws Exception {
    structure("customers", "customer_id", "serial");
    structure("orders", "customer_id", "ref[customers.customer_id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err())
        .contains(
            "ref[customers.customer_id] in orders.customer_id: no row count for 'customers' — add"
                + " it to the job's counts file or pass --counts customers=N");
    assertThat(outDir.resolve("orders/output.json")).doesNotExist();
  }

  @Test
  void shouldFailWhenCountsFileLacksParentEntry() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");
    countsFile("counts.yaml", "other: 5\n");

    CliTestSupport.Result r = exec(job("orders", "counts: counts.yaml"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("no row count for 'users'");
  }

  @Test
  void shouldFailSparseWhenParentUniqueRangeIsWiderThanCount() throws Exception {
    structure("users", "id", "unique[1..1000000]");
    structure("orders", "user_id", "ref[users.id]");
    countsFile("counts.yaml", "users: 500\n");

    CliTestSupport.Result r = exec(job("orders", "counts: counts.yaml"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err())
        .contains(
            "ref[users.id] in orders.user_id: users.id is unique[1..1000000] but users has 500"
                + " rows, so only some of its values exist. Use serial or unique[1..count] on"
                + " users.id, or an explicit ref[users.id, 1..N].");
    assertThat(outDir.resolve("orders/output.json")).doesNotExist();
  }

  @Test
  void shouldFailSparseWhenBareUniqueRefOverSparseParent() throws Exception {
    structure("users", "id", "unique[1..1000000]");
    structure("orders", "user_id", "ref[users.id, unique]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=500");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("so only some of its values exist");
  }

  @Test
  void shouldFailWhenParentRefUniqueIsSparse() throws Exception {
    structure("hub", "id", "unique[1..count]");
    structure("users", "id", "ref[hub.id, 1..1000, unique]");
    structure("orders", "user_id", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=500");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("users.id").contains("so only some of its values exist");
  }

  @Test
  void shouldAcceptParentRefUniqueWhenExactlyNRows() throws Exception {
    structure("hub", "id", "unique[1..count]");
    structure("users", "id", "ref[hub.id, 1..500, unique]");
    structure("orders", "user_id", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=500");

    assertThat(r.exit()).as(r.err()).isZero();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"serial[1..100]", "unique[1..300]", "unique[1..100]"})
  void shouldFailWhenParentCannotHaveNRows(String parentType) throws Exception {
    structure("users", "id", parentType);
    structure("orders", "user_id", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=500");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err())
        .contains("ref[users.id]")
        .contains("orders.user_id")
        .contains("users.id")
        .contains("500");
    assertThat(outDir.resolve("orders/output.json")).doesNotExist();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "int[1..1000]|int",
        "char[3..5]|char",
        "cycle[1,2,3]|cycle",
        "ref[hub.id, 1..100]|ref[hub.id, 1..100]",
        "decimal[0.0..1.0]|decimal"
      })
  void shouldFailWhenParentFieldIsNotReferenceable(String parentType, String named)
      throws Exception {
    structure("hub", "id", "serial");
    structure("users", "id", parentType);
    structure("orders", "user_id", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=500");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err())
        .contains("ref[users.id]")
        .contains("orders.user_id")
        .contains("users.id")
        .contains(named);
  }

  @Test
  void shouldFailWhenParentStructureNotFound() throws Exception {
    structure("orders", "user_id", "ref[ghost.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "ghost=5");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("ref[ghost.id]").contains("orders.user_id").contains("ghost");
  }

  @Test
  void shouldFailWhenFieldNotInParent() throws Exception {
    structure("users", "id", "serial");
    structure("orders", "user_id", "ref[users.nope]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=5");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err())
        .contains("ref[users.nope]")
        .contains("orders.user_id")
        .contains("users.nope");
  }

  @Test
  void shouldFailWhenStructureReferencesItself() throws Exception {
    structure("t", "id", "serial", "parent", "ref[t.id]");

    CliTestSupport.Result r = exec(job("t"), 10, 1, OPT_COUNTS, "t=10");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("self-references are not supported yet (#213)");
  }

  @Test
  void shouldFailBeforeConnectingWhenDatabaseJobHasUnresolvableRef() throws Exception {
    structure("users", "id", "serial");
    structure("orders", "user_id", "ref[users.id]");
    Path jobFile = tempDir.resolve("db_job.yaml");
    Files.writeString(
        jobFile,
        String.join(
            "\n",
            "source: orders.yaml",
            "type: database",
            "structures_path: " + structDir.toAbsolutePath(),
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  jdbc_url: jdbc:postgresql://127.0.0.1:1/test",
            "  username: sa",
            "  password: \"\"",
            "  table: orders",
            "  truncate_before_insert: true",
            ""));

    CliTestSupport.Result r = exec(jobFile, 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("no row count for 'users'").doesNotContain("failed after");
  }

  // ── counts validation ────────────────────────────────────────────────────────

  @Test
  void shouldFailNamingResolvedPathWhenCountsFileMissing() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders", "counts: nowhere.yaml"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains(tempDir.resolve("nowhere.yaml").toString());
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"users: abc|users", "users: 0|users", "users: -3|users", "users: 2.5|users"})
  void shouldFailNamingEntryWhenCountsFileValueIsNotPositiveInteger(String content, String entry)
      throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");
    countsFile("counts.yaml", content + "\n");

    CliTestSupport.Result r = exec(job("orders", "counts: counts.yaml"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("'" + entry + "'");
  }

  @Test
  void shouldFailWhenCountsFileIsNotAMap() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");
    countsFile("counts.yaml", "- users\n- 5\n");

    CliTestSupport.Result r = exec(job("orders", "counts: counts.yaml"), 10, 1);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("counts.yaml");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"users=abc|users", "users=0|users", "users=-3|users"})
  void shouldFailNamingEntryWhenCliCountsValueIsNotPositiveInteger(String arg, String entry)
      throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, arg);

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("--counts").contains("'" + entry + "'");
  }

  @Test
  void shouldFailWhenCliCountsPairHasNoEquals() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");

    CliTestSupport.Result r = exec(job("orders"), 10, 1, OPT_COUNTS, "users=5,orders");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(r.err()).contains("--counts").contains("orders");
  }

  @Test
  void shouldRejectCountsOptionGivenTwice() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");

    CliTestSupport.Result r =
        exec(job("orders"), 10, 1, OPT_COUNTS, "users=5", OPT_COUNTS, "other=6");

    assertThat(r.exit()).isNotZero();
    assertThat(r.err()).contains("--counts").contains("only once");
  }

  @Test
  void shouldIgnoreUnknownNamesWhenInFileAndOnCli() throws Exception {
    structure("users", "id", "serial");
    child("orders", "ref[users.id]");
    countsFile("counts.yaml", "users: 50\nunrelated: 7\n");

    List<String> out =
        lines("orders", job("orders", "counts: counts.yaml"), 100, 1, OPT_COUNTS, "stranger=3");

    assertThat(longs(out, "fk")).allMatch(v -> v >= 1 && v <= 50);
  }

  // ── counts precedence and resolution ─────────────────────────────────────────

  @Test
  void shouldUseFileCountWhenOnlyFileGiven() throws Exception {
    assertBareEqualsExplicitWith("p: 50\n", new String[] {}, "1..50", "1..60");
  }

  @Test
  void shouldUseCliCountWhenOnlyCliGiven() throws Exception {
    structure("p", "id", "serial");
    child("bare", "ref[p.id]");
    child("expl", "ref[p.id, 1..40]");

    assertThat(lines("bare", job("bare"), 500, 1, OPT_COUNTS, "p=40"))
        .isEqualTo(lines("expl", job("expl"), 500, 1));
  }

  @Test
  void shouldLetCliWinPerEntryAndKeepOtherFileEntriesWhenBothGiven() throws Exception {
    structure("p1", "id", "serial");
    structure("p2", "id", "serial");
    structure("bare", "a", "ref[p1.id]", "b", "ref[p2.id]");
    structure("expl", "a", "ref[p1.id, 1..100]", "b", "ref[p2.id, 1..50]");
    countsFile("counts.yaml", "p1: 500\np2: 50\n");

    assertThat(lines("bare", job("bare", "counts: counts.yaml"), 400, 1, OPT_COUNTS, "p1=100"))
        .isEqualTo(lines("expl", job("expl"), 400, 1));
  }

  @Test
  void shouldResolveCountsRelativeToJobFileWhenRunFromAnotherDirectory() throws Exception {
    structure("p", "id", "serial");
    child("bare", "ref[p.id]");
    child("expl", "ref[p.id, 1..30]");
    Path jobsDir = Files.createDirectories(tempDir.resolve("deep/jobs"));
    Files.writeString(jobsDir.resolve("counts.yaml"), "p: 30\n");
    Path jobFile = jobsDir.resolve("bare.yaml");
    Files.writeString(
        jobFile,
        String.join(
            "\n",
            "source: bare.yaml",
            "type: file",
            "structures_path: " + structDir.toAbsolutePath(),
            "counts: counts.yaml",
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  path: " + outDir.toAbsolutePath() + "/bare/output",
            ""));

    assertThat(lines("bare", jobFile, 300, 1)).isEqualTo(lines("expl", job("expl"), 300, 1));
  }

  // ── regression ───────────────────────────────────────────────────────────────

  @Test
  void shouldRunUnchangedWhenNoCountsAndNoBareRefs() throws Exception {
    structure("p", "id", "serial");
    child("kid", "ref[p.id, 1..count]");

    List<String> out = lines("kid", job("kid"), 50, 2);

    assertThat(out).hasSize(50);
    assertThat(longs(out, "fk")).allMatch(v -> v >= 1 && v <= 50);
  }

  @Test
  void shouldNotLoadParentWhenExplicitRangeUsedEvenIfParentIsMissing() throws Exception {
    child("kid", "ref[ghost.id, 1..10]");

    assertThat(lines("kid", job("kid"), 20, 1)).hasSize(20);
  }

  // ── small private helpers kept last ──────────────────────────────────────────

  /** File-only counts: bare ref equals explicit range {@code expectedRange}. */
  private void assertBareEqualsExplicitWith(
      String counts, String[] cli, String expectedRange, String wrongRange) throws IOException {
    structure("p", "id", "serial");
    child("bare", "ref[p.id]");
    child("expl", "ref[p.id, " + expectedRange + "]");
    child("wrong", "ref[p.id, " + wrongRange + "]");
    countsFile("counts.yaml", counts);

    List<String> bare = lines("bare", job("bare", "counts: counts.yaml"), 500, 1, cli);

    assertThat(bare).isEqualTo(lines("expl", job("expl"), 500, 1));
    assertThat(bare).isNotEqualTo(lines("wrong", job("wrong"), 500, 1));
  }
}
