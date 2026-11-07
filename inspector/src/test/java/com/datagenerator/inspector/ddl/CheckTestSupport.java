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
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared helpers of the CHECK constraint tests (#449, #214). All tables are named {@code t}. */
final class CheckTestSupport {

  private static final Pattern RANGE =
      Pattern.compile("^(?:int|decimal)\\[([^.\\]]+(?:\\.\\d+)?)\\.\\.(.+)]$");
  private static final Pattern DATES =
      Pattern.compile("^date\\[(\\d{4}-\\d\\d-\\d\\d)\\.\\.(\\d{4}-\\d\\d-\\d\\d)]$");

  private CheckTestSupport() {}

  /** Strict inspection of {@code t (id int PRIMARY KEY, <colDef>, CHECK (<check>))}. */
  static Inspection inspectCheck(Path dir, String colDef, String check) {
    return inspectDdl(
        dir, "CREATE TABLE t (id int PRIMARY KEY, " + colDef + ", CHECK (" + check + "));");
  }

  /** The same table without any CHECK: today's type for the column. */
  static String baseType(Path dir, String colDef) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, " + colDef + ");");
    return datatypesOf(in, "t").get(columnName(colDef));
  }

  /** Datatype of the column of {@code colDef} when {@code check} is declared on the table. */
  static String type(Path dir, String colDef, String check) {
    return datatypesOf(inspectCheck(dir, colDef, check), "t").get(columnName(colDef));
  }

  static String columnName(String colDef) {
    return colDef.trim().split("\\s+")[0];
  }

  /** Review comment of {@code table.field}, or null. */
  static String comment(Inspection in, String table, String field) {
    return in.comments().getOrDefault(table, Map.of()).get(field);
  }

  /** The column is unchanged, a review comment mentions the CHECK, and nothing aborts. */
  static void assertKeptWithComment(Path dir, String colDef, String check) {
    Inspection in = inspectCheck(dir, colDef, check);
    String col = columnName(colDef);
    assertThat(datatypesOf(in, "t").get(col))
        .as("type for %s", check)
        .isEqualTo(baseType(dir, colDef));
    assertThat(comment(in, "t", col))
        .as("comment for %s", check)
        .isNotNull()
        .containsIgnoringCase("check");
  }

  /** The column has exactly {@code expected} and neither a review comment nor a warning. */
  static void assertNarrowed(Path dir, String colDef, String check, String expected) {
    Inspection in = inspectCheck(dir, colDef, check);
    String col = columnName(colDef);
    assertThat(datatypesOf(in, "t").get(col)).as("type for %s", check).isEqualTo(expected);
    assertThat(comment(in, "t", col)).as("comment for %s", check).isNull();
    assertThat(in.warnings()).as("warnings for %s", check).isEmpty();
  }

  /** Numeric bounds of an {@code int[a..b]} / {@code decimal[a..b]} datatype. */
  static BigDecimal[] bounds(String datatype) {
    Matcher m = RANGE.matcher(datatype);
    assertThat(m.matches()).as("not a numeric range: %s", datatype).isTrue();
    return new BigDecimal[] {new BigDecimal(m.group(1)), new BigDecimal(m.group(2))};
  }

  /** Numeric equality of the bounds (so {@code 1} and {@code 1.0} are the same). */
  static void assertBounds(String datatype, String min, String max) {
    BigDecimal[] b = bounds(datatype);
    assertThat(b[0]).as("min of %s", datatype).isEqualByComparingTo(min);
    assertThat(b[1]).as("max of %s", datatype).isEqualByComparingTo(max);
  }

  /** Bounds of a {@code date[a..b]} datatype. */
  static LocalDate[] dates(String datatype) {
    Matcher m = DATES.matcher(datatype);
    assertThat(m.matches()).as("not a date range: %s", datatype).isTrue();
    return new LocalDate[] {LocalDate.parse(m.group(1)), LocalDate.parse(m.group(2))};
  }
}
