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

    // Post-inspect edit (the "review, then adjust" step): the inspector emits `serial` for every
    // PK (recordIndex + 1), so the flat FK `ref[customers.id, 1..count]` lines up with it as long
    // as parent and child jobs run at the same --count. The join table needs no edit at all: its
    // composite PK comes out as `ref[..., 1..count, unique=pk]` on both columns.
    // orders.status: the inspector does not translate CHECK (status IN (...)) into an enum, so
    // it emits char[1..16] (gap tracked as issue #214); replace it with the allowed values or
    // the CHECK rejects the insert.
    replaceDatatype(
        structures.resolve("orders.yaml"), "status", "enum[new,paid,shipped,cancelled]");

    runSql("DROP TABLE IF EXISTS order_tags, tags, orders, customers CASCADE");
    runSql(Files.readString(ddl));

    // Parents first; all counts equal ROWS so ref[..., 1..count] stays inside the pool.
    for (String table : new String[] {"customers", "orders", "tags", "order_tags"}) {
      Path job = tmp.resolve(table + "_job.yaml");
      writeString(job, databaseJob(structures.toString(), table + ".yaml", table, 42));
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

  /** Rewrites the {@code datatype:} line of one field in a generated structure YAML. */
  private static void replaceDatatype(Path file, String field, String datatype) throws Exception {
    String yaml = Files.readString(file);
    String updated =
        yaml.replaceFirst("(?m)^(  " + field + ":\\R    datatype: ).*$", "$1\"" + datatype + "\"");
    assertThat(updated).as("field %s found in %s", field, file).isNotEqualTo(yaml);
    Files.writeString(file, updated);
  }
}
