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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full {@code execute} → PostgreSQL run of {@code use-cases/dev-env-bootstrapping}: proves the
 * {@code unique} composite key yields collision-free join rows, is thread-count independent, and
 * that over-capacity counts are rejected before anything is written.
 *
 * <p>Run with: {@code ./gradlew :cli:slowTest}
 */
@Tag("integration")
@Tag("slow")
class UniqueDatabaseE2EIT extends PostgresE2ESupport {

  private static final Path USE_CASE =
      Path.of(System.getProperty("user.dir")).resolveSibling("use-cases/dev-env-bootstrapping");
  private static final long SEED = 20260929L;
  private static final String FINGERPRINT_SQL =
      "SELECT md5(string_agg(task_id || ',' || label_id, ';' ORDER BY id)) FROM task_labels";

  /** Table to row count, in bootstrap.sh order (parents first). */
  private static final Map<String, Integer> SEED_PLAN = new LinkedHashMap<>();

  static {
    SEED_PLAN.put("users", 200);
    SEED_PLAN.put("labels", 20);
    SEED_PLAN.put("projects", 50);
    SEED_PLAN.put("tasks", 500);
    SEED_PLAN.put("comments", 2000);
    SEED_PLAN.put("task_labels", 800);
  }

  @TempDir Path jobsDir;

  @Test
  void shouldSeedCollisionFreeTaskLabelsAndStayDeterministicAcrossThreadsAndFailFast()
      throws Exception {
    writeJobs();

    recreateSchemaAndSeed();
    assertThat(count("SELECT count(*) FROM task_labels")).isEqualTo(800);
    assertThat(count("SELECT count(DISTINCT (task_id, label_id)) FROM task_labels")).isEqualTo(800);
    assertThat(
            count(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = 'task_labels' AND c.contype = 'u'
                """))
        .isEqualTo(1);
    assertThat(
            count(
                """
                SELECT count(*) FROM task_labels tl
                LEFT JOIN tasks t ON t.id = tl.task_id
                LEFT JOIN labels l ON l.id = tl.label_id
                WHERE t.id IS NULL OR l.id IS NULL
                """))
        .as("orphan task_labels FKs")
        .isZero();

    // Determinism: identical fingerprint for 1 and 4 threads on freshly created schemas.
    recreateSchemaAndSeed("--threads", "1");
    String single = queryString(FINGERPRINT_SQL);
    recreateSchemaAndSeed("--threads", "4");
    assertThat(queryString(FINGERPRINT_SQL)).as("fingerprint 1 vs 4 threads").isEqualTo(single);

    // Fail-fast: 10001 > 500 x 20 domain. This negative run uses a copy of the task_labels job
    // with truncate_before_insert: true, so an unchanged table proves the validator rejected the
    // count before destination.open() (which would have truncated).
    Path truncatingJob = jobsDir.resolve("db_task_labels_truncate.yaml");
    writeString(
        truncatingJob,
        Files.readString(jobsDir.resolve("db_task_labels.yaml"))
            .replace("  batch_size:", "  truncate_before_insert: true\n  batch_size:"));
    int code = execute(truncatingJob, "--seed", "" + SEED, "--count", "10001");
    assertThat(code).as("over-capacity execute exit code").isNotZero();
    assertThat(count("SELECT count(*) FROM task_labels")).isEqualTo(800);
    assertThat(queryString(FINGERPRINT_SQL)).isEqualTo(single);
  }

  @Test
  void shouldFillJoinTableViaUniqueRefGroupBelowParentPoolProduct() throws Exception {
    int rows = 30;
    writeString(
        jobsDir.resolve("pair_a.yaml"), "name: pair_a\ndata:\n  id:\n    datatype: serial\n");
    writeString(
        jobsDir.resolve("pair_b.yaml"), "name: pair_b\ndata:\n  id:\n    datatype: serial\n");
    writeString(
        jobsDir.resolve("pair_ab.yaml"),
        String.join(
            "\n",
            "name: pair_ab",
            "data:",
            "  a_id:",
            "    datatype: \"ref[pair_a.id, 1..count, unique=g]\"",
            "  b_id:",
            "    datatype: \"ref[pair_b.id, 1..count, unique=g]\"",
            ""));
    runSql(
        """
        DROP TABLE IF EXISTS pair_ab, pair_a, pair_b CASCADE;
        CREATE TABLE pair_a (id BIGINT PRIMARY KEY);
        CREATE TABLE pair_b (id BIGINT PRIMARY KEY);
        CREATE TABLE pair_ab (
          a_id BIGINT NOT NULL REFERENCES pair_a(id),
          b_id BIGINT NOT NULL REFERENCES pair_b(id),
          PRIMARY KEY (a_id, b_id)
        );
        """);

    for (String table : new String[] {"pair_a", "pair_b", "pair_ab"}) {
      Path job = jobsDir.resolve(table + "_job.yaml");
      writeString(job, databaseJob(jobsDir.toString(), table + ".yaml", table, SEED));
      assertThat(execute(job, "--count", Integer.toString(rows)))
          .as("execute %s exit code", table)
          .isZero();
    }

    // The PK constraint is real, so a successful insert proves no pair collided.
    assertThat(
            count(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = 'pair_ab' AND c.contype = 'p'
                """))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM pair_ab")).isEqualTo(rows);
    assertThat(count("SELECT count(DISTINCT (a_id, b_id)) FROM pair_ab")).isEqualTo(rows);
  }

  private void recreateSchemaAndSeed(String... extraArgs) throws Exception {
    runSql(Files.readString(USE_CASE.resolve("schema.sql")));
    for (Map.Entry<String, Integer> e : SEED_PLAN.entrySet()) {
      String[] args = new String[extraArgs.length + 4];
      args[0] = "--seed";
      args[1] = Long.toString(SEED);
      args[2] = "--count";
      args[3] = e.getValue().toString();
      System.arraycopy(extraArgs, 0, args, 4, extraArgs.length);
      assertThat(execute(jobsDir.resolve("db_" + e.getKey() + ".yaml"), args))
          .as("execute %s exit code", e.getKey())
          .isZero();
      assertThat(count("SELECT count(*) FROM " + e.getKey())).isEqualTo(e.getValue().longValue());
    }
  }

  /** Copies the use-case jobs, rewriting only connection settings and pinning structures_path. */
  private void writeJobs() throws Exception {
    String structures = USE_CASE.resolve("structures").toAbsolutePath().toString();
    for (String table : SEED_PLAN.keySet()) {
      String job = Files.readString(USE_CASE.resolve("jobs/db_" + table + ".yaml"));
      job =
          job.replaceAll("(?m)^(\\s*jdbc_url:).*$", "$1 \"" + postgres.getJdbcUrl() + "\"")
              .replaceAll("(?m)^(\\s*username:).*$", "$1 \"" + postgres.getUsername() + "\"")
              .replaceAll("(?m)^(\\s*password:).*$", "$1 \"" + postgres.getPassword() + "\"")
              .replace("type: database\n", "type: database\nstructures_path: " + structures + "\n");
      writeString(jobsDir.resolve("db_" + table + ".yaml"), job);
    }
  }
}
