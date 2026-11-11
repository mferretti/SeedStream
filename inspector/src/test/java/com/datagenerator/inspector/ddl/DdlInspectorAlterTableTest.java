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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Constraints declared with {@code ALTER TABLE} after the {@code CREATE TABLE} (#376): every form
 * the major exporters emit, the order/dedup/case corner cases, and nesting. Tests without a "guard"
 * label fail on the code before #376.
 */
class DdlInspectorAlterTableTest {

  private static final String INT = "int[1..999999]";
  private static final NestingOptions NEST = NestingOptions.parse("auto", null);

  // ---------------------------------------------------------------- AST forms

  @Test
  void shouldMapPrimaryKeyAddedByPgDumpAlterToSerial(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.items (id integer NOT NULL, n integer);
            ALTER TABLE ONLY public.items
                ADD CONSTRAINT items_pkey PRIMARY KEY (id);
            """);

    assertThat(datatypesOf(in, "items"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("n", INT);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldMapUniqueAddedByAlterToUniqueRange(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.items (id integer NOT NULL, code integer);
            ALTER TABLE ONLY public.items ADD CONSTRAINT items_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY public.items ADD CONSTRAINT items_code_key UNIQUE (code);
            """);

    assertThat(datatypesOf(in, "items"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("code", "unique[1..count]");
  }

  @Test
  void shouldEnforceTextUniqueAddedByAlterAsNumericStringsLikeInline(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id integer, sku varchar(20));
            ALTER TABLE t ADD CONSTRAINT t_sku_key UNIQUE (sku);
            """);

    assertThat(datatypesOf(in, "t")).containsEntry("sku", "unique[1..count]");
    assertThat(in.comments().get("t"))
        .containsEntry("sku", "text key generated as numeric strings");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldMapForeignKeyAddedByAlterToRef(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.customers (id integer PRIMARY KEY);
            CREATE TABLE public.orders (id integer PRIMARY KEY, customer_id integer);
            ALTER TABLE ONLY public.orders
                ADD CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id)
                REFERENCES public.customers(id);
            """);

    assertThat(datatypesOf(in, "orders")).containsEntry("customer_id", "ref[customers.id]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldApplyQuotedIdentifiersInAlter(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE "public"."customers" ("id" integer);
            CREATE TABLE "public"."orders" ("id" integer, "customer_id" integer);
            ALTER TABLE ONLY "public"."customers" ADD CONSTRAINT "c_pk" PRIMARY KEY ("id");
            ALTER TABLE ONLY "public"."orders" ADD CONSTRAINT "o_pk" PRIMARY KEY ("id");
            ALTER TABLE ONLY "public"."orders" ADD CONSTRAINT "o_fk" FOREIGN KEY ("customer_id")
                REFERENCES "public"."customers" ("id");
            """);

    assertThat(datatypesOf(in, "customers")).containsEntry("id", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "orders"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("customer_id", "ref[customers.id]");
  }

  @Test
  void shouldApplyAllAddClausesOfOneAlter(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE p (id integer PRIMARY KEY);
            CREATE TABLE c (id integer, p_id integer, code integer);
            ALTER TABLE c
                ADD CONSTRAINT c_pk PRIMARY KEY (id),
                ADD CONSTRAINT c_fk FOREIGN KEY (p_id) REFERENCES p (id),
                ADD CONSTRAINT c_uq UNIQUE (code);
            """);

    assertThat(datatypesOf(in, "c"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("p_id", "ref[p.id]")
        .containsEntry("code", "unique[1..count]");
  }

  @Test
  void shouldMapMysqlAddPrimaryKeyAndIgnorePlainKeyIndex(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id int NOT NULL, code int NOT NULL);
            ALTER TABLE t
              ADD PRIMARY KEY (id),
              ADD KEY ix_code (code);
            """);

    // a plain KEY is a (non-unique) index: neither unique nor a warning
    assertThat(datatypesOf(in, "t"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("code", INT);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldMapMysqlModifyAutoIncrementOfPrimaryKeyToSerial(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id int NOT NULL, name varchar(20));
            ALTER TABLE t ADD PRIMARY KEY (id);
            ALTER TABLE t MODIFY id int NOT NULL AUTO_INCREMENT;
            """);

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..2147483647]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldTreatMysqlModifyAutoIncrementOfPlainColumnLikeInlineAutoIncrement(@TempDir Path dir) {
    // equivalence (guard today): whatever inline does for a non-key AUTO_INCREMENT column
    Inspection inline =
        inspectDdl(
            dir,
            "CREATE TABLE t (id int NOT NULL PRIMARY KEY, seq int NOT NULL AUTO_INCREMENT, n int);\n");
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id int NOT NULL PRIMARY KEY, seq int NOT NULL, n int);
            ALTER TABLE t MODIFY seq int NOT NULL AUTO_INCREMENT;
            """);

    assertSameAsInline(altered, inline, "t");
  }

  @Test
  void shouldMapOraclePrimaryKeyWithUsingIndexClauseToSerial(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE "NORTHWIND"."CATS" ("CAT_ID" NUMBER(9,0), "NAME" VARCHAR2(15 BYTE));
              ALTER TABLE "NORTHWIND"."CATS" ADD CONSTRAINT "PK_CATS" PRIMARY KEY ("CAT_ID")
              USING INDEX PCTFREE 10 INITRANS 2 MAXTRANS 255 COMPUTE STATISTICS
              STORAGE(INITIAL 65536 NEXT 1048576 MINEXTENTS 1 MAXEXTENTS 2147483645
              PCTINCREASE 0 FREELISTS 1 FREELIST GROUPS 1 BUFFER_POOL DEFAULT)
              TABLESPACE "NORTHWIND"  ENABLE;
            """);

    assertThat(datatypesOf(in, "cats")).containsEntry("CAT_ID", "serial[1..999999999]");
  }

  @Test
  void shouldMapOracleForeignKeyWithTrailingEnableToRef(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE "NORTHWIND"."CATS" ("CAT_ID" NUMBER(9,0));
            CREATE TABLE "NORTHWIND"."PRODS" ("PROD_ID" NUMBER(9,0), "CAT_ID" NUMBER(9,0));
              ALTER TABLE "NORTHWIND"."CATS" ADD CONSTRAINT "PK_CATS" PRIMARY KEY ("CAT_ID") ENABLE;
              ALTER TABLE "NORTHWIND"."PRODS" ADD CONSTRAINT "FK_CAT" FOREIGN KEY ("CAT_ID")
                REFERENCES "NORTHWIND"."CATS" ("CAT_ID") ENABLE;
            """);

    assertThat(datatypesOf(in, "prods")).containsEntry("CAT_ID", "ref[cats.cat_id]");
  }

  // ---------------------------------------------------------------- rewrites

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DEFERRABLE",
        "DEFERRABLE INITIALLY DEFERRED",
        "DEFERRABLE INITIALLY IMMEDIATE",
        "INITIALLY DEFERRED",
        "NOT VALID",
        "ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED"
      })
  void shouldApplyForeignKeyWithConstraintAttributes(String attributes, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id integer PRIMARY KEY);\n"
                + "CREATE TABLE c (id integer PRIMARY KEY, p_id integer);\n"
                + "ALTER TABLE ONLY c ADD CONSTRAINT c_fk FOREIGN KEY (p_id) REFERENCES p(id) "
                + attributes
                + ";\n");

    assertThat(datatypesOf(in, "c")).containsEntry("p_id", "ref[p.id]");
    assertThat(in.warnings()).as(attributes).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"WITH CHECK", "WITH NOCHECK", "with check"})
  void shouldApplySqlServerWithCheckAddForeignKey(String withCheck, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE [dbo].[Region] ([RegionID] [int] NOT NULL)\n"
                + "GO\n"
                + "CREATE TABLE [dbo].[Territories] ([TerritoryID] [int] NOT NULL, [RegionID] [int])\n"
                + "GO\n"
                + "ALTER TABLE [dbo].[Territories] "
                + withCheck
                + " ADD CONSTRAINT [FK_T_R] FOREIGN KEY ([RegionID])"
                + " REFERENCES [dbo].[Region] ([RegionID])\n"
                + "GO\n"
                + "ALTER TABLE [dbo].[Territories] CHECK CONSTRAINT [FK_T_R]\n"
                + "GO\n",
            NestingOptions.none(),
            false);

    assertThat(datatypesOf(in, "territories"))
        .containsEntry("RegionID", "ref[region.region_id, 1..count]");
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CLUSTERED", "NONCLUSTERED", "nonclustered"})
  void shouldApplySqlServerClusteredPrimaryKeyWithFilegroup(String kind, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE [dbo].[Region] ([RegionID] [int] NOT NULL, [Code] [int])\n"
                + "GO\n"
                + "ALTER TABLE [dbo].[Region]\n"
                + "\tADD CONSTRAINT [PK_Region] PRIMARY KEY  "
                + kind
                + " \n"
                + "\t(\n\t\t[RegionID] ASC\n\t) ON [PRIMARY]\n"
                + "GO\n"
                + "ALTER TABLE [dbo].[Region]\n"
                + "\tADD CONSTRAINT [UQ_Region] UNIQUE "
                + kind
                + " ([Code]) ON [PRIMARY]\n"
                + "GO\n",
            NestingOptions.none(),
            false);

    assertThat(datatypesOf(in, "region"))
        .containsEntry("RegionID", "serial[1..2147483647]")
        .containsEntry("Code", "unique[1..count]");
  }

  @Test
  void shouldTreatPgAlterAddGeneratedAlwaysIdentityOnUnreferencedPkAsOmitted(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.account (id bigint NOT NULL, name varchar(100) NOT NULL);
            ALTER TABLE public.account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
                SEQUENCE NAME public.account_id_seq
                START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
            );
            ALTER TABLE ONLY public.account ADD CONSTRAINT account_pkey PRIMARY KEY (id);
            """);

    assertThat(datatypesOf(in, "account")).doesNotContainKey("id").containsKey("name");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("account.id")
                    && w.contains("GENERATED ALWAYS")
                    && w.toLowerCase(Locale.ROOT).contains("omit"));
  }

  @Test
  void shouldKeepPgAlterGeneratedAlwaysIdentityPkAsSerialWithSameWarningAsInlineWhenReferenced(
      @TempDir Path dir) {
    Inspection inline =
        inspectDdl(
            dir,
            """
            CREATE TABLE account (
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              name VARCHAR(100) NOT NULL
            );
            CREATE TABLE txn (id BIGINT PRIMARY KEY, account_id BIGINT REFERENCES account(id));
            """);
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE account (id BIGINT NOT NULL, name VARCHAR(100) NOT NULL);
            CREATE TABLE txn (id BIGINT NOT NULL, account_id BIGINT);
            ALTER TABLE account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
            ALTER TABLE ONLY account ADD CONSTRAINT account_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY txn ADD CONSTRAINT txn_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY txn ADD CONSTRAINT txn_fk FOREIGN KEY (account_id)
                REFERENCES account(id);
            """);

    assertThat(datatypesOf(altered, "account")).containsEntry("id", "serial");
    assertThat(datatypesOf(altered, "txn")).containsEntry("account_id", "ref[account.id]");
    assertThat(altered.warnings())
        .anyMatch(w -> w.contains("account.id") && w.contains("referenc") && w.contains("#400"));
    assertThat(altered.warnings()).containsExactlyInAnyOrderElementsOf(inline.warnings());
  }

  @Test
  void shouldMapPgAlterAddGeneratedByDefaultIdentityPrimaryKeyToSerial(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.ledger (id bigint NOT NULL, note varchar(50));
            ALTER TABLE public.ledger ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
                SEQUENCE NAME public.ledger_id_seq START WITH 1 INCREMENT BY 1 CACHE 1
            );
            ALTER TABLE ONLY public.ledger ADD CONSTRAINT ledger_pkey PRIMARY KEY (id);
            """);

    // BY DEFAULT accepts a client-supplied value: serial, never omitted
    assertThat(datatypesOf(in, "ledger")).containsEntry("id", "serial");
  }

  @Test
  void shouldTreatPgAlterGeneratedByDefaultIdentityOnPlainColumnLikeInline(@TempDir Path dir) {
    // equivalence (guard today)
    Inspection inline =
        inspectDdl(
            dir,
            "CREATE TABLE ledger (id bigint PRIMARY KEY,"
                + " seq bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL);\n");
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE ledger (id bigint PRIMARY KEY, seq bigint NOT NULL);
            ALTER TABLE ledger ALTER COLUMN seq ADD GENERATED BY DEFAULT AS IDENTITY;
            """);

    assertSameAsInline(altered, inline, "ledger");
  }

  @Test
  void shouldTreatPgAlterColumnSetDefaultNextvalLikeInlineDefault(@TempDir Path dir) {
    // equivalence (guard today)
    Inspection inline =
        inspectDdl(
            dir,
            "CREATE TABLE t (id integer PRIMARY KEY,"
                + " seq integer NOT NULL DEFAULT nextval('public.t_seq_seq'::regclass),"
                + " n integer);\n");
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.t (id integer PRIMARY KEY, seq integer NOT NULL, n integer);
            ALTER TABLE ONLY public.t
                ALTER COLUMN seq SET DEFAULT nextval('public.t_seq_seq'::regclass);
            """);

    assertSameAsInline(altered, inline, "t");
  }

  @Test
  void shouldTreatMysqlAddUniqueKeyOnIntegerLikeInlineUniqueKey(@TempDir Path dir) {
    Inspection inline =
        inspectDdl(
            dir,
            "CREATE TABLE u (id int PRIMARY KEY, code int NOT NULL, UNIQUE KEY uq_code (code));\n");
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE u (id int PRIMARY KEY, code int NOT NULL);
            ALTER TABLE u ADD UNIQUE KEY uq_code (code);
            """);

    assertThat(datatypesOf(inline, "u")).containsEntry("code", "unique[1..count]");
    assertSameAsInline(altered, inline, "u");
  }

  @Test
  void shouldTreatMysqlAddUniqueKeyOnVarcharLikeInlineUniqueKey(@TempDir Path dir) {
    Inspection inline =
        inspectDdl(
            dir,
            "CREATE TABLE u (id int PRIMARY KEY, code varchar(20) NOT NULL,"
                + " UNIQUE KEY uq_code (code));\n");
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE u (id int PRIMARY KEY, code varchar(20) NOT NULL);
            ALTER TABLE u ADD UNIQUE KEY uq_code (code);
            """);

    assertThat(datatypesOf(inline, "u")).containsEntry("code", "unique[1..count]");
    assertThat(inline.warnings()).noneMatch(w -> w.contains("not enforced"));
    assertSameAsInline(altered, inline, "u");
  }

  /** Datatypes, review comments and warnings of {@code altered} equal those of {@code inline}. */
  private static void assertSameAsInline(Inspection altered, Inspection inline, String table) {
    assertThat(datatypesOf(altered, table)).isEqualTo(datatypesOf(inline, table));
    assertThat(altered.comments()).isEqualTo(inline.comments());
    assertThat(altered.warnings()).containsExactlyInAnyOrderElementsOf(inline.warnings());
  }

  // ---------------------------------------------------------------- recorded / ignored (guards)

  @Test
  void shouldAcceptCheckConstraintsWithoutWarningOrOutputChange(@TempDir Path dir) {
    // guard: CHECK is only recorded on the model (#449/#214 map it later)
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id integer PRIMARY KEY, qty integer, discount real, flag varchar(1));
            ALTER TABLE t ADD CONSTRAINT t_qty_check CHECK (qty > 0);
            ALTER TABLE "T" ADD CONSTRAINT "CK_T_DISCOUNT" CHECK (Discount between 0 and 1) ENABLE;
            ALTER TABLE t ADD CONSTRAINT t_flag_check CHECK (flag in ('Y','N')) ENABLE;
            """);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("qty", INT);
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldAcceptOracleModifyNotNullWithoutWarningOrOutputChange(@TempDir Path dir) {
    // guard: NOT NULL is only recorded on the model
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE "S"."T" ("ID" NUMBER(9,0), "LABEL" VARCHAR2(15 BYTE));
              ALTER TABLE "S"."T" MODIFY ("LABEL" NOT NULL ENABLE);
              ALTER TABLE "S"."T" MODIFY ("ID" NOT NULL ENABLE);
            """);

    assertThat(datatypesOf(in, "t")).containsEntry("LABEL", "char[1..15]");
    assertThat(datatypesOf(in, "t").get("ID")).startsWith("int[");
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ALTER TABLE ONLY public.t OWNER TO postgres;",
        "ALTER TABLE t CHECK CONSTRAINT fk_t;",
        "ALTER TABLE t NOCHECK CONSTRAINT fk_t;",
        "ALTER TABLE \"T\" NOCHECK CONSTRAINT ALL\nGO",
        "ALTER TABLE t CHECK CONSTRAINT ALL\nGO",
        "ALTER SEQUENCE public.t_id_seq OWNED BY public.t.id;",
        "ALTER TABLE t REPLICA IDENTITY FULL;",
        "ALTER TABLE t SET (fillfactor = 70);",
        "ALTER TABLE t ENABLE TRIGGER ALL;",
        "ALTER TABLE t DISABLE TRIGGER trg;",
        "ALTER TABLE t CLUSTER ON t_ix;",
        "ALTER TABLE ONLY t ATTACH PARTITION t_2026 FOR VALUES FROM (1) TO (10);"
      })
  void shouldIgnoreKnownAlterNoiseSilently(String statement, @TempDir Path dir) {
    // guard: silent today, must stay silent
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id integer, n integer);\n" + statement + "\n");

    assertThat(datatypesOf(in, "t")).containsEntry("id", INT).containsEntry("n", INT);
    assertThat(in.warnings()).as(statement).isEmpty();
  }

  // ---------------------------------------------------------------- structure-changing

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "ALTER TABLE orders ADD COLUMN note text;|add column",
        "ALTER TABLE ONLY public.orders ADD COLUMN note text;|add column",
        "ALTER TABLE orders DROP COLUMN n;|drop column",
        "ALTER TABLE orders RENAME TO orders_old;|rename",
        "ALTER TABLE orders RENAME COLUMN n TO m;|rename",
        "ALTER TABLE orders ALTER COLUMN n TYPE bigint;|alter column"
      })
  void shouldWarnAboutStructureChangingAlterWithoutApplyingIt(
      String statement, String operation, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE orders (id integer PRIMARY KEY, n integer);\n" + statement + "\n");

    assertThat(in.warnings())
        .as(statement)
        .anyMatch(w -> w.contains("orders") && w.toLowerCase(Locale.ROOT).contains(operation));
    // not applied: the structure is exactly the CREATE TABLE
    assertThat(datatypesOf(in, "orders")).containsOnlyKeys("id", "n").containsEntry("n", INT);
  }

  @Test
  void shouldNotMistakeAlterOnTableNamedCreateTableForACreate(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE base (id INT);
            ALTER TABLE base ADD COLUMN c int;
            CREATE TABLE "create table" (x INT);
            ALTER TABLE "create table" ADD COLUMN c int;
            """);

    assertThat(in.structures()).hasSize(2);
    assertThat(in.warnings())
        .filteredOn(w -> w.toLowerCase(Locale.ROOT).contains("add column"))
        .hasSize(2);
  }

  // ---------------------------------------------------------------- unparseable ALTER

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ALTER TABLE ONLY t ALTER COLUMN n SET STATISTICS 100;",
        "ALTER TABLE ONLY t ALTER COLUMN n SET STORAGE EXTERNAL;"
      })
  void shouldIgnorePgDumpColumnStorageSettingsSilently(String alter, @TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id integer, n integer);\n" + alter + "\n");

    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldStillWarnWhenAlterColumnChangesTheType(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id integer, n integer);\nALTER TABLE t ALTER COLUMN n TYPE bigint;\n");

    assertThat(in.warnings()).anyMatch(w -> w.contains("t") && w.contains("not applied"));
  }

  @Test
  void shouldReportUnparseableAlterAsWarningAndKeepApplyingTheRestInStrictMode(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id integer, n integer);
            ALTER TABLE t ADD CONSTRAINT x FROBNICATE WIDGET;
            ALTER TABLE t ADD CONSTRAINT t_pk PRIMARY KEY (id);
            """);

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..2147483647]");
    assertThat(in.warnings()).anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("alter"));
  }

  @Test
  void shouldReportUnparseableAlterAsWarningInBestEffortMode(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id integer);\nALTER TABLE t ADD CONSTRAINT x FROBNICATE WIDGET;\n",
            NestingOptions.none(),
            true);

    assertThat(datatypesOf(in, "t")).containsKey("id");
    assertThat(in.warnings()).anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("alter"));
  }

  // ---------------------------------------------------------------- order, missing tables

  @Test
  void shouldApplyAlterThatPrecedesItsCreateTable(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            ALTER TABLE ONLY public.child ADD CONSTRAINT child_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY public.child ADD CONSTRAINT child_fk FOREIGN KEY (parent_id)
                REFERENCES public.parent(id);
            CREATE TABLE public.parent (id integer);
            CREATE TABLE public.child (id integer, parent_id integer);
            ALTER TABLE ONLY public.parent ADD CONSTRAINT parent_pkey PRIMARY KEY (id);
            """);

    assertThat(datatypesOf(in, "parent")).containsEntry("id", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "child"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsEntry("parent_id", "ref[parent.id]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldWarnAndIgnoreAlterOfTableNotInScript(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE real_t (id integer, n integer);
            ALTER TABLE ONLY public.ghost ADD CONSTRAINT ghost_pkey PRIMARY KEY (id);
            ALTER TABLE ghost2 ADD CONSTRAINT g2_fk FOREIGN KEY (a) REFERENCES real_t(id);
            ALTER TABLE real_t ADD CONSTRAINT real_pkey PRIMARY KEY (id);
            """);

    assertThat(in.structures()).hasSize(1);
    assertThat(datatypesOf(in, "real_t")).containsEntry("id", "serial[1..2147483647]");
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("ghost") && !w.contains("ghost2"))
        .anyMatch(w -> w.contains("ghost2"));
  }

  @Test
  void shouldWarnAndKeepPlainTypeForAlterForeignKeyToTableNotInScript(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE child (id integer PRIMARY KEY, ghost_id integer);
            ALTER TABLE child ADD CONSTRAINT child_fk FOREIGN KEY (ghost_id)
                REFERENCES ghost(id);
            """);

    assertThat(datatypesOf(in, "child")).containsEntry("ghost_id", INT);
    assertThat(in.structures())
        .flatExtracting(s -> s.getData().values())
        .extracting(f -> f.getDatatype())
        .noneMatch(d -> d.startsWith("ref["));
    assertThat(in.warnings()).anyMatch(w -> w.contains("ghost"));
  }

  // ---------------------------------------------------------------- dedup (guards)

  @Test
  void shouldNotDoubleCountPrimaryKeyDeclaredInlineAndByAlter(@TempDir Path dir) {
    // guard: ALTERs are ignored today; once applied they must not duplicate the inline keys
    Inspection inlineOnly =
        inspectDdl(dir, "CREATE TABLE t (id integer PRIMARY KEY, code integer UNIQUE);\n");
    Inspection both =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id integer PRIMARY KEY, code integer UNIQUE);
            ALTER TABLE t ADD CONSTRAINT t_pk PRIMARY KEY (id);
            ALTER TABLE t ADD CONSTRAINT t_uq UNIQUE (code);
            """);

    assertThat(datatypesOf(both, "t")).isEqualTo(datatypesOf(inlineOnly, "t"));
    assertThat(both.warnings()).isEqualTo(inlineOnly.warnings()).isEmpty();
  }

  @Test
  void shouldEnforceTextPrimaryKeyOnceWhenDeclaredInlineAndByAlter(@TempDir Path dir) {
    // guard
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (sku varchar(10) PRIMARY KEY);
            ALTER TABLE t ADD CONSTRAINT t_pk PRIMARY KEY (sku);
            """);

    // enforced once: no "not enforced" warning, no overlap warning, comment not duplicated
    assertThat(datatypesOf(in, "t")).containsEntry("sku", "serial[1..9999999999]");
    assertThat(in.comments().get("t"))
        .containsEntry("sku", "text key generated as numeric strings — max 9999999999 rows");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
    assertThat(in.warnings()).noneMatch(w -> w.contains("only the first key"));
  }

  @Test
  void shouldNotRenameGroupOfCompositeUniqueDeclaredInlineAndByAlter(@TempDir Path dir) {
    // guard
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (id integer PRIMARY KEY, a integer, b integer, UNIQUE (a, b));
            ALTER TABLE t ADD CONSTRAINT t_ab_uq UNIQUE (a, b);
            """);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "unique[uq1, 1..count]")
        .containsEntry("b", "unique[uq1, 1..count]");
    assertThat(in.warnings()).isEmpty();
  }

  @Test
  void shouldNotDuplicateForeignKeyDeclaredInlineAndByAlterFlatOrNested(@TempDir Path dir) {
    // guard
    String ddl =
        """
        CREATE TABLE p (id integer PRIMARY KEY);
        CREATE TABLE c (id integer PRIMARY KEY, p_id integer REFERENCES p(id));
        ALTER TABLE c ADD CONSTRAINT c_fk FOREIGN KEY (p_id) REFERENCES p(id);
        """;

    Inspection flat = inspectDdl(dir, ddl);
    Inspection nested = inspectDdl(dir, ddl, NEST, false);

    assertThat(datatypesOf(flat, "c")).containsEntry("p_id", "ref[p.id]");
    assertThat(flat.warnings()).isEmpty();
    assertThat(datatypesOf(nested, "p")).hasSize(2).containsValue("array[object[c], 1..10]");
    // only the planner's own "nested 'c' into 'p'" note, once
    assertThat(nested.warnings()).hasSize(1).allMatch(w -> w.startsWith("nested 'c' into 'p'"));
  }

  // ---------------------------------------------------------------- key interplay

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMakeAlterPrimaryKeyOnForeignKeyColumnAUniqueRef(boolean pkFirst, @TempDir Path dir) {
    String pk = "ALTER TABLE detail ADD CONSTRAINT detail_pk PRIMARY KEY (a_id);\n";
    String fk =
        "ALTER TABLE detail ADD CONSTRAINT detail_fk FOREIGN KEY (a_id) REFERENCES a(id);\n";
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE a (id integer PRIMARY KEY);\n"
                + "CREATE TABLE detail (a_id integer, info varchar(10));\n"
                + (pkFirst ? pk + fk : fk + pk));

    assertThat(datatypesOf(in, "detail")).containsEntry("a_id", "ref[a.id, unique]");
  }

  @Test
  void shouldMapCompositePrimaryKeyOfPlainIntegersAddedByAlterToUniqueGroup(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (a integer NOT NULL, b integer NOT NULL, n integer);
            ALTER TABLE ONLY t ADD CONSTRAINT t_pkey PRIMARY KEY (a, b);
            """);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "unique[pk, 1..count]")
        .containsEntry("b", "unique[pk, 1..count]")
        .containsEntry("n", INT);
  }

  @Test
  void shouldMapCompositePrimaryKeyOfForeignKeysAddedByAlterToUniqueRefGroup(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (id bigint);
            CREATE TABLE b (id bigint);
            CREATE TABLE ab (a_id bigint NOT NULL, b_id bigint NOT NULL);
            ALTER TABLE ONLY a ADD CONSTRAINT a_pk PRIMARY KEY (id);
            ALTER TABLE ONLY b ADD CONSTRAINT b_pk PRIMARY KEY (id);
            ALTER TABLE ONLY ab ADD CONSTRAINT ab_pk PRIMARY KEY (a_id, b_id);
            ALTER TABLE ONLY ab ADD CONSTRAINT ab_a_fk FOREIGN KEY (a_id) REFERENCES a(id);
            ALTER TABLE ONLY ab ADD CONSTRAINT ab_b_fk FOREIGN KEY (b_id) REFERENCES b(id);
            """);

    assertThat(datatypesOf(in, "ab"))
        .containsEntry("a_id", "ref[a.id, unique=pk]")
        .containsEntry("b_id", "ref[b.id, unique=pk]");
  }

  @Test
  void shouldMapCompositeUniqueMixingForeignKeyAndIntegerAddedByAlterToGroup(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (id bigint PRIMARY KEY);
            CREATE TABLE t (id bigint PRIMARY KEY, a_id bigint, slot integer);
            ALTER TABLE t ADD CONSTRAINT t_fk FOREIGN KEY (a_id) REFERENCES a(id);
            ALTER TABLE t ADD CONSTRAINT t_uq UNIQUE (a_id, slot);
            """);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a_id", "ref[a.id, unique=uq1]")
        .containsEntry("slot", "unique[uq1, 1..count]");
  }

  // ---------------------------------------------------------------- case / quoting / schema

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "\"Orders\"|orders",
        "orders|\"Orders\"",
        "public.orders|orders",
        "orders|public.orders",
        "[dbo].[Orders]|Orders",
        "Orders|[dbo].[Orders]",
        "public.orders|\"public\".\"orders\""
      })
  void shouldMatchAlterTableNameToCreateTableIgnoringCaseQuotingAndSchema(
      String created, String altered, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE "
                + created
                + " (id integer, n integer);\n"
                + "ALTER TABLE ONLY "
                + altered
                + " ADD CONSTRAINT o_pk PRIMARY KEY (id);\n");

    assertThat(datatypesOf(in, "orders")).containsEntry("id", "serial[1..2147483647]");
    assertThat(in.warnings()).as("%s / %s", created, altered).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "public.parent",
        "\"parent\"",
        "\"public\".\"parent\"",
        "[dbo].[parent]",
        "PARENT",
        "\"Parent\""
      })
  void shouldMatchAlterForeignKeyTargetToCreateTableIgnoringCaseQuotingAndSchema(
      String target, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE parent (id integer PRIMARY KEY);\n"
                + "CREATE TABLE child (id integer PRIMARY KEY, parent_id integer);\n"
                + "ALTER TABLE child ADD CONSTRAINT c_fk FOREIGN KEY (parent_id) REFERENCES "
                + target
                + " (id);\n");

    assertThat(datatypesOf(in, "child")).containsEntry("parent_id", "ref[parent.id]");
    assertThat(in.warnings()).as(target).isEmpty();
  }

  @Test
  void shouldMatchSqlServerAlterColumnsIgnoringBracketsAndCase(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE [dbo].[Territories] ([TerritoryID] [nvarchar](20) NOT NULL,"
                + " [RegionID] [int] NOT NULL)\nGO\n"
                + "CREATE TABLE [dbo].[Region] ([RegionID] [int] NOT NULL)\nGO\n"
                + "ALTER TABLE Region ADD CONSTRAINT [PK_Region] PRIMARY KEY NONCLUSTERED ([regionid])"
                + " ON [PRIMARY]\nGO\n"
                + "ALTER TABLE Territories ADD CONSTRAINT [FK_T_R] FOREIGN KEY ([REGIONID])"
                + " REFERENCES [dbo].[Region] ([RegionID])\nGO\n",
            NestingOptions.none(),
            false);

    assertThat(datatypesOf(in, "region")).containsEntry("RegionID", "serial[1..2147483647]");
    assertThat(datatypesOf(in, "territories")).containsEntry("RegionID", "ref[region.region_id]");
  }

  // ---------------------------------------------------------------- nesting

  @Test
  void shouldNestForeignKeysDeclaredByAlterExactlyLikeInlineOnes(@TempDir Path dir) {
    Inspection altered =
        inspectDdl(
            dir,
            """
            CREATE TABLE customer (id bigint NOT NULL, name varchar(120));
            CREATE TABLE invoice (id bigint NOT NULL, customer_id bigint, total decimal(10,2));
            CREATE TABLE invoice_item (id bigint NOT NULL, invoice_id bigint, description varchar(200));
            ALTER TABLE ONLY customer ADD CONSTRAINT customer_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY invoice ADD CONSTRAINT invoice_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY invoice_item ADD CONSTRAINT invoice_item_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY invoice ADD CONSTRAINT fk_cust FOREIGN KEY (customer_id) REFERENCES customer(id);
            ALTER TABLE ONLY invoice_item ADD CONSTRAINT fk_inv FOREIGN KEY (invoice_id) REFERENCES invoice(id);
            """,
            NEST,
            false);
    Inspection inline =
        inspectDdl(
            dir,
            """
            CREATE TABLE customer (id bigint PRIMARY KEY, name varchar(120));
            CREATE TABLE invoice (id bigint PRIMARY KEY, customer_id bigint, total decimal(10,2),
              CONSTRAINT fk_cust FOREIGN KEY (customer_id) REFERENCES customer(id));
            CREATE TABLE invoice_item (id bigint PRIMARY KEY, invoice_id bigint, description varchar(200),
              CONSTRAINT fk_inv FOREIGN KEY (invoice_id) REFERENCES invoice(id));
            """,
            NEST,
            false);

    assertThat(datatypesOf(altered, "customer"))
        .containsEntry("id", "serial")
        .containsEntry("invoices", "array[object[invoice], 1..10]");
    assertThat(datatypesOf(altered, "invoice"))
        .containsEntry("invoice_items", "array[object[invoice_item], 1..10]")
        .doesNotContainKey("customer_id");
    assertThat(datatypesOf(altered, "invoice_item")).doesNotContainKey("invoice_id");
    for (String table : List.of("customer", "invoice", "invoice_item")) {
      assertThat(datatypesOf(altered, table)).isEqualTo(datatypesOf(inline, table));
    }
  }

  @Test
  void shouldNestUniqueForeignKeyDeclaredByAlterAsObject(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE app_user (id bigint NOT NULL, name varchar(80));
            CREATE TABLE profile (id bigint NOT NULL, user_id bigint, bio varchar(200));
            ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY profile ADD CONSTRAINT profile_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY profile ADD CONSTRAINT profile_user_uq UNIQUE (user_id);
            ALTER TABLE ONLY profile ADD CONSTRAINT profile_user_fk
                FOREIGN KEY (user_id) REFERENCES app_user(id);
            """,
            NEST,
            false);

    assertThat(datatypesOf(in, "app_user")).containsEntry("profile", "object[profile]");
    assertThat(datatypesOf(in, "profile")).doesNotContainKey("user_id");
  }

  // ---------------------------------------------------------------- loadability / guard

  @Test
  void shouldWriteStructuresForAMixedAlterScriptThatLoadWithTheRealParser(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE public.a (id integer NOT NULL, code integer, sku varchar(9));
            CREATE TABLE public.b (id integer NOT NULL, a_id integer, a2_id integer);
            CREATE TABLE public.ab (a_id integer NOT NULL, b_id integer NOT NULL);
            ALTER TABLE ONLY public.a ADD CONSTRAINT a_pk PRIMARY KEY (id);
            ALTER TABLE ONLY public.a ADD CONSTRAINT a_code_uq UNIQUE (code);
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_pk PRIMARY KEY (id);
            ALTER TABLE ONLY public.ab ADD CONSTRAINT ab_pk PRIMARY KEY (a_id, b_id);
            ALTER TABLE ONLY public.ab ADD CONSTRAINT ab_a FOREIGN KEY (a_id) REFERENCES public.a(id);
            ALTER TABLE ONLY public.ab ADD CONSTRAINT ab_b FOREIGN KEY (b_id)
                REFERENCES public.b(id) DEFERRABLE INITIALLY DEFERRED;
            ALTER TABLE ONLY public.b ADD CONSTRAINT b_a FOREIGN KEY (a_id) REFERENCES public.a(id);
            """);

    Map<String, DataStructure> loaded = loadAll(in, dir);

    assertThat(loaded.get("ab").getData().get("a_id").getDatatype())
        .isEqualTo("ref[a.id, unique=pk]");
    assertThat(loaded.get("b").getData().get("a_id").getDatatype()).isEqualTo("ref[a.id]");
  }

  @Test
  void shouldKeepInlineConstraintsUnchangedWhenNoAlterIsPresent(@TempDir Path dir) {
    // guard: the two-phase restructuring must keep inline behaviour byte-identical
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE a (id BIGINT PRIMARY KEY, code INT UNIQUE);
            CREATE TABLE b (
              id BIGINT PRIMARY KEY,
              a_id BIGINT REFERENCES a(id),
              slot INT,
              UNIQUE (a_id, slot)
            );
            CREATE TABLE c (id BIGINT PRIMARY KEY, a_id BIGINT, FOREIGN KEY (a_id) REFERENCES a(id));
            """);

    assertThat(datatypesOf(in, "a"))
        .containsEntry("id", "serial")
        .containsEntry("code", "unique[1..count]");
    assertThat(datatypesOf(in, "b"))
        .containsEntry("a_id", "ref[a.id, unique=uq1]")
        .containsEntry("slot", "unique[uq1, 1..count]");
    assertThat(datatypesOf(in, "c")).containsEntry("a_id", "ref[a.id]");
    assertThat(in.warnings()).isEmpty();
  }
}
