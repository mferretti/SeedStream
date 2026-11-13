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

import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * #440 part 1: a name-hinted text column keeps its declared length as {@code max_length}, in the
 * unit the type text implies ({@code BYTE} for Oracle's byte-semantics spellings, else chars).
 */
class DdlInspectorMaxLengthTest {

  @TempDir Path dir;

  private FieldDefinition field(String ddl, String column) {
    Inspection inspection = inspectDdl(dir, ddl);
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals("t"))
            .findFirst()
            .orElseThrow();
    return structure.getData().get(column);
  }

  private FieldDefinition hintedColumn(String sqlType) {
    return field("CREATE TABLE t (id INT PRIMARY KEY, city " + sqlType + ");", "city");
  }

  @ParameterizedTest
  @CsvSource({
    // PostgreSQL / ANSI
    "VARCHAR(15), 15, false",
    "character varying(15), 15, false",
    "CHAR(15), 15, false",
    "CHARACTER(15), 15, false",
    // MySQL / SQL Server
    "NVARCHAR(15), 15, false",
    "NCHAR(15), 15, false",
    // Oracle with an explicit unit
    "VARCHAR2(15 BYTE), 15, true",
    "CHAR(5 BYTE), 5, true",
    "VARCHAR2(15 CHAR), 15, false",
    "CHAR(5 CHAR), 5, false",
    // Oracle bare VARCHAR2: NLS_LENGTH_SEMANTICS defaults to BYTE
    "VARCHAR2(15), 15, true"
  })
  void shouldKeepHintAndCapAtDeclaredLengthInTheUnitTheTypeImplies(
      String sqlType, int cap, boolean bytes) {
    FieldDefinition city = hintedColumn(sqlType);

    assertThat(city.getDatatype()).isEqualTo("city");
    assertThat(city.getMaxLength()).isEqualTo(cap);
    assertThat(city.isLengthInBytes()).isEqualTo(bytes);
  }

  @Test
  void shouldCapNvarchar2AtDeclaredLength() {
    // NVARCHAR2 counts characters in Oracle; the spec's "bare Oracle spelling => bytes" is
    // ambiguous for it, so only the cap is asserted here, not the unit.
    FieldDefinition city = hintedColumn("NVARCHAR2(15)");

    assertThat(city.getDatatype()).isEqualTo("city");
    assertThat(city.getMaxLength()).isEqualTo(15);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"TEXT", "VARCHAR", "VARCHAR(MAX)", "NVARCHAR(MAX)", "CLOB", "LONGTEXT", "NTEXT"})
  void shouldKeepHintWithoutCapWhenNoLengthIsDeclared(String sqlType) {
    FieldDefinition city = hintedColumn(sqlType);

    assertThat(city.getDatatype()).isEqualTo("city");
    assertThat(city.getMaxLength()).isNull();
    assertThat(city.isLengthInBytes()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"VARCHAR(15)", "VARCHAR2(15 BYTE)", "VARCHAR2(15)", "CHAR(15)"})
  void shouldKeepCharRangeWithoutCapWhenColumnIsNotHinted(String sqlType) {
    FieldDefinition label =
        field("CREATE TABLE t (id INT PRIMARY KEY, widget_label " + sqlType + ");", "widget_label");

    assertThat(label.getDatatype()).isEqualTo("char[1..15]");
    assertThat(label.getMaxLength()).isNull();
    assertThat(label.isLengthInBytes()).isFalse();
  }

  @Test
  void shouldKeepEmptyCharRangeWithoutCapForZeroLengthHintedColumn() {
    FieldDefinition city = hintedColumn("VARCHAR(0)");

    assertThat(city.getDatatype()).isEqualTo("char[0..0]");
    assertThat(city.getMaxLength()).isNull();
  }

  @Test
  void shouldKeepGuessedFromColumnNameComment() {
    Inspection inspection =
        inspectDdl(dir, "CREATE TABLE t (id INT PRIMARY KEY, city VARCHAR(15));");

    assertThat(inspection.comments().get("t").get("city")).contains("guessed from column name");
  }

  @Test
  void shouldCapEveryHintedColumnOfATableAtItsOwnLength() {
    Inspection inspection =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (
              id INT PRIMARY KEY,
              first_name VARCHAR(10),
              city VARCHAR(15),
              address VARCHAR(60),
              email VARCHAR(120)
            );
            """);
    Map<String, FieldDefinition> data =
        inspection.structures().stream()
            .filter(s -> s.getName().equals("t"))
            .findFirst()
            .orElseThrow()
            .getData();

    assertThat(data.get("first_name").getMaxLength()).isEqualTo(10);
    assertThat(data.get("city").getMaxLength()).isEqualTo(15);
    assertThat(data.get("address").getMaxLength()).isEqualTo(60);
    assertThat(data.get("email").getMaxLength()).isEqualTo(120);
  }

  @Test
  void shouldWriteCappedStructuresThatTheRealParserLoadsWithCapAndUnit() {
    Inspection inspection =
        inspectDdl(
            dir,
            """
            CREATE TABLE t (
              id NUMBER(9,0) PRIMARY KEY,
              city VARCHAR2(15 BYTE),
              country VARCHAR2(15 CHAR),
              address VARCHAR2(60)
            );
            """);

    Map<String, DataStructure> loaded = loadAll(inspection, dir.resolve("out"));

    Map<String, FieldDefinition> data = loaded.get("t").getData();
    assertThat(data.get("city").getMaxLength()).isEqualTo(15);
    assertThat(data.get("city").isLengthInBytes()).isTrue();
    assertThat(data.get("country").getMaxLength()).isEqualTo(15);
    assertThat(data.get("country").isLengthInBytes()).isFalse();
    assertThat(data.get("address").getMaxLength()).isEqualTo(60);
    assertThat(data.get("address").isLengthInBytes()).isTrue();
  }

  @Test
  void shouldNotLeaveCapOnAColumnWhoseTypeACheckInListReplaced() {
    Inspection inspection =
        inspectDdl(
            dir,
            "CREATE TABLE t (id INT PRIMARY KEY, city VARCHAR(15) CHECK (city IN ('Rome','Turin')));");

    Map<String, DataStructure> loaded = loadAll(inspection, dir.resolve("out"));

    assertThat(loaded.get("t").getData().get("city").getDatatype()).isEqualTo("enum[Rome,Turin]");
    assertThat(loaded.get("t").getData().get("city").getMaxLength()).isNull();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "CREATE TABLE t (city VARCHAR(15) PRIMARY KEY, n INT); | t | serial[",
        "CREATE TABLE p (code VARCHAR(15) PRIMARY KEY);"
            + " CREATE TABLE t (id INT PRIMARY KEY, city VARCHAR(15) REFERENCES p(code)); | t | ref["
      })
  void shouldNotLeaveCapOnHintedColumnsTheInspectorRewritesAsKeys(
      String ddl, String table, String prefix) {
    Inspection inspection = inspectDdl(dir, ddl);

    Map<String, DataStructure> loaded = loadAll(inspection, dir.resolve("out"));

    FieldDefinition city = loaded.get(table).getData().get("city");
    assertThat(city.getDatatype()).startsWith(prefix);
    assertThat(city.getMaxLength()).isNull();
    assertThat(city.isLengthInBytes()).isFalse();
  }
}
