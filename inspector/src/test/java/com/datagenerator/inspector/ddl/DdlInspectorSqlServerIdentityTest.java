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
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * #444: SQL Server {@code IDENTITY(seed, increment)} is DB-assigned (explicit inserts fail with
 * "Cannot insert explicit value for identity column"). The preprocessor strips it so JSQLParser can
 * parse the column, but the inspector must still treat the column exactly like a {@code GENERATED
 * ALWAYS AS IDENTITY} one (#215): unreferenced single-column PK omitted + warning; referenced PK
 * kept as {@code serial} + warning pointing at #400.
 */
class DdlInspectorSqlServerIdentityTest {

  /**
   * Each form declares a single-column PK called {@code Id}/{@code id} plus a plain {@code name}.
   * The last argument says whether the key maps to an integer type (so a kept PK shows as {@code
   * serial}); a quoted {@code "int"} and {@code numeric(18,0)} do not today.
   */
  static Stream<Arguments> identityForms() {
    return Stream.of(
        Arguments.of(
            "IDENTITY(1,1) inline pk",
            "CREATE TABLE parent (id INT IDENTITY(1,1) PRIMARY KEY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "IDENTITY (1, 1) spaced",
            "CREATE TABLE parent (id INT IDENTITY (1, 1) PRIMARY KEY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "IDENTITY(1000, 10) custom seed",
            "CREATE TABLE parent (id INT IDENTITY(1000, 10) PRIMARY KEY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "lowercase identity",
            "CREATE TABLE parent (id INT identity(1,1) PRIMARY KEY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "[int] IDENTITY(1,1) bracket-quoted",
            """
            CREATE TABLE [dbo].[parent] (
              [Id] [int] IDENTITY(1,1) NOT NULL,
              [name] [nvarchar](50) NULL,
              CONSTRAINT [pk_parent] PRIMARY KEY CLUSTERED ([Id] ASC)
            ) ON [PRIMARY]
            GO
            """,
            "Id",
            true),
        Arguments.of(
            "\"int\" IDENTITY (1, 1) double-quoted",
            """
            CREATE TABLE "parent" (
              "Id" "int" IDENTITY (1, 1) NOT NULL ,
              "name" nvarchar (50) NULL ,
              CONSTRAINT "PK_parent" PRIMARY KEY  CLUSTERED
              (
                "Id"
              )
            )
            GO
            """,
            "Id",
            false),
        Arguments.of(
            "bare int IDENTITY",
            "CREATE TABLE parent (id int IDENTITY PRIMARY KEY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "IDENTITY(1,1) NOT FOR REPLICATION",
            "CREATE TABLE parent (id INT IDENTITY(1,1) NOT FOR REPLICATION NOT NULL PRIMARY KEY,"
                + " name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "numeric(18,0) IDENTITY(1,1)",
            "CREATE TABLE parent (id numeric(18,0) IDENTITY(1,1) NOT NULL PRIMARY KEY,"
                + " name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "bigint IDENTITY table-level pk",
            """
            CREATE TABLE parent (
              id BIGINT IDENTITY(1,1) NOT NULL,
              name VARCHAR(50),
              PRIMARY KEY (id)
            );
            """,
            "id",
            true),
        Arguments.of(
            "IDENTITY after PRIMARY KEY",
            "CREATE TABLE parent (id INT PRIMARY KEY IDENTITY(1,1), name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "bare IDENTITY after NOT NULL PRIMARY KEY",
            "CREATE TABLE parent (id INT NOT NULL PRIMARY KEY IDENTITY, name VARCHAR(50));",
            "id",
            true),
        Arguments.of(
            "IDENTITY after UNIQUE, non-pk",
            "CREATE TABLE parent (code VARCHAR(10) PRIMARY KEY, id INT UNIQUE IDENTITY(1,1),"
                + " name VARCHAR(50));",
            "id",
            false),
        Arguments.of(
            "IDENTITY after DEFAULT ((0))",
            "CREATE TABLE parent (id INT NOT NULL DEFAULT ((0)) IDENTITY(1,1), name VARCHAR(50),"
                + " PRIMARY KEY (id));",
            "id",
            true),
        Arguments.of(
            "IDENTITY after PRIMARY KEY CLUSTERED",
            "CREATE TABLE parent (id INT PRIMARY KEY CLUSTERED IDENTITY(1,1), name VARCHAR(50));",
            "id",
            true));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("identityForms")
  void shouldOmitUnreferencedIdentityPkAndWarn(
      String form, String ddl, String pk, boolean integerKey, @TempDir Path dir)
      throws IOException {
    Inspection in = inspect(dir, ddl);

    assertThat(datatypesOf(in, "parent")).doesNotContainKey(pk).containsKey("name");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("parent." + pk)
                    && w.contains("IDENTITY")
                    && w.toLowerCase(Locale.ROOT).contains("omit"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("identityForms")
  void shouldKeepReferencedIdentityPkAsSerialAndWarn(
      String form, String ddl, String pk, boolean integerKey, @TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            ddl
                + "\nCREATE TABLE child (cid INT PRIMARY KEY, parent_id INT,"
                + " FOREIGN KEY (parent_id) REFERENCES parent ("
                + pk
                + "));");

    // kept (omitting would dangle the ref); only integer-typed keys are rewritten to serial
    assertThat(datatypesOf(in, "parent")).containsKey(pk);
    if (integerKey) {
      String expected =
          ddl.contains("numeric(18,0)")
              ? "serial[1..999999999999999999]"
              : ddl.toLowerCase(Locale.ROOT).contains("bigint")
                  ? "serial"
                  : "serial[1..2147483647]";
      assertThat(datatypesOf(in, "parent")).containsEntry(pk, expected);
    }
    // an integer identity PK is serial, and a bare ref resolves from it; the UNIQUE (non-pk) form
    // is a plain unique[1..count] key, which the bare form resolves from too
    assertThat(datatypesOf(in, "parent"))
        .containsEntry(pk, integerKey ? datatypesOf(in, "parent").get(pk) : "unique[1..count]");
    assertThat(datatypesOf(in, "child"))
        .containsEntry("parent_id", "ref[parent." + pk.toLowerCase(Locale.ROOT) + "]");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("parent." + pk)
                    && w.contains("IDENTITY")
                    && w.toLowerCase(Locale.ROOT).contains("referenc")
                    && w.contains("#400"));
  }

  @Test
  void shouldOmitIdentityPkInNestedModeToo(@TempDir Path dir) throws IOException {
    Inspection in =
        new DdlInspector()
            .inspect(
                write(
                    dir,
                    "CREATE TABLE parent (id INT IDENTITY(1,1) PRIMARY KEY, name VARCHAR(5));"),
                NestingOptions.parse("auto", null));

    assertNoIdentityMarker(in);
    assertThat(datatypesOf(in, "parent")).doesNotContainKey("id").containsKey("name");
  }

  // --- identity on a non-PK column: SQL Server rejects explicit values there too ---

  @Test
  void shouldOmitUnreferencedNonPkIdentityColumnAndWarn(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE t (code VARCHAR(10) PRIMARY KEY, seq INT IDENTITY(1,1) NOT NULL,"
                + " n INT);");

    assertThat(datatypesOf(in, "t")).doesNotContainKey("seq").containsKeys("code", "n");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("t.seq")
                    && w.contains("IDENTITY")
                    && w.toLowerCase(Locale.ROOT).contains("omit"));
  }

  @Test
  void shouldKeepReferencedNonPkIdentityColumnAndWarn(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE t (
              code VARCHAR(10) PRIMARY KEY,
              seq INT IDENTITY(1,1) NOT NULL UNIQUE,
              n INT
            );
            CREATE TABLE u (id INT PRIMARY KEY, t_seq INT, FOREIGN KEY (t_seq) REFERENCES t (seq));
            """);

    assertThat(datatypesOf(in, "t")).containsKeys("code", "seq", "n");
    assertThat(datatypesOf(in, "t")).containsEntry("seq", "unique[1..count]");
    assertThat(datatypesOf(in, "u")).containsEntry("t_seq", "ref[t.seq]");
    assertThat(in.warnings()).anyMatch(w -> w.contains("t.seq") && w.contains("IDENTITY"));
  }

  @Test
  void shouldRetainIdentityColumnInCompositePrimaryKey(@TempDir Path dir) throws IOException {
    // guard, passes today: only single-column identity PKs are omitted (same as GENERATED ALWAYS)
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE t (
              id INT IDENTITY(1,1) NOT NULL,
              tenant INT NOT NULL,
              PRIMARY KEY (id, tenant)
            );
            """);

    assertThat(datatypesOf(in, "t")).containsKey("id").containsKey("tenant");
  }

  // --- false-positive guards: the word IDENTITY is not an identity property ---

  @Test
  void shouldKeepColumnWhoseNameContainsIdentity(@TempDir Path dir) throws IOException {
    // guard, passes today
    Inspection in =
        inspect(dir, "CREATE TABLE t (IdentityNumber INT PRIMARY KEY, name NVARCHAR(20));");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("IdentityNumber", "serial[1..2147483647]")
        .containsKey("name");
    assertThat(in.warnings()).noneMatch(w -> w.contains("IDENTITY"));
  }

  @Test
  void shouldKeepColumnWithIdentityInStringLiteralDefault(@TempDir Path dir) throws IOException {
    // guard, passes today
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE t (id INT PRIMARY KEY, note VARCHAR(30) DEFAULT 'IDENTITY(1,1)',"
                + " other VARCHAR(30));");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsKeys("note", "other");
    assertThat(in.warnings()).noneMatch(w -> w.contains("IDENTITY"));
  }

  @Test
  void shouldNotTreatStringLiteralIdentityOnPkColumnAsIdentity(@TempDir Path dir)
      throws IOException {
    // guard, passes today
    Inspection in =
        inspect(
            dir, "CREATE TABLE t (code VARCHAR(30) PRIMARY KEY DEFAULT 'IDENTITY(1,1)', n INT);");

    assertThat(datatypesOf(in, "t")).containsKeys("code", "n");
    assertThat(in.warnings()).noneMatch(w -> w.contains("omitted"));
  }

  @Test
  void shouldNotTreatMysqlKeyNamedIdentityAsIdentity(@TempDir Path dir) throws IOException {
    // guard, passes today: "KEY identity (col)" is an index definition, not a column property
    Inspection indexed =
        inspect(dir, "CREATE TABLE t (id INT PRIMARY KEY, col INT, KEY identity (col));");
    Inspection control =
        inspect(dir, "CREATE TABLE t (id INT PRIMARY KEY, col INT, KEY idx_col (col));");

    assertThat(datatypesOf(indexed, "t")).isEqualTo(datatypesOf(control, "t"));
    assertThat(datatypesOf(indexed, "t"))
        .containsEntry("id", "serial[1..2147483647]")
        .containsKey("col");
    assertThat(indexed.warnings()).isEqualTo(control.warnings());
    assertThat(indexed.warnings()).noneMatch(w -> w.contains("IDENTITY"));
  }

  // --- regression: GENERATED ALWAYS behaviour (#215) unchanged, incl. non-PK ---

  @Test
  void shouldKeepGeneratedAlwaysNonPkIdentityColumnUnchanged(@TempDir Path dir) throws IOException {
    // guard, passes today: the shared path must not start omitting non-PK GENERATED ALWAYS
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE t (id BIGINT PRIMARY KEY, seq BIGINT GENERATED ALWAYS AS IDENTITY,"
                + " n INT);");

    assertThat(datatypesOf(in, "t")).containsKeys("id", "seq", "n");
  }

  private Path write(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return sql;
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    Inspection in = new DdlInspector().inspect(write(dir, ddl));
    assertNoIdentityMarker(in);
    return in;
  }
}
