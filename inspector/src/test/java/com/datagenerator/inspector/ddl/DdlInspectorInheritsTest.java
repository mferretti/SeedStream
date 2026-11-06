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
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PostgreSQL {@code INHERITS} (#451): a child yields what the same table written flat, with the
 * inherited columns declared in it and without the parent's keys, would yield. Tests without a
 * "guard" label fail on the code before #451.
 */
class DdlInspectorInheritsTest {

  private static final String INT = "int[1..999999]";
  private static final NestingOptions NEST = NestingOptions.parse("auto", null);

  // ---------------------------------------------------------------- columns

  @Test
  void shouldPrependParentColumnsToChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE a (x int);\nCREATE TABLE b (y int) INHERITS (a);\n");

    assertThat(datatypesOf(in, "b")).containsExactly(entry("x", INT), entry("y", INT));
    assertThat(datatypesOf(in, "a")).containsExactly(entry("x", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldMatchFlatEquivalentWithMixedTypes(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int, label varchar(30) NOT NULL, born date);
            CREATE TABLE b (y numeric(8,2), flag boolean) INHERITS (a);
            """);
    Inspection flat =
        inspectDdl(
            dir,
            "CREATE TABLE b (x int, label varchar(30) NOT NULL, born date,"
                + " y numeric(8,2), flag boolean);\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("label", "char[1..30]");
    loadAll(in, dir);
  }

  @Test
  void shouldHandlePgDumpChildWithNoOwnColumns(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE public.a (\n    x integer,\n    w integer\n);\n"
                + "CREATE TABLE public.e (\n)\nINHERITS (public.a);\n");

    assertThat(datatypesOf(in, "e")).containsExactly(entry("x", INT), entry("w", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldHandlePgDumpChildWithOwnColumns(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE public.parent (\n    x integer\n);\n"
                + "CREATE TABLE public.child (\n    y integer\n)\nINHERITS (public.parent);\n");

    assertThat(datatypesOf(in, "child")).containsExactly(entry("x", INT), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldAcceptEmptyParenthesesChild(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE a (x int);\nCREATE TABLE e () INHERITS (a);\n");

    assertThat(datatypesOf(in, "e")).containsExactly(entry("x", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldResolveGrandparentsRecursively(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int) INHERITS (a);
            CREATE TABLE c (z int) INHERITS (b);
            """);

    assertThat(datatypesOf(in, "c"))
        .containsExactly(entry("x", INT), entry("y", INT), entry("z", INT));
    assertThat(datatypesOf(in, "b")).containsExactly(entry("x", INT), entry("y", INT));
  }

  @Test
  void shouldOrderMultipleParentsByInheritsClause(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int);
            CREATE TABLE c (z int) INHERITS (b, a);
            """);

    assertThat(datatypesOf(in, "c"))
        .containsExactly(entry("y", INT), entry("x", INT), entry("z", INT));
  }

  @Test
  void shouldKeepDiamondColumnOnceAtFirstPosition(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int) INHERITS (a);
            CREATE TABLE c (z int) INHERITS (a, b);
            """);

    assertThat(datatypesOf(in, "c"))
        .containsExactly(entry("x", INT), entry("y", INT), entry("z", INT));
  }

  @Test
  void shouldKeepSharedColumnAtFirstParentPositionWhenSecondParentDeclaresItToo(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (p int, x int);
            CREATE TABLE b (x int, q int);
            CREATE TABLE c (z int) INHERITS (a, b);
            """);

    assertThat(datatypesOf(in, "c"))
        .containsExactly(entry("p", INT), entry("x", INT), entry("q", INT), entry("z", INT));
  }

  @Test
  void shouldUseChildTypeAtParentPositionWhenChildRedeclaresColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int, y int, w int);
            CREATE TABLE b (z int, y varchar(5)) INHERITS (a);
            """);

    assertThat(datatypesOf(in, "b"))
        .containsExactly(
            entry("x", INT), entry("y", "char[1..5]"), entry("w", INT), entry("z", INT));
  }

  @Test
  void shouldTreatNamesDifferingOnlyByCaseAsSameColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int, "Val" int);
            CREATE TABLE b (z int, "VAL" varchar(5)) INHERITS (a);
            """);

    Map<String, String> b = datatypesOf(in, "b");
    assertThat(b).hasSize(3);
    assertThat(b.values()).containsExactly(INT, "char[1..5]", INT);
    assertThat(b.keySet()).first().isEqualTo("x");
  }

  @Test
  void shouldResolveParentDeclaredAfterChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE b (y int) INHERITS (a);\nCREATE TABLE a (x int);\n");

    assertThat(datatypesOf(in, "b")).containsExactly(entry("x", INT), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ---------------------------------------------------------------- keys are not inherited

  @Test
  void shouldNotInheritParentPrimaryKeyUniqueOrForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE other (id int PRIMARY KEY);
            CREATE TABLE a (id int PRIMARY KEY, code int UNIQUE, o int REFERENCES other(id));
            CREATE TABLE b (y int) INHERITS (a);
            """);
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (id int, code int, o int, y int);\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("id", INT);
    assertThat(datatypesOf(in, "a"))
        .containsEntry("id", "serial")
        .containsEntry("code", "unique[1..count]")
        .containsEntry("o", "ref[other.id, 1..count]");
    loadAll(in, dir);
  }

  @Test
  void shouldNotLeakParentAlterAddedPrimaryKeyIntoChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.a (id integer NOT NULL, code integer);
            CREATE TABLE public.b (
            )
            INHERITS (public.a);
            ALTER TABLE ONLY public.a ADD CONSTRAINT a_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY public.a ADD CONSTRAINT a_code_key UNIQUE (code);
            """);

    assertThat(datatypesOf(in, "a"))
        .containsEntry("id", "serial")
        .containsEntry("code", "unique[1..count]");
    assertThat(datatypesOf(in, "b")).containsExactly(entry("id", INT), entry("code", INT));
  }

  @Test
  void shouldNotCreateNestingEdgeFromParentForeignKeyInChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE cust (id int PRIMARY KEY);
            CREATE TABLE p (id int PRIMARY KEY, cust_id int REFERENCES cust(id));
            CREATE TABLE c (extra int) INHERITS (p);
            """,
            NEST,
            false);

    // c keeps the inherited cust_id as a plain column; only p nests under cust
    assertThat(datatypesOf(in, "c"))
        .containsExactly(entry("id", INT), entry("cust_id", INT), entry("extra", INT));
    long nestedC =
        datatypesOf(in, "cust").values().stream().filter(t -> t.contains("object[c]")).count();
    assertThat(nestedC).isZero();
    assertThat(datatypesOf(in, "cust").values()).anyMatch(t -> t.contains("object[p]"));
  }

  @Test
  void shouldNestChildOwnForeignKeyAsUsual(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE cust (id int PRIMARY KEY);
            CREATE TABLE p (id int, w int);
            CREATE TABLE c (cid int PRIMARY KEY, cust_id int REFERENCES cust(id)) INHERITS (p);
            """,
            NEST,
            false);
    Inspection flat =
        inspectDdl(
            dir,
            """
            CREATE TABLE cust (id int PRIMARY KEY);
            CREATE TABLE c (id int, w int, cid int PRIMARY KEY, cust_id int REFERENCES cust(id));
            """,
            NEST,
            false);

    assertSameAsFlat(in, flat, "c");
    assertThat(datatypesOf(in, "c")).containsKeys("id", "w").doesNotContainKey("cust_id");
    assertThat(datatypesOf(in, "cust")).isEqualTo(datatypesOf(flat, "cust"));
    assertThat(datatypesOf(in, "cust").values()).anyMatch(t -> t.contains("object[c]"));
  }

  @Test
  void shouldNotInheritIdentityProperty(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (id int GENERATED ALWAYS AS IDENTITY, n int);
            CREATE TABLE b (y int) INHERITS (a);
            """);
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (id int, n int, y int);\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("id", INT);
  }

  @Test
  void shouldInheritSerialLikeDeclaredSerialWithoutKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (id serial, n int);
            CREATE TABLE b (y int) INHERITS (a);
            """);
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (id serial, n int, y int);\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("id", "serial");
  }

  // ---------------------------------------------------------------- child keys on inherited
  // columns

  @Test
  void shouldApplyInlineTableLevelPrimaryKeyOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int, PRIMARY KEY (x)) INHERITS (a);
            """);
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (x int, y int, PRIMARY KEY (x));\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("x", "serial");
  }

  @Test
  void shouldApplyInlineTableLevelUniqueOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int, k int);
            CREATE TABLE b (y int, UNIQUE (k)) INHERITS (a);
            """);
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (x int, k int, y int, UNIQUE (k));\n");

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("k", "unique[1..count]");
  }

  @Test
  void shouldApplyInlineTableLevelForeignKeyOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE other (id int PRIMARY KEY);
            CREATE TABLE a (o int);
            CREATE TABLE b (y int, FOREIGN KEY (o) REFERENCES other(id)) INHERITS (a);
            """);
    Inspection flat =
        inspectDdl(
            dir,
            """
            CREATE TABLE other (id int PRIMARY KEY);
            CREATE TABLE b (o int, y int, FOREIGN KEY (o) REFERENCES other(id));
            """);

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b")).containsEntry("o", "ref[other.id, 1..count]");
  }

  @Test
  void shouldApplyAlterOnlyConstraintsOnInheritedColumns(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.other (id integer NOT NULL);
            ALTER TABLE ONLY public.other ADD CONSTRAINT other_pkey PRIMARY KEY (id);
            CREATE TABLE public.a (x integer NOT NULL, k integer, o integer);
            CREATE TABLE public.b (
                y integer
            )
            INHERITS (public.a);
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_pkey PRIMARY KEY (x);
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_k_key UNIQUE (k);
            ALTER TABLE ONLY public.b
                ADD CONSTRAINT b_o_fk FOREIGN KEY (o) REFERENCES public.other(id);
            """);
    Inspection flat =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.other (id integer NOT NULL);
            ALTER TABLE ONLY public.other ADD CONSTRAINT other_pkey PRIMARY KEY (id);
            CREATE TABLE public.b (x integer NOT NULL, k integer, o integer, y integer);
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_pkey PRIMARY KEY (x);
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_k_key UNIQUE (k);
            ALTER TABLE ONLY public.b
                ADD CONSTRAINT b_o_fk FOREIGN KEY (o) REFERENCES public.other(id);
            """);

    assertSameAsFlat(in, flat, "b");
    assertThat(datatypesOf(in, "b"))
        .containsEntry("x", "serial")
        .containsEntry("k", "unique[1..count]")
        .containsEntry("o", "ref[other.id, 1..count]");
    // the parent is untouched by the child's constraints
    assertThat(datatypesOf(in, "a")).containsEntry("x", INT).containsEntry("k", INT);
    loadAll(in, dir);
  }

  @Test
  void shouldApplyAlterSetDefaultNextvalOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.a (seq integer NOT NULL, n integer);
            CREATE TABLE public.b (
                y integer
            )
            INHERITS (public.a);
            ALTER TABLE ONLY public.b
                ALTER COLUMN seq SET DEFAULT nextval('public.b_seq_seq'::regclass);
            """);
    Inspection flat =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.b (seq integer NOT NULL, n integer, y integer);
            ALTER TABLE ONLY public.b
                ALTER COLUMN seq SET DEFAULT nextval('public.b_seq_seq'::regclass);
            """);

    assertSameAsFlat(in, flat, "b");
  }

  @Test
  void shouldResolveForeignKeyToKeyDeclaredByChildOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int, PRIMARY KEY (x)) INHERITS (a);
            CREATE TABLE r (id int PRIMARY KEY, bx int REFERENCES b(x));
            """);

    assertThat(datatypesOf(in, "r")).containsEntry("bx", "ref[b.x, 1..count]");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldResolveColumnlessForeignKeyToChildKeyOnInheritedColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (y int, PRIMARY KEY (x)) INHERITS (a);
            CREATE TABLE r (id int PRIMARY KEY, bx int REFERENCES b);
            """);

    assertThat(datatypesOf(in, "r")).containsEntry("bx", "ref[b.x, 1..count]");
    assertThat(in.warnings()).isEmpty();
  }

  // ---------------------------------------------------------------- missing parent / cycles

  @Test
  void shouldWarnAndKeepOwnColumnsWhenParentIsNotInScript(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE kid (y int) INHERITS (ghost);\n");

    assertThat(datatypesOf(in, "kid")).containsExactly(entry("y", INT));
    assertThat(in.warnings()).anyMatch(w -> w.contains("kid") && w.contains("ghost"));
  }

  @Test
  void shouldWarnAndSkipChildWithNoOwnColumnsWhenParentMissing(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE other (id int, n int);
            CREATE TABLE kid () INHERITS (ghost);
            """);

    assertThat(in.structures()).extracting(DataStructure::getName).containsExactly("other");
    assertThat(in.warnings()).anyMatch(w -> w.contains("kid") && w.contains("ghost"));
  }

  @Test
  void shouldTerminateOnInheritanceCycleKeepingOwnColumns(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE cyc_g (gx int) INHERITS (cyc_h);
            CREATE TABLE cyc_h (hx int) INHERITS (cyc_g);
            """);

    assertThat(datatypesOf(in, "cyc_g")).containsExactly(entry("gx", INT));
    assertThat(datatypesOf(in, "cyc_h")).containsExactly(entry("hx", INT));
    assertThat(in.warnings()).anyMatch(w -> w.contains("cyc_g") && w.contains("cyc_h"));
  }

  @Test
  void shouldTerminateOnSelfInheritance(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE selfy (sx int) INHERITS (selfy);\n");

    assertThat(datatypesOf(in, "selfy")).containsExactly(entry("sx", INT));
    assertThat(in.warnings()).anyMatch(w -> w.contains("selfy"));
  }

  // ---------------------------------------------------------------- parent name forms

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "CREATE TABLE public.par (x int)|public.par",
        "CREATE TABLE \"Parent\" (x int)|\"Parent\"",
        "CREATE TABLE \"Parent Table\" (x int)|\"Parent Table\"",
        "CREATE TABLE \"a,b\" (x int)|\"a,b\"",
        "CREATE TABLE MixedCase (x int)|MixedCase",
        "CREATE TABLE MixedCase (x int)|\"public\".MixedCase",
        "CREATE TABLE public.par (x int)|par",
      })
  void shouldMatchParentNamesLikeAlterTableTargets(
      String parentDdl, String inheritsName, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, parentDdl + ";\nCREATE TABLE kid (y int) INHERITS (" + inheritsName + ");\n");

    assertThat(datatypesOf(in, "kid")).containsExactly(entry("x", INT), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ---------------------------------------------------------------- clauses and modifiers

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE TABLE b (y int) INHERITS (a) WITH (fillfactor = 70)",
        "CREATE TABLE b (y int) INHERITS (a) TABLESPACE pg_default",
        "CREATE TABLE b (y int) INHERITS (a) WITH (fillfactor = 70) TABLESPACE pg_default",
        "CREATE TEMP TABLE b (y int) INHERITS (a)",
        "CREATE TEMPORARY TABLE b (y int) INHERITS (a)",
        "CREATE UNLOGGED TABLE b (y int) INHERITS (a)",
        "CREATE TABLE IF NOT EXISTS b (y int) INHERITS (a)",
        "CREATE TABLE b (y int) INHERITS (a, c)",
      })
  void shouldHandleClausesAndModifiersAroundInherits(String childDdl, @TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE a (x int);\nCREATE TABLE c (w int);\n" + childDdl + ";\n");

    List<String> names = List.copyOf(datatypesOf(in, "b").keySet());
    assertThat(names).startsWith("x").contains("y").doesNotHaveDuplicates();
    assertThat(names).containsSubsequence("x", "y");
    if (childDdl.endsWith("(a, c)")) {
      assertThat(names).containsExactly("x", "w", "y");
    } else {
      assertThat(names).containsExactly("x", "y");
    }
  }

  // ---------------------------------------------------------------- no false positives (guards)

  @Test
  void guardShouldNotTreatColumnNamedInheritsAsInheritance(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE a (x int);\nCREATE TABLE b (inherits int, y int);\n");

    assertThat(datatypesOf(in, "b")).containsExactly(entry("inherits", INT), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardShouldNotTreatTableNamedInheritsAsInheritance(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE a (x int);\nCREATE TABLE inherits (y int);\n");

    assertThat(datatypesOf(in, "inherits")).containsExactly(entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardShouldNotTreatLiteralCommentOrDefaultTextAsInheritance(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int);
            CREATE TABLE b (
                note varchar(20) DEFAULT 'INHERITS (a)', -- INHERITS (a)
                /* INHERITS (a) */
                y int COMMENT 'INHERITS (a)'
            );
            """);

    assertThat(datatypesOf(in, "b")).containsExactly(entry("note", "char[1..20]"), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardShouldNotTreatTrailingCommentAsInheritance(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (x int);\n"
                + "CREATE TABLE b (y int) -- INHERITS (a)\n;\n"
                + "CREATE TABLE c (z int) /* INHERITS (a) */;\n");

    assertThat(datatypesOf(in, "b")).containsExactly(entry("y", INT));
    assertThat(datatypesOf(in, "c")).containsExactly(entry("z", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardShouldLeaveTablesWithoutInheritsUnchanged(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (x int PRIMARY KEY);
            CREATE TABLE b (id int PRIMARY KEY, a_id int REFERENCES a(x), s varchar(5));
            CREATE TABLE c (LIKE a);
            """);

    assertThat(datatypesOf(in, "a")).containsExactly(entry("x", "serial"));
    assertThat(datatypesOf(in, "b"))
        .containsExactly(
            entry("id", "serial"), entry("a_id", "ref[a.x, 1..count]"), entry("s", "char[1..5]"));
  }

  @Test
  void guardShouldStillFailOnEmptyColumnListWithoutInherits(@TempDir Path dir) {
    assertThatThrownBy(() -> inspectDdl(dir, "CREATE TABLE t ();\nCREATE TABLE u (id int);\n"))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("Failed to parse 1 CREATE TABLE");
  }

  @Test
  void guardShouldNotChangePartitionOf(@TempDir Path dir) {
    String ddl =
        """
        CREATE TABLE m (id int, d date) PARTITION BY RANGE (d);
        CREATE TABLE m1 PARTITION OF m FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');
        """;
    Inspection in = inspectDdl(dir, ddl);

    assertThat(in.structures()).extracting(DataStructure::getName).contains("m");
    assertThat(datatypesOf(in, "m")).containsKeys("id", "d");
  }

  // ---------------------------------------------------------------- helpers

  /** Datatypes (in order) and review comments of {@code table} equal in both inspections. */
  private static void assertSameAsFlat(Inspection inherited, Inspection flat, String table) {
    assertThat(datatypesOf(inherited, table).entrySet())
        .containsExactlyElementsOf(datatypesOf(flat, table).entrySet());
    assertThat(inherited.comments().getOrDefault(table, Map.of()))
        .isEqualTo(flat.comments().getOrDefault(table, Map.of()));
  }
}
