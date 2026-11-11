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

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Self-references {@code ref[t.c]} against a real PostgreSQL with the FK enforced (#213): small
 * batches make references cross batches and commits, under every transaction strategy and thread
 * count.
 *
 * <p>Run with: {@code ./gradlew :cli:slowTest}
 */
@Tag("integration")
@Tag("slow")
class SelfRefDatabaseE2EIT extends PostgresE2ESupport {

  private static final long SEED = 20261111L;
  private static final int ROWS = 500;

  @TempDir Path dir;

  @ParameterizedTest
  @CsvSource({
    "per_batch,1,true",
    "per_batch,4,true",
    "per_job,1,true",
    "per_job,4,true",
    "auto_commit,1,true",
    "auto_commit,4,true",
    "per_batch,1,false",
    "per_batch,4,false",
    "per_job,4,false",
    "auto_commit,4,false"
  })
  void shouldLoadSelfReferencingTableWithEnforcedForeignKey(
      String strategy, int threads, boolean nullableColumn) throws Exception {
    String table = "selfref_t";
    runSql(
        "DROP TABLE IF EXISTS "
            + table
            + " CASCADE;"
            + "CREATE TABLE "
            + table
            + " (id BIGINT PRIMARY KEY, parent_id BIGINT "
            + (nullableColumn ? "" : "NOT NULL ")
            + "REFERENCES "
            + table
            + "(id));");
    writeString(
        dir.resolve(table + ".yaml"),
        "name: "
            + table
            + "\ndata:\n  id:\n    datatype: \"serial\"\n  parent_id:\n    datatype: \"ref["
            + table
            + ".id]\"\n"
            + (nullableColumn ? "    nullable: 0.05\n" : ""));
    Path job = dir.resolve("job.yaml");
    writeString(
        job,
        databaseJob(dir.toString(), table + ".yaml", table, SEED)
            .replace("batch_size: 500", "batch_size: 7")
            .replace("transaction_strategy: per_batch", "transaction_strategy: " + strategy));

    assertThat(
            execute(job, "--count", Integer.toString(ROWS), "--threads", Integer.toString(threads)))
        .as("load with %s / %d threads", strategy, threads)
        .isZero();

    assertThat(count("SELECT count(*) FROM " + table)).isEqualTo(ROWS);
    assertThat(
            count(
                "SELECT count(*) FROM "
                    + table
                    + " c WHERE c.parent_id IS NOT NULL AND NOT EXISTS"
                    + " (SELECT 1 FROM "
                    + table
                    + " p WHERE p.id = c.parent_id)"))
        .as("orphans")
        .isZero();
    assertThat(count("SELECT count(*) FROM " + table + " WHERE parent_id > id")).isZero();
    if (nullableColumn) {
      assertThat(count("SELECT count(*) FROM " + table + " WHERE id = 1 AND parent_id IS NULL"))
          .as("record 0 is the NULL root")
          .isEqualTo(1);
      assertThat(count("SELECT count(*) FROM " + table + " WHERE parent_id IS NULL"))
          .isBetween(1L, 80L);
    } else {
      assertThat(count("SELECT count(*) FROM " + table + " WHERE id = 1 AND parent_id = 1"))
          .as("record 0 references itself")
          .isEqualTo(1);
      assertThat(count("SELECT count(*) FROM " + table + " WHERE parent_id = id"))
          .as("only the root is its own parent")
          .isEqualTo(1);
    }
  }
}
