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
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertKeptWithComment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.assertNarrowed;
import static com.datagenerator.inspector.ddl.CheckTestSupport.comment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.inspectCheck;
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Inputs a real PostgreSQL 17 rejected after a CHECK looked satisfied (#449): a type is narrowed
 * only when the result is certain to satisfy the constraint.
 */
class DdlInspectorCheckConstraintSafetyTest {

  /** A cast that changes the value hides what is compared: 4.99::int is 5, so it isn't < 5. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "c::int < 5",
        "c::int > 5",
        "CAST(c AS int) < 5",
        "c::bigint < 5",
        "c::smallint < 5",
        "c::numeric(10,0) < 5",
        "c::numeric(10,1) < 5",
        "c::real <= 0.3",
        "c > (5.7)::int"
      })
  void shouldNotSeeThroughAValueChangingCast(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "c numeric(10,2)", check);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"(s)::varchar(1) = 'a'", "(s)::char(1) = 'a'", "CAST(s AS varchar(1)) = 'a'"})
  void shouldNotSeeThroughATruncatingCast(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(5)", check);
  }

  @Test
  void guardShouldStillSeeThroughValuePreservingCasts(@TempDir Path dir) {
    assertNarrowed(dir, "s varchar(5)", "(s)::text = 'a'::text", "enum[a]");
    assertNarrowed(dir, "s varchar(5)", "(s)::character varying = 'a'", "enum[a]");
    assertNarrowed(dir, "c numeric(10,2)", "c::numeric <= (1)::numeric", "decimal[0.0..1.0]");
    assertNarrowed(dir, "r real", "r >= (0)::double precision", "decimal[0.0..9999.99]");
  }

  /** float4(0.2) is above 0.2, so the bound itself violates the check once stored. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "x real|x <= 1.1",
        "x real|x <= 0.2",
        "x real|x <= 0.1",
        "x real|x >= 0.7",
        "x float4|x <= 0.1",
        "x float(10)|x <= 0.1",
        "x float(24)|x <= 0.1",
        "x float|x <= 0.1"
      })
  void shouldNotNarrowASinglePrecisionColumnToAnInexactBound(String row, @TempDir Path dir) {
    String[] parts = row.split("\\|");
    assertKeptWithComment(dir, parts[0], parts[1]);
  }

  @Test
  void shouldNarrowFloatsWhereTheBoundIsExact(@TempDir Path dir) {
    assertNarrowed(dir, "x real", "x >= 0 AND x <= 1", "decimal[0.0..1.0]");
    assertNarrowed(dir, "x real", "x <= 0.5", "decimal[0.0..0.5]");
    assertNarrowed(dir, "x double precision", "x <= 0.1", "decimal[0.0..0.1]");
    assertNarrowed(dir, "x float(53)", "x <= 0.1", "decimal[0.0..0.1]");
  }

  /** Today's midnight is before now: a timestamp generated today exceeds CURRENT_DATE. */
  @ParameterizedTest
  @ValueSource(
      strings = {"ts < CURRENT_DATE", "ts <= CURRENT_DATE", "ts < CURDATE()", "CURRENT_DATE > ts"})
  void shouldNotTreatTodayAsNowOnATimestamp(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "ts timestamp", check);
  }

  @ParameterizedTest
  @ValueSource(strings = {"s IN ('a','b') AND s <> 'a'", "s IN ('a','b') AND length(s) > 5"})
  void shouldNotMapToAnEnumAnotherPartCanViolate(String check, @TempDir Path dir) {
    assertKeptWithComment(dir, "s varchar(5)", check);
  }

  @Test
  void shouldNameTheWholeConditionWhenNoValueRemains(@TempDir Path dir) {
    Inspection in = inspectCheck(dir, "x int", "x > 5 AND x < 3");

    assertThat(comment(in, "t", "x")).contains("x > 5").contains("x < 3");
  }

  @Test
  void shouldKeepLaterTablesWhenAnArrayLiteralHoldsABracket(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (s varchar(5) CHECK (s = ANY (ARRAY['a]'::text, 'b'::text])));\n"
                + "CREATE TABLE b (id int);\n"
                + "CREATE TABLE c (id int);\n");

    assertThat(in.structures()).extracting("name").containsExactly("a", "b", "c");
    assertThat(datatypesOf(in, "a")).containsEntry("s", "char[1..5]");
    assertThat(comment(in, "a", "s")).isNotBlank();
  }
}
