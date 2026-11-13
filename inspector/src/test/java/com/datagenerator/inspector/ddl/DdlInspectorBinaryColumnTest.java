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
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * #439 phase A: binary columns have no SeedStream type and the {@code char[1..50]} fallback cannot
 * be inserted into them. Nullable ones (or ones with a DEFAULT) are omitted so the database fills
 * them; NOT NULL ones are kept but warned about.
 */
class DdlInspectorBinaryColumnTest {

  private static final String FALLBACK = "char[1..50]";

  /** SQL type as written, and a fragment (matched ignoring case) the warning must show. */
  static Stream<Arguments> binaryTypes() {
    return Stream.of(
        Arguments.of("BYTEA", "BYTEA"),
        Arguments.of("bytea", "BYTEA"),
        Arguments.of("BLOB", "BLOB"),
        Arguments.of("TINYBLOB", "TINYBLOB"),
        Arguments.of("MEDIUMBLOB", "MEDIUMBLOB"),
        Arguments.of("LONGBLOB", "LONGBLOB"),
        Arguments.of("BINARY(16)", "BINARY(16)"),
        Arguments.of("VARBINARY(16)", "VARBINARY(16)"),
        Arguments.of("VARBINARY(max)", "VARBINARY(max)"),
        Arguments.of("IMAGE", "IMAGE"),
        Arguments.of("LONG RAW", "LONG RAW"),
        // the preprocessor rewrites RAW(n) to VARBINARY(n); either spelling is fine in the warning
        Arguments.of("RAW(16)", "RAW(16)|VARBINARY(16)"));
  }

  /** Regex matching any of the {@code |}-separated type spellings literally. */
  private static String typeRegex(String shown) {
    return Stream.of(shown.split("\\|"))
        .map(Pattern::quote)
        .collect(Collectors.joining("|", "(?:", ")"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("binaryTypes")
  void shouldOmitNullableBinaryColumnAndWarn(String type, String shown, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE t (id INT PRIMARY KEY, code VARCHAR(10), data " + type + ");");

    assertThat(datatypesOf(in, "t")).doesNotContainKey("data").containsEntry("code", "char[1..10]");
    assertThat(in.comments().getOrDefault("t", Map.of())).doesNotContainKey("data");
    assertThat(in.warnings())
        .filteredOn(w -> w.startsWith("t.data: "))
        .singleElement(STRING)
        .matches(
            "(?is)t\\.data: binary column \\("
                + typeRegex(shown)
                + "\\) omitted, stored as NULL \\(#439\\)");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("binaryTypes")
  void shouldKeepNotNullBinaryColumnAsFallbackAndWarn(
      String type, String shown, @TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id INT PRIMARY KEY, code VARCHAR(10), data " + type + " NOT NULL);");

    assertThat(datatypesOf(in, "t")).containsEntry("data", FALLBACK);
    assertThat(in.warnings())
        .filteredOn(w -> w.startsWith("t.data: "))
        .singleElement(STRING)
        .matches(
            "(?is)t\\.data: binary column \\("
                + typeRegex(shown)
                + "\\) is NOT NULL; the generated text will not insert"
                + " — needs a bytes type \\(#439\\)");
  }

  @Test
  void shouldKeepBinaryColumnMadeNotNullByLaterAlterSetNotNull(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id INT PRIMARY KEY, data BYTEA);"
                + "ALTER TABLE t ALTER COLUMN data SET NOT NULL;");

    assertThat(datatypesOf(in, "t")).containsEntry("data", FALLBACK);
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("t.data: ") && w.contains("is NOT NULL") && w.contains("#439"))
        .noneMatch(w -> w.contains("omitted, stored as NULL"));
  }

  @Test
  void shouldKeepBinaryColumnMadeNotNullByLaterOracleModify(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE "S"."T" ("ID" NUMBER(10), "DATA" RAW(16));
            ALTER TABLE "S"."T" ADD CONSTRAINT "T_PK" PRIMARY KEY ("ID");
            ALTER TABLE "S"."T" MODIFY ("DATA" NOT NULL ENABLE);
            """);

    assertThat(datatypesOf(in, "t")).containsEntry("DATA", FALLBACK);
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("t.DATA: ") && w.contains("is NOT NULL") && w.contains("#439"))
        .noneMatch(w -> w.contains("omitted, stored as NULL"));
  }

  @Test
  void shouldKeepBinaryPrimaryKeyAndWarnNotNull(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (k BYTEA PRIMARY KEY, code VARCHAR(10));");

    assertThat(datatypesOf(in, "t")).containsKey("k").containsKey("code");
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("t.k: ") && w.contains("is NOT NULL") && w.contains("#439"))
        .noneMatch(w -> w.contains("omitted"));
  }

  @Test
  void shouldKeepBinaryColumnInCompositePrimaryKey(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (a INT, k BYTEA, PRIMARY KEY (a, k));");

    assertThat(datatypesOf(in, "t")).containsKey("k");
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("t.k: ") && w.contains("is NOT NULL") && w.contains("#439"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "data BYTEA DEFAULT '\\x00'",
        "data BLOB DEFAULT NULL",
        "data VARBINARY(16) DEFAULT 'ab'",
        "data VARBINARY(16) NOT NULL DEFAULT 'ab'",
        "data BYTEA NOT NULL DEFAULT '\\x00'"
      })
  void shouldOmitBinaryColumnWithDefaultAndSayTheDatabaseFillsIt(String column, @TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id INT PRIMARY KEY, code VARCHAR(10), " + column + ");");

    assertThat(datatypesOf(in, "t")).doesNotContainKey("data").containsEntry("code", "char[1..10]");
    assertThat(in.warnings())
        .filteredOn(w -> w.startsWith("t.data: "))
        .singleElement(STRING)
        .matches(
            "(?is)t\\.data: binary column \\(\\w+(\\(\\d+\\))?\\) omitted,"
                + " the database fills its DEFAULT \\(#439\\)");
  }

  @Test
  void shouldKeepBinaryColumnThatIsAForeignKeyTarget(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE p (id INT PRIMARY KEY, k BYTEA UNIQUE);"
                + "CREATE TABLE c (id INT PRIMARY KEY, pk BYTEA REFERENCES p (k));");

    assertThat(datatypesOf(in, "p")).containsKey("k");
    assertThat(datatypesOf(in, "c")).containsKey("pk");
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("p.k: ") && w.contains("is NOT NULL") && w.contains("#439"))
        .anyMatch(w -> w.startsWith("c.pk: ") && w.contains("is NOT NULL") && w.contains("#439"))
        .noneMatch(w -> w.contains("binary column") && w.contains("omitted"));
  }

  @Test
  void shouldLoadStructureWithOmittedBinaryColumnsWithTheRealParser(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE cat (
              id INT PRIMARY KEY,
              code VARCHAR(10),
              picture BYTEA,
              stamp BYTEA DEFAULT '\\x00'
            );
            CREATE TABLE item (id INT PRIMARY KEY, cat_id INT REFERENCES cat (id), raw_data BLOB);
            """);

    Map<String, DataStructure> loaded = loadAll(in, dir);

    assertThat(loaded.get("cat").getData().keySet()).containsExactly("id", "code");
    assertThat(loaded.get("item").getData().keySet()).containsExactly("id", "cat_id");
  }

  @Test
  void shouldNotTouchNonBinaryColumnsWhoseNamesLookBinary(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id INT PRIMARY KEY, blob_note VARCHAR(20), image_url TEXT,"
                + " raw_total INT);");

    assertThat(datatypesOf(in, "t")).containsKeys("blob_note", "image_url", "raw_total");
    assertThat(in.warnings()).noneMatch(w -> w.contains("#439"));
  }

  @Test
  void shouldBeNoOpForSchemaWithoutBinaryColumns(@TempDir Path dir) {
    List<String> warnings =
        inspectDdl(dir, "CREATE TABLE t (id INT PRIMARY KEY, code VARCHAR(10));").warnings();

    assertThat(warnings).noneMatch(w -> w.contains("#439"));
  }
}
