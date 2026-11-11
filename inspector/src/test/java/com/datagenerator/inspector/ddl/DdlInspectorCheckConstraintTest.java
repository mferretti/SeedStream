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
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertBounds;
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertKeptWithComment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertNarrowed;
import static com.datagenerator.inspector.ddl.CheckTestSupport.baseType;
import static com.datagenerator.inspector.ddl.CheckTestSupport.columnName;
import static com.datagenerator.inspector.ddl.CheckTestSupport.comment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.dates;
import static com.datagenerator.inspector.ddl.CheckTestSupport.inspectCheck;
import static com.datagenerator.inspector.ddl.CheckTestSupport.type;
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CHECK constraints to ranges and value lists (#449, #214): column types per condition. Tests named
 * {@code guard...} already pass before the feature (a type that is already valid, no false
 * positives); every other test fails there.
 */
class DdlInspectorCheckConstraintTest {

  private static final String SMALL = "x smallint";
  private static final String DATE_DEFAULT = "date[2020-01-01..2030-12-31]";

  // ------------------------------------------------------------------ text columns

  static Stream<Arguments> enumRows() {
    return Stream.of(
        Arguments.of("s varchar(16)", "s IN ('a','b')", "enum[a,b]"),
        Arguments.of("s varchar(16)", "s IN ('b', 'a', 'b')", "enum[b,a]"),
        Arguments.of("s varchar(16)", "s = 'x'", "enum[x]"),
        Arguments.of("s varchar(16)", "s = 'a' OR s = 'b'", "enum[a,b]"),
        Arguments.of("s varchar(16)", "S IN ('a','b')", "enum[a,b]"),
        Arguments.of("s varchar(16)", "\"s\" IN ('a','b')", "enum[a,b]"),
        Arguments.of("s varchar(16)", "s IN ('it''s','x')", "enum[it's,x]"),
        Arguments.of("s char(3)", "s IN ('abc','d')", "enum[abc,d]"),
        Arguments.of("s varchar(16)", "s IN ('a','b','c') AND s IN ('b','c','d')", "enum[b,c]"),
        Arguments.of("s varchar(16)", "s IN ('a','b') AND s IS NOT NULL", "enum[a,b]"),
        Arguments.of("name varchar(20)", "name IN ('a','b')", "enum[a,b]"),
        Arguments.of("email varchar(40)", "email IN ('a@x.org')", "enum[a@x.org]"));
  }

  @ParameterizedTest
  @MethodSource("enumRows")
  void shouldMapInListAndEqualityToEnumWithoutComment(
      String colDef, String check, String expected, @TempDir Path dir) {
    assertNarrowed(dir, colDef, check, expected);
  }

  @Test
  void shouldReplaceNameHintWithEnum(@TempDir Path dir) {
    assertThat(baseType(dir, "name varchar(20)")).isEqualTo("name");
    assertThat(type(dir, "name varchar(20)", "name IN ('a','b')")).isEqualTo("enum[a,b]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "s IN ('a,b','c')",
        "s IN ('a]','c')",
        "s IN ('', 'c')",
        "s IN (' a','c')",
        "s IN ('a ','c')",
        "s IN ('a','b,c')",
        "s = ''",
        "s = 'x,y'"
      })
  void shouldKeepCharAndCommentWhenAValueEnumCannotExpress(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(16)", check);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "s IN ('abcd')",
        "s IN ('ab','abcd')",
        "s = 'abcd'",
      })
  void shouldKeepCharAndCommentWhenAValueIsLongerThanTheColumn(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(3)", check);
    assertKeptWithComment(dir, "s char(3)", check);
  }

  @Test
  void shouldAcceptValueExactlyAsLongAsTheColumn(@TempDir Path dir) {
    assertNarrowed(dir, "s varchar(3)", "s IN ('abc')", "enum[abc]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "s NOT IN ('a','b')",
        "s <> 'a'",
        "s != 'a'",
        "s LIKE 'a%'",
        "LENGTH(s) > 0",
        "s >= 'a'",
        "s IN ('a','b') OR s LIKE 'z%'",
        "s IN ('a', NULL)",
        "s IN (SELECT 'a')"
      })
  void shouldKeepCharAndCommentForOtherTextConditions(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(16)", check);
  }

  @Test
  void shouldKeepCharAndCommentWhenTextChecksHaveNoCommonValue(@TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(16)", "s IN ('a') AND s IN ('b')");
  }

  @Test
  void shouldLoadEnumWithTheRealParser(@TempDir Path dir) {
    Inspection in = inspectCheck(dir, "s varchar(16)", "s IN ('a','b')");

    assertThat(loadAll(in, dir).get("t").getData().get("s").getDatatype()).isEqualTo("enum[a,b]");
  }

  // ------------------------------------------------------------------ integer columns

  static Stream<Arguments> narrowedIntRows() {
    return Stream.of(
        Arguments.of(SMALL, "x >= 0", "int[0..32767]"),
        Arguments.of(SMALL, "x >= 10", "int[10..32767]"),
        Arguments.of(SMALL, "x >= -5", "int[-5..32767]"),
        Arguments.of(SMALL, "x < 100", "int[1..99]"),
        Arguments.of(SMALL, "x <= 99", "int[1..99]"),
        Arguments.of(SMALL, "x <= 0", "int[-32768..0]"),
        Arguments.of(SMALL, "x < 0", "int[-32768..-1]"),
        Arguments.of(SMALL, "x <= -5", "int[-32768..-5]"),
        Arguments.of(SMALL, "x BETWEEN -5 AND 5", "int[-5..5]"),
        Arguments.of(SMALL, "x between 2 and 9", "int[2..9]"),
        Arguments.of(SMALL, "x = 5", "int[5..5]"),
        Arguments.of(SMALL, "x = 0", "int[0..0]"),
        Arguments.of(SMALL, "x IN (0, 1)", "int[0..1]"),
        Arguments.of(SMALL, "x IN (3,1,2)", "int[1..3]"),
        Arguments.of(SMALL, "x > 5 AND x < 10", "int[6..9]"),
        Arguments.of(SMALL, "x >= 5 AND x <= 5", "int[5..5]"),
        Arguments.of(SMALL, "x > 10.5", "int[11..32767]"),
        Arguments.of(SMALL, "x < 2.5", "int[1..2]"),
        Arguments.of(SMALL, "x <= 2.5", "int[1..2]"),
        Arguments.of(SMALL, "x >= -0.5", "int[0..32767]"),
        Arguments.of(SMALL, "x <= -0.5", "int[-32768..-1]"),
        Arguments.of("x tinyint", "x <= 0", "int[-128..0]"),
        Arguments.of("x tinyint", "x >= 0", "int[0..127]"),
        Arguments.of("x int", "x <= 0", "int[-1000000..0]"),
        Arguments.of("x integer", "x >= 0", "int[0..999999]"),
        Arguments.of("x bigint", "x >= 0", "int[0..999999]"),
        Arguments.of("x bigint", "x < 0", "int[-1000000..-1]"));
  }

  @ParameterizedTest
  @MethodSource("narrowedIntRows")
  void shouldNarrowIntegerRange(String colDef, String check, String expected, @TempDir Path dir) {
    assertNarrowed(dir, colDef, check, expected);
  }

  static Stream<Arguments> unchangedIntRows() {
    return Stream.of(
        Arguments.of("x > 0", "int[1..32767]"),
        Arguments.of("x >= 1", "int[1..32767]"),
        Arguments.of("x < 100000", "int[1..32767]"),
        Arguments.of("x > 0.5", "int[1..32767]"),
        Arguments.of("x >= 0.5", "int[1..32767]"),
        Arguments.of("x <= 100000 AND x >= -100000 AND x >= 1", "int[1..32767]"));
  }

  @ParameterizedTest
  @MethodSource("unchangedIntRows")
  void guardIntegerRangeAlreadyInsideTheCheckStaysDefault(
      String check, String expected, @TempDir Path dir) {
    assertNarrowed(dir, SMALL, check, expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x IN (1, 5)",
        "x IN (0, 2)",
        "x > 5 AND x < 3",
        "x > 40000",
        "x = 40000",
        "x < -40000"
      })
  void shouldKeepIntegerAndCommentWhenNoSafeRangeExists(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, SMALL, check);
  }

  @Test
  void shouldIntersectWithSeveralChecksOfTheSameColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, x smallint CHECK (x >= 5),"
                + " CONSTRAINT hi CHECK (x <= 50), CHECK (x > 10));");

    assertThat(datatypesOf(in, "t")).containsEntry("x", "int[11..50]");
    assertThat(comment(in, "t", "x")).isNull();
  }

  @Test
  void shouldLoadNarrowedIntegerWithTheRealParser(@TempDir Path dir) {
    Inspection in = inspectCheck(dir, SMALL, "x BETWEEN -5 AND 5");

    assertThat(loadAll(in, dir).get("t").getData().get("x").getDatatype()).isEqualTo("int[-5..5]");
  }

  // ------------------------------------------------------------------ decimal columns

  static Stream<Arguments> exactDecimalRows() {
    return Stream.of(
        Arguments.of("x numeric(10,2)", "x >= 0 AND x <= 1", "decimal[0.0..1.0]"),
        Arguments.of("x real", "x >= 0 AND x <= 1", "decimal[0.0..1.0]"),
        Arguments.of("x decimal", "x >= 0 AND x <= 1", "decimal[0.0..1.0]"),
        Arguments.of("x double precision", "x BETWEEN 0 AND 1", "decimal[0.0..1.0]"),
        Arguments.of("x numeric(10,2)", "x > 0", "decimal[0.01..9999.99]"),
        Arguments.of("x real", "x > 0", "decimal[0.01..9999.99]"),
        Arguments.of("x numeric(10,2)", "x < 1", "decimal[0.0..0.99]"));
  }

  @ParameterizedTest
  @MethodSource("exactDecimalRows")
  void shouldNarrowDecimalRange(String colDef, String check, String expected, @TempDir Path dir) {
    assertNarrowed(dir, colDef, check, expected);
  }

  @Test
  void guardDecimalLowerBoundZeroKeepsTheDefault(@TempDir Path dir) {
    assertNarrowed(dir, "x numeric(10,2)", "x >= 0", "decimal[0.0..9999.99]");
  }

  static Stream<Arguments> decimalBoundRows() {
    return Stream.of(
        // colDef, check, min, max
        Arguments.of("x numeric(10,3)", "x > 0", "0.001", "9999.99"),
        Arguments.of("x numeric(10,3)", "x < 1", "0", "0.999"),
        Arguments.of("x numeric(10,2)", "x <= 0", "-9999.99", "0"),
        Arguments.of("x real", "x <= 0", "-9999.99", "0"),
        Arguments.of("x numeric(10,2)", "x < 0", "-9999.99", "-0.01"),
        Arguments.of("x numeric(10,2)", "x <= 0.125", "0", "0.12"),
        Arguments.of("x numeric(10,2)", "x < 0.125", "0", "0.12"),
        Arguments.of("x numeric(10,2)", "x >= 0.125", "0.13", "9999.99"),
        Arguments.of("x numeric(10,2)", "x > 0.125", "0.13", "9999.99"),
        Arguments.of("x numeric(10,2)", "x BETWEEN 0.5 AND 2.5", "0.5", "2.5"),
        Arguments.of("x numeric(10,2)", "x >= 5", "5", "9999.99"),
        Arguments.of("x numeric(10,1)", "x > 0", "0.1", "9999.99"));
  }

  @ParameterizedTest
  @MethodSource("decimalBoundRows")
  void shouldComputeDecimalBounds(
      String colDef, String check, String min, String max, @TempDir Path dir) {
    Inspection in = inspectCheck(dir, colDef, check);

    String datatype = datatypesOf(in, "t").get(columnName(colDef));
    assertBounds(datatype, min, max);
    assertThat(comment(in, "t", columnName(colDef))).isNull();
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardDecimalBoundBeyondCapacityStaysAtCapacity(@TempDir Path dir) {
    String datatype = type(dir, "x numeric(5,2)", "x >= 0 AND x <= 1000");

    assertBounds(datatype, "0", "999.99");
  }

  @Test
  void shouldKeepDecimalAndCommentForInList(@TempDir Path dir) {
    assertKeptWithComment(dir, "x numeric(10,2)", "x IN (0, 1)");
    assertKeptWithComment(dir, "x real", "x IN (0, 1)");
  }

  @Test
  void shouldKeepDecimalAndCommentWhenBoundIsBeyondCapacity(@TempDir Path dir) {
    assertKeptWithComment(dir, "x numeric(5,2)", "x > 5000");
  }

  // ------------------------------------------------------------------ date columns

  static Stream<Arguments> dateRows() {
    return Stream.of(
        Arguments.of("d >= '2021-01-01'", "date[2021-01-01..2030-12-31]"),
        Arguments.of("d >= DATE '2021-01-01'", "date[2021-01-01..2030-12-31]"),
        Arguments.of("d >= '2021-01-01'::date", "date[2021-01-01..2030-12-31]"),
        Arguments.of("d >= TO_DATE('2021-01-01','YYYY-MM-DD')", "date[2021-01-01..2030-12-31]"),
        Arguments.of("d > '2021-01-01'", "date[2021-01-02..2030-12-31]"),
        Arguments.of("d <= '2025-06-30'", "date[2020-01-01..2025-06-30]"),
        Arguments.of("d < '2025-01-01'", "date[2020-01-01..2024-12-31]"),
        Arguments.of("d BETWEEN '2021-01-01' AND '2022-12-31'", "date[2021-01-01..2022-12-31]"),
        Arguments.of("d >= '2021-01-01' AND d < '2022-01-01'", "date[2021-01-01..2021-12-31]"),
        Arguments.of("'2021-01-01' <= d", "date[2021-01-01..2030-12-31]"));
  }

  @ParameterizedTest
  @MethodSource("dateRows")
  void shouldIntersectDateRange(String check, String expected, @TempDir Path dir) {
    assertThat(baseType(dir, "d date")).isEqualTo(DATE_DEFAULT);
    assertNarrowed(dir, "d date", check, expected);
  }

  @Test
  void guardDateBoundBeforeTheDefaultLowerBoundChangesNothing(@TempDir Path dir) {
    assertNarrowed(dir, "d date", "d >= '2015-01-01'", DATE_DEFAULT);
  }

  @Test
  void shouldKeepConsistentRangeWhenOnlyTheUpperBoundIsBeforeTheDefault(@TempDir Path dir) {
    LocalDate[] range = dates(type(dir, "d date", "d < '2000-01-01'"));

    assertThat(range[1]).isEqualTo(LocalDate.of(1999, 12, 31));
    assertThat(range[0]).isBetween(LocalDate.of(1989, 12, 30), LocalDate.of(1990, 1, 2));
  }

  @Test
  void shouldKeepConsistentRangeWhenOnlyTheLowerBoundIsAfterTheDefault(@TempDir Path dir) {
    LocalDate[] range = dates(type(dir, "d date", "d > '2040-01-01'"));

    assertThat(range[0]).isEqualTo(LocalDate.of(2040, 1, 2));
    assertThat(range[1]).isAfter(range[0]);
    assertThat(range[1]).isBetween(LocalDate.of(2049, 12, 31), LocalDate.of(2050, 1, 3));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "d < CURRENT_DATE",
        "d <= CURRENT_DATE",
        "d < getdate()",
        "d <= now()",
        "d < SYSDATE",
        "d < CURRENT_TIMESTAMP",
        "CURRENT_DATE > d",
        "CURRENT_DATE >= d",
        "d < (CURRENT_DATE)"
      })
  void shouldUseFixedPastRangeForDatesBeforeNow(String check, @TempDir Path dir) {
    Inspection in = inspectCheck(dir, "d date", check);

    assertThat(datatypesOf(in, "t")).containsEntry("d", "date[1970-01-01..2019-12-31]");
    assertThat(comment(in, "t", "d")).isNotNull().containsIgnoringCase("check");
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "d > CURRENT_DATE",
        "d >= CURRENT_DATE",
        "d > getdate()",
        "CURRENT_DATE < d",
        "CURRENT_DATE <= d"
      })
  void shouldUseFixedFutureRangeForDatesAfterNow(String check, @TempDir Path dir) {
    Inspection in = inspectCheck(dir, "d date", check);

    assertThat(datatypesOf(in, "t")).containsEntry("d", "date[2100-01-01..2100-12-31]");
    assertThat(comment(in, "t", "d")).isNotNull().containsIgnoringCase("check");
  }

  @ParameterizedTest
  @ValueSource(strings = {"d >= '01/02/2021'", "d >= 'next tuesday'", "d >= 20210101"})
  void shouldKeepDateAndCommentForOtherLiteralFormats(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "d date", check);
  }

  @Test
  void shouldKeepDatesAndCommentOnBothColumnsWhenComparedWithEachOther(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, ordered date, shipped date,"
                + " CONSTRAINT ck_dates CHECK (shipped >= ordered));");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("ordered", DATE_DEFAULT)
        .containsEntry("shipped", DATE_DEFAULT);
    assertThat(comment(in, "t", "ordered")).isNotNull().contains("ck_dates");
    assertThat(comment(in, "t", "shipped")).isNotNull().contains("ck_dates");
  }

  @Test
  void shouldKeepWritingLoadablePastRange(@TempDir Path dir) {
    Inspection in = inspectCheck(dir, "d date", "d < CURRENT_DATE");

    assertThat(loadAll(in, dir).get("t").getData().get("d").getDatatype())
        .isEqualTo("date[1970-01-01..2019-12-31]");
  }

  // ------------------------------------------------------------------ timestamp columns

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ts <= now()",
        "ts < now()",
        "ts < CURRENT_TIMESTAMP",
        "ts <= CURRENT_TIMESTAMP",
        "ts < getdate()",
        "ts <= SYSDATE"
      })
  void guardTimestampBeforeNowIsAlreadySatisfied(String check, @TempDir Path dir) {
    assertNarrowed(dir, "ts timestamp", check, "timestamp[now-365d..now]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ts > '2020-01-01'",
        "ts >= CURRENT_TIMESTAMP",
        "ts > now()",
        "ts IS NOT NULL AND ts < '2030-01-01'"
      })
  void shouldKeepTimestampAndCommentForAnyOtherCheck(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "ts timestamp", check);
  }

  // ------------------------------------------------------------------ everything else

  private static Inspection two(Path dir, String check) {
    return inspectDdl(
        dir,
        "CREATE TABLE t (id int PRIMARY KEY, a smallint, b smallint,"
            + " CONSTRAINT ck_ab CHECK ("
            + check
            + "));");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"a > b", "a + b > 0", "a > 0 OR b > 0", "NOT (a > 0 AND b > 0)", "a * 2 > b"})
  void shouldKeepBothColumnsAndNameTheConstraintWhenChecksSpanColumns(
      String check, @TempDir Path dir) {
    Inspection in = two(dir, check);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "int[1..32767]")
        .containsEntry("b", "int[1..32767]");
    assertThat(comment(in, "t", "a")).as("a for %s", check).isNotNull().contains("ck_ab");
    assertThat(comment(in, "t", "b")).as("b for %s", check).isNotNull().contains("ck_ab");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abs(a) > 1",
        "NOT (a > 0)",
        "CASE WHEN a > 0 THEN 1 ELSE 0 END = 1",
        "a > (SELECT max(id) FROM t)",
        "a % 2 = 0"
      })
  void shouldKeepTypeAndCommentOnTheNamedColumnForFunctionsAndSubqueries(
      String check, @TempDir Path dir) {
    Inspection in = two(dir, check);

    assertThat(datatypesOf(in, "t")).containsEntry("a", "int[1..32767]");
    assertThat(comment(in, "t", "a")).as("a for %s", check).isNotNull().contains("ck_ab");
    assertThat(comment(in, "t", "b")).as("b is not named by %s", check).isNull();
  }

  @Test
  void shouldApplySimplePartAndCommentOnBothColumnsOfAMixedAnd(@TempDir Path dir) {
    Inspection in = two(dir, "a >= 10 AND a < b");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "int[10..32767]")
        .containsEntry("b", "int[1..32767]");
    assertThat(comment(in, "t", "a")).isNotNull().contains("ck_ab");
    assertThat(comment(in, "t", "b")).isNotNull().contains("ck_ab");
  }

  @Test
  void shouldApplyMultiColumnAndToEachColumn(@TempDir Path dir) {
    Inspection in = two(dir, "a >= 2 AND b <= 3");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "int[2..32767]")
        .containsEntry("b", "int[1..3]");
    assertThat(comment(in, "t", "a")).isNull();
    assertThat(comment(in, "t", "b")).isNull();
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldNameInlineConstraintInTheComment(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, a smallint CONSTRAINT ck_inline CHECK (abs(a) > 1));");

    assertThat(comment(in, "t", "a")).isNotNull().contains("ck_inline");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "flag boolean|flag IN (true)",
        "u uuid|u <> '00000000-0000-0000-0000-000000000000'"
      })
  void shouldKeepBooleanAndUuidAndComment(String spec, @TempDir Path dir) {
    String[] parts = spec.split("\\|");
    assertKeptWithComment(dir, parts[0], parts[1]);
  }

  @Test
  void shouldKeepUnknownTypeAndCommentAboutTheCheck(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, g foobar, CONSTRAINT ck_g CHECK (g > 0));",
            NestingOptions.none(),
            true);

    assertThat(comment(in, "t", "g")).isNotNull().containsIgnoringCase("check");
  }

  @Test
  void shouldWarnWithTableAndConstraintWhenColumnIsNotFound(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE things (id int PRIMARY KEY, a smallint, CONSTRAINT ck_bad CHECK (zzz > 0));");

    assertThat(in.warnings()).anyMatch(w -> w.contains("things") && w.contains("ck_bad"));
    assertThat(datatypesOf(in, "things")).containsEntry("a", "int[1..32767]");
  }

  @Test
  void shouldNeverAbortOnAnUnparseableConditionAndSayWhy(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE things (id int PRIMARY KEY, a smallint,"
                + " CONSTRAINT ck_bad CHECK (JSON_VALID(a) = 1 AND a MEMBER OF (a)));");

    assertThat(datatypesOf(in, "things")).containsEntry("a", "int[1..32767]");
    assertThat(
            in.warnings().stream().anyMatch(w -> w.contains("things") && w.contains("ck_bad"))
                || comment(in, "things", "a") != null)
        .as("the unusable CHECK is reported as a warning or a comment")
        .isTrue();
  }

  @Test
  void guardCheckOnAnOmittedGeneratedColumnDoesNothing(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,"
                + " x smallint, CONSTRAINT ck_id CHECK (id > 0));");

    assertThat(datatypesOf(in, "t")).doesNotContainKey("id");
    assertThat(comment(in, "t", "id")).isNull();
    assertThat(in.warnings()).noneMatch(w -> w.contains("ck_id"));
  }

  @Test
  void guardTableWithoutCheckIsUnchanged(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, x smallint, s varchar(5), d date);");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("x", "int[1..32767]")
        .containsEntry("s", "char[1..5]")
        .containsEntry("d", DATE_DEFAULT);
    assertThat(in.comments()).isEmpty();
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------------------ keys and references win

  @ParameterizedTest
  @ValueSource(strings = {"id > 0", "id >= 0", "id >= 1"})
  void guardSerialPrimaryKeyWithoutCommentWhenCheckHoldsFromOne(String check, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, CHECK (" + check + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..2147483647]");
    assertThat(comment(in, "t", "id")).as("comment for %s", check).isNull();
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id > 0", "id >= 0", "id >= 1"})
  void guardUniqueColumnWithoutCommentWhenCheckHoldsFromOne(String check, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, u int UNIQUE, CHECK ("
                + check.replace("id", "u")
                + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("u", "unique[1..count]");
    assertThat(comment(in, "t", "u")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id >= 1000", "id <= 100", "id > 5", "id BETWEEN 1 AND 50"})
  void shouldKeepKeyAndCommentThatTheCheckCanBeViolated(String check, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, CHECK (" + check + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..2147483647]");
    assertThat(comment(in, "t", "id"))
        .as("comment for %s", check)
        .isNotNull()
        .containsIgnoringCase("check");
  }

  @Test
  void shouldKeepUniqueColumnAndCommentForOtherBound(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, u int UNIQUE, CHECK (u >= 1000));");

    assertThat(datatypesOf(in, "t")).containsEntry("u", "unique[1..count]");
    assertThat(comment(in, "t", "u")).isNotNull().containsIgnoringCase("check");
  }

  @ParameterizedTest
  @ValueSource(strings = {"parent_id > 0", "parent_id >= 5", "parent_id IN (1, 2)"})
  void shouldKeepForeignKeyReferenceAndComment(String check, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id int PRIMARY KEY);\n"
                + "CREATE TABLE t (id int PRIMARY KEY, parent_id int REFERENCES p(id),"
                + " CHECK ("
                + check
                + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("parent_id", "ref[p.id]");
    assertThat(comment(in, "t", "parent_id"))
        .as("comment for %s", check)
        .isNotNull()
        .containsIgnoringCase("check");
  }

  @Test
  void shouldMapTextUniqueWithValueListToCycleWithoutTheNotEnforcedWarning(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, code varchar(8) UNIQUE CHECK (code IN ('a','b')));");

    assertThat(datatypesOf(in, "t")).containsEntry("code", "cycle[a,b]");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldMapTextPrimaryKeyWithValueListToCycleWithoutTheNotEnforcedWarning(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE t (code varchar(8) PRIMARY KEY CHECK (code IN ('a','b')), n int);");

    assertThat(datatypesOf(in, "t")).containsEntry("code", "cycle[a,b]");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldApplyChecksUnderNesting(@TempDir Path dir) {
    String ddl =
        "CREATE TABLE customers (id int PRIMARY KEY, label varchar(9));\n"
            + "CREATE TABLE orders (id int PRIMARY KEY,"
            + " customer_id int REFERENCES customers(id),"
            + " qty smallint CHECK (qty >= 5), kind varchar(5) CHECK (kind IN ('a','b')));";

    Inspection nested = inspectDdl(dir, ddl, NestingOptions.parse("auto", null), false);

    assertThat(datatypesOf(nested, "customers").values()).contains("array[object[orders], 1..10]");
    assertThat(datatypesOf(nested, "orders"))
        .containsEntry("qty", "int[5..32767]")
        .containsEntry("kind", "enum[a,b]");
  }
}
