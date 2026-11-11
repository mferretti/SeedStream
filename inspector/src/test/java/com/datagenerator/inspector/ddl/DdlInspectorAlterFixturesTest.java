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
import static com.datagenerator.inspector.ddl.DdlTestSupport.assertNoIdentityMarker;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The four Northwind exports declare their keys with {@code ALTER TABLE} (#376). Expected datatypes
 * are spelled out per field; the key columns are the ones that used to stay plain {@code int[...]}
 * / text.
 */
class DdlInspectorAlterFixturesTest {

  private static final Path ROOT = Path.of("src/test/resources/northwind");
  private static final String SMALL_SERIAL = "serial[1..32767]";
  private static final String INT_SERIAL = "serial[1..2147483647]";
  private static final String NUMBER9_SERIAL = "serial[1..999999999]";

  // ---------------------------------------------------------------- PostgreSQL (pg_dump)

  @Test
  void shouldApplyEveryAlterConstraintOfThePgDumpInStrictMode() {
    Inspection in = pgDump(NestingOptions.none());

    assertThat(in.structures()).hasSize(13);
    assertFields(in, "categories", "category_id", SMALL_SERIAL);
    assertFields(
        in,
        "employees",
        "employee_id",
        SMALL_SERIAL,
        "reports_to",
        selfRef("employees.employee_id"));
    assertFields(
        in,
        "orders",
        "order_id",
        SMALL_SERIAL,
        "customer_id",
        ref("customers.customer_id"),
        "employee_id",
        ref("employees.employee_id"),
        "ship_via",
        ref("shippers.shipper_id"));
    assertFields(
        in,
        "products",
        "product_id",
        SMALL_SERIAL,
        "supplier_id",
        ref("suppliers.supplier_id"),
        "category_id",
        ref("categories.category_id"));
    assertFields(in, "region", "region_id", SMALL_SERIAL);
    assertFields(in, "shippers", "shipper_id", SMALL_SERIAL);
    assertFields(in, "suppliers", "supplier_id", SMALL_SERIAL);
    assertFields(in, "territories", "region_id", ref("region.region_id"));
    // composite primary keys whose members are all foreign keys
    assertFields(
        in,
        "customer_customer_demo",
        "customer_id",
        refPk("customers.customer_id"),
        "customer_type_id",
        refPk("customer_demographics.customer_type_id"));
    assertFields(
        in,
        "employee_territories",
        "employee_id",
        refPk("employees.employee_id"),
        "territory_id",
        refPk("territories.territory_id"));
    assertFields(
        in,
        "order_details",
        "order_id",
        refPk("orders.order_id"),
        "product_id",
        refPk("products.product_id"));
  }

  @Test
  void shouldProduceSevenSerialThirteenRefsAndSixUniquePkRefsForThePgDump() {
    Inspection in = pgDump(NestingOptions.none());

    assertThat(count(in, d -> d.equals(SMALL_SERIAL))).as("serial").isEqualTo(7);
    assertThat(count(in, d -> d.startsWith("ref["))).as("refs").isEqualTo(13);
    assertThat(count(in, d -> d.endsWith(", unique=pk]"))).as("unique=pk refs").isEqualTo(6);
  }

  @Test
  void shouldEnforceTheThreeTextPrimaryKeysOfThePgDumpAsNumericStrings() {
    Inspection in = pgDump(NestingOptions.none());

    assertFields(in, "customers", "customer_id", "serial[1..99999]");
    assertFields(in, "customer_demographics", "customer_type_id", "serial[1..9999999999]");
    assertFields(in, "territories", "territory_id", "serial");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
    assertThat(in.comments().get("customers"))
        .containsEntry("customer_id", "text key generated as numeric strings — max 99999 rows");
    assertThat(in.comments().get("territories"))
        .containsEntry("territory_id", "text key generated as numeric strings");
  }

  @Test
  void shouldNestPgDumpForeignKeysDeclaredByAlter(@TempDir Path dir) {
    Inspection in = pgDump(NestingOptions.parse("auto", null));

    assertThat(datatypesOf(in, "customers")).containsValue("array[object[orders], 1..10]");
    assertThat(datatypesOf(in, "orders")).doesNotContainKey("customer_id");
    assertThat(loadAll(in, dir)).isNotEmpty();
  }

  @Test
  void shouldWritePgDumpStructuresThatLoadWithTheRealParser(@TempDir Path dir) {
    Map<String, DataStructure> loaded = loadAll(pgDump(NestingOptions.none()), dir);

    assertThat(loaded).hasSize(13);
    assertThat(loaded.get("orders").getData().get("customer_id").getDatatype())
        .isEqualTo(ref("customers.customer_id"));
  }

  // ---------------------------------------------------------------- SQL Server

  @Test
  void shouldApplyAlterKeysOfTheSqlServerScript() {
    Inspection in = bestEffort("sqlserver/instnwnd-schema.sql", NestingOptions.none());

    assertFields(in, "region", "RegionID", INT_SERIAL);
    assertFields(in, "territories", "RegionID", ref("region.region_id"));
    assertFields(
        in,
        "employee_territories",
        "EmployeeID",
        refPk("employees.employee_id"),
        "TerritoryID",
        refPk("territories.territory_id"));
    assertFields(
        in,
        "customer_customer_demo",
        "CustomerID",
        refPk("customers.customer_id"),
        "CustomerTypeID",
        refPk("customer_demographics.customer_type_id"));
    assertFields(in, "customers", "CustomerID", "serial[1..99999]");
    assertFields(in, "customer_demographics", "CustomerTypeID", "serial[1..9999999999]");
    assertFields(in, "territories", "TerritoryID", "serial");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
    assertNoIdentityMarker(in);
  }

  @Test
  void shouldWriteSqlServerStructuresThatLoadWithTheRealParser(@TempDir Path dir) {
    Inspection in = bestEffort("sqlserver/instnwnd-schema.sql", NestingOptions.none());

    assertThat(loadAll(in, dir)).containsKeys("region", "territories", "employee_territories");
  }

  // ---------------------------------------------------------------- MySQL

  @Test
  void shouldApplyTheEightAlterForeignKeysOfTheMysqlSchema(@TempDir Path dir) {
    Inspection in = bestEffort("mysql/northwind-mysql-schema.sql", NestingOptions.none());

    assertFields(in, "employee", "reports_to", selfRef("employee.employee_id"));
    assertFields(
        in,
        "order_detail",
        "order_id",
        refPk("order.order_id"),
        "product_id",
        refPk("product.product_id"));
    assertFields(
        in,
        "order",
        "customer_id",
        ref("customer.customer_id"),
        "employee_id",
        ref("employee.employee_id"),
        "ship_via",
        ref("shipper.shipper_id"));
    assertFields(
        in,
        "product",
        "category_id",
        ref("category.category_id"),
        "supplier_id",
        ref("supplier.supplier_id"));
    assertThat(count(in, d -> d.startsWith("ref["))).as("refs").isEqualTo(8);
    assertThat(loadAll(in, dir)).containsKey("order_detail");
  }

  // ---------------------------------------------------------------- Oracle

  @Test
  void shouldApplyTheAlterPrimaryKeysAndForeignKeysOfTheOracleExport(@TempDir Path dir) {
    Inspection in = bestEffort("oracle/northwind-oracle-schema.sql", NestingOptions.none());

    // 7 single-column PKs become serial (NUMBER(9,0) maps to int); ORDER_DETAILS has the 8th PK,
    // a composite one whose members are both foreign keys
    assertFields(in, "categories", "CATEGORY_ID", NUMBER9_SERIAL);
    assertFields(in, "customers", "CUSTOMER_ID", NUMBER9_SERIAL);
    assertFields(
        in,
        "employees",
        "EMPLOYEE_ID",
        NUMBER9_SERIAL,
        "REPORTS_TO",
        selfRef("employees.employee_id"));
    assertFields(
        in,
        "orders",
        "ORDER_ID",
        NUMBER9_SERIAL,
        "CUSTOMER_ID",
        ref("customers.customer_id"),
        "EMPLOYEE_ID",
        ref("employees.employee_id"),
        "SHIP_VIA",
        ref("shippers.shipper_id"));
    assertFields(
        in,
        "order_details",
        "ORDER_ID",
        refPk("orders.order_id"),
        "PRODUCT_ID",
        refPk("products.product_id"));
    assertFields(
        in,
        "products",
        "PRODUCT_ID",
        NUMBER9_SERIAL,
        "CATEGORY_ID",
        ref("categories.category_id"),
        "SUPPLIER_ID",
        ref("suppliers.supplier_id"));
    assertFields(in, "shippers", "SHIPPER_ID", NUMBER9_SERIAL);
    assertFields(in, "suppliers", "SUPPLIER_ID", NUMBER9_SERIAL);
    assertThat(count(in, d -> d.equals(NUMBER9_SERIAL))).as("serial").isEqualTo(7);
    assertThat(count(in, d -> d.startsWith("ref["))).as("refs").isEqualTo(8);
    assertThat(loadAll(in, dir)).hasSize(8);
  }

  // ---------------------------------------------------------------- helpers

  private static String ref(String target) {
    return "ref[" + target + "]";
  }

  /** A self-referencing FK keeps the explicit pool (#213). */
  private static String selfRef(String target) {
    return "ref[" + target + ", 1..count]";
  }

  private static String refPk(String target) {
    return "ref[" + target + ", unique=pk]";
  }

  private static Inspection pgDump(NestingOptions nesting) {
    return new DdlInspector()
        .inspect(ROOT.resolve("postgres/northwind-pg-dump.sql"), nesting, false);
  }

  private static Inspection bestEffort(String fixture, NestingOptions nesting) {
    return new DdlInspector().inspect(ROOT.resolve(fixture), nesting, true);
  }

  /** Counts the datatypes of all structures matching the predicate. */
  private static long count(Inspection in, Predicate<String> predicate) {
    return in.structures().stream()
        .flatMap(s -> s.getData().values().stream())
        .map(f -> f.getDatatype())
        .filter(predicate)
        .count();
  }

  /** Asserts {@code field, datatype} pairs of one structure. */
  private static void assertFields(Inspection in, String table, String... fieldAndType) {
    Map<String, String> actual = datatypesOf(in, table);
    Stream.iterate(0, i -> i < fieldAndType.length, i -> i + 2)
        .forEach(
            i ->
                assertThat(actual)
                    .as("%s.%s", table, fieldAndType[i])
                    .containsEntry(fieldAndType[i], fieldAndType[i + 1]));
  }
}
