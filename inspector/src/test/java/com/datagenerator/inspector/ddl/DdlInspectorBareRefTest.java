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
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #442: an FK column is emitted as the bare {@code ref[t.c]} form only when the engine can resolve
 * the pool from the parent's key (single-column FK, not a self-reference, a serial / unique parent
 * key, a top-level parent). Everything else keeps the explicit {@code 1..count} pool.
 */
class DdlInspectorBareRefTest {

  private static final NestingOptions NEST = NestingOptions.parse("auto", null);

  // ---------------------------------------------------------------- the three bare forms

  @Test
  void shouldEmitBareRefForPlainForeignKeyToSerialParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id INT PRIMARY KEY);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_id INT REFERENCES p(id));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("id", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id]");
    loadAll(in, dir);
  }

  /**
   * {@code ref[parent.c]} is the nested parent-record reference, so a plain FK to a table named
   * {@code parent} keeps its explicit pool; the unique form is not ambiguous and goes bare.
   */
  @Test
  void shouldKeepExplicitPoolForPlainForeignKeyToTableNamedParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE parent (id INT PRIMARY KEY);
            CREATE TABLE child (id INT PRIMARY KEY, parent_id INT REFERENCES parent(id));
            CREATE TABLE detail (parent_id INT PRIMARY KEY REFERENCES parent(id));
            """);
    loadAll(in, dir);

    assertThat(datatypesOf(in, "child")).containsEntry("parent_id", "ref[parent.id, 1..count]");
    assertThat(datatypesOf(in, "detail")).containsEntry("parent_id", "ref[parent.id, unique]");
  }

  @Test
  void shouldEmitBareUniqueRefForOneToOneForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id INT PRIMARY KEY);\n"
                + "CREATE TABLE d (p_id INT PRIMARY KEY REFERENCES p(id), info VARCHAR(10));\n");

    assertThat(datatypesOf(in, "d")).containsEntry("p_id", "ref[p.id, unique]");
    loadAll(in, dir);
  }

  @Test
  void shouldEmitBareUniqueGroupRefForCompositeKeyMembers(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (id INT PRIMARY KEY);\n"
                + "CREATE TABLE b (id INT PRIMARY KEY);\n"
                + "CREATE TABLE ab (a_id INT REFERENCES a(id), b_id INT REFERENCES b(id),"
                + " PRIMARY KEY (a_id, b_id));\n");

    assertThat(datatypesOf(in, "ab"))
        .containsEntry("a_id", "ref[a.id, unique=pk]")
        .containsEntry("b_id", "ref[b.id, unique=pk]");
    loadAll(in, dir);
  }

  // ---------------------------------------------------------------- parent key forms (rule 3)

  @Test
  void shouldEmitBareRefToSmallintSerialParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id SMALLINT PRIMARY KEY);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_id SMALLINT REFERENCES p(id));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("id", "serial[1..32767]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id]");
  }

  @Test
  void shouldEmitBareRefToBigintSerialParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id BIGINT PRIMARY KEY);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_id BIGINT REFERENCES p(id));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("id", "serial");
    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id]");
  }

  @Test
  void shouldEmitBareRefToUniqueColumnParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id INT PRIMARY KEY, code INT UNIQUE);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_code INT REFERENCES p(code));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("code", "unique[1..count]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_code", "ref[p.code]");
  }

  @Test
  void shouldEmitBareRefToTextPrimaryKeyParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (code VARCHAR(5) PRIMARY KEY);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_code VARCHAR(5) REFERENCES p(code));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("code", "serial[1..99999]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_code", "ref[p.code]");
  }

  @Test
  void shouldEmitBareRefToParentThatIsItselfAUniqueRefKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (id INT PRIMARY KEY);\n"
                + "CREATE TABLE b (a_id INT PRIMARY KEY REFERENCES a(id));\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, b_ref INT REFERENCES b(a_id));\n");

    assertThat(datatypesOf(in, "b")).containsEntry("a_id", "ref[a.id, unique]");
    assertThat(datatypesOf(in, "c")).containsEntry("b_ref", "ref[b.a_id]");
    loadAll(in, dir);
  }

  @Test
  void shouldResolveParentKeyDeclaredAfterTheChildAndByAlter(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE c (id INT PRIMARY KEY, p_id INT REFERENCES p(id));\n"
                + "CREATE TABLE p (id INT);\n"
                + "ALTER TABLE p ADD CONSTRAINT p_pk PRIMARY KEY (id);\n");

    assertThat(datatypesOf(in, "p")).containsEntry("id", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id]");
  }

  // ---------------------------------------------------------------- negatives (rules 1, 2, 3)

  @Test
  void shouldKeepExplicitPoolForSelfReference(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE emp (id INT PRIMARY KEY, boss INT REFERENCES emp(id));\n");

    assertThat(datatypesOf(in, "emp")).containsEntry("boss", "ref[emp.id, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolForMultiColumnForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE pairs (a INT NOT NULL, b INT NOT NULL, PRIMARY KEY (a, b));\n"
                + "CREATE TABLE refs (id INT PRIMARY KEY, x INT, y INT,"
                + " FOREIGN KEY (x, y) REFERENCES pairs (a, b));\n");

    assertThat(datatypesOf(in, "refs"))
        .containsEntry("x", "ref[pairs.a, 1..count]")
        .containsEntry("y", "ref[pairs.b, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolWhenParentKeyIsAColumnOfACompositeKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE pairs (a INT NOT NULL, b INT NOT NULL, PRIMARY KEY (a, b));\n"
                + "CREATE TABLE refs (id INT PRIMARY KEY, x INT REFERENCES pairs (a));\n");

    assertThat(datatypesOf(in, "pairs")).containsEntry("a", "unique[pk, 1..count]");
    assertThat(datatypesOf(in, "refs")).containsEntry("x", "ref[pairs.a, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolForCycleKeyedParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('new','paid')));\n"
                + "CREATE TABLE orders (id INT PRIMARY KEY, st VARCHAR(5) REFERENCES status(kind));\n");

    assertThat(datatypesOf(in, "status")).containsEntry("kind", "cycle[new,paid]");
    assertThat(datatypesOf(in, "orders")).containsEntry("st", "ref[status.kind, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolForUuidKeyedParent(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id UUID PRIMARY KEY);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_id UUID REFERENCES p(id));\n",
            NestingOptions.none(),
            true);

    assertThat(datatypesOf(in, "p")).containsEntry("id", "uuid");
    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolWhenParentColumnIsNotAKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id INT PRIMARY KEY, code INT);\n"
                + "CREATE TABLE c (id INT PRIMARY KEY, p_code INT REFERENCES p(code));\n");

    assertThat(datatypesOf(in, "p")).containsEntry("code", "int[1..999999]");
    assertThat(datatypesOf(in, "c")).containsEntry("p_code", "ref[p.code, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolWhenParentTableIsNotInTheScript(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE c (id INT PRIMARY KEY, p_id INT REFERENCES missing(id));\n",
            NestingOptions.none(),
            true);

    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[missing.id, 1..count]");
  }

  // ---------------------------------------------------------------- rule 4: nested parents

  @Test
  void shouldKeepExplicitPoolForRefsToAParentFoldedByNesting(@TempDir Path dir) {
    // b and x nest under a (first declared parent); x keeps a flat ref to b, which is folded
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (id INT PRIMARY KEY);\n"
                + "CREATE TABLE b (id INT PRIMARY KEY, a_id INT REFERENCES a(id));\n"
                + "CREATE TABLE x (id INT PRIMARY KEY, a_id INT REFERENCES a(id),"
                + " b_id INT REFERENCES b(id));\n",
            NEST,
            false);

    Set<String> folded = new HashSet<>();
    Matcher embedded = Pattern.compile("object\\[([a-z_0-9]+)\\]").matcher("");
    in.structures()
        .forEach(
            s ->
                s.getData()
                    .values()
                    .forEach(
                        def -> {
                          embedded.reset(def.getDatatype());
                          while (embedded.find()) {
                            folded.add(embedded.group(1));
                          }
                        }));
    assertThat(folded).as("fixture sanity: b and x are folded").containsExactlyInAnyOrder("b", "x");

    String flatRefToB = datatypesOf(in, "x").get("b_id");
    assertThat(flatRefToB).isEqualTo("ref[b.id, 1..count]");
  }

  @Test
  void shouldKeepBareRefForTopLevelParentWhenOtherTablesAreNested(@TempDir Path dir) {
    // invoice nests under customer; its flat ref to shipper (top level) is bare
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE customer (id INT PRIMARY KEY);\n"
                + "CREATE TABLE shipper (id INT PRIMARY KEY);\n"
                + "CREATE TABLE invoice (id INT PRIMARY KEY, customer_id INT REFERENCES customer(id),"
                + " shipper_id INT REFERENCES shipper(id));\n",
            NEST,
            false);

    assertThat(datatypesOf(in, "customer")).containsValue("array[object[invoice], 1..10]");
    assertThat(datatypesOf(in, "invoice")).containsEntry("shipper_id", "ref[shipper.id]");
  }
}
