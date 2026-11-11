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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #445: {@code TypeParser.IDENT} is {@code [a-z_][a-z0-9_]*}, so every {@code ref[table.column]}
 * the inspector emits must carry the snake_cased referenced column. The FIELD KEYS keep the
 * declared case (they are the DB column names the destination inserts into).
 */
class DdlInspectorForeignKeyCaseTest {

  @Test
  void shouldSnakeCaseReferencedColumnOfPascalCaseTableLevelFk(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (EmployeeID INT PRIMARY KEY, Name VARCHAR(50));
            CREATE TABLE Orders (
              OrderID INT PRIMARY KEY,
              EmployeeID INT,
              CONSTRAINT FK_Orders_Employees FOREIGN KEY (EmployeeID) REFERENCES Employees (EmployeeID)
            );
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsExactly(
            Map.entry("OrderID", "serial[1..2147483647]"),
            Map.entry("EmployeeID", "ref[employees.employee_id]"));
    assertThat(datatypesOf(in, "employees")).containsKeys("EmployeeID", "Name");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfInlineReferencesWithBrackets(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE [dbo].[Employees] ([EmployeeID] INT PRIMARY KEY, [Name] VARCHAR(50));
            GO
            CREATE TABLE [dbo].[Orders] (
              [OrderID] INT PRIMARY KEY,
              [EmployeeID] INT REFERENCES [dbo].[Employees] ([EmployeeID])
            );
            GO
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsEntry("EmployeeID", "ref[employees.employee_id]")
        .containsKey("OrderID");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfInlineReferencesWithoutBrackets(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (EmployeeID INT PRIMARY KEY, Name VARCHAR(50));
            CREATE TABLE Orders (OrderID INT PRIMARY KEY, EmployeeID INT REFERENCES Employees (EmployeeID));
            """);

    assertThat(datatypesOf(in, "orders")).containsEntry("EmployeeID", "ref[employees.employee_id]");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfUppercaseOracleStyleFk(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE EMPLOYEES (EMPLOYEE_ID NUMBER(10) PRIMARY KEY, LAST_NAME VARCHAR2(40));
            CREATE TABLE ORDERS (
              ORDER_ID NUMBER(10) PRIMARY KEY,
              EMPLOYEE_ID NUMBER(10),
              CONSTRAINT FK_ORDERS_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES (EMPLOYEE_ID)
            );
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsEntry("EMPLOYEE_ID", "ref[employees.employee_id]")
        .containsKey("ORDER_ID");
    assertThat(datatypesOf(in, "employees")).containsKeys("EMPLOYEE_ID", "LAST_NAME");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnsOfCompositePkThatIsForeignKey(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Orders (OrderID INT PRIMARY KEY);
            CREATE TABLE Products (ProductID INT PRIMARY KEY);
            CREATE TABLE OrderDetails (
              OrderID INT,
              ProductID INT,
              Quantity INT,
              PRIMARY KEY (OrderID, ProductID),
              FOREIGN KEY (OrderID) REFERENCES Orders (OrderID),
              FOREIGN KEY (ProductID) REFERENCES Products (ProductID)
            );
            """);

    assertThat(datatypesOf(in, "order_details"))
        .containsEntry("OrderID", "ref[orders.order_id, unique=pk]")
        .containsEntry("ProductID", "ref[products.product_id, unique=pk]")
        .containsKey("Quantity");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfSingleForeignKeyPrimaryKey(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (EmployeeID INT PRIMARY KEY);
            CREATE TABLE EmployeeProfile (
              EmployeeID INT PRIMARY KEY REFERENCES Employees (EmployeeID),
              Bio VARCHAR(50)
            );
            """);

    assertThat(datatypesOf(in, "employee_profile"))
        .containsEntry("EmployeeID", "ref[employees.employee_id, unique]");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfUniqueGroupForeignKey(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (EmployeeID INT PRIMARY KEY);
            CREATE TABLE Badges (
              BadgeID INT PRIMARY KEY,
              EmployeeID INT,
              Slot INT,
              UNIQUE (EmployeeID, Slot),
              FOREIGN KEY (EmployeeID) REFERENCES Employees (EmployeeID)
            );
            """);

    assertThat(datatypesOf(in, "badges"))
        .containsEntry("EmployeeID", "ref[employees.employee_id, unique=uq1]");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfSelfReferencingFk(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (
              EmployeeID INT PRIMARY KEY,
              ReportsTo INT,
              CONSTRAINT FK_Employees_Employees FOREIGN KEY (ReportsTo) REFERENCES Employees (EmployeeID)
            );
            """);

    assertThat(datatypesOf(in, "employees"))
        .containsEntry("ReportsTo", "ref[employees.employee_id]")
        .containsKey("EmployeeID");
    assertLoads(in, dir);
  }

  @Test
  void shouldSnakeCaseReferencedColumnOfFlatRefLeftByNesting(@TempDir Path dir) throws IOException {
    // Invoice nests under Customers (first declared parent); the FK to Shippers stays a flat ref.
    Inspection in =
        new DdlInspector()
            .inspect(
                write(
                    dir,
                    """
                    CREATE TABLE Customers (CustomerID INT PRIMARY KEY, Name VARCHAR(50));
                    CREATE TABLE Shippers (ShipperID INT PRIMARY KEY, Name VARCHAR(50));
                    CREATE TABLE Invoice (
                      InvoiceID INT PRIMARY KEY,
                      CustomerID INT,
                      ShipperID INT,
                      FOREIGN KEY (CustomerID) REFERENCES Customers (CustomerID),
                      FOREIGN KEY (ShipperID) REFERENCES Shippers (ShipperID)
                    );
                    """),
                NestingOptions.parse("auto", null));

    assertThat(datatypesOf(in, "customers"))
        .containsEntry("invoices", "array[object[invoice], 1..10]");
    assertThat(datatypesOf(in, "invoice"))
        .doesNotContainKey("CustomerID")
        .containsEntry("ShipperID", "ref[shippers.shipper_id]")
        .containsKey("InvoiceID");
    assertLoads(in, dir);
  }

  @Test
  void shouldWarnNamingBothColumnsWhenReferencedTableHasSnakeCaseCollision(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Orders (OrderId INT PRIMARY KEY, ORDER_ID INT);
            CREATE TABLE Lines (
              LineId INT PRIMARY KEY,
              OrderRef INT,
              FOREIGN KEY (OrderRef) REFERENCES Orders (OrderId)
            );
            """);

    // the target is documentation-only: still a valid ref, generation works
    assertThat(datatypesOf(in, "lines"))
        .containsEntry("OrderRef", "ref[orders.order_id, 1..count]");
    assertThat(datatypesOf(in, "orders")).containsKeys("OrderId", "ORDER_ID");
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("orders") && w.contains("OrderId") && w.contains("ORDER_ID"));
    assertLoads(in, dir);
  }

  @Test
  void shouldNotWarnAboutCollisionWhenColumnsAreDistinct(@TempDir Path dir) throws IOException {
    // guard, passes today
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE Employees (EmployeeID INT PRIMARY KEY, EmployeeName VARCHAR(50));
            CREATE TABLE Orders (OrderID INT PRIMARY KEY, EmployeeID INT,
              FOREIGN KEY (EmployeeID) REFERENCES Employees (EmployeeID));
            """);

    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldKeepAlreadySnakeCaseForeignKeyUnchanged(@TempDir Path dir) throws IOException {
    // guard, passes today
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE customer (id BIGINT PRIMARY KEY);
            CREATE TABLE invoice (id BIGINT PRIMARY KEY, customer_id BIGINT REFERENCES customer(id));
            """);

    assertThat(datatypesOf(in, "invoice")).containsEntry("customer_id", "ref[customer.id]");
    assertLoads(in, dir);
  }

  /** Every structure loads with the real parsers and every ref target is IDENT.IDENT. */
  private void assertLoads(Inspection in, Path dir) {
    Map<String, DataStructure> loaded = loadAll(in, dir.resolve("out"));
    assertThat(loaded.keySet())
        .containsExactlyInAnyOrderElementsOf(
            in.structures().stream().map(DataStructure::getName).toList());
    loaded.values().stream()
        .flatMap(s -> s.getData().values().stream())
        .map(f -> refTarget(f.getDatatype()))
        .filter(t -> t != null)
        .forEach(t -> assertThat(t).matches("[a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*"));
  }

  private Path write(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return sql;
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    return new DdlInspector().inspect(write(dir, ddl));
  }
}
