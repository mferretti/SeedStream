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
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertNarrowed;
import static com.datagenerator.inspector.ddl.CheckTestSupport.baseType;
import static com.datagenerator.inspector.ddl.CheckTestSupport.comment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.type;
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Where CHECKs come from (inline, table-level, ALTER TABLE, vendor tails, INHERITS) and the vendor
 * spellings of one condition (#449, #214). Every test fails before the feature unless named {@code
 * guard...}.
 */
class DdlInspectorCheckConstraintSourcesTest {

  private static final String T_BODY = "id int PRIMARY KEY, x smallint";

  private static void assertXIs(Inspection in, String expected) {
    assertThat(datatypesOf(in, "t")).containsEntry("x", expected);
    assertThat(comment(in, "t", "x")).isNull();
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------------------ extraction

  static Stream<String> declarations() {
    return Stream.of(
        "CREATE TABLE t (id int PRIMARY KEY, x smallint CHECK (x >= 5));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint NOT NULL CHECK (x >= 5));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint CONSTRAINT ck CHECK (x >= 5));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT \"CK_Quantity\" CHECK (x >= 5));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CHECK (x >= 5));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CHECK ((x >= 5)));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CHECK ((x >= 5) AND (x <= 32767)));",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE public.t ADD CONSTRAINT ck CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE \"t\" ADD CONSTRAINT \"ck\" CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5) ENABLE;",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5) DISABLE;",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5) ENABLE NOVALIDATE;",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5) NOT VALID;",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t WITH CHECK ADD CONSTRAINT ck CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t WITH NOCHECK ADD CONSTRAINT ck CHECK (x >= 5);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint);\nALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5) NOT ENFORCED;",
        "ALTER TABLE t ADD CONSTRAINT ck CHECK (x >= 5);\nCREATE TABLE t (id int PRIMARY KEY, x smallint);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) ENABLE);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) DISABLE);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) NOVALIDATE);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) NOT ENFORCED);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) /*!80016 NOT ENFORCED */);",
        "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5) NO INHERIT);");
  }

  @ParameterizedTest
  @MethodSource("declarations")
  void shouldReadTheCheckFromEveryDeclarationForm(String ddl, @TempDir Path dir) {
    assertXIs(inspectDdl(dir, ddl), "int[5..32767]");
  }

  @Test
  void shouldMergeInlineAndAlterChecksOfTheSameColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, x smallint CHECK (x >= 5));\n"
                + "ALTER TABLE t ADD CONSTRAINT hi CHECK (x <= 50);\n");

    assertXIs(in, "int[5..50]");
  }

  @Test
  void shouldIgnoreNocheckAndCheckConstraintStatementsSilentlyAndKeepApplyingTheCheck(
      @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, x smallint, CONSTRAINT ck CHECK (x >= 5));\n"
                + "ALTER TABLE t NOCHECK CONSTRAINT ck;\n"
                + "ALTER TABLE t NOCHECK CONSTRAINT ALL;\n"
                + "ALTER TABLE t CHECK CONSTRAINT ck;\n"
                + "ALTER TABLE t CHECK CONSTRAINT ALL;\n");

    assertXIs(in, "int[5..32767]");
  }

  @Test
  void shouldLoadAlterDeclaredCheckWithTheRealParser(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, x smallint);\n"
                + "ALTER TABLE t ADD CONSTRAINT ck CHECK (x BETWEEN 5 AND 9) ENABLE;\n");

    assertThat(loadAll(in, dir).get("t").getData().get("x").getDatatype()).isEqualTo("int[5..9]");
  }

  // ------------------------------------------------------------------ inheritance

  @Test
  void shouldApplyParentCheckToInheritingChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id int PRIMARY KEY, x smallint CHECK (x >= 5));\n"
                + "CREATE TABLE c (extra int) INHERITS (p);\n");

    assertThat(datatypesOf(in, "p")).containsEntry("x", "int[5..32767]");
    assertThat(datatypesOf(in, "c")).containsEntry("x", "int[5..32767]");
    assertThat(comment(in, "c", "x")).isNull();
  }

  @Test
  void shouldApplyAlterDeclaredParentCheckToChildEvenWhenDeclaredAfterIt(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id int PRIMARY KEY, x smallint);\n"
                + "CREATE TABLE c (extra int) INHERITS (p);\n"
                + "ALTER TABLE p ADD CONSTRAINT ck CHECK (x BETWEEN 5 AND 9);\n");

    assertThat(datatypesOf(in, "p")).containsEntry("x", "int[5..9]");
    assertThat(datatypesOf(in, "c")).containsEntry("x", "int[5..9]");
  }

  @Test
  void shouldNotInheritANoInheritCheck(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id int PRIMARY KEY, x smallint,"
                + " CONSTRAINT ck CHECK (x >= 5) NO INHERIT);\n"
                + "CREATE TABLE c (extra int) INHERITS (p);\n");

    assertThat(datatypesOf(in, "p")).containsEntry("x", "int[5..32767]");
    assertThat(datatypesOf(in, "c")).containsEntry("x", "int[1..32767]");
    assertThat(comment(in, "c", "x")).isNull();
  }

  @Test
  void shouldIntersectChildOwnCheckWithInheritedOne(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id int PRIMARY KEY, x smallint CHECK (x >= 5));\n"
                + "CREATE TABLE c (extra int, CHECK (x <= 20)) INHERITS (p);\n");

    assertThat(datatypesOf(in, "p")).containsEntry("x", "int[5..32767]");
    assertThat(datatypesOf(in, "c")).containsEntry("x", "int[5..20]");
  }

  // ------------------------------------------------------------------ vendor spellings

  /** column definition, vendor spelling, plain form, expected datatype. */
  static Stream<Arguments> spellings() {
    String sm = "x smallint";
    return Stream.of(
        // SQL Server normalised
        Arguments.of(
            "Discount real",
            "[Discount]>=(0) AND [Discount]<=(1)",
            "Discount >= 0 AND Discount <= 1",
            "decimal[0.0..1.0]"),
        Arguments.of("Qty smallint", "([Qty]>=(5))", "Qty >= 5", "int[5..32767]"),
        Arguments.of(
            "Qty smallint",
            "(([Qty]>=(5)) AND ([Qty]<=(9)))",
            "Qty >= 5 AND Qty <= 9",
            "int[5..9]"),
        // pg_dump casts
        Arguments.of(
            "discount real",
            "((discount >= (0)::double precision) AND (discount <= (1)::double precision))",
            "discount >= 0 AND discount <= 1",
            "decimal[0.0..1.0]"),
        Arguments.of(
            "unit_price numeric(10,2)",
            "(unit_price >= (5)::numeric)",
            "unit_price >= 5",
            "decimal[5.0..9999.99]"),
        Arguments.of(sm, "(x >= (5)::smallint)", "x >= 5", "int[5..32767]"),
        Arguments.of(sm, "x >= CAST(5 AS numeric)", "x >= 5", "int[5..32767]"),
        Arguments.of(sm, "x >= CAST(5 AS integer)", "x >= 5", "int[5..32767]"),
        // quoting and keyword case
        Arguments.of(sm, "\"x\" between 2 and 9", "x BETWEEN 2 AND 9", "int[2..9]"),
        Arguments.of(sm, "\"x\" BeTwEeN 2 aNd 9", "x BETWEEN 2 AND 9", "int[2..9]"),
        Arguments.of(sm, "`x` > 5", "x > 5", "int[6..32767]"),
        Arguments.of(sm, "[x] > 5", "x > 5", "int[6..32767]"),
        Arguments.of(sm, "\"X\" > 5", "x > 5", "int[6..32767]"),
        // reversed operands
        Arguments.of(sm, "5 <= x", "x >= 5", "int[5..32767]"),
        Arguments.of(sm, "5 < x", "x > 5", "int[6..32767]"),
        Arguments.of(sm, "100 > x", "x < 100", "int[1..99]"),
        Arguments.of(sm, "100 >= x", "x <= 100", "int[1..100]"),
        Arguments.of(sm, "5 = x", "x = 5", "int[5..5]"),
        // pg_dump IN
        Arguments.of(
            "status varchar(16)",
            "((status)::text = ANY ((ARRAY['a'::character varying, 'b'::character varying])::text[]))",
            "status IN ('a','b')",
            "enum[a,b]"),
        Arguments.of(
            "status varchar(16)",
            "(status = ANY (ARRAY['a'::text, 'b'::text]))",
            "status IN ('a','b')",
            "enum[a,b]"),
        // SQL Server normalised IN
        Arguments.of(
            "Status varchar(16)",
            "[Status]='a' OR [Status]='b'",
            "Status IN ('a','b')",
            "enum[a,b]"),
        Arguments.of(
            "Status varchar(16)",
            "[Status]=N'a' OR [Status]=N'b'",
            "Status IN ('a','b')",
            "enum[a,b]"),
        Arguments.of(
            "Status varchar(16)",
            "(([Status]='a') OR ([Status]='b'))",
            "Status IN ('a','b')",
            "enum[a,b]"),
        Arguments.of("status varchar(16)", "(status)::text = 'a'::text", "status = 'a'", "enum[a]"),
        // NULL branch
        Arguments.of(sm, "x IS NULL OR x > 5", "x > 5", "int[6..32767]"),
        Arguments.of("s varchar(16)", "s IS NULL OR s IN ('a')", "s IN ('a')", "enum[a]"),
        Arguments.of("s varchar(16)", "(s IS NULL) OR (s = 'a')", "s = 'a'", "enum[a]"),
        // NOT NULL as an AND part
        Arguments.of(sm, "x IS NOT NULL AND x >= 5", "x >= 5", "int[5..32767]"),
        Arguments.of(sm, "x >= 5 AND x IS NOT NULL", "x >= 5", "int[5..32767]"));
  }

  @ParameterizedTest
  @MethodSource("spellings")
  void shouldReadEveryVendorSpellingLikeItsPlainForm(
      String colDef, String vendor, String plain, String expected, @TempDir Path dir) {
    assertThat(type(dir, colDef, plain)).as("plain form %s", plain).isEqualTo(expected);
    assertThat(type(dir, colDef, vendor)).as("vendor form %s", vendor).isEqualTo(expected);
    assertThat(expected).as("the row must narrow something").isNotEqualTo(baseType(dir, colDef));
    assertNarrowed(dir, colDef, vendor, expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x IS NOT NULL",
        "(x IS NOT NULL)",
        "\"X\" IS NOT NULL",
        "x IS NOT NULL AND x IS NOT NULL"
      })
  void guardIsNotNullIsNeitherAConstraintNorAComment(String check, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (" + T_BODY + ", CONSTRAINT \"SYS_C0012\" CHECK (" + check + "));");

    assertXIs(in, "int[1..32767]");
  }
}
