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

package com.datagenerator.inspector.ddl;

import static com.datagenerator.inspector.InspectionTestSupport.datatypesOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #492: a nullable non-key column gets {@code nullable: 0.05}; keys and NOT NULL columns do not.
 */
class DdlInspectorNullableTest {

  private static final Path NORTHWIND_PG =
      Path.of("src/test/resources/northwind/postgres/northwind-pg-dump.sql");

  /** Field name to {@code nullable} rate (absent key maps to null). */
  private static Map<String, Double> nullables(Inspection in, String table) {
    DataStructure structure =
        in.structures().stream()
            .filter(s -> s.getName().equals(table))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no structure named " + table));
    Map<String, Double> result = new LinkedHashMap<>();
    structure.getData().forEach((field, def) -> result.put(field, def.getNullable()));
    return result;
  }

  @Test
  void shouldMarkNullableColumnAndLeaveNotNullColumnsAlone(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE t (id BIGINT PRIMARY KEY, a VARCHAR(10), b VARCHAR(10) NOT NULL,"
                + " c INT NOT NULL, d INT, e DATE, f DECIMAL(8,2));");

    assertThat(nullables(in, "t"))
        .containsEntry("a", 0.05)
        .containsEntry("d", 0.05)
        .containsEntry("e", 0.05)
        .containsEntry("f", 0.05)
        .containsEntry("b", null)
        .containsEntry("c", null);
  }

  @Test
  void shouldNotMarkInlinePrimaryKeyEvenWithoutNotNull(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(dir, "CREATE TABLE t (id BIGINT PRIMARY KEY, n INT);");

    assertThat(nullables(in, "t")).containsEntry("id", null).containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkTableLevelPrimaryKeyColumns(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE t (id BIGINT, n INT, PRIMARY KEY (id));"
                + "CREATE TABLE u (a INT, b INT, n INT, PRIMARY KEY (a, b));");

    assertThat(nullables(in, "t")).containsEntry("id", null).containsEntry("n", 0.05);
    assertThat(nullables(in, "u"))
        .containsEntry("a", null)
        .containsEntry("b", null)
        .containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkUniqueColumn(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir, "CREATE TABLE t (id INT PRIMARY KEY, code INT UNIQUE, n INT);");

    assertThat(datatypesOf(in, "t")).containsEntry("code", "unique[1..count]");
    assertThat(nullables(in, "t")).containsEntry("code", null).containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkSerialColumn(@TempDir Path dir) {
    Inspection in = DdlTestSupport.inspectDdl(dir, "CREATE TABLE t (seq BIGSERIAL, n INT);");

    assertThat(datatypesOf(in, "t")).containsEntry("seq", "serial");
    assertThat(nullables(in, "t")).containsEntry("seq", null).containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkCycleKeyColumn(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5) UNIQUE"
                + " CHECK (kind IN ('new','paid','done')), n INT);");

    assertThat(datatypesOf(in, "status")).containsEntry("kind", "cycle[new,paid,done]");
    assertThat(nullables(in, "status")).containsEntry("kind", null).containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkUniqueRefKeyColumn(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE a (id BIGINT PRIMARY KEY);"
                + "CREATE TABLE a_detail (a_id BIGINT PRIMARY KEY REFERENCES a(id), n INT);");

    assertThat(datatypesOf(in, "a_detail")).containsEntry("a_id", "ref[a.id, unique]");
    assertThat(nullables(in, "a_detail")).containsEntry("a_id", null).containsEntry("n", 0.05);
  }

  @Test
  void shouldNotMarkCompositeUniqueRefGroupColumns(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE a (id BIGINT PRIMARY KEY);"
                + "CREATE TABLE b (id BIGINT PRIMARY KEY);"
                + "CREATE TABLE ab (a_id BIGINT REFERENCES a(id), b_id BIGINT REFERENCES b(id),"
                + " PRIMARY KEY (a_id, b_id));");

    assertThat(nullables(in, "ab")).containsEntry("a_id", null).containsEntry("b_id", null);
  }

  @Test
  void shouldMarkNullablePlainForeignKeyAndNotTheNotNullOne(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE p (id BIGINT PRIMARY KEY);"
                + "CREATE TABLE c (id BIGINT PRIMARY KEY,"
                + " opt_p BIGINT REFERENCES p(id), req_p BIGINT NOT NULL REFERENCES p(id));");

    assertThat(datatypesOf(in, "c"))
        .containsEntry("opt_p", "ref[p.id]")
        .containsEntry("req_p", "ref[p.id]");
    assertThat(nullables(in, "c")).containsEntry("opt_p", 0.05).containsEntry("req_p", null);
  }

  @Test
  void shouldLeaveOtherColumnsMarkedWhenASiblingColumnIsOmitted(@TempDir Path dir) {
    Inspection in =
        DdlTestSupport.inspectDdl(
            dir,
            "CREATE TABLE t (id INT IDENTITY(1,1) PRIMARY KEY, name VARCHAR(10),"
                + " code VARCHAR(5) NOT NULL);");

    Map<String, Double> rates = nullables(in, "t");
    assertThat(rates).containsEntry("name", 0.05).containsEntry("code", null);
    assertThat(rates.get("id")).as("key column, whether kept or omitted").isNull();
  }

  @Test
  void shouldMarkNorthwindPostgresNullableColumnsOnly() {
    Inspection in = new DdlInspector().inspect(NORTHWIND_PG, NestingOptions.none(), false);

    assertThat(nullables(in, "customers"))
        .containsEntry("fax", 0.05)
        .containsEntry("company_name", null);
    assertThat(nullables(in, "suppliers"))
        .containsEntry("fax", 0.05)
        .containsEntry("company_name", null);
  }
}
