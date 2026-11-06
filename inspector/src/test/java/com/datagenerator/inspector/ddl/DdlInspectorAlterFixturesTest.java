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
  private static final String SERIAL = "serial";

  // ---------------------------------------------------------------- PostgreSQL (pg_dump)

  @Test
  void shouldApplyEveryAlterConstraintOfThePgDumpInStrictMode() {
    Inspection in = pgDump(NestingOptions.none());

    assertThat(in.structures()).hasSize(13);
    assertFields(in, "categories", "category_id", SERIAL);
    assertFields(
        in, "employees", "employee_id", SERIAL, "reports_to", ref("employees.employee_id"));
    assertFields(
        in,
        "orders",
        "order_id",
        SERIAL,
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
        SERIAL,
        "supplier_id",
        ref("suppliers.supplier_id"),
        "category_id",
        ref("categories.category_id"));
    assertFields(in, "region", "region_id", SERIAL);
    assertFields(in, "shippers", "shipper_id", SERIAL);
    assertFields(in, "suppliers", "supplier_id", SERIAL);
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

    assertThat(count(in, d -> d.equals(SERIAL))).as("serial").isEqualTo(7);
    assertThat(count(in, d -> d.startsWith("ref["))).as("refs").isEqualTo(13);
    assertThat(count(in, d -> d.endsWith(", unique=pk]"))).as("unique=pk refs").isEqualTo(6);
  }

  @Test
  void shouldWarnExactlyAboutTheThreeTextPrimaryKeysOfThePgDump() {
    Inspection in = pgDump(NestingOptions.none());

    assertThat(in.warnings())
        .hasSize(3)
        .allMatch(w -> w.contains("PRIMARY KEY(") && w.contains("not enforced"))
        .anyMatch(w -> w.startsWith("customers:") && w.contains("customer_id"))
        .anyMatch(w -> w.startsWith("customer_demographics:") && w.contains("customer_type_id"))
        .anyMatch(w -> w.startsWith("territories:") && w.contains("territory_id"));
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

    assertFields(in, "region", "RegionID", SERIAL);
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
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("customer_demographics") && w.contains("not enforced"))
        .anyMatch(w -> w.contains("territories") && w.contains("not enforced"));
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

    assertFields(in, "employee", "reports_to", ref("employee.employee_id"));
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
    assertFields(in, "categories", "CATEGORY_ID", SERIAL);
    assertFields(in, "customers", "CUSTOMER_ID", SERIAL);
    assertFields(
        in, "employees", "EMPLOYEE_ID", SERIAL, "REPORTS_TO", ref("employees.employee_id"));
    assertFields(
        in,
        "orders",
        "ORDER_ID",
        SERIAL,
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
        SERIAL,
        "CATEGORY_ID",
        ref("categories.category_id"),
        "SUPPLIER_ID",
        ref("suppliers.supplier_id"));
    assertFields(in, "shippers", "SHIPPER_ID", SERIAL);
    assertFields(in, "suppliers", "SUPPLIER_ID", SERIAL);
    assertThat(count(in, d -> d.equals(SERIAL))).as("serial").isEqualTo(7);
    assertThat(count(in, d -> d.startsWith("ref["))).as("refs").isEqualTo(8);
    assertThat(loadAll(in, dir)).hasSize(8);
  }

  // ---------------------------------------------------------------- helpers

  private static String ref(String target) {
    return "ref[" + target + ", 1..count]";
  }

  private static String refPk(String target) {
    return "ref[" + target + ", 1..count, unique=pk]";
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
