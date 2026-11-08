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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * #444: the preprocessor must only react to the SQL Server identity COLUMN PROPERTY. The bare word
 * {@code identity} as a table name, column name, constraint name or in a referenced table must be
 * left alone, and the preprocessor's internal marker token must never leak into the result.
 */
class DdlInspectorIdentityMarkerTest {

  private static final String ID_SERIAL = "serial[1..2147483647]";
  private static final String INT = "int[1..999999]";

  // --- table named Identity ---

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "CREATE TABLE [dbo].[Identity] (id int PRIMARY KEY, name varchar(10))",
        "CREATE TABLE identity (id int PRIMARY KEY, name varchar(10))",
        "CREATE TABLE \"Identity\" (id int PRIMARY KEY, name varchar(10))",
        "CREATE TABLE dbo.Identity (id int PRIMARY KEY, name varchar(10))"
      })
  void shouldKeepTableNamedIdentity(String ddl, @TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, ddl + ";");

    assertThat(in.structures()).extracting(s -> s.getName()).containsExactly("identity");
    assertThat(datatypesOf(in, "identity")).containsEntry("id", ID_SERIAL).containsKey("name");
    assertThat(in.warnings()).noneMatch(w -> w.contains("IDENTITY"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "iid int REFERENCES [dbo].[Identity] (id)",
        "iid int REFERENCES Identity (id)",
        "iid int, FOREIGN KEY (iid) REFERENCES [dbo].[Identity] (id)",
        "iid int, CONSTRAINT fk_i FOREIGN KEY (iid) REFERENCES Identity(id)"
      })
  void shouldKeepForeignKeyToTableNamedIdentity(String fk, @TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE [dbo].[Identity] (id int PRIMARY KEY, name varchar(10));\n"
                + "CREATE TABLE child (cid int PRIMARY KEY, "
                + fk
                + ");");

    assertThat(datatypesOf(in, "child"))
        .containsEntry("iid", "ref[identity.id, 1..count]")
        .containsKey("cid");
    assertThat(in.warnings()).noneMatch(w -> w.contains("IDENTITY") || w.contains("omitted"));
  }

  // --- column named identity ---

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "id int PRIMARY KEY,\n identity int NOT NULL",
        "id int PRIMARY KEY,\n -- c\n identity int NOT NULL",
        "id int PRIMARY KEY,\n /* c */ identity int NOT NULL",
        "id int PRIMARY KEY,\n -- c\n [identity] int NOT NULL",
        "id int PRIMARY KEY,\n /* c */ \"identity\" int NOT NULL",
        "id int PRIMARY KEY, /* c */ [Identity] int"
      })
  void shouldKeepColumnNamedIdentity(String columns, @TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (\n" + columns + "\n);");

    var fields = datatypesOf(in, "t");
    assertThat(fields.keySet())
        .hasSize(2)
        .contains("id")
        .anyMatch(k -> k.equalsIgnoreCase("identity"));
    assertThat(fields).containsEntry("id", ID_SERIAL);
    assertThat(fields.entrySet())
        .filteredOn(e -> e.getKey().equalsIgnoreCase("identity"))
        .allMatch(e -> e.getValue().equals(INT));
    assertThat(in.warnings()).noneMatch(w -> w.contains("IDENTITY") || w.contains("omitted"));
  }

  @Test
  void shouldKeepColumnNamedIdentityAsFirstColumn(@TempDir Path dir) throws IOException {
    // guard, passes today
    Inspection in = inspect(dir, "CREATE TABLE t (identity int, id int PRIMARY KEY);");

    assertThat(datatypesOf(in, "t")).containsEntry("identity", INT).containsEntry("id", ID_SERIAL);
  }

  @Test
  void shouldStillDetectRealIdentityAfterCommentedColumnNamedIdentity(@TempDir Path dir)
      throws IOException {
    // the real property on id must still be seen next to a column called identity
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE t (\n -- c\n identity int NOT NULL,\n"
                + " id int IDENTITY(1,1) NOT NULL PRIMARY KEY\n);");

    assertThat(datatypesOf(in, "t")).containsEntry("identity", INT).doesNotContainKey("id");
    assertThat(in.warnings()).anyMatch(w -> w.contains("t.id") && w.contains("omit"));
  }

  // --- constraint named identity ---

  @Test
  void shouldNotLetConstraintNamedIdentityChangeTheOutput(@TempDir Path dir) throws IOException {
    Inspection named =
        inspect(
            dir, "CREATE TABLE t (id int NOT NULL, n int, CONSTRAINT identity PRIMARY KEY (id));");
    Inspection control =
        inspect(dir, "CREATE TABLE t (id int NOT NULL, n int, CONSTRAINT pk_t PRIMARY KEY (id));");

    assertThat(datatypesOf(named, "t")).containsEntry("id", ID_SERIAL).containsEntry("n", INT);
    assertThat(datatypesOf(named, "t")).isEqualTo(datatypesOf(control, "t"));
    assertThat(named.warnings()).isEqualTo(control.warnings());
  }

  // --- wording of the "kept" warning ---

  @Test
  void shouldNotClaimSerialForReferencedNonPkIdentityColumn(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE t (
              code VARCHAR(10) PRIMARY KEY,
              seq INT IDENTITY(1,1) NOT NULL UNIQUE
            );
            CREATE TABLE u (id INT PRIMARY KEY, t_seq INT, FOREIGN KEY (t_seq) REFERENCES t (seq));
            """);

    assertThat(datatypesOf(in, "t")).containsKey("seq").doesNotContainEntry("seq", ID_SERIAL);
    assertThat(in.warnings())
        .filteredOn(w -> w.contains("t.seq"))
        .isNotEmpty()
        .allMatch(w -> w.contains("IDENTITY") && w.contains("#400") && !w.contains("as serial"));
  }

  @Test
  void shouldKeepAndWarnReferencedIdentityMemberOfCompositePrimaryKey(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE t (
              id INT IDENTITY(1,1) NOT NULL,
              tenant INT NOT NULL,
              PRIMARY KEY (id, tenant)
            );
            CREATE TABLE u (uid INT PRIMARY KEY, t_id INT, FOREIGN KEY (t_id) REFERENCES t (id));
            """);

    assertThat(datatypesOf(in, "t"))
        .containsKeys("id", "tenant")
        .doesNotContainEntry("id", ID_SERIAL);
    assertThat(datatypesOf(in, "u")).containsEntry("t_id", "ref[t.id, 1..count]");
    assertThat(in.warnings())
        .filteredOn(w -> w.contains("t.id"))
        .isNotEmpty()
        .allMatch(w -> w.contains("IDENTITY") && w.contains("#400") && !w.contains("as serial"));
  }

  @Test
  void shouldWarnForUnreferencedIdentityMemberOfCompositePrimaryKey(@TempDir Path dir)
      throws IOException {
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

    assertThat(datatypesOf(in, "t")).containsKeys("id", "tenant");
    assertThat(in.warnings())
        .anyMatch(w -> w.contains("t.id") && w.contains("IDENTITY") && !w.contains("omitted"));
  }

  // --- FK spelled in a different case than the identity PK ---

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "pid int REFERENCES Parent(ID)",
        "pid int REFERENCES PARENT(id)",
        "pid int, FOREIGN KEY (pid) REFERENCES Parent(ID)",
        "pid int, FOREIGN KEY (pid) REFERENCES parent (ID)"
      })
  void shouldKeepIdentityPkReferencedWithDifferentCase(String fk, @TempDir Path dir)
      throws IOException {
    // guard, passes today if the referenced lookup is case-insensitive
    Inspection in =
        inspect(
            dir,
            "CREATE TABLE Parent (Id INT IDENTITY(1,1) PRIMARY KEY, name varchar(10));\n"
                + "CREATE TABLE child (cid int PRIMARY KEY, "
                + fk
                + ");");

    assertThat(datatypesOf(in, "parent")).containsEntry("Id", ID_SERIAL);
    assertThat(datatypesOf(in, "child")).containsEntry("pid", "ref[parent.id, 1..count]");
    assertThat(in.warnings())
        .anyMatch(
            w ->
                w.contains("parent.Id")
                    && w.contains("IDENTITY")
                    && w.toLowerCase(Locale.ROOT).contains("referenc")
                    && w.contains("#400"));
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    Inspection in = new DdlInspector().inspect(sql);
    assertNoIdentityMarker(in);
    return in;
  }
}
