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
import static com.datagenerator.inspector.ddl.CheckTestSupport.comment;
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A single-column PRIMARY KEY / UNIQUE with a value list becomes {@code cycle[...]} (#456). Tests
 * named {@code guard...} already pass before the feature (behaviour that must not change); every
 * other test fails there.
 */
class DdlInspectorCycleKeyTest {

  private static final String CYCLE = "cycle[new,paid,done]";
  private static final String NOT_ENFORCED = "not enforced";

  // ------------------------------------------------------------------ keys with a value list

  static Stream<Arguments> cycleKeys() {
    return Stream.of(
        Arguments.of(
            "inline PK + inline CHECK",
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('new','paid','done')));"),
        Arguments.of(
            "inline UNIQUE + inline CHECK",
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5) UNIQUE"
                + " CHECK (kind IN ('new','paid','done')));"),
        Arguments.of(
            "table-level PK + table-level CHECK",
            "CREATE TABLE status (kind VARCHAR(5), PRIMARY KEY (kind),"
                + " CHECK (kind IN ('new','paid','done')));"),
        Arguments.of(
            "table-level UNIQUE + OR-equalities",
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5), UNIQUE (kind),"
                + " CHECK (kind = 'new' OR kind = 'paid' OR kind = 'done'));"),
        Arguments.of(
            "key and CHECK by ALTER",
            "CREATE TABLE status (kind VARCHAR(5) NOT NULL);\n"
                + "ALTER TABLE status ADD CONSTRAINT pk PRIMARY KEY (kind);\n"
                + "ALTER TABLE status ADD CONSTRAINT ck CHECK (kind IN ('new','paid','done'));"),
        Arguments.of(
            "CREATE UNIQUE INDEX",
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5),"
                + " CHECK (kind IN ('new','paid','done')));\n"
                + "CREATE UNIQUE INDEX ux ON status (kind);"),
        Arguments.of(
            "pg_dump ANY(ARRAY[...])",
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY, CONSTRAINT ck CHECK"
                + " (((kind)::text = ANY ((ARRAY['new'::character varying,"
                + " 'paid'::character varying, 'done'::character varying])::text[]))));"),
        Arguments.of(
            "SQL Server bracket form",
            "CREATE TABLE status ([kind] VARCHAR(5) PRIMARY KEY,"
                + " CHECK ([kind]='new' OR [kind]='paid' OR [kind]='done'));"),
        Arguments.of(
            "MySQL ENUM primary key",
            "CREATE TABLE status (kind ENUM('new','paid','done') PRIMARY KEY);"),
        Arguments.of(
            "MySQL ENUM unique key",
            "CREATE TABLE status (id int PRIMARY KEY, kind ENUM('new','paid','done'),"
                + " UNIQUE KEY uq (kind));"),
        Arguments.of(
            "MySQL SET unique key",
            "CREATE TABLE status (id int PRIMARY KEY, kind SET('new','paid','done') UNIQUE);"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cycleKeys")
  void shouldMapKeyWithValueListToCycle(String label, String ddl, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, ddl);

    assertThat(datatypesOf(in, "status")).containsEntry("kind", CYCLE);
    assertThat(in.warnings())
        .as("warnings for %s", label)
        .singleElement()
        .asString()
        .contains("status")
        .contains("kind")
        .contains("3")
        .doesNotContain(NOT_ENFORCED);
    assertThat(loadAll(in, dir)).containsKey("status");
  }

  @Test
  void shouldNameTheKeyKindInTheCycleWarning(@TempDir Path dir) {
    Inspection pk =
        inspectDdl(
            dir, "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('a','b')));");
    Inspection uq =
        inspectDdl(
            dir,
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5) UNIQUE"
                + " CHECK (kind IN ('a','b')));");

    assertThat(datatypesOf(pk, "status")).containsEntry("kind", "cycle[a,b]");
    assertThat(datatypesOf(uq, "status")).containsEntry("kind", "cycle[a,b]");
    assertThat(pk.warnings()).singleElement().asString().contains("PRIMARY KEY");
    assertThat(uq.warnings()).singleElement().asString().contains("UNIQUE");
  }

  @Test
  void shouldKeepTheRuleVisibleInTheReviewComment(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY"
                + " CHECK (kind IN ('new','paid','done')));");

    assertThat(comment(in, "status", "kind"))
        .isNotNull()
        .contains("once")
        .contains("--count")
        .contains("3")
        .doesNotContain(NOT_ENFORCED);
  }

  @Test
  void shouldKeepOrderAndCountDistinctValuesOnly(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY"
                + " CHECK (kind IN ('z','a','z','m')));");

    assertThat(datatypesOf(in, "status")).containsEntry("kind", "cycle[z,a,m]");
    assertThat(in.warnings()).singleElement().asString().contains("3");
  }

  @Test
  void shouldMapSingleValueKeyToCycleWithCountOne(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('x')));");

    assertThat(datatypesOf(in, "status")).containsEntry("kind", "cycle[x]");
    assertThat(in.warnings())
        .singleElement()
        .asString()
        .contains("status")
        .contains("kind")
        .contains("1")
        .doesNotContain(NOT_ENFORCED);
    assertThat(loadAll(in, dir)).containsKey("status");
  }

  @Test
  void shouldMapParentOfAForeignKeyToCycleAndLeaveTheChildRef(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('new','paid')));\n"
                + "CREATE TABLE orders (id int PRIMARY KEY, st VARCHAR(5) REFERENCES status (kind));");

    assertThat(datatypesOf(in, "status")).containsEntry("kind", "cycle[new,paid]");
    assertThat(datatypesOf(in, "orders").get("st")).startsWith("ref[status.kind");
    assertThat(loadAll(in, dir)).containsKeys("status", "orders");
  }

  // ------------------------------------------------------------------ what must not change

  @Test
  void guardCompositeKeyMemberStaysEnumWithNotEnforcedWarning(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (a VARCHAR(5) CHECK (a IN ('x','y')), b VARCHAR(5),"
                + " PRIMARY KEY (a, b));");

    assertThat(datatypesOf(in, "status")).containsEntry("a", "enum[x,y]");
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("status") && w.contains(NOT_ENFORCED))
        .noneMatch(w -> w.contains("cycle"));
    assertThat(datatypesOf(in, "status").values()).noneMatch(d -> d.startsWith("cycle["));
  }

  @Test
  void guardCompositeUniqueMemberStaysEnum(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (id int PRIMARY KEY, a ENUM('x','y'), b VARCHAR(5),"
                + " UNIQUE (a, b));");

    assertThat(datatypesOf(in, "status")).containsEntry("a", "enum[x,y]");
    assertThat(in.warnings()).anyMatch(w -> w.contains("UNIQUE") && w.contains(NOT_ENFORCED));
  }

  @Test
  void guardNonKeyEnumStaysEnumWithoutWarning(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (id int PRIMARY KEY, kind VARCHAR(5) CHECK (kind IN ('a','b')),"
                + " m ENUM('p','q'));");

    assertThat(datatypesOf(in, "status"))
        .containsEntry("kind", "enum[a,b]")
        .containsEntry("m", "enum[p,q]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardForeignKeyColumnThatIsAlsoAKeyStaysRef(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('a','b')));\n"
                + "CREATE TABLE detail (kind VARCHAR(5) PRIMARY KEY REFERENCES status (kind)"
                + " CHECK (kind IN ('a','b')));");

    assertThat(datatypesOf(in, "detail").get("kind")).startsWith("ref[status.kind");
    assertThat(loadAll(in, dir)).containsKeys("status", "detail");
  }

  @Test
  void guardIntegerKeyWithValueListStaysSerial(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE status (n int PRIMARY KEY CHECK (n IN (1,2,3)));");

    assertThat(datatypesOf(in, "status")).containsEntry("n", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "status").values()).noneMatch(d -> d.startsWith("cycle["));
  }

  @Test
  void shouldEnforceTextKeysWithoutValueListAsNumericStrings(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE status (kind VARCHAR(5) PRIMARY KEY, other VARCHAR(5) UNIQUE);");

    assertThat(datatypesOf(in, "status").values()).noneMatch(d -> d.startsWith("cycle["));
    assertThat(datatypesOf(in, "status"))
        .containsEntry("kind", "serial[1..99999]")
        .containsEntry("other", "unique[1..count]");
    assertThat(in.warnings()).noneMatch(w -> w.contains(NOT_ENFORCED));
  }

  // ------------------------------------------------------------------ nesting

  @ParameterizedTest
  @ValueSource(strings = {"auto", "all"})
  void guardNestingKeepsEnumWithNotEnforcedWarningForEveryTable(String mode, @TempDir Path dir) {
    String ddl =
        "CREATE TABLE customers (id int PRIMARY KEY,"
            + " tier VARCHAR(5) UNIQUE CHECK (tier IN ('gold','blue')));\n"
            + "CREATE TABLE orders (id int PRIMARY KEY,"
            + " customer_id int REFERENCES customers(id),"
            + " st VARCHAR(5) UNIQUE CHECK (st IN ('new','paid')));\n"
            + "CREATE TABLE lookup (id int PRIMARY KEY,"
            + " lk VARCHAR(5) UNIQUE CHECK (lk IN ('u','v')));";

    Inspection in = inspectDdl(dir, ddl, NestingOptions.parse(mode, null), false);

    assertThat(in.structures()).isNotEmpty();
    in.structures()
        .forEach(
            s ->
                s.getData()
                    .forEach(
                        (field, def) ->
                            assertThat(def.getDatatype())
                                .as("%s.%s under --nest %s", s.getName(), field, mode)
                                .doesNotContain("cycle[")));
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("customers") && w.contains(NOT_ENFORCED))
        .anyMatch(w -> w.contains("lookup") && w.contains(NOT_ENFORCED))
        .noneMatch(w -> w.contains("cycle"));
  }

  // ------------------------------------------------------------------ INHERITS

  @Test
  void shouldKeepInheritedCopyEnumAndCycleTheKeyTheChildDeclares(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE base (kind VARCHAR(5) PRIMARY KEY CHECK (kind IN ('a','b')));\n"
                + "CREATE TABLE child (extra VARCHAR(5)) INHERITS (base);\n"
                + "CREATE TABLE child2 (tag VARCHAR(5) UNIQUE CHECK (tag IN ('p','q','r')))"
                + " INHERITS (base);");

    assertThat(datatypesOf(in, "base")).containsEntry("kind", "cycle[a,b]");
    assertThat(datatypesOf(in, "child")).containsEntry("kind", "enum[a,b]");
    assertThat(datatypesOf(in, "child2"))
        .containsEntry("kind", "enum[a,b]")
        .containsEntry("tag", "cycle[p,q,r]");
    assertThat(loadAll(in, dir)).containsKeys("base", "child", "child2");
  }
}
