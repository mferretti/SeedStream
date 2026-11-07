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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code CREATE UNIQUE INDEX} as a uniqueness key (#450): a plain unique index yields what the same
 * table with an inline {@code UNIQUE (...)} yields. Tests named {@code guard...} already pass on
 * the code before #450 (silently-ignored statements, no false positives); every other test fails
 * there.
 */
class DdlInspectorUniqueIndexTest {

  private static final String INT = "int[1..999999]";
  private static final String UNIQ = "unique[1..count]";
  private static final String T = "CREATE TABLE t (id int PRIMARY KEY, c int, d int);\n";
  private static final NestingOptions NEST = NestingOptions.parse("auto", null);

  // ------------------------------------------------------- key applied, all spellings

  /** Index statement variants, all of which must yield the key {@code (c)} on table {@code t}. */
  static Stream<String> sameKeyIndexes() {
    return Stream.of(
        "CREATE UNIQUE INDEX u ON t (c)",
        "CREATE UNIQUE INDEX u ON public.t (c)",
        "CREATE UNIQUE INDEX u ON T (C)",
        "CREATE UNIQUE INDEX \"u\" ON \"t\" (\"c\")",
        "CREATE UNIQUE INDEX u ON t (c ASC)",
        "CREATE UNIQUE INDEX u ON t (c DESC)",
        "CREATE UNIQUE INDEX u ON t (\"c\" ASC)",
        "CREATE UNIQUE INDEX IF NOT EXISTS u ON t (c)",
        "CREATE UNIQUE INDEX CONCURRENTLY u ON t (c)",
        "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS u ON t (c)",
        "CREATE UNIQUE INDEX u ON t USING btree (c)",
        "CREATE UNIQUE INDEX u ON ONLY public.t USING btree (c)",
        "CREATE UNIQUE INDEX u USING BTREE ON t (c)",
        "CREATE UNIQUE INDEX u ON t (c) USING BTREE",
        "CREATE UNIQUE INDEX u ON t (c) INCLUDE (d)",
        "CREATE UNIQUE INDEX u ON t (c) NULLS NOT DISTINCT",
        "CREATE UNIQUE INDEX u ON t (c) NULLS DISTINCT",
        "CREATE UNIQUE CLUSTERED INDEX u ON t (c)",
        "CREATE UNIQUE NONCLUSTERED INDEX u ON t (c)",
        "CREATE UNIQUE INDEX u ON t (c) WITH (fillfactor = 70)",
        "CREATE UNIQUE NONCLUSTERED INDEX u ON t (c ASC) WITH (PAD_INDEX = OFF) ON [PRIMARY]",
        "CREATE UNIQUE INDEX u ON t (c) TABLESPACE ts1",
        "CREATE UNIQUE INDEX u ON t (c) ALGORITHM=INPLACE LOCK=NONE",
        "CREATE UNIQUE INDEX u ON t (c) PCTFREE 10 INITRANS 2 MAXTRANS 255 COMPUTE STATISTICS"
            + " STORAGE(INITIAL 65536 NEXT 1048576 MINEXTENTS 1 MAXEXTENTS 2147483645)"
            + " TABLESPACE users LOCAL ONLINE",
        "CREATE UNIQUE INDEX u ON t (c) REVERSE",
        "CREATE UNIQUE INDEX u ON t (c)\n  -- trailing comment WHERE x\n");
  }

  @ParameterizedTest
  @MethodSource("sameKeyIndexes")
  void shouldApplySingleColumnKeyForEveryIndexSpelling(String index, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + index + ";\n");
    Inspection flat =
        inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, c int, d int, UNIQUE (c));");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", UNIQ), entry("d", INT));
    assertSameAsFlat(in, flat, "t");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldApplyIndexBeforeTheCreateTable(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE UNIQUE INDEX u ON t (c);\n" + T);

    assertThat(datatypesOf(in, "t")).containsEntry("c", UNIQ).containsEntry("d", INT);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldApplyIndexBeforeAndAfterAlterTable(@TempDir Path dir) {
    String alter = "ALTER TABLE t ADD CONSTRAINT t_pk PRIMARY KEY (id);\n";
    String create = "CREATE TABLE t (id int, c int, d int);\n";
    String index = "CREATE UNIQUE INDEX u ON t (c);\n";
    Inspection before = inspectDdl(dir, index + create + alter);
    Inspection between = inspectDdl(dir, create + index + alter);
    Inspection after = inspectDdl(dir, create + alter + index);
    Inspection flat =
        inspectDdl(dir, create + alter + "ALTER TABLE t ADD CONSTRAINT uq UNIQUE (c);\n");

    assertThat(datatypesOf(flat, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", UNIQ), entry("d", INT));
    for (Inspection in : List.of(before, between, after)) {
      assertSameAsFlat(in, flat, "t");
      assertThat(in.warnings()).isEmpty();
    }
  }

  @Test
  void shouldMatchQuotedMixedCaseNames(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE \"MyTab\" (\"Id\" int PRIMARY KEY, \"MyCol\" int);\n"
                + "CREATE UNIQUE INDEX \"Ux\" ON \"MyTab\" (\"MyCol\");\n");
    Inspection flat =
        inspectDdl(
            dir,
            "CREATE TABLE \"MyTab\" (\"Id\" int PRIMARY KEY, \"MyCol\" int, UNIQUE (\"MyCol\"));\n");

    String table = in.structures().get(0).getName();
    assertThat(datatypesOf(in, table)).containsEntry("MyCol", UNIQ);
    assertSameAsFlat(in, flat, table);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldMatchSchemaQualifiedUpperCaseNames(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE \"NORTH\".\"ITEMS\" (\"ID\" NUMBER(9,0), \"CODE\" NUMBER(9,0));\n"
                + "CREATE UNIQUE INDEX \"NORTH\".\"UIDX_CODE\" ON \"NORTH\".\"ITEMS\" (\"CODE\");\n");

    assertThat(datatypesOf(in, "items")).containsEntry("CODE", UNIQ).containsEntry("ID", INT);
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldMatchSqlServerBracketedNames(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE [dbo].[Items] ([Id] int PRIMARY KEY, [Code] int);\n"
                + "CREATE UNIQUE NONCLUSTERED INDEX [UX_Code] ON [dbo].[Items] ([Code] ASC)"
                + " WITH (PAD_INDEX = OFF) ON [PRIMARY];\n");
    Inspection flat =
        inspectDdl(
            dir,
            "CREATE TABLE [dbo].[Items] ([Id] int PRIMARY KEY, [Code] int, UNIQUE ([Code]));\n");

    String table = in.structures().get(0).getName();
    assertThat(datatypesOf(in, table)).containsEntry("Code", UNIQ);
    assertSameAsFlat(in, flat, table);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldApplyIndexOnNumericAndIntegerOracleColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE \"N\".\"T\" (\"ID\" NUMBER(9,0), \"CUSTOMER_CODE\" NUMBER(9,0));\n"
                + "CREATE UNIQUE INDEX \"N\".\"UIDX_T_CODE\" ON \"N\".\"T\" (\"CUSTOMER_CODE\")\n"
                + "  PCTFREE 10 INITRANS 2 MAXTRANS 255 COMPUTE STATISTICS\n"
                + "  STORAGE(INITIAL 65536 NEXT 1048576 MINEXTENTS 1 MAXEXTENTS 2147483645\n"
                + "  PCTINCREASE 0 FREELISTS 1 FREELIST GROUPS 1 BUFFER_POOL DEFAULT)\n"
                + "  TABLESPACE \"N\" ;\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("ID", INT), entry("CUSTOMER_CODE", UNIQ));
  }

  // ------------------------------------------------------- text columns: not enforced

  @Test
  void shouldWarnNotEnforcedLikeInlineUniqueForTextColumn(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, d varchar(10));\nCREATE UNIQUE INDEX u ON t (d);\n");
    Inspection flat =
        inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, d varchar(10), UNIQUE (d));\n");

    assertThat(datatypesOf(in, "t")).containsEntry("d", "char[1..10]");
    assertSameAsFlat(in, flat, "t");
    assertThat(in.comments().get("t")).containsKey("d");
    assertThat(in.warnings())
        .singleElement()
        .asString()
        .contains("t")
        .contains("UNIQUE(d)")
        .contains("not enforced");
  }

  // ------------------------------------------------------- composite keys

  @Test
  void shouldApplyCompositeKeyLikeInlineUnique(@TempDir Path dir) {
    String create = "CREATE TABLE t (id int PRIMARY KEY, a int, b int, c int);\n";
    Inspection in = inspectDdl(dir, create + "CREATE UNIQUE INDEX u ON t (a, b);\n");
    Inspection flat =
        inspectDdl(
            dir, "CREATE TABLE t (id int PRIMARY KEY, a int, b int, c int, UNIQUE (a, b));\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("id", "serial"),
            entry("a", "unique[uq1, 1..count]"),
            entry("b", "unique[uq1, 1..count]"),
            entry("c", INT));
    assertSameAsFlat(in, flat, "t");
    loadAll(in, dir);
  }

  @Test
  void shouldIgnoreSortDirectionInCompositeKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (a int, b int);\nCREATE UNIQUE INDEX u ON t (a DESC, \"b\" ASC);\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("a", "unique[uq1, 1..count]"), entry("b", "unique[uq1, 1..count]"));
  }

  @Test
  void shouldNumberCompositeGroupsAcrossIndexesAndConstraints(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (a int, b int, c int, d int, UNIQUE (a, b));\n"
                + "CREATE UNIQUE INDEX u ON t (c, d);\n");
    Inspection flat =
        inspectDdl(
            dir, "CREATE TABLE t (a int, b int, c int, d int, UNIQUE (a, b), UNIQUE (c, d));\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("a", "unique[uq1, 1..count]"),
            entry("b", "unique[uq1, 1..count]"),
            entry("c", "unique[uq2, 1..count]"),
            entry("d", "unique[uq2, 1..count]"));
    assertSameAsFlat(in, flat, "t");
  }

  @Test
  void shouldWarnWhenIndexOverlapsAnotherKeyLikeInline(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (a int, b int, c int, d int, UNIQUE (a, b));\n"
                + "CREATE UNIQUE INDEX u ON t (b, c);\n");
    Inspection flat =
        inspectDdl(
            dir, "CREATE TABLE t (a int, b int, c int, d int, UNIQUE (a, b), UNIQUE (b, c));\n");

    assertSameAsFlat(in, flat, "t");
    assertThat(in.warnings()).hasSameSizeAs(flat.warnings()).anyMatch(w -> w.contains("t.b"));
  }

  // ------------------------------------------------------- foreign keys

  @Test
  void shouldMakeUniqueForeignKeyColumnAUniqueReference(@TempDir Path dir) {
    String parent = "CREATE TABLE p (id int PRIMARY KEY);\n";
    Inspection in =
        inspectDdl(
            dir,
            parent
                + "CREATE TABLE t (id int PRIMARY KEY, p_id int,"
                + " FOREIGN KEY (p_id) REFERENCES p(id));\n"
                + "CREATE UNIQUE INDEX u ON t (p_id);\n");
    Inspection flat =
        inspectDdl(
            dir,
            parent
                + "CREATE TABLE t (id int PRIMARY KEY, p_id int,"
                + " FOREIGN KEY (p_id) REFERENCES p(id), UNIQUE (p_id));\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("id", "serial"), entry("p_id", "ref[p.id, 1..count, unique]"));
    assertSameAsFlat(in, flat, "t");
    assertThat(in.warnings()).isEmpty();
    loadAll(in, dir);
  }

  @Test
  void shouldMakeUniqueIndexBeforeAlterForeignKeyAUniqueReference(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE UNIQUE INDEX u ON t (p_id);\n"
                + "CREATE TABLE p (id int PRIMARY KEY);\n"
                + "CREATE TABLE t (id int PRIMARY KEY, p_id int);\n"
                + "ALTER TABLE t ADD CONSTRAINT fk FOREIGN KEY (p_id) REFERENCES p (id);\n");

    assertThat(datatypesOf(in, "t")).containsEntry("p_id", "ref[p.id, 1..count, unique]");
  }

  @Test
  void shouldNestUniqueIndexForeignKeyAsOneToOne(@TempDir Path dir) {
    String indexed =
        """
        CREATE TABLE app_user (id BIGINT PRIMARY KEY, name VARCHAR(80));
        CREATE TABLE profile (id BIGINT PRIMARY KEY, user_id BIGINT, bio VARCHAR(200),
          CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES app_user(id));
        CREATE UNIQUE INDEX ux_profile_user ON profile (user_id);
        """;
    String inline =
        """
        CREATE TABLE app_user (id BIGINT PRIMARY KEY, name VARCHAR(80));
        CREATE TABLE profile (id BIGINT PRIMARY KEY, user_id BIGINT UNIQUE, bio VARCHAR(200),
          CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES app_user(id));
        """;
    Inspection in = inspectDdl(dir, indexed, NEST, false);
    Inspection flat = inspectDdl(dir, inline, NEST, false);

    assertThat(datatypesOf(flat, "app_user")).containsEntry("profile", "object[profile]");
    assertThat(datatypesOf(in, "app_user")).containsEntry("profile", "object[profile]");
    assertThat(datatypesOf(in, "profile")).doesNotContainKey("user_id");
    assertSameAsFlat(in, flat, "app_user");
    assertSameAsFlat(in, flat, "profile");
  }

  // ------------------------------------------------------- same key as PK / UNIQUE counts once

  @Test
  void guardNotDoubleCountIndexOnInlinePrimaryKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE t (id int PRIMARY KEY, c int);\nCREATE UNIQUE INDEX u ON t (id);\n");
    Inspection withoutIndex = inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, c int);\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", INT));
    assertSameAsFlat(in, withoutIndex, "t");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardNotDoubleCountOracleIndexWithAlterPrimaryKeyUsingIndex(@TempDir Path dir) {
    String table = "CREATE TABLE \"N\".\"T\" (\"ID\" NUMBER(9,0) NOT NULL, \"C\" NUMBER(9,0));\n";
    String alter =
        "ALTER TABLE \"N\".\"T\" ADD CONSTRAINT \"PK_T\" PRIMARY KEY (\"ID\")\n"
            + "  USING INDEX PCTFREE 10 INITRANS 2 MAXTRANS 255 COMPUTE STATISTICS\n"
            + "  TABLESPACE \"N\" ENABLE;\n";
    String index =
        "CREATE UNIQUE INDEX \"N\".\"PK_T\" ON \"N\".\"T\" (\"ID\") PCTFREE 10 TABLESPACE \"N\" ;\n";
    Inspection in = inspectDdl(dir, table + index + alter);
    Inspection withoutIndex = inspectDdl(dir, table + alter);

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("ID", "serial"), entry("C", INT));
    assertSameAsFlat(in, withoutIndex, "t");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardNotRenameCompositeGroupWhenIndexRepeatsPrimaryKey(@TempDir Path dir) {
    String create = "CREATE TABLE t (a int, b int, c int, PRIMARY KEY (a, b));\n";
    Inspection withoutIndex = inspectDdl(dir, create);

    for (String index : List.of("(a, b)", "(b, a)", "(b DESC, a)")) {
      Inspection in = inspectDdl(dir, create + "CREATE UNIQUE INDEX u ON t " + index + ";\n");

      assertThat(datatypesOf(in, "t").entrySet())
          .containsExactlyInAnyOrder(
              entry("a", "unique[pk, 1..count]"),
              entry("b", "unique[pk, 1..count]"),
              entry("c", INT));
      assertSameAsFlat(in, withoutIndex, "t");
      assertThat(in.warnings()).as(index).isEmpty();
    }
  }

  @Test
  void guardCountInlineUniqueAndSameIndexOnce(@TempDir Path dir) {
    String create = "CREATE TABLE t (id int PRIMARY KEY, c int, UNIQUE (c));\n";
    Inspection in = inspectDdl(dir, create + "CREATE UNIQUE INDEX u ON t (c);\n");
    Inspection withoutIndex = inspectDdl(dir, create);

    assertThat(datatypesOf(in, "t")).containsEntry("c", UNIQ);
    assertSameAsFlat(in, withoutIndex, "t");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardCountAlterUniqueAndSameIndexOnce(@TempDir Path dir) {
    String ddl =
        "CREATE TABLE t (a int, b int, c int);\n"
            + "ALTER TABLE t ADD CONSTRAINT uq UNIQUE (a, b) USING INDEX u;\n";
    Inspection in = inspectDdl(dir, ddl + "CREATE UNIQUE INDEX u ON t (a, b);\n");
    Inspection withoutIndex = inspectDdl(dir, ddl);

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("a", "unique[uq1, 1..count]"),
            entry("b", "unique[uq1, 1..count]"),
            entry("c", INT));
    assertSameAsFlat(in, withoutIndex, "t");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardWarnOnceForTextColumnWhenUniqueAndSameIndexRepeat(@TempDir Path dir) {
    String create = "CREATE TABLE t (id int PRIMARY KEY, d varchar(10), UNIQUE (d));\n";
    Inspection in = inspectDdl(dir, create + "CREATE UNIQUE INDEX u ON t (d);\n");
    Inspection withoutIndex = inspectDdl(dir, create);

    assertThat(withoutIndex.warnings()).hasSize(1);
    assertThat(in.warnings()).isEqualTo(withoutIndex.warnings());
    assertSameAsFlat(in, withoutIndex, "t");
  }

  @Test
  void shouldCountTwoIndexesWithTheSameKeyOnce(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, T + "CREATE UNIQUE INDEX u1 ON t (c);\nCREATE UNIQUE INDEX u2 ON t (c DESC);\n");
    Inspection flat =
        inspectDdl(dir, "CREATE TABLE t (id int PRIMARY KEY, c int, d int, UNIQUE (c));\n");

    assertThat(datatypesOf(in, "t")).containsEntry("c", UNIQ);
    assertSameAsFlat(in, flat, "t");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldCountTwoCompositeIndexesWithTheSameKeyOnce(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (a int, b int);\n"
                + "CREATE UNIQUE INDEX u1 ON t (a, b);\nCREATE UNIQUE INDEX u2 ON t (a, b);\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("a", "unique[uq1, 1..count]"), entry("b", "unique[uq1, 1..count]"));
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- partial / expression: no key, warning

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE UNIQUE INDEX uq_part ON t (c) WHERE d > 0",
        "CREATE UNIQUE INDEX uq_part ON t USING btree (c) WHERE (d IS NOT NULL)",
        "CREATE UNIQUE INDEX uq_part ON t (c) INCLUDE (d) WHERE d = 1",
        "CREATE UNIQUE NONCLUSTERED INDEX uq_part ON t (c) WHERE ([d] IS NOT NULL)"
            + " WITH (PAD_INDEX = OFF) ON [PRIMARY]"
      })
  void shouldSkipPartialIndexWithWarning(String index, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + index + ";\nCREATE TABLE other (x int);\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", INT), entry("d", INT));
    assertThat(datatypesOf(in, "other").entrySet()).containsExactlyInAnyOrder(entry("x", INT));
    assertThat(in.warnings()).singleElement().asString().contains("t").contains("uq_part");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE UNIQUE INDEX uq_expr ON t (lower(s))",
        "CREATE UNIQUE INDEX uq_expr ON t ((lower(s)))",
        "CREATE UNIQUE INDEX uq_expr ON t (UPPER(\"S\"))",
        "CREATE UNIQUE INDEX uq_expr ON t (a, lower(s))",
        "CREATE UNIQUE INDEX uq_expr ON t (lower(s), a)",
        "CREATE UNIQUE INDEX uq_expr ON t (a, (b + 1))",
        "CREATE UNIQUE INDEX uq_expr ON t (s(10))",
        "CREATE UNIQUE INDEX uq_expr ON t (a, s(10))"
      })
  void shouldSkipExpressionOrPrefixIndexWithWarning(String index, @TempDir Path dir) {
    String create = "CREATE TABLE t (a int, b int, s varchar(40), \"S\" varchar(40));\n";
    Inspection in = inspectDdl(dir, create + index + ";\n");
    Inspection without = inspectDdl(dir, create);

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("a", INT), entry("b", INT), entry("s", "char[1..40]"), entry("S", "char[1..40]"));
    assertSameAsFlat(in, without, "t");
    assertThat(in.warnings()).singleElement().asString().contains("t").contains("uq_expr");
  }

  // ------------------------------------------------------- missing column / table, unparseable

  @Test
  void shouldWarnWhenIndexColumnIsNotInTheTable(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + "CREATE UNIQUE INDEX u ON t (c, ghost);\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", INT), entry("d", INT));
    assertThat(in.warnings()).singleElement().asString().contains("t").contains("ghost");
  }

  @Test
  void shouldWarnWhenIndexTableIsNotInTheScript(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + "CREATE UNIQUE INDEX uq_orphan ON nowhere (c);\n");

    assertThat(datatypesOf(in, "t")).containsEntry("c", INT);
    assertThat(in.warnings()).singleElement().asString().contains("nowhere").contains("uq_orphan");
  }

  @Test
  void shouldNeverAbortStrictModeOnUnparseableUniqueIndex(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, T + "CREATE UNIQUE INDEX uq_broken ON t c;\nCREATE TABLE other (x int);\n");

    assertThat(datatypesOf(in, "t")).containsEntry("c", INT);
    assertThat(datatypesOf(in, "other").entrySet()).containsExactlyInAnyOrder(entry("x", INT));
    assertThat(in.warnings()).anyMatch(w -> w.contains("uq_broken"));
    loadAll(in, dir);
  }

  @Test
  void shouldKeepPlainKeyWhenVendorTailDefeatsTheSqlParser(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            T
                + "CREATE UNIQUE INDEX u ON t (c) FOO_VENDOR_OPTION (bar = 1) BAZ;\n"
                + "CREATE TABLE other (x int);\n");

    assertThat(datatypesOf(in, "t")).containsEntry("c", UNIQ);
    assertThat(datatypesOf(in, "other")).containsEntry("x", INT);
  }

  // ------------------------------------------------------- inheritance

  @Test
  void shouldApplyIndexOnInheritedColumnToTheChild(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (x int);\nCREATE TABLE b (y int) INHERITS (a);\n"
                + "CREATE UNIQUE INDEX u ON b (x);\n");
    Inspection flat = inspectDdl(dir, "CREATE TABLE b (x int, y int, UNIQUE (x));\n");

    assertThat(datatypesOf(in, "b").entrySet())
        .containsExactlyInAnyOrder(entry("x", UNIQ), entry("y", INT));
    assertThat(datatypesOf(in, "a").entrySet()).containsExactlyInAnyOrder(entry("x", INT));
    assertSameAsFlat(in, flat, "b");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldNotApplyParentIndexToChildren(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE UNIQUE INDEX u ON a (x);\n"
                + "CREATE TABLE a (x int);\nCREATE TABLE b (y int) INHERITS (a);\n");

    assertThat(datatypesOf(in, "a").entrySet()).containsExactlyInAnyOrder(entry("x", UNIQ));
    assertThat(datatypesOf(in, "b").entrySet())
        .containsExactlyInAnyOrder(entry("x", INT), entry("y", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- silently ignored (guards)

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE INDEX i ON t (c)",
        "CREATE BITMAP INDEX i ON t (c)",
        "CREATE FULLTEXT INDEX i ON t (c)",
        "CREATE INDEX i ON t (c) /* UNIQUE */",
        "CREATE INDEX i ON t (c) -- UNIQUE INDEX",
        "-- CREATE UNIQUE INDEX z ON t (c);"
      })
  void guardPlainIndexesAreSilentlyIgnored(String index, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + index + ";\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", INT), entry("d", INT));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardUniqueIndexKeywordsInLiteralsAndCommentsDoNotCreateKeys(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "-- WHERE x, CREATE UNIQUE INDEX in a comment\n"
                + "/* WHERE */\n"
                + "CREATE TABLE t (id int PRIMARY KEY, c int COMMENT 'CREATE UNIQUE INDEX u ON t (c)',"
                + " d varchar(20) DEFAULT 'WHERE UNIQUE INDEX');\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(
            entry("id", "serial"), entry("c", INT), entry("d", "char[1..20]"));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void guardRoutineBodyContainingCreateUniqueIndexIsNotAKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            T
                + "CREATE FUNCTION make_idx() RETURNS void AS $$\n"
                + "BEGIN\n  CREATE UNIQUE INDEX in_body ON t (c);\nEND;\n"
                + "$$ LANGUAGE plpgsql;\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", INT), entry("d", INT));
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- no false positives on real keys

  @Test
  void shouldIgnoreWhereInCommentsAfterAPlainUniqueIndex(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            T
                + "CREATE UNIQUE INDEX u1 ON t (c) -- WHERE x\n;\n"
                + "CREATE UNIQUE INDEX u2 ON t (d) /* WHERE */;\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("c", UNIQ), entry("d", UNIQ));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldNotTreatQuotedIdentifierNamedWhereAsPartialIndex(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int PRIMARY KEY, \"where\" int);\n"
                + "CREATE UNIQUE INDEX u ON t (\"where\");\n");

    assertThat(datatypesOf(in, "t").entrySet())
        .containsExactlyInAnyOrder(entry("id", "serial"), entry("where", UNIQ));
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldNotTreatIndexNamedWhereOrCommentedExpressionAsSkipped(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, T + "CREATE UNIQUE INDEX \"where\" ON t (c /* lower(c) */);\n");

    assertThat(datatypesOf(in, "t")).containsEntry("c", UNIQ);
    assertThat(in.warnings()).isEmpty();
  }

  // ------------------------------------------------------- Oracle Northwind acceptance

  @Test
  void shouldApplyUidxKeysOfTheOracleNorthwindExport(@TempDir Path dir) {
    Inspection in = oracleNorthwind();

    assertThat(datatypesOf(in, "customers")).containsEntry("CUSTOMER_CODE", "char[1..5]");
    assertThat(datatypesOf(in, "categories")).containsEntry("CATEGORY_NAME", "char[1..15]");
    // both UIDX_* keys are on text columns: the key cannot be enforced, exactly like inline UNIQUE
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("customers")
                    && w.contains("UNIQUE(CUSTOMER_CODE)")
                    && w.contains("not enforced"))
        .anyMatch(
            w ->
                w.contains("categories")
                    && w.contains("UNIQUE(CATEGORY_NAME)")
                    && w.contains("not enforced"));
    assertThat(in.comments().get("customers")).containsKey("CUSTOMER_CODE");
    assertThat(in.comments().get("categories")).containsKey("CATEGORY_NAME");
    assertThat(loadAll(in, dir)).hasSize(8);
  }

  @Test
  void shouldMatchInlineUniqueForTheOracleUidxColumns(@TempDir Path dir) {
    Inspection in = oracleNorthwind();
    Inspection flat =
        inspectDdl(
            dir,
            "CREATE TABLE \"NORTHWIND\".\"CUSTOMERS\" (\"CUSTOMER_ID\" NUMBER(9,0) NOT NULL,"
                + " \"CUSTOMER_CODE\" VARCHAR2(5 BYTE) NOT NULL, UNIQUE (\"CUSTOMER_CODE\"));\n");

    assertThat(datatypesOf(in, "customers").get("CUSTOMER_CODE"))
        .isEqualTo(datatypesOf(flat, "customers").get("CUSTOMER_CODE"));
    assertThat(in.comments().get("customers").get("CUSTOMER_CODE"))
        .isEqualTo(flat.comments().get("customers").get("CUSTOMER_CODE"));
  }

  @Test
  void guardOraclePkIndexesChangeNothing(@TempDir Path dir) {
    Inspection in = oracleNorthwind();

    assertThat(datatypesOf(in, "customers")).containsEntry("CUSTOMER_ID", "serial");
    assertThat(datatypesOf(in, "categories")).containsEntry("CATEGORY_ID", "serial");
    assertThat(datatypesOf(in, "order_details"))
        .containsEntry("ORDER_ID", "ref[orders.order_id, 1..count, unique=pk]")
        .containsEntry("PRODUCT_ID", "ref[products.product_id, 1..count, unique=pk]");
    assertThat(in.warnings()).noneMatch(w -> w.contains("also in") || w.contains("PK_"));
    assertThat(in.comments().values().stream().flatMap(m -> m.keySet().stream()))
        .doesNotContain("CUSTOMER_ID", "CATEGORY_ID", "SHIPPER_ID", "ORDER_ID", "PRODUCT_ID");
  }

  // ------------------------------------------------------- helpers

  private static Inspection oracleNorthwind() {
    return new DdlInspector()
        .inspect(
            Path.of("src/test/resources/northwind/oracle/northwind-oracle-schema.sql"),
            NestingOptions.none(),
            true);
  }

  /** Datatypes (in order) and review comments of {@code table} equal in both inspections. */
  private static void assertSameAsFlat(Inspection actual, Inspection expected, String table) {
    assertThat(datatypesOf(actual, table).entrySet())
        .containsExactlyElementsOf(datatypesOf(expected, table).entrySet());
    assertThat(actual.comments().getOrDefault(table, Map.of()))
        .isEqualTo(expected.comments().getOrDefault(table, Map.of()));
  }
}
