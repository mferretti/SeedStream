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
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Strict-mode behaviour for statements that are not modellable tables (#443) and empty-line runs
 * (#436).
 */
class DdlInspectorNonTableStatementsTest {

  private static final String REAL_TABLE = "CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY);\n";

  @Test
  void shouldSkipSilentlyCommentMentioningCreateTableBeforeSet(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        strict(
            dir,
            "-- the CREATE TABLE statements below are hand-written\n"
                + "SET client_encoding = 'UTF8';\n"
                + REAL_TABLE);

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).isEmpty();
  }

  @Test
  void shouldSkipSilentlyFunctionWhoseBodyContainsCreateTable(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        strict(
            dir,
            REAL_TABLE
                + "CREATE FUNCTION make_tmp() RETURNS void AS $$\n"
                + "BEGIN\n"
                + "    CREATE TABLE tmp_scratch (y integer);\n"
                + "END\n"
                + "$$ LANGUAGE plpgsql;\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).isEmpty();
  }

  @Test
  void shouldSkipWithWarningCreateTableAsSelect(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(dir, REAL_TABLE + "CREATE TABLE ledgers_copy AS SELECT * FROM ledgers;\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).singleElement().asString().contains("ledgers_copy");
  }

  @Test
  void shouldSkipWithWarningPartitionOfTable(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(
            dir,
            REAL_TABLE
                + "CREATE TABLE events_2026 PARTITION OF events"
                + " FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).singleElement().asString().contains("events_2026");
  }

  @Test
  void shouldSkipWithWarningMysqlCreateTableLike(@TempDir Path dir) throws IOException {
    Inspection inspection = strict(dir, REAL_TABLE + "CREATE TABLE b_copy LIKE base;\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).singleElement().asString().contains("b_copy");
  }

  @Test
  void shouldEmitTableFollowingPsqlMetaCommands(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(
            dir,
            "\\restrict tok123\n"
                + "\\connect northwind\n"
                + "CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY);\n"
                + "\\unrestrict tok123\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).isEmpty();
  }

  // ---- #436: empty-line runs end to end ----

  @Test
  void shouldParseTableWithEmptyLineRunsBeforeInsideAndAfterComment(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        strict(
            dir,
            "\n\n\n-- orders\n\n\nCREATE TABLE orders (\n  id INTEGER NOT NULL,\n\n\n  note VARCHAR(20)\n);\n");

    assertThat(names(inspection)).containsExactly("orders");
    assertThat(datatypesOf(inspection, "orders")).containsKeys("id", "note");
  }

  @Test
  void shouldParseTableWithWhitespaceOnlyLines(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(dir, "CREATE TABLE t (\n  id INTEGER,\n   \n\t\n  note VARCHAR(20)\n);\n");

    assertThat(datatypesOf(inspection, "t")).containsKeys("id", "note");
  }

  @Test
  void shouldKeepEmptyLineRunInsideDefaultStringLiteral(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(dir, "CREATE TABLE t (id INTEGER, note VARCHAR(20) DEFAULT 'a\n\n\nb');\n");

    assertThat(datatypesOf(inspection, "t")).containsKeys("id", "note");
  }

  // ---- must keep working ----

  @Test
  void shouldKeepModellingTableVariants(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(
            dir,
            "CREATE TABLE IF NOT EXISTS t_ine (a INTEGER);\n"
                + "CREATE TEMPORARY TABLE t_temporary (a INTEGER);\n"
                + "CREATE TEMP TABLE t_temp (a INTEGER);\n"
                + "CREATE UNLOGGED TABLE t_unlogged (a INTEGER);\n"
                + "CREATE GLOBAL TEMPORARY TABLE t_gtt (a NUMBER(10));\n");

    assertThat(names(inspection))
        .containsExactly("t_ine", "t_temporary", "t_temp", "t_unlogged", "t_gtt");
    assertThat(inspection.warnings()).isEmpty();
  }

  @Test
  void shouldIgnoreCreateTableInsideStringLiteralAndCommentOnTable(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        strict(
            dir,
            REAL_TABLE
                + "COMMENT ON TABLE base IS 'CREATE TABLE is documented elsewhere';\n"
                + "CREATE EXTENSION IF NOT EXISTS foo WITH SCHEMA 'CREATE TABLE';\n");

    assertThat(names(inspection)).containsExactly("base");
    assertThat(inspection.warnings()).isEmpty();
  }

  @Test
  void shouldStillAbortStrictModeOnUnparseableCreateTable(@TempDir Path dir) throws IOException {
    Path sql = dir.resolve("broken.sql");
    Files.writeString(sql, REAL_TABLE + "CREATE TABLE broken (id INTEGER, ,, );\n");

    assertThatThrownBy(() -> new DdlInspector().inspect(sql, NestingOptions.none(), false))
        .hasMessageContaining("broken");
  }

  private static Inspection strict(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return new DdlInspector().inspect(sql, NestingOptions.none(), false);
  }

  private static List<String> names(Inspection inspection) {
    return inspection.structures().stream().map(DataStructure::getName).toList();
  }
}
