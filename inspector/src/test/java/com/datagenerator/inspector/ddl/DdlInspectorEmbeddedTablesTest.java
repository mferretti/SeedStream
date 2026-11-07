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
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tables embedded in {@code CREATE SCHEMA} and T-SQL control flow (#459): an embedded table yields
 * what the same statement standing alone yields. Tests named {@code guard...} already pass on the
 * code before #459 (no false positives, unchanged behaviour); every other test fails there.
 */
class DdlInspectorEmbeddedTablesTest {

  private static final String INT = "int[1..999999]";
  private static final String BASE = "CREATE TABLE base (id INT);\n";

  // ------------------------------------------------------- CREATE SCHEMA

  @Test
  void shouldModelTableInsideCreateSchemaAuthorization(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE SCHEMA s AUTHORIZATION dbo CREATE TABLE sch_tbl (id INT);");

    assertThat(names(in)).containsExactly("sch_tbl");
    assertThat(datatypesOf(in, "sch_tbl")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE SCHEMA s CREATE TABLE t (id INT, n VARCHAR(5))",
        "CREATE SCHEMA AUTHORIZATION u CREATE TABLE t (id INT, n VARCHAR(5))",
        "CREATE SCHEMA s AUTHORIZATION u CREATE TABLE t (id INT, n VARCHAR(5))",
        "CREATE SCHEMA IF NOT EXISTS s CREATE TABLE t (id INT, n VARCHAR(5))",
        "create schema s authorization u create table t (id int, n varchar(5))",
        "CREATE SCHEMA s\nCREATE TABLE t (id INT, n VARCHAR(5))",
        "CREATE SCHEMA s CREATE TABLE s.t (id INT, n VARCHAR(5))"
      })
  void shouldModelSameAsStandaloneForEverySchemaForm(String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, ddl + ";");

    assertSameAsStandalone(dir, in, "CREATE TABLE t (id INT, n VARCHAR(5));", "t");
    assertThat(datatypesOf(in, "t")).containsExactly(entry("id", INT), entry("n", "char[1..5]"));
    loadAll(in, dir);
  }

  @Test
  void shouldModelTableAndSkipViewAndGrantInOraclesCreateSchema(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE SCHEMA AUTHORIZATION u\n"
                + "  CREATE TABLE a (id INT, n VARCHAR(5))\n"
                + "  CREATE VIEW v AS SELECT 1 FROM a\n"
                + "  GRANT SELECT ON a TO x;");

    assertThat(names(in)).containsExactly("a");
    assertSameAsStandalone(dir, in, "CREATE TABLE a (id INT, n VARCHAR(5));", "a");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldModelKeysAndForeignKeysAndAltersAndIndexesInsideCreateSchema(@TempDir Path dir) {
    String body =
        "CREATE TABLE parent (id INT PRIMARY KEY, code INT, email VARCHAR(20))\n"
            + "CREATE TABLE child (cid INT PRIMARY KEY, pid INT REFERENCES parent (id))\n"
            + "CREATE TABLE other (oid INT, pid INT)\n"
            + "CREATE UNIQUE INDEX uq_code ON parent (code)\n"
            + "CREATE INDEX ix_other ON other (pid)\n"
            + "ALTER TABLE other ADD CONSTRAINT other_pk PRIMARY KEY (oid)\n"
            + "ALTER TABLE other ADD CONSTRAINT other_fk FOREIGN KEY (pid) REFERENCES parent (id)\n"
            + "GRANT SELECT ON parent TO PUBLIC";
    Inspection in = inspectDdl(dir, "CREATE SCHEMA s AUTHORIZATION dbo\n" + body + ";");
    Inspection flat = inspectDdl(dir, body.replace("\n", ";\n") + ";");

    assertThat(names(in)).containsExactly("parent", "child", "other");
    for (String table : List.of("parent", "child", "other")) {
      assertSameAsStandalone(in, flat, table);
    }
    assertThat(datatypesOf(in, "parent"))
        .containsEntry("id", "serial")
        .containsEntry("code", "unique[1..count]");
    assertThat(datatypesOf(in, "child")).containsEntry("pid", "ref[parent.id, 1..count]");
    assertThat(datatypesOf(in, "other"))
        .containsEntry("oid", "serial")
        .containsEntry("pid", "ref[parent.id, 1..count]");
    assertThat(in.warnings()).isEqualTo(flat.warnings());
    loadAll(in, dir);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE SCHEMA only_schema;",
        "CREATE SCHEMA IF NOT EXISTS s2;",
        "CREATE SCHEMA s3 AUTHORIZATION me;",
        "CREATE SCHEMA foo DEFAULT CHARACTER SET utf8;",
        "CREATE SCHEMA \"create table\";",
        "CREATE SCHEMA s AUTHORIZATION \"create table\";"
      })
  void guardSchemaWithoutElementsAddsNothing(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + "\n");
  }

  @Test
  void guardSchemaWithoutElementsBeforeTableKeepsTable(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE SCHEMA s;\nCREATE TABLE t (id INT);");

    assertThat(names(in)).containsExactly("t");
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- IF / DROP without separator

  static Stream<String> dropThenCreateX() {
    return Stream.of(
        "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\nCREATE TABLE x (id INT)",
        "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x CREATE TABLE x (id INT)",
        "if object_id('dbo.x') is not null drop table x\ncreate table x (id int)",
        "IF OBJECT_ID('dbo.x') IS NOT NULL\n  DROP TABLE x\nCREATE TABLE x (id INT)",
        "DROP TABLE x CREATE TABLE x (id INT)",
        "DROP TABLE x\nCREATE TABLE x (id INT)",
        "SET ANSI_NULLS ON\nIF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\nCREATE TABLE x (id INT)",
        "SET ANSI_NULLS ON\nSET QUOTED_IDENTIFIER ON\nDROP TABLE x\nCREATE TABLE x (id INT)",
        "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x -- drop first\nCREATE TABLE x (id INT)",
        "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x /* CREATE TABLE q (id INT) */\n"
            + "CREATE TABLE x (id INT)");
  }

  @ParameterizedTest
  @MethodSource("dropThenCreateX")
  void shouldModelTableAfterUnseparatedDrop(String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, ddl + ";");

    assertThat(names(in)).containsExactly("x");
    assertThat(datatypesOf(in, "x")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldModelTableAfterUnseparatedDropWithoutTrailingSemicolon(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\nCREATE TABLE x (id INT)\n");

    assertThat(names(in)).containsExactly("x");
  }

  @Test
  void shouldModelTableAfterUnseparatedDropFollowedByGoBatches(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n"
                + "CREATE TABLE x (id INT)\n"
                + "GO\n"
                + "IF OBJECT_ID('dbo.y') IS NOT NULL DROP TABLE y\n"
                + "CREATE TABLE y (id INT, x_id INT REFERENCES x (id))\n"
                + "GO\n");

    assertThat(names(in)).containsExactly("x", "y");
    assertThat(datatypesOf(in, "y")).containsEntry("x_id", "ref[x.id, 1..count]");
    loadAll(in, dir);
  }

  @Test
  void shouldModelTableAfterUnseparatedDropIntoSameResultAsStandalone(@TempDir Path dir) {
    String table = "CREATE TABLE x (id INT PRIMARY KEY, label VARCHAR(30) NOT NULL)";
    Inspection in =
        inspectDdl(dir, "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n" + table + ";");

    assertSameAsStandalone(dir, in, table + ";", "x");
    assertThat(datatypesOf(in, "x"))
        .containsExactly(entry("id", "serial"), entry("label", "char[1..30]"));
  }

  @Test
  void shouldModelTableAfterIfNotExistsGuard(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='y') CREATE TABLE y (id INT);");

    assertThat(names(in)).containsExactly("y");
    assertThat(datatypesOf(in, "y")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldModelTableAfterIfNotExistsGuardOnItsOwnLine(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'y')\n"
                + "    CREATE TABLE y (id INT)\n"
                + "GO\n");

    assertThat(names(in)).containsExactly("y");
  }

  // ------------------------------------------------------- IF ... BEGIN ... END

  /** The splitter cuts at the {@code ;} inside the block, leaving a lone {@code END} chunk. */
  @Test
  void shouldCutBlockAtSemicolonAndModelTable(@TempDir Path dir) {
    String ddl =
        "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='z') BEGIN CREATE TABLE z (id INT); END";
    assertThat(new SqlStatementSplitter().split(ddl))
        .containsExactly(
            "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='z') BEGIN CREATE TABLE z (id INT)",
            "END");

    Inspection in = inspectDdl(dir, ddl);

    assertThat(names(in)).containsExactly("z");
    assertThat(datatypesOf(in, "z")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "IF 1 = 1 BEGIN CREATE TABLE z (id INT); END",
        "IF 1 = 1\nBEGIN\n  CREATE TABLE z (id INT);\nEND",
        "IF 1 = 1\nBEGIN\n  CREATE TABLE z (id INT)\nEND",
        "if 1 = 1 begin create table z (id int); end",
        "IF 1 = 1 BEGIN CREATE TABLE z (id INT) END",
        "IF OBJECT_ID('dbo.z') IS NULL\nBEGIN\n  CREATE TABLE z (id INT);\nEND\nGO",
        "IF 1 = 1 BEGIN CREATE TABLE z (id INT);"
      })
  void shouldModelTableInsideIfBeginEndForEverySpelling(String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, ddl);

    assertThat(names(in)).containsExactly("z");
    assertThat(datatypesOf(in, "z")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldModelBothTablesInsideOneBeginEndBlock(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF 1 = 1 BEGIN CREATE TABLE a (id INT PRIMARY KEY) "
                + "CREATE TABLE b (id INT, a_id INT REFERENCES a (id)) END");

    assertThat(names(in)).containsExactly("a", "b");
    assertThat(datatypesOf(in, "a")).containsEntry("id", "serial");
    assertThat(datatypesOf(in, "b")).containsEntry("a_id", "ref[a.id, 1..count]");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldModelBothTablesInsideOneBeginEndBlockSeparatedBySemicolons(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "IF 1 = 1 BEGIN CREATE TABLE a (id INT); CREATE TABLE b (id INT); END");

    assertThat(names(in)).containsExactly("a", "b");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldModelBothBranchesOfIfElse(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "IF c = 1 CREATE TABLE a (id INT) ELSE CREATE TABLE b (id INT);");

    assertThat(names(in)).containsExactly("a", "b");
    assertThat(datatypesOf(in, "a")).containsExactly(entry("id", INT));
    assertThat(datatypesOf(in, "b")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldModelBothBranchesOfIfElseWithBlocks(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF c = 1 BEGIN CREATE TABLE a (id INT) END\n"
                + "ELSE BEGIN CREATE TABLE b (id INT) END");

    assertThat(names(in)).containsExactly("a", "b");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldModelTableInsideBeginTransaction(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "BEGIN TRANSACTION\nCREATE TABLE t (id INT)\nCOMMIT");

    assertThat(names(in)).containsExactly("t");
    assertThat(datatypesOf(in, "t")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BEGIN TRAN\nCREATE TABLE t (id INT)\nCOMMIT TRAN",
        "begin transaction\ncreate table t (id int)\ncommit",
        "BEGIN\nCREATE TABLE t (id INT)\nEND",
        "BEGIN TRANSACTION\nCREATE TABLE t (id INT)\nCOMMIT\nGO"
      })
  void shouldModelTableInsideBeginForEverySpelling(String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, ddl);

    assertThat(names(in)).containsExactly("t");
    assertThat(datatypesOf(in, "t")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- what follows an embedded table

  @Test
  void shouldApplyAlterAfterEmbeddedTable(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n"
                + "CREATE TABLE x (id INT, n INT)\n"
                + "ALTER TABLE x ADD PRIMARY KEY (id);");

    assertThat(names(in)).containsExactly("x");
    assertThat(datatypesOf(in, "x")).containsEntry("id", "serial").containsEntry("n", INT);
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE INDEX ix ON x (n)",
        "CREATE UNIQUE INDEX ix ON x (m)",
        "INSERT INTO x VALUES (1)",
        "EXEC sp_help 'x'",
        "EXEC('CREATE TABLE q (id INT)')",
        "PRINT 'CREATE TABLE q (id INT)'",
        "END",
        "COMMIT",
        "SELECT 1"
      })
  void shouldSkipSilentlyWhatFollowsEmbeddedTable(String tail, @TempDir Path dir) {
    boolean unique = tail.contains("UNIQUE");
    Inspection in =
        inspectDdl(
            dir,
            "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n"
                + "CREATE TABLE x (id INT, n INT, m INT)\n"
                + tail
                + ";");

    assertThat(names(in)).containsExactly("x");
    assertThat(datatypesOf(in, "x"))
        .containsExactly(
            entry("id", INT), entry("n", INT), entry("m", unique ? "unique[1..count]" : INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldSkipSilentlyWhatFollowsEmbeddedTableWithoutSeparator(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n"
                + "CREATE TABLE x (id INT, n INT)\n"
                + "CREATE INDEX ix ON x (n)\n"
                + "INSERT INTO x VALUES (1, 2)\n"
                + "PRINT 'done'\n");

    assertThat(names(in)).containsExactly("x");
    assertThat(datatypesOf(in, "x")).containsExactly(entry("id", INT), entry("n", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldKeepSqlServerTailWithEmbeddedTable(@TempDir Path dir) {
    String tail = " ON [PRIMARY] WITH (DATA_COMPRESSION = PAGE)";
    String table = "CREATE TABLE x (id INT NOT NULL, label VARCHAR(10))";
    Inspection in =
        inspectDdl(dir, "IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n" + table + tail + ";");

    assertSameAsStandalone(dir, in, table + tail + ";", "x");
    assertThat(names(in)).containsExactly("x");
    assertThat(datatypesOf(in, "x"))
        .containsExactly(entry("id", INT), entry("label", "char[1..10]"));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldKeepOnPrimaryTailWithEmbeddedTableFollowedByAnotherStatement(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "BEGIN TRANSACTION\n"
                + "CREATE TABLE t (id INT, n INT) ON [PRIMARY]\n"
                + "CREATE INDEX ix ON t (n)\n"
                + "COMMIT");

    assertThat(names(in)).containsExactly("t");
    assertThat(datatypesOf(in, "t")).containsExactly(entry("id", INT), entry("n", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- literals, defaults, comments

  @Test
  void shouldKeepDefaultsAndChecksIntactInEmbeddedTable(@TempDir Path dir) {
    String table =
        "CREATE TABLE t (\n"
            + "  id INT PRIMARY KEY,\n"
            + "  a VARCHAR(10) DEFAULT 'CREATE TABLE q',\n"
            + "  b DATETIME DEFAULT (getdate()),\n"
            + "  c VARCHAR(5) CHECK (c IN ('x', 'y')), -- CREATE TABLE r (id INT)\n"
            + "  d VARCHAR(5) /* DROP TABLE t CREATE TABLE s (id INT) */\n"
            + ")";
    Inspection in =
        inspectDdl(dir, "IF OBJECT_ID('dbo.t') IS NOT NULL DROP TABLE t\n" + table + ";");

    assertThat(names(in)).containsExactly("t");
    assertSameAsStandalone(dir, in, table + ";", "t");
    assertThat(datatypesOf(in, "t")).containsKeys("id", "a", "b", "c", "d");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldKeepDefaultsAndChecksIntactInSchemaEmbeddedTable(@TempDir Path dir) {
    String table =
        "CREATE TABLE t (id INT, a VARCHAR(20) DEFAULT 'GRANT x CREATE TABLE q (i INT)', "
            + "c VARCHAR(5) CHECK (c IN ('x', 'y')))";
    Inspection in = inspectDdl(dir, "CREATE SCHEMA s AUTHORIZATION dbo " + table + ";");

    assertThat(names(in)).containsExactly("t");
    assertSameAsStandalone(dir, in, table + ";", "t");
  }

  @Test
  void shouldSplitOnlyAtDepthZeroInsideEmbeddedTable(@TempDir Path dir) {
    String table =
        "CREATE TABLE t (id INT, CONSTRAINT ck CHECK (id > 0), "
            + "n VARCHAR(5) DEFAULT (CONCAT('a', 'b')))";
    Inspection in = inspectDdl(dir, "BEGIN TRANSACTION\n" + table + "\nCOMMIT");

    assertThat(names(in)).containsExactly("t");
    assertSameAsStandalone(dir, in, table + ";", "t");
  }

  @Test
  void shouldIgnoreCommentsBeforeTheLeadingKeyword(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "-- header\n/* more */ IF OBJECT_ID('dbo.x') IS NOT NULL DROP TABLE x\n"
                + "CREATE TABLE x (id INT);");

    assertThat(names(in)).containsExactly("x");
  }

  // ------------------------------------------------------- failures stay loud

  @Test
  void shouldAbortStrictNamingEmbeddedTableThatCannotBeParsed(@TempDir Path dir) {
    String ddl =
        BASE
            + "IF OBJECT_ID('dbo.bad_tbl') IS NOT NULL DROP TABLE bad_tbl\n"
            + "CREATE TABLE bad_tbl (id INT,, ,);";

    assertThatThrownBy(() -> inspectDdl(dir, ddl))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("bad_tbl");
  }

  @Test
  void guardWarnInBestEffortAboutEmbeddedTableThatCannotBeParsed(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            BASE + "CREATE SCHEMA s CREATE TABLE bad_tbl (id INT,, ,);",
            NestingOptions.none(),
            true);

    assertThat(names(in)).containsExactly("base");
    assertThat(in.warnings()).anyMatch(w -> w.contains("bad_tbl"));
  }

  @Test
  void guardAbortStrictNamingUnparseableTableInsideCreateSchema(@TempDir Path dir) {
    String ddl = BASE + "CREATE SCHEMA s AUTHORIZATION dbo CREATE TABLE bad_tbl (id INT,, ,);";

    assertThatThrownBy(() -> inspectDdl(dir, ddl))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("bad_tbl");
  }

  @Test
  void shouldWarnAboutNoColumnListTableBehindIf(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, BASE + "IF c = 1 CREATE TABLE t AS SELECT 1;");

    assertThat(names(in)).containsExactly("base");
    assertThat(in.warnings())
        .singleElement()
        .satisfies(
            w ->
                assertThat(w)
                    .contains("'t'")
                    .contains("without a column list")
                    .contains("skipped"));
  }

  // ------------------------------------------------------- guards: nothing to model

  @ParameterizedTest
  @ValueSource(
      strings = {
        "IF OBJECT_ID('x') IS NOT NULL DROP TABLE x",
        "DROP TABLE x",
        "DROP TABLE IF EXISTS x",
        "BEGIN",
        "BEGIN TRANSACTION",
        "COMMIT",
        "IF OBJECT_ID('x') IS NOT NULL DROP TABLE x\nGO",
        "IF 1 = 1 BEGIN DROP TABLE x END",
        "BEGIN TRANSACTION\nDROP TABLE x\nCOMMIT"
      })
  void guardStatementsWithoutCreateTableAddNothing(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + ";\n");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "IF EXISTS (SELECT 1 FROM t WHERE n='CREATE TABLE q') PRINT 'x'",
        "IF 1=1 EXEC('CREATE TABLE q (id INT)')",
        "IF 1=1 PRINT 'CREATE TABLE q (id INT)'",
        "-- CREATE TABLE q (id INT)\nIF 1=1 PRINT 'x'",
        "/* CREATE TABLE q (id INT) */ SELECT 1",
        "IF 1=1 BEGIN EXEC('CREATE TABLE q (id INT)') END",
        "BEGIN TRANSACTION\nEXEC sp_executesql N'CREATE TABLE q (id INT)'\nCOMMIT",
        "DROP TABLE x -- CREATE TABLE q (id INT)"
      })
  void guardLiteralsAndCommentsNeverYieldTables(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + ";\n");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "IF OBJECT_ID('p') IS NOT NULL DROP PROCEDURE p\n"
            + "CREATE PROCEDURE p AS BEGIN CREATE TABLE #t (i INT) END",
        "IF OBJECT_ID('p') IS NOT NULL DROP PROCEDURE p\n"
            + "CREATE PROCEDURE p AS BEGIN CREATE TABLE q (i INT) END",
        "IF OBJECT_ID('f') IS NOT NULL DROP FUNCTION f\n"
            + "CREATE FUNCTION f() RETURNS INT AS BEGIN DECLARE @x INT; RETURN 1; END",
        "IF OBJECT_ID('tr') IS NOT NULL DROP TRIGGER tr\n"
            + "CREATE TRIGGER tr ON base AFTER INSERT AS BEGIN CREATE TABLE q (i INT) END",
        "DROP VIEW v\nCREATE VIEW v AS SELECT 'CREATE TABLE q (i INT)' AS c",
        "CREATE SCHEMA s CREATE VIEW v AS SELECT 'CREATE TABLE q (i INT)'"
      })
  void guardRoutineBodyStopsExpansion(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + ";\n");
  }

  /** Today these abort strict mode (the whole chunk is parsed as a table); expansion fixes it. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "IF 1 = 1 BEGIN DROP PROCEDURE p END\nCREATE PROCEDURE p AS CREATE TABLE q (i INT)",
        "BEGIN TRANSACTION\nCREATE PROCEDURE p AS BEGIN CREATE TABLE q (i INT) END\nCOMMIT"
      })
  void shouldNotAbortOnRoutineBehindControlFlow(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + ";\n");
  }

  @Test
  void guardSemicolonSeparatedStatementsAlreadyWork(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "IF 1 = 1 BEGIN PRINT 'a'; CREATE TABLE z (id INT); PRINT 'b'; END\n"
                + "BEGIN TRANSACTION;\nCREATE TABLE t (id INT);\nCOMMIT;");

    assertThat(names(in)).containsExactly("z", "t");
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"GRANT CREATE TABLE TO u", "REVOKE CREATE TABLE FROM u", "DENY CREATE TABLE TO u"})
  void guardPrivilegeStatementsAddNothing(String ddl, @TempDir Path dir) {
    assertOnlyBase(dir, BASE + ddl + ";\n");
  }

  /**
   * Known limit: GRANT is never expanded, so a table glued to it without a separator stays lost.
   */
  @Test
  void guardGrantIsNeverExpanded(@TempDir Path dir) {
    assertOnlyBase(dir, BASE + "GRANT CREATE TABLE TO u\nCREATE TABLE t (id INT);\n");
  }

  // ------------------------------------------------------- guards: other dialect bodies

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DO $$ BEGIN CREATE TABLE q (id INT); END $$",
        "CREATE FUNCTION f() RETURNS void AS $$ BEGIN CREATE TABLE q (id INT); END $$ LANGUAGE plpgsql",
        "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE q (id INT)'; END",
        "DECLARE n INT; BEGIN SELECT 1; END"
      })
  void guardProceduralBodiesNeverYieldTables(String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, BASE + ddl + ";\n");

    assertThat(names(in)).containsExactly("base");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardOracleBlockWithCreateTableInsideIsNotExpanded(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, BASE + "BEGIN\n  EXECUTE IMMEDIATE 'CREATE TABLE q (id INT)';\nEND;\n/\n");

    assertThat(names(in)).containsExactly("base");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardCreateTableIfNotExistsIsUnchanged(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE IF NOT EXISTS t (id INT);");

    assertThat(names(in)).containsExactly("t");
    assertThat(datatypesOf(in, "t")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardTablesAndColumnsNamedAfterKeywordsAreUnchanged(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE \"if\" (\"begin\" INT, \"drop\" VARCHAR(5));\n"
                + "CREATE TABLE \"begin\" (id INT, \"if\" INT);\n"
                + "CREATE TABLE \"drop\" (id INT, \"create schema\" INT);\n");

    assertThat(names(in)).containsExactly("if", "begin", "drop");
    assertThat(datatypesOf(in, "if"))
        .containsExactly(entry("begin", INT), entry("drop", "char[1..5]"));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void guardOrdinaryScriptIsUnchanged(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (id INT PRIMARY KEY);\n"
                + "CREATE TABLE b (id INT, a_id INT REFERENCES a (id));\n"
                + "CREATE INDEX ix ON b (a_id);\n"
                + "DROP TABLE IF EXISTS gone;\n");

    assertThat(names(in)).containsExactly("a", "b");
    assertThat(datatypesOf(in, "a")).containsEntry("id", "serial");
    assertThat(datatypesOf(in, "b")).containsEntry("a_id", "ref[a.id, 1..count]");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  // ------------------------------------------------------- helpers

  private static List<String> names(Inspection in) {
    return in.structures().stream().map(DataStructure::getName).toList();
  }

  /** Strict inspection must model exactly the {@code base} table, with no warning. */
  private static void assertOnlyBase(Path dir, String ddl) {
    Inspection in = inspectDdl(dir, ddl);

    assertThat(names(in)).containsExactly("base");
    assertThat(datatypesOf(in, "base")).containsExactly(entry("id", INT));
    assertThat(in.warnings()).isEmpty();
  }

  private static void assertSameAsStandalone(
      Path dir, Inspection actual, String standaloneDdl, String table) {
    assertSameAsStandalone(actual, inspectDdl(dir, standaloneDdl), table);
  }

  private static void assertSameAsStandalone(Inspection actual, Inspection expected, String table) {
    assertThat(datatypesOf(actual, table).entrySet())
        .containsExactlyElementsOf(datatypesOf(expected, table).entrySet());
    assertThat(actual.comments().getOrDefault(table, Map.of()))
        .isEqualTo(expected.comments().getOrDefault(table, Map.of()));
  }
}
