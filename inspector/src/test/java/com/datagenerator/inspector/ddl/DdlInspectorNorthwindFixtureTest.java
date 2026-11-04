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
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static com.datagenerator.inspector.ddl.DdlTestSupport.refTarget;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * #444 / #445 regression on the real SQL Server Northwind export (IDENTITY PKs, PascalCase FKs).
 */
class DdlInspectorNorthwindFixtureTest {

  private static final Path SQLSERVER =
      Path.of("src/test/resources/northwind/sqlserver/instnwnd-schema.sql");

  /** Structure name, declared IDENTITY PK column. */
  static Stream<Arguments> identityPks() {
    return Stream.of(
        Arguments.of("employees", "EmployeeID"),
        Arguments.of("categories", "CategoryID"),
        Arguments.of("shippers", "ShipperID"),
        Arguments.of("suppliers", "SupplierID"),
        Arguments.of("orders", "OrderID"),
        Arguments.of("products", "ProductID"));
  }

  @ParameterizedTest(name = "{0}.{1}")
  @MethodSource("identityPks")
  void shouldNotEmitIdentityPkAsPlainUnwarnedSerial(String table, String pk) {
    Inspection in = new DdlInspector().inspect(SQLSERVER, NestingOptions.none(), true);

    String label = table + "." + pk;
    Map<String, String> fields = datatypesOf(in, table);
    if (fields.containsKey(pk)) {
      assertThat(in.warnings())
          .as("%s kept as %s must carry the #400 warning", label, fields.get(pk))
          .anyMatch(w -> w.contains(label) && w.contains("IDENTITY") && w.contains("#400"));
    } else {
      assertThat(in.warnings())
          .anyMatch(
              w ->
                  w.contains(label)
                      && w.contains("IDENTITY")
                      && w.toLowerCase(Locale.ROOT).contains("omit"));
    }
  }

  @Test
  void shouldKeepIdentityPkThatOtherTablesReferenceAsSerialWithWarning() {
    Inspection in = new DdlInspector().inspect(SQLSERVER, NestingOptions.none(), true);

    // Orders.EmployeeID -> Employees.EmployeeID, so Employees.EmployeeID must stay for the ref.
    assertThat(datatypesOf(in, "employees")).containsKey("EmployeeID");
    assertThat(datatypesOf(in, "orders"))
        .containsEntry("EmployeeID", "ref[employees.employee_id, 1..count]");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("employees.EmployeeID")
                    && w.contains("IDENTITY")
                    && w.contains("referenc")
                    && w.contains("#400"));
  }

  @Test
  void shouldEmitOnlyLoadableRefsAndKeepDeclaredFieldKeys(@TempDir Path dir) {
    Inspection in = new DdlInspector().inspect(SQLSERVER, NestingOptions.none(), true);

    Map<String, DataStructure> loaded = loadAll(in, dir);

    long refs =
        loaded.values().stream()
            .flatMap(s -> s.getData().values().stream())
            .map(f -> refTarget(f.getDatatype()))
            .filter(t -> t != null)
            .peek(t -> assertThat(t).matches("[a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*"))
            .count();
    assertThat(refs).as("table-level FKs inside CREATE TABLE").isGreaterThanOrEqualTo(8);
    // field keys stay as declared: they are the column names the destination inserts into
    assertThat(loaded.get("employees").getData()).containsKeys("EmployeeID", "ReportsTo");
    assertThat(loaded.get("orders").getData()).containsKeys("OrderID", "CustomerID", "EmployeeID");
    assertThat(loaded.get("employees").getData().get("ReportsTo").getDatatype())
        .isEqualTo("ref[employees.employee_id, 1..count]");
  }
}
