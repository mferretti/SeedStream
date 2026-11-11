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

import static com.datagenerator.inspector.InspectionTestSupport.assertEngineLoadable;
import static com.datagenerator.inspector.InspectionTestSupport.datatypesOf;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests on the vendor Northwind exports in {@code src/test/resources/northwind} (#447,
 * #448). Best-effort mode: other tables of these scripts are owned by other fixes.
 */
class DdlInspectorNorthwindTypesTest {

  private static final Path ROOT = Path.of("src/test/resources/northwind");
  private static final String UNRECOGNIZED = "unrecognized source type";

  private final DdlInspector inspector = new DdlInspector();

  private Inspection inspectBestEffort(String relative) {
    return inspector.inspect(ROOT.resolve(relative), NestingOptions.none(), true);
  }

  @Test
  void shouldMapEverySqlServerTypeExceptBinaryImageColumns() {
    Inspection inspection = inspectBestEffort("sqlserver/instnwnd-schema.sql");

    List<String> unrecognized = new ArrayList<>();
    inspection
        .comments()
        .forEach(
            (table, fields) ->
                fields.forEach(
                    (field, comment) -> {
                      if (comment.contains(UNRECOGNIZED)) {
                        unrecognized.add(table + "." + field);
                      }
                    }));
    // image columns are #439, out of scope
    assertThat(unrecognized).isSubsetOf("employees.Photo", "categories.Picture");
  }

  @Test
  void shouldMapQuotedSqlServerTypesToTheirRealDatatypes() {
    Inspection inspection = inspectBestEffort("sqlserver/instnwnd-schema.sql");

    assertThat(datatypesOf(inspection, "orders").get("OrderDate"))
        .matches("(timestamp|date)\\[.*\\]");
    assertThat(datatypesOf(inspection, "products").get("UnitPrice")).startsWith("decimal[");
    assertThat(datatypesOf(inspection, "products").get("Discontinued")).isEqualTo("boolean");
    assertThat(datatypesOf(inspection, "employees").get("EmployeeID"))
        .isEqualTo("serial[1..2147483647]");
    assertThat(datatypesOf(inspection, "orders").get("OrderID")).isEqualTo("serial[1..2147483647]");
  }

  @Test
  void shouldMapOracleScaleZeroNumbersToIntegersAndKeepStringLengths() throws IOException {
    Inspection inspection = inspectBestEffort("oracle/northwind-oracle-schema.sql");

    long declared =
        countOccurrences(ROOT.resolve("oracle/northwind-oracle-schema.sql"), "NUMBER\\(9,0\\)");
    long asInteger =
        inspection.structures().stream()
            .flatMap(s -> s.getData().values().stream())
            .map(f -> f.getDatatype())
            // keys declared via ALTER TABLE (#376) are integers rendered as serial / ref[...];
            // a CHECK (#449) can narrow the range, e.g. int[0..999999] from CHECK (x >= 0)
            .filter(d -> d.startsWith("int[") || d.startsWith("serial[") || d.startsWith("ref["))
            .count();
    assertThat(declared).as("fixture sanity: NUMBER(9,0) columns").isEqualTo(19);
    assertThat(asInteger).as("every NUMBER(9,0) column is an integer").isEqualTo(declared);
    assertThat(datatypesOf(inspection, "categories"))
        .containsEntry("CATEGORY_ID", "serial[1..999999999]")
        .containsEntry("CATEGORY_NAME", "unique[1..count]");
    assertThat(datatypesOf(inspection, "products"))
        .containsEntry("CATEGORY_ID", "ref[categories.category_id]");
  }

  @Test
  void shouldEmitLoadableEmptyStringTypeForMysqlZeroLengthColumn() {
    Inspection inspection = inspectBestEffort("mysql/northwind-mysql-schema.sql");

    assertThat(datatypesOf(inspection, "employee")).containsEntry("photo", "char[0..0]");
  }

  @Test
  void shouldWriteMysqlStructuresThatTheEngineParsersAccept(@TempDir Path dir) {
    Inspection inspection = inspectBestEffort("mysql/northwind-mysql-schema.sql");
    StructureYamlWriter writer = new StructureYamlWriter();
    DataStructureParser parser = new DataStructureParser();

    for (DataStructure structure : inspection.structures()) {
      writer.write(
          structure, dir, true, inspection.comments().getOrDefault(structure.getName(), Map.of()));
      DataStructure loaded = parser.parse(dir.resolve(structure.getName() + ".yaml"));
      assertThat(loaded.getData()).isNotEmpty();
      loaded
          .getData()
          .forEach(
              (field, def) -> {
                assertThat(def.getDatatype()).as("%s.%s", structure.getName(), field).isNotBlank();
                assertEngineLoadable(def.getDatatype());
              });
    }
  }

  private static long countOccurrences(Path file, String regex) throws IOException {
    Matcher m = Pattern.compile(regex).matcher(Files.readString(file));
    long count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }
}
