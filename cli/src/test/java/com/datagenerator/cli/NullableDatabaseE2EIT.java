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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code nullable: <rate>} against a real PostgreSQL (#492, #453): nullable int / date / decimal /
 * char / ref columns receive SQL NULL, a NOT NULL column with {@code nullable} fails at insert.
 *
 * <p>Run with: {@code ./gradlew :cli:slowTest}
 */
@Tag("integration")
@Tag("slow")
class NullableDatabaseE2EIT extends PostgresE2ESupport {

  private static final long SEED = 20261111L;
  private static final int ROWS = 500;

  @TempDir Path dir;

  private void structure(String name, String... fieldTypeNullable) throws Exception {
    StringBuilder sb = new StringBuilder("name: " + name + "\ndata:\n");
    for (int i = 0; i < fieldTypeNullable.length; i += 3) {
      sb.append("  ")
          .append(fieldTypeNullable[i])
          .append(":\n    datatype: \"")
          .append(fieldTypeNullable[i + 1])
          .append("\"\n");
      if (fieldTypeNullable[i + 2] != null) {
        sb.append("    nullable: ").append(fieldTypeNullable[i + 2]).append('\n');
      }
    }
    writeString(dir.resolve(name + ".yaml"), sb.toString());
  }

  private int load(String table, int count) throws Exception {
    Path job = dir.resolve("job_" + table + ".yaml");
    writeString(job, databaseJob(dir.toString(), table + ".yaml", table, SEED));
    return execute(job, "--count", Integer.toString(count));
  }

  @Test
  void shouldInsertSqlNullForEveryNullableColumnTypeAndNeverForNotNullColumn() throws Exception {
    runSql(
        """
        DROP TABLE IF EXISTS nul_t, nul_parent CASCADE;
        CREATE TABLE nul_parent (id BIGINT PRIMARY KEY);
        CREATE TABLE nul_t (
          id BIGINT PRIMARY KEY,
          i INT,
          d DATE,
          amt NUMERIC(10,2),
          c VARCHAR(20),
          r BIGINT REFERENCES nul_parent(id),
          req INT NOT NULL
        );
        """);
    structure("nul_parent", "id", "serial", null);
    structure(
        "nul_t",
        "id",
        "serial",
        null,
        "i",
        "int[1..1000]",
        "0.3",
        "d",
        "date[2020-01-01..2025-12-31]",
        "0.3",
        "amt",
        "decimal[0.0..100.0]",
        "0.3",
        "c",
        "char[3..10]",
        "0.3",
        "r",
        "ref[nul_parent.id, 1..20]",
        "0.3",
        "req",
        "int[1..100]",
        null);

    assertThat(load("nul_parent", 20)).isZero();
    assertThat(load("nul_t", ROWS)).as("insert with nullable columns").isZero();

    assertThat(count("SELECT count(*) FROM nul_t")).isEqualTo(ROWS);
    for (String col : new String[] {"i", "d", "amt", "c", "r"}) {
      long nulls = count("SELECT count(*) FROM nul_t WHERE " + col + " IS NULL");
      assertThat(nulls).as("NULLs in " + col).isBetween(70L, 250L);
    }
    assertThat(count("SELECT count(*) FROM nul_t WHERE req IS NULL")).isZero();
    assertThat(count("SELECT count(*) FROM nul_t WHERE id IS NULL")).isZero();
  }

  @Test
  void shouldFailAtInsertWhenNullableFieldMapsToNotNullColumn() throws Exception {
    runSql(
        """
        DROP TABLE IF EXISTS nul_nn;
        CREATE TABLE nul_nn (id BIGINT PRIMARY KEY, v INT NOT NULL);
        """);
    structure("nul_nn", "id", "serial", null, "v", "int[1..100]", "1");

    assertThat(load("nul_nn", 10)).as("NOT NULL violation").isNotZero();
    assertThat(count("SELECT count(*) FROM nul_nn")).isZero();
  }
}
