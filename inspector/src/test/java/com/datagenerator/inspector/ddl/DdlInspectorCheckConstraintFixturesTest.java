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
import static com.datagenerator.inspector.ddl.CheckTestSupport.bounds;
import static com.datagenerator.inspector.ddl.CheckTestSupport.comment;
import static com.datagenerator.inspector.ddl.CheckTestSupport.dates;
import static com.datagenerator.inspector.ddl.CheckTestSupport.type;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property check of the bounds emitted for CHECK constraints (#449), plus the real Northwind
 * fixtures. Both bounds, rounded the way the database rounds to the column scale, must satisfy a
 * Java predicate equivalent to the CHECK; this catches off-by-one and rounding slips. Every test
 * fails before the feature unless named {@code guard...}.
 */
class DdlInspectorCheckConstraintFixturesTest {

  private static final Path ROOT = Path.of("src/test/resources/northwind");

  private static Predicate<BigDecimal> p(Predicate<BigDecimal> predicate) {
    return predicate;
  }

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  // ------------------------------------------------------------------ property

  /** column definition, declared scale, CHECK, predicate equivalent to the CHECK. */
  static Stream<Arguments> numericProperties() {
    return Stream.of(
        Arguments.of("x smallint", 0, "x > 0", p(v -> v.signum() > 0)),
        Arguments.of("x smallint", 0, "x >= 0", p(v -> v.signum() >= 0)),
        Arguments.of("x smallint", 0, "x < 100", p(v -> v.compareTo(d("100")) < 0)),
        Arguments.of("x smallint", 0, "x <= 99", p(v -> v.compareTo(d("99")) <= 0)),
        Arguments.of("x smallint", 0, "x <= 0", p(v -> v.signum() <= 0)),
        Arguments.of("x smallint", 0, "x < 0", p(v -> v.signum() < 0)),
        Arguments.of("x smallint", 0, "x BETWEEN -5 AND 5", p(v -> v.abs().compareTo(d("5")) <= 0)),
        Arguments.of("x smallint", 0, "x = 5", p(v -> v.compareTo(d("5")) == 0)),
        Arguments.of(
            "x smallint",
            0,
            "x IN (3,1,2)",
            p(v -> v.compareTo(d("1")) >= 0 && v.compareTo(d("3")) <= 0)),
        Arguments.of("x smallint", 0, "x > 0.5", p(v -> v.compareTo(d("0.5")) > 0)),
        Arguments.of(
            "x smallint",
            0,
            "x >= 10.5 AND x < 20.5",
            p(v -> v.compareTo(d("10.5")) >= 0 && v.compareTo(d("20.5")) < 0)),
        Arguments.of("x smallint", 0, "x < 100000", p(v -> v.compareTo(d("100000")) < 0)),
        Arguments.of("x tinyint", 0, "x <= 0", p(v -> v.signum() <= 0)),
        Arguments.of("x int", 0, "x > -1", p(v -> v.compareTo(d("-1")) > 0)),
        Arguments.of("x bigint", 0, "x > 1000", p(v -> v.compareTo(d("1000")) > 0)),
        Arguments.of("x numeric(10,2)", 2, "x > 0", p(v -> v.signum() > 0)),
        Arguments.of("x numeric(10,2)", 2, "x < 1", p(v -> v.compareTo(d("1")) < 0)),
        Arguments.of(
            "x numeric(10,2)",
            2,
            "x >= 0 AND x <= 1",
            p(v -> v.signum() >= 0 && v.compareTo(d("1")) <= 0)),
        Arguments.of("x numeric(10,2)", 2, "x <= 0.125", p(v -> v.compareTo(d("0.125")) <= 0)),
        Arguments.of("x numeric(10,2)", 2, "x < 0.125", p(v -> v.compareTo(d("0.125")) < 0)),
        Arguments.of("x numeric(10,2)", 2, "x >= 0.125", p(v -> v.compareTo(d("0.125")) >= 0)),
        Arguments.of("x numeric(10,2)", 2, "x > 0.125", p(v -> v.compareTo(d("0.125")) > 0)),
        Arguments.of(
            "x numeric(10,2)",
            2,
            "x BETWEEN 0.001 AND 0.999",
            p(v -> v.compareTo(d("0.001")) >= 0 && v.compareTo(d("0.999")) <= 0)),
        Arguments.of("x numeric(10,3)", 3, "x > 0", p(v -> v.signum() > 0)),
        Arguments.of("x numeric(10,3)", 3, "x < 0.0005", p(v -> v.compareTo(d("0.0005")) < 0)),
        Arguments.of("x numeric(10,1)", 1, "x > 0", p(v -> v.signum() > 0)),
        Arguments.of("x numeric(10,1)", 1, "x <= -0.05", p(v -> v.compareTo(d("-0.05")) <= 0)),
        Arguments.of("x real", 2, "x > 0", p(v -> v.signum() > 0)),
        Arguments.of("x real", 2, "x <= 0", p(v -> v.signum() <= 0)),
        Arguments.of("x decimal", 2, "x < 1", p(v -> v.compareTo(d("1")) < 0)));
  }

  @ParameterizedTest
  @MethodSource("numericProperties")
  void shouldEmitBoundsThatSatisfyTheCheckAfterRoundingToScale(
      String colDef, int scale, String check, Predicate<BigDecimal> predicate, @TempDir Path dir) {
    String datatype = type(dir, colDef, check);

    BigDecimal[] b = bounds(datatype);
    assertThat(b[0]).as("min <= max in %s", datatype).isLessThanOrEqualTo(b[1]);
    for (BigDecimal bound : b) {
      BigDecimal stored = bound.setScale(scale, RoundingMode.HALF_UP);
      assertThat(predicate.test(stored))
          .as("%s for '%s': bound %s (stored %s)", datatype, check, bound, stored)
          .isTrue();
    }
  }

  /** CHECK on a date column, predicate equivalent to it. */
  static Stream<Arguments> dateProperties() {
    return Stream.of(
        Arguments.of(
            "d >= '2021-01-01'", (Predicate<LocalDate>) v -> !v.isBefore(LocalDate.of(2021, 1, 1))),
        Arguments.of(
            "d > '2021-01-01'", (Predicate<LocalDate>) v -> v.isAfter(LocalDate.of(2021, 1, 1))),
        Arguments.of(
            "d <= '2025-06-30'", (Predicate<LocalDate>) v -> !v.isAfter(LocalDate.of(2025, 6, 30))),
        Arguments.of(
            "d < '2025-01-01'", (Predicate<LocalDate>) v -> v.isBefore(LocalDate.of(2025, 1, 1))),
        Arguments.of(
            "d < '2000-01-01'", (Predicate<LocalDate>) v -> v.isBefore(LocalDate.of(2000, 1, 1))),
        Arguments.of(
            "d > '2040-01-01'", (Predicate<LocalDate>) v -> v.isAfter(LocalDate.of(2040, 1, 1))),
        Arguments.of(
            "d BETWEEN '2021-03-01' AND '2021-03-31'",
            (Predicate<LocalDate>)
                v ->
                    !v.isBefore(LocalDate.of(2021, 3, 1)) && !v.isAfter(LocalDate.of(2021, 3, 31))),
        Arguments.of("d < CURRENT_DATE", (Predicate<LocalDate>) v -> v.isBefore(LocalDate.now())),
        Arguments.of("d > CURRENT_DATE", (Predicate<LocalDate>) v -> v.isAfter(LocalDate.now())));
  }

  @ParameterizedTest
  @MethodSource("dateProperties")
  void shouldEmitDateBoundsThatSatisfyTheCheck(
      String check, Predicate<LocalDate> predicate, @TempDir Path dir) {
    String datatype = type(dir, "d date", check);

    LocalDate[] range = dates(datatype);
    assertThat(range[0]).as("min <= max in %s", datatype).isBeforeOrEqualTo(range[1]);
    assertThat(predicate.test(range[0])).as("%s min for '%s'", datatype, check).isTrue();
    assertThat(predicate.test(range[1])).as("%s max for '%s'", datatype, check).isTrue();
  }

  // ------------------------------------------------------------------ fixtures

  private static Inspection bestEffort(String fixture) {
    return new DdlInspector().inspect(ROOT.resolve(fixture), NestingOptions.none(), true);
  }

  private static String field(Inspection in, String table, String name) {
    Map<String, String> fields = datatypesOf(in, table);
    return fields.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError(table + "." + name + " missing in " + fields.keySet()));
  }

  private static String fieldName(Inspection in, String table, String name) {
    return datatypesOf(in, table).keySet().stream()
        .filter(k -> k.equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void shouldHonourTheSqlServerNorthwindChecks(@TempDir Path dir) {
    Inspection in = bestEffort("sqlserver/instnwnd-schema.sql");

    assertThat(field(in, "order_details", "Discount")).isEqualTo("decimal[0.0..1.0]");
    assertThat(field(in, "order_details", "Quantity")).isEqualTo("int[1..32767]");
    assertThat(field(in, "products", "UnitsInStock")).isEqualTo("int[0..32767]");
    assertThat(field(in, "products", "ReorderLevel")).isEqualTo("int[0..32767]");
    assertThat(field(in, "products", "UnitsOnOrder")).isEqualTo("int[0..32767]");
    assertThat(bounds(field(in, "order_details", "UnitPrice"))[0])
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(bounds(field(in, "products", "UnitPrice"))[0])
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(loadAll(in, dir)).containsKey("order_details");
  }

  @Test
  void guardSqlServerTimestampBirthDateBeforeNowStaysWithoutComment() {
    // BirthDate is datetime (timestamp[now-365d..now]), which already satisfies
    // CHECK (BirthDate < getdate()); the WP449 acceptance line "fixed past range" applies to
    // date columns only (see PostgreSQL birth_date below).
    Inspection in = bestEffort("sqlserver/instnwnd-schema.sql");

    assertThat(field(in, "employees", "BirthDate")).isEqualTo("timestamp[now-365d..now]");
    assertThat(comment(in, "employees", fieldName(in, "employees", "BirthDate"))).isNull();
  }

  @Test
  void shouldHonourTheOracleNorthwindAlterChecks(@TempDir Path dir) {
    Inspection in = bestEffort("oracle/northwind-oracle-schema.sql");

    assertThat(field(in, "order_details", "DISCOUNT")).isEqualTo("decimal[0.0..1.0]");
    assertThat(field(in, "products", "DISCONTINUED")).isEqualTo("enum[Y,N]");
    assertThat(bounds(field(in, "order_details", "QUANTITY"))[0])
        .isGreaterThanOrEqualTo(BigDecimal.ONE);
    assertThat(bounds(field(in, "products", "UNITS_IN_STOCK"))[0])
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(bounds(field(in, "order_details", "UNIT_PRICE"))[0])
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(loadAll(in, dir)).containsKey("products");
  }

  @Test
  void shouldHonourThePostgresDumpChecks(@TempDir Path dir) {
    Inspection in =
        new DdlInspector()
            .inspect(ROOT.resolve("postgres/northwind-pg-dump.sql"), NestingOptions.none(), false);

    assertThat(field(in, "order_details", "discount")).isEqualTo("decimal[0.0..1.0]");
    assertThat(field(in, "order_details", "quantity")).isEqualTo("int[1..32767]");
    assertThat(field(in, "products", "units_in_stock")).isEqualTo("int[0..32767]");
    assertThat(field(in, "products", "reorder_level")).isEqualTo("int[0..32767]");
    assertThat(field(in, "employees", "birth_date")).isEqualTo("date[1970-01-01..2019-12-31]");
    assertThat(comment(in, "employees", "birth_date")).isNotNull().containsIgnoringCase("check");
    assertBounds(field(in, "order_details", "unit_price"), "0", "9999.99");
    assertThat(loadAll(in, dir)).hasSize(13);
  }

  @Test
  void guardFixturesStillLoadWithTheRealParser(@TempDir Path dir) {
    assertThat(loadAll(bestEffort("mysql/northwind-mysql-schema.sql"), dir)).isNotEmpty();
  }
}
