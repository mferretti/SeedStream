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
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdlInspectorKeysTest {

  private static final String NOT_ENFORCED = "UNIQUE/PRIMARY KEY not enforced — values may collide";

  @Test
  void shouldMapSinglePrimaryKeyToSerial(@TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (id BIGINT PRIMARY KEY, n INT);");
    assertThat(datatypesOf(in, "t"))
        .containsEntry("id", "serial")
        .containsEntry("n", "int[1..999999]");
  }

  @Test
  void shouldMapSerialColumnWithoutKeyToSerial(@TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (seq BIGSERIAL, n INT);");
    assertThat(datatypesOf(in, "t"))
        .containsEntry("seq", "serial")
        .containsEntry("n", "int[1..999999]");
  }

  @Test
  void shouldMapSingleUniqueIntToUniqueRange(@TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (id INT PRIMARY KEY, code INT UNIQUE);");
    assertThat(datatypesOf(in, "t")).containsEntry("code", "unique[1..count]");
  }

  @Test
  void shouldMapCompositePrimaryKeyOfForeignKeysToUniqueRefGroup(@TempDir Path dir)
      throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE a (id BIGINT PRIMARY KEY);
            CREATE TABLE b (id BIGINT PRIMARY KEY);
            CREATE TABLE ab (
              a_id BIGINT REFERENCES a(id),
              b_id BIGINT REFERENCES b(id),
              PRIMARY KEY (a_id, b_id)
            );
            """);
    assertThat(datatypesOf(in, "ab"))
        .containsEntry("a_id", "ref[a.id, 1..count, unique=pk]")
        .containsEntry("b_id", "ref[b.id, 1..count, unique=pk]");
  }

  @Test
  void shouldMapSingleForeignKeyPrimaryKeyToUniqueRef(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE a (id BIGINT PRIMARY KEY);
            CREATE TABLE a_detail (a_id BIGINT PRIMARY KEY REFERENCES a(id));
            """);
    assertThat(datatypesOf(in, "a_detail")).containsEntry("a_id", "ref[a.id, 1..count, unique]");
  }

  @Test
  void shouldMapCompositeUniqueMixedIntAndForeignKeyToGroup(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE a (id BIGINT PRIMARY KEY);
            CREATE TABLE t (
              id BIGINT PRIMARY KEY,
              a_id BIGINT REFERENCES a(id),
              slot INT,
              UNIQUE (a_id, slot)
            );
            """);
    assertThat(datatypesOf(in, "t"))
        .containsEntry("a_id", "ref[a.id, 1..count, unique=uq1]")
        .containsEntry("slot", "unique[uq1, 1..count]");
  }

  @Test
  void shouldLetPrimaryKeyWinOverlapAndWarn(@TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (id BIGINT PRIMARY KEY UNIQUE);");
    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial");
    assertThat(in.warnings()).contains("t.id: also in UNIQUE(id) — only the first key is enforced");
  }

  @Test
  void shouldLeaveNonIntegerUniqueUnchangedWithCommentAndWarning(@TempDir Path dir)
      throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (id BIGINT PRIMARY KEY, sku VARCHAR(20) UNIQUE);");
    assertThat(datatypesOf(in, "t")).containsEntry("sku", "char[1..20]");
    assertThat(in.comments().get("t")).containsEntry("sku", NOT_ENFORCED);
    assertThat(in.warnings()).anyMatch(w -> w.contains("UNIQUE(sku) not enforced"));
  }

  @Test
  void shouldLeaveUuidPrimaryKeyUncommented(@TempDir Path dir) throws IOException {
    Inspection in = inspect(dir, "CREATE TABLE t (id UUID PRIMARY KEY, n INT);");
    assertThat(datatypesOf(in, "t")).doesNotContainEntry("id", "serial");
    assertThat(in.comments().getOrDefault("t", Map.of())).doesNotContainKey("id");
    assertThat(in.warnings()).noneMatch(w -> w.contains("not enforced"));
  }

  @Test
  void shouldNotTouchFoldedChildInNestedMode(@TempDir Path dir) throws IOException {
    Inspection in =
        new DdlInspector()
            .inspect(
                write(
                    dir,
                    """
                    CREATE TABLE p (id BIGINT PRIMARY KEY);
                    CREATE TABLE c (
                      id BIGINT PRIMARY KEY,
                      p_id BIGINT,
                      FOREIGN KEY (p_id) REFERENCES p(id)
                    );
                    """),
                NestingOptions.parse("auto", null));
    assertThat(datatypesOf(in, "p")).containsEntry("id", "serial");
    assertThat(datatypesOf(in, "c")).containsEntry("id", "int[1..999999]");
  }

  @Test
  void shouldEmitOnlyDatatypesTypeParserAccepts(@TempDir Path dir) throws IOException {
    Inspection in =
        inspect(
            dir,
            """
            CREATE TABLE a (id BIGINT PRIMARY KEY, code INT UNIQUE, sku VARCHAR(9) UNIQUE);
            CREATE TABLE b (id SERIAL PRIMARY KEY, seq BIGSERIAL);
            CREATE TABLE ab (
              a_id BIGINT REFERENCES a(id),
              b_id BIGINT REFERENCES b(id),
              slot INT,
              PRIMARY KEY (a_id, b_id),
              UNIQUE (slot)
            );
            CREATE TABLE one (a_id BIGINT PRIMARY KEY REFERENCES a(id));
            """);
    TypeParser parser = new TypeParser();
    for (DataStructure s : in.structures()) {
      s.getData()
          .forEach(
              (field, def) ->
                  assertThatCode(() -> parser.parse(def.getDatatype()))
                      .as(s.getName() + "." + field + " = " + def.getDatatype())
                      .doesNotThrowAnyException());
    }
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
