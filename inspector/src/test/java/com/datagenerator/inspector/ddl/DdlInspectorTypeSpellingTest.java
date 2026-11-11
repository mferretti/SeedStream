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

import static com.datagenerator.inspector.InspectionTestSupport.assertEngineLoadable;
import static com.datagenerator.inspector.InspectionTestSupport.datatypesOf;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Type spellings that used to lose information, driven through the public {@link DdlInspector}
 * entry point: quoted type names, Oracle length semantics, zero-length strings, MySQL ENUM / SET /
 * TINYINT(1) (#448) and scale-0 exact numerics as keys (#447).
 */
class DdlInspectorTypeSpellingTest {

  private final DdlInspector inspector = new DdlInspector();

  // ── #448: quoted type names ────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "int||int[1..999999]",
        "smallint||int[1..32767]",
        "tinyint||int[1..127]",
        "datetime||timestamp[now-365d..now]",
        "money||decimal[0.0..9999.99]",
        "real||decimal[0.0..9999.99]",
        "bit||boolean",
        "ntext||char[1..500]",
        "nvarchar|(15)|char[1..15]",
        "char|(3)|char[1..3]"
      })
  void shouldMapDoubleQuotedSqlServerTypeNameLikeTheUnquotedOne(
      String type, String args, String expected, @TempDir Path dir) throws IOException {
    String suffix = args == null ? "" : args;

    Inspection inspection =
        inspect(dir, "CREATE TABLE t (\"Col\" \"" + type + "\"" + suffix + " NULL, n INT);");

    assertThat(datatypesOf(inspection, "t")).containsEntry("Col", expected);
    assertThat(inspection.comments().getOrDefault("t", Map.of())).doesNotContainKey("Col");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "int||int[1..999999]",
        "datetime||timestamp[now-365d..now]",
        "varchar|(10)|char[1..10]"
      })
  void shouldMapBacktickQuotedMysqlTypeNameLikeTheUnquotedOne(
      String type, String args, String expected, @TempDir Path dir) throws IOException {
    String suffix = args == null ? "" : args;

    Inspection inspection =
        inspect(dir, "CREATE TABLE t (`col` `" + type + "`" + suffix + " NULL, n INT);");

    assertThat(datatypesOf(inspection, "t")).containsEntry("col", expected);
    assertThat(inspection.comments().getOrDefault("t", Map.of())).doesNotContainKey("col");
  }

  // ── #448: Oracle length semantics, zero-length strings ─────────────────────

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "VARCHAR2(15 BYTE)|char[1..15]",
        "VARCHAR2(15 CHAR)|char[1..15]",
        "VARCHAR2(15 BYTE) NOT NULL|char[1..15]",
        "NVARCHAR2(15)|char[1..15]",
        "CHAR(5 BYTE)|char[1..5]",
        "CHAR(5 CHAR)|char[1..5]"
      })
  void shouldKeepDeclaredLengthWhenOracleLengthSemanticsQualifierIsPresent(
      String type, String expected, @TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (generic_col " + type + ", n INT);");

    assertThat(datatypesOf(inspection, "t")).containsEntry("generic_col", expected);
  }

  @ParameterizedTest
  @CsvSource({"VARCHAR(0)", "CHAR(0)"})
  void shouldEmitLoadableEmptyStringTypeForZeroLengthString(String type, @TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (generic_col " + type + ", n INT);");

    String datatype = datatypesOf(inspection, "t").get("generic_col");
    assertThat(datatype).isEqualTo("char[0..0]");
    assertEngineLoadable(datatype);
  }

  // ── #448: MySQL ENUM / SET / TINYINT(1) ────────────────────────────────────

  static Stream<Arguments> mysqlSpellings() {
    return Stream.of(
        Arguments.of("ENUM('a','b')", "enum[a,b]", false),
        Arguments.of("ENUM('a','b') NOT NULL DEFAULT 'a'", "enum[a,b]", false),
        Arguments.of("ENUM('it''s','x')", "enum[it's,x]", false),
        Arguments.of("ENUM('a,b','c')", "char[1..3]", true),
        Arguments.of("ENUM('a]b','cc')", "char[1..3]", true),
        Arguments.of("ENUM('','zz')", "char[1..2]", true),
        Arguments.of("ENUM(' a','b ')", "char[1..2]", true),
        Arguments.of("ENUM(\"a\",\"b\")", "enum[a,b]", false),
        Arguments.of("ENUM(\"it\"\"s\",\"x\")", "enum[it\"s,x]", false),
        Arguments.of("SET('x','y')", "enum[x,y]", true),
        Arguments.of("TINYINT(1)", "boolean", false),
        Arguments.of("TINYINT(1) NOT NULL DEFAULT 0", "boolean", false),
        Arguments.of("TINYINT(4)", "int[1..127]", false));
  }

  @ParameterizedTest
  @MethodSource("mysqlSpellings")
  void shouldMapMysqlEnumSetAndTinyintOneColumns(
      String type, String expected, boolean commented, @TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (`col` " + type + ", n INT);");

    String datatype = datatypesOf(inspection, "t").get("col");
    assertThat(datatype).isEqualTo(expected);
    assertEngineLoadable(datatype);
    Map<String, String> comments = inspection.comments().getOrDefault("t", Map.of());
    if (commented) {
      assertThat(comments.get("col")).isNotBlank().doesNotContain("unrecognized");
    } else {
      assertThat(comments).doesNotContainKey("col");
    }
  }

  // ── key detection on top of the new type mappings ──────────────────────────

  @Test
  void shouldEnforceSqlServerPrimaryKeyDeclaredWithQuotedTypeName(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE t ("Id" "int" NOT NULL, "Name" nvarchar(15),
              CONSTRAINT pk PRIMARY KEY ("Id"));
            """);

    assertThat(datatypesOf(inspection, "t")).containsEntry("Id", "serial[1..2147483647]");
    assertThat(inspection.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldEnforceOraclePrimaryKeyOnScaleZeroNumberAndKeepForeignKeyAsRef(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE "S"."T" ("ID" NUMBER(9,0), CONSTRAINT "PK" PRIMARY KEY ("ID"));
            CREATE TABLE "S"."C" (
              "CID" NUMBER(9,0),
              "T_ID" NUMBER(9,0) REFERENCES "S"."T" ("ID"),
              CONSTRAINT "PKC" PRIMARY KEY ("CID"));
            """);

    assertThat(datatypesOf(inspection, "t")).containsEntry("ID", "serial[1..999999999]");
    assertThat(datatypesOf(inspection, "c")).containsEntry("CID", "serial[1..999999999]");
    assertThat(datatypesOf(inspection, "c").get("T_ID")).startsWith("ref[");
    assertThat(inspection.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldEnforceTableLevelForeignKeyOnScaleZeroNumber(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE t (id NUMBER(9,0), CONSTRAINT pk PRIMARY KEY (id));
            CREATE TABLE c (
              cid INT PRIMARY KEY,
              t_id NUMBER(9,0),
              CONSTRAINT fk FOREIGN KEY (t_id) REFERENCES t (id));
            """);

    assertThat(datatypesOf(inspection, "t")).containsEntry("id", "serial[1..999999999]");
    assertThat(datatypesOf(inspection, "c")).containsEntry("t_id", "ref[t.id]");
  }

  @Test
  void shouldEnforceCompositeAndUniqueKeysOnScaleZeroNumbers(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE t (
              a NUMBER(9,0), b NUMBER(5), code DECIMAL(10,0), amount NUMBER(12,2),
              CONSTRAINT pk PRIMARY KEY (a, b), CONSTRAINT uq UNIQUE (code));
            """);

    Map<String, String> types = datatypesOf(inspection, "t");
    assertThat(types.get("a")).startsWith("unique[pk,");
    assertThat(types.get("b")).startsWith("unique[pk,");
    assertThat(types.get("code")).startsWith("unique[");
    assertThat(types.get("amount")).startsWith("decimal[");
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return inspector.inspect(sql);
  }
}
