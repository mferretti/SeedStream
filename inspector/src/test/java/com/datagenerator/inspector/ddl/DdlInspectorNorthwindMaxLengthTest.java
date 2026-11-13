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

import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * #440: the hinted Northwind columns that overflowed their {@code varchar(n)} now carry {@code
 * max_length} = n on every vendor fixture; the Oracle fixture declares {@code VARCHAR2(n BYTE)}, so
 * its unit is bytes.
 */
class DdlInspectorNorthwindMaxLengthTest {

  private static final Path ROOT = Path.of("src/test/resources/northwind");

  private static final String PG = "postgres/northwind-pg-dump.sql";
  private static final String MYSQL = "mysql/northwind-mysql-schema.sql";
  private static final String MSSQL = "sqlserver/instnwnd-schema.sql";
  private static final String ORACLE = "oracle/northwind-oracle-schema.sql";

  @ParameterizedTest
  @CsvSource({
    PG + ", customer, city, 15, false",
    PG + ", customer, country, 15, false",
    PG + ", customer, address, 60, false",
    PG + ", employee, title, 30, false",
    PG + ", employee, first_name, 10, false",
    MYSQL + ", customer, city, 15, false",
    MYSQL + ", customer, country, 15, false",
    MYSQL + ", customer, address, 60, false",
    MYSQL + ", employee, title, 30, false",
    MYSQL + ", employee, first_name, 10, false",
    MSSQL + ", customer, city, 15, false",
    MSSQL + ", customer, country, 15, false",
    MSSQL + ", customer, address, 60, false",
    MSSQL + ", employee, title, 30, false",
    MSSQL + ", employee, first_name, 10, false",
    ORACLE + ", customer, city, 15, true",
    ORACLE + ", customer, country, 15, true",
    ORACLE + ", customer, address, 60, true",
    ORACLE + ", employee, title, 30, true",
    ORACLE + ", employee, first_name, 10, true"
  })
  void shouldCapHintedColumnAtItsDeclaredLengthInTheVendorsUnit(
      String fixture, String table, String column, int cap, boolean bytes, @TempDir Path dir) {
    Inspection inspection =
        new DdlInspector().inspect(ROOT.resolve(fixture), NestingOptions.none(), true);

    FieldDefinition field = find(inspection, table, column);

    assertThat(field.getMaxLength())
        .as("%s %s.%s max_length", fixture, table, column)
        .isEqualTo(cap);
    assertThat(field.isLengthInBytes())
        .as("%s %s.%s unit", fixture, table, column)
        .isEqualTo(bytes);
    assertThat(field.getDatatype()).as("still the hint, not char[...]").doesNotStartWith("char[");
  }

  @ParameterizedTest
  @CsvSource({PG, MYSQL, MSSQL, ORACLE})
  void shouldWriteFixtureStructuresThatTheRealParserLoads(String fixture, @TempDir Path dir) {
    Inspection inspection =
        new DdlInspector().inspect(ROOT.resolve(fixture), NestingOptions.none(), true);

    Map<String, DataStructure> loaded = loadAll(inspection, dir);

    assertThat(loaded).isNotEmpty();
    assertThat(loaded.values())
        .flatExtracting(s -> s.getData().values())
        .anyMatch(f -> f.getMaxLength() != null);
  }

  /** Looks a structure and a field up ignoring case, underscores and a plural 's'. */
  private static FieldDefinition find(Inspection inspection, String table, String column) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> squash(s.getName()).replaceAll("s$", "").equals(table))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no structure for " + table));
    return structure.getData().entrySet().stream()
        .filter(e -> squash(e.getKey()).equals(squash(column)))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no field " + column + " in " + table));
  }

  private static String squash(String name) {
    return name.toLowerCase(Locale.ROOT).replace("_", "");
  }
}
