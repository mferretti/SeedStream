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
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Foreign keys that omit the referenced column list ({@code REFERENCES customers}) target the
 * referenced table's primary key (#437). Before the fix the table-level form aborted the whole
 * inspection with a NullPointerException and the inline form guessed a column called {@code id}.
 */
class DdlInspectorColumnlessReferenceTest {

  private static final String INT = "int[1..999999]";
  private static final NestingOptions NEST = NestingOptions.parse("auto", null);
  private static final Path FIXTURE =
      Path.of("src/test/resources/northwind/postgres/pg-refs-without-columns.sql");

  @Test
  void shouldResolveTableLevelColumnlessForeignKeyToReferencedPrimaryKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id varchar(5), CONSTRAINT pk PRIMARY KEY (customer_id));
            CREATE TABLE orders (order_id int PRIMARY KEY, customer_id varchar(5),
              CONSTRAINT fk FOREIGN KEY (customer_id) REFERENCES customers);
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsEntry("customer_id", "ref[customers.customer_id]");
  }

  @Test
  void shouldResolveInlineColumnlessReferenceToReferencedPrimaryKeyNotToId(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id varchar(5) PRIMARY KEY);
            CREATE TABLE orders (order_id int PRIMARY KEY, customer_id varchar(5) REFERENCES customers);
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsEntry("customer_id", "ref[customers.customer_id]");
  }

  @Test
  void shouldResolveColumnlessReferenceToInlineColumnPrimaryKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE accounts (account_no int NOT NULL PRIMARY KEY, label varchar(10));
            CREATE TABLE ledgers (id int PRIMARY KEY, account_no int,
              FOREIGN KEY (account_no) REFERENCES accounts);
            """);

    assertThat(datatypesOf(in, "ledgers")).containsEntry("account_no", "ref[accounts.account_no]");
  }

  @Test
  void shouldResolveCompositeColumnlessForeignKeyPositionally(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE pairs (a int NOT NULL, b int NOT NULL, CONSTRAINT pairs_pkey PRIMARY KEY (a, b));
            CREATE TABLE pair_refs (id int PRIMARY KEY, x int, y int,
              CONSTRAINT pair_refs_fk FOREIGN KEY (y, x) REFERENCES pairs);
            """);

    // positional: first local column (y) -> first PK column (a), second (x) -> second (b)
    assertThat(datatypesOf(in, "pair_refs"))
        .containsEntry("y", "ref[pairs.a, 1..count]")
        .containsEntry("x", "ref[pairs.b, 1..count]");
  }

  @Test
  void shouldResolveColumnlessReferenceToPrimaryKeyDeclaredLaterInScript(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE child (id int PRIMARY KEY, parent_ref int REFERENCES parent);
            CREATE TABLE parent (parent_id int, CONSTRAINT parent_pkey PRIMARY KEY (parent_id));
            """);

    assertThat(datatypesOf(in, "child"))
        .containsEntry("parent_ref", "ref[parent.parent_id, 1..count]");
  }

  @Test
  void shouldResolveColumnlessReferenceToPrimaryKeyAddedByAlter(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE accounts (account_id integer NOT NULL);
            CREATE TABLE ledgers (ledger_id integer NOT NULL, account_id integer);
            ALTER TABLE ONLY ledgers
                ADD CONSTRAINT ledgers_account_fk FOREIGN KEY (account_id) REFERENCES accounts;
            ALTER TABLE ONLY accounts ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);
            """);

    assertThat(datatypesOf(in, "ledgers")).containsEntry("account_id", "ref[accounts.account_id]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldResolveCompositeColumnlessAlterForeignKeyToAlterPrimaryKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE pairs (a int NOT NULL, b int NOT NULL);
            CREATE TABLE pair_refs (id int PRIMARY KEY, a int, b int);
            ALTER TABLE ONLY pair_refs ADD CONSTRAINT pr_fk FOREIGN KEY (a, b) REFERENCES pairs;
            ALTER TABLE ONLY pairs ADD CONSTRAINT pairs_pkey PRIMARY KEY (a, b);
            """);

    assertThat(datatypesOf(in, "pair_refs"))
        .containsEntry("a", "ref[pairs.a, 1..count]")
        .containsEntry("b", "ref[pairs.b, 1..count]");
  }

  @Test
  void shouldSnakeCaseTheResolvedPrimaryKeyColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE Accounts ("AccountId" int, PRIMARY KEY ("AccountId"));
            CREATE TABLE Ledgers (id int PRIMARY KEY, acc int REFERENCES Accounts);
            """);

    assertThat(datatypesOf(in, "ledgers")).containsEntry("acc", "ref[accounts.account_id]");
  }

  @Test
  void shouldResolveSelfReferencingColumnlessReference(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE emp (emp_id int PRIMARY KEY, boss int REFERENCES emp);
            """);

    assertThat(datatypesOf(in, "emp")).containsEntry("boss", "ref[emp.emp_id]");
  }

  @Test
  void shouldNestChildUnderParentWhenReferenceIsColumnless(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id int, name varchar(50),
              CONSTRAINT customers_pkey PRIMARY KEY (customer_id));
            CREATE TABLE orders (order_id int PRIMARY KEY, customer_id int,
              CONSTRAINT orders_fk FOREIGN KEY (customer_id) REFERENCES customers);
            """,
            NEST,
            false);

    assertThat(datatypesOf(in, "customers")).containsValue("array[object[orders], 1..10]");
    assertThat(datatypesOf(in, "orders")).doesNotContainKey("customer_id");
  }

  @Test
  void shouldWarnAndKeepPlainTypeWhenReferencedTableHasNoPrimaryKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id int, name varchar(50));
            CREATE TABLE orders (order_id int PRIMARY KEY, customer_id int REFERENCES customers,
              alt_id int, CONSTRAINT orders_fk FOREIGN KEY (alt_id) REFERENCES customers);
            """);

    // never a guessed "id", never a dangling ref
    assertThat(datatypesOf(in, "orders"))
        .containsEntry("customer_id", INT)
        .containsEntry("alt_id", INT);
    assertThat(in.structures())
        .flatExtracting(s -> s.getData().values())
        .extracting(f -> f.getDatatype())
        .noneMatch(d -> d.startsWith("ref["));
    assertThat(in.warnings()).anyMatch(w -> w.contains("customers"));
  }

  @Test
  void shouldWarnAndKeepPlainTypesWhenCompositeReferenceMeetsSingleColumnKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE singles (id int PRIMARY KEY);
            CREATE TABLE refs (rid int PRIMARY KEY, a int, b int,
              CONSTRAINT refs_fk FOREIGN KEY (a, b) REFERENCES singles);
            """);

    assertThat(datatypesOf(in, "refs")).containsEntry("a", INT).containsEntry("b", INT);
    assertThat(in.warnings()).anyMatch(w -> w.contains("singles"));
  }

  @Test
  void shouldWarnAndKeepPlainTypeWhenSingleReferenceMeetsCompositeKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE pairs (a int NOT NULL, b int NOT NULL, PRIMARY KEY (a, b));
            CREATE TABLE refs (rid int PRIMARY KEY, a int,
              CONSTRAINT refs_fk FOREIGN KEY (a) REFERENCES pairs);
            """);

    assertThat(datatypesOf(in, "refs")).containsEntry("a", INT);
    assertThat(in.warnings()).anyMatch(w -> w.contains("pairs"));
  }

  @Test
  void shouldKeepExplicitColumnReferencesUnchanged(@TempDir Path dir) {
    // guard: spelled-out referenced columns are untouched by the resolution pass
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id int PRIMARY KEY, code int UNIQUE);
            CREATE TABLE orders (order_id int PRIMARY KEY, c1 int REFERENCES customers(customer_id),
              c2 int, CONSTRAINT fk2 FOREIGN KEY (c2) REFERENCES customers (code));
            """);

    assertThat(datatypesOf(in, "orders"))
        .containsEntry("c1", "ref[customers.customer_id]")
        .containsEntry("c2", "ref[customers.code]");
  }

  @Test
  void shouldInspectColumnlessFixtureInStrictModeAndResolveEveryReference(@TempDir Path dir) {
    Inspection in = new DdlInspector().inspect(FIXTURE, NestingOptions.none(), false);

    assertThat(datatypesOf(in, "ledgers"))
        .containsEntry("account_id", "ref[accounts.account_id]")
        .containsEntry("owner_account", "ref[accounts.account_id]");
    assertThat(datatypesOf(in, "pair_refs"))
        .containsEntry("a", "ref[pairs.a, 1..count]")
        .containsEntry("b", "ref[pairs.b, 1..count]");
    assertThat(in.warnings()).isEmpty();
    Map<String, DataStructure> loaded = loadAll(in, dir);
    assertThat(loaded.keySet()).containsExactly("accounts", "ledgers", "pairs", "pair_refs");
  }
}
