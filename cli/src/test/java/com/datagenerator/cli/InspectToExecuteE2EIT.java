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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * DDL → {@code inspect} → {@code execute} → PostgreSQL, following the documented "inspect, review
 * and adjust, then write a job" workflow.
 *
 * <p>Run with: {@code ./gradlew :cli:slowTest}
 */
@Tag("integration")
@Tag("slow")
class InspectToExecuteE2EIT extends PostgresE2ESupport {

  private static final int ROWS = 50;

  @TempDir Path tmp;

  @Test
  void shouldInspectDdlThenSeedTablesWithValidForeignKeysAndChecks() throws Exception {
    Path ddl = Path.of(System.getProperty("user.dir"), "src/test/resources/e2e/shop.sql");
    Path structures = tmp.resolve("structures");

    int inspectCode =
        new CommandLine(new InspectCommand()).execute(ddl.toString(), "-o", structures.toString());
    assertThat(inspectCode).as("inspect exit code").isZero();
    assertThat(structures.resolve("customers.yaml")).exists();
    assertThat(structures.resolve("orders.yaml")).exists();

    // No structure edit: the inspector emits `serial` for every PK (recordIndex + 1) and the FK
    // `ref[customers.id]`, whose pool comes from the parent's key and its row count in counts.yaml
    // (#442); the join table's composite PK comes out as `ref[..., unique=pk]` on both columns;
    // and CHECK (status IN (...)) becomes an enum (#214), which the database's own CHECK verifies
    // on load below.
    assertThat(structures.resolve("orders.yaml"))
        .content()
        .contains("enum[new,paid,shipped,cancelled]");

    runSql("DROP TABLE IF EXISTS order_tags, tags, orders, customers CASCADE");
    runSql(Files.readString(ddl));

    // inspect emits bare ref[t.c] plus a counts.yaml skeleton (#442): set the real sizes, as the
    // skeleton's header says, and point every job at it. Parents first.
    String[] tables = {"customers", "orders", "tags", "order_tags"};
    Path countsFile = structures.resolve("counts.yaml");
    assertThat(countsFile).exists();
    StringBuilder counts = new StringBuilder();
    for (String table : tables) {
      counts.append(table).append(": ").append(ROWS).append('\n');
    }
    writeString(countsFile, counts.toString());
    for (String table : tables) {
      Path job = tmp.resolve(table + "_job.yaml");
      writeString(
          job,
          databaseJob(structures.toString(), table + ".yaml", table, 42)
              + "counts: "
              + countsFile.toAbsolutePath()
              + "\n");
      assertThat(execute(job, "--count", Integer.toString(ROWS)))
          .as("execute %s exit code", table)
          .isZero();
      assertThat(count("SELECT count(*) FROM " + table)).isEqualTo(ROWS);
    }

    assertThat(
            count(
                "SELECT count(*) FROM orders o LEFT JOIN customers c ON c.id = o.customer_id"
                    + " WHERE c.id IS NULL"))
        .as("orphan orders")
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM orders"
                    + " WHERE status NOT IN ('new','paid','shipped','cancelled')"))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM order_tags ot"
                    + " LEFT JOIN orders o ON o.id = ot.order_id"
                    + " LEFT JOIN tags t ON t.id = ot.tag_id"
                    + " WHERE o.id IS NULL OR t.id IS NULL"))
        .as("orphan order_tags")
        .isZero();
    assertThat(count("SELECT count(DISTINCT (order_id, tag_id)) FROM order_tags"))
        .as("distinct join pairs")
        .isEqualTo(ROWS);
  }
}
