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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests running the real vendor fixtures through strict-mode DDL inspection. */
class DdlInspectorNorthwindFixturesTest {

  private static final Path FIXTURES = Path.of("src/test/resources/northwind");

  @Test
  void shouldInspectPostgresDumpInStrictModeToThirteenStructures(@TempDir Path dir) {
    Inspection inspection = strict("postgres/northwind-pg-dump.sql");

    assertThat(names(inspection))
        .hasSize(13)
        .contains("categories", "customer_customer_demo", "orders", "order_details", "territories");
    assertLoadable(inspection, dir);
  }

  @Test
  void shouldInspectPostgresNonTableStatementsToBaseAndPartitionedParent(@TempDir Path dir) {
    Inspection inspection = strict("postgres/pg-non-table-statements.sql");

    // 'events' is a real (partitioned) table with columns, so it is modelled; only the CTAS table
    // and the partition child are skipped with a warning.
    assertThat(names(inspection)).containsExactly("base", "events");
    assertThat(inspection.warnings())
        .hasSize(2)
        .anyMatch(w -> w.contains("ledgers_copy"))
        .anyMatch(w -> w.contains("events_2026"));
    assertLoadable(inspection, dir);
  }

  @Test
  void shouldKeepCustomerCustomerDemoFromSqlServerScript() {
    // Best-effort: other SQL Server problems (quoted type names, IDENTITY) belong to other work
    // packages and would abort strict mode; this test only pins the table lost to empty-line runs.
    Inspection inspection =
        new DdlInspector()
            .inspect(
                FIXTURES.resolve("sqlserver/instnwnd-schema.sql"), NestingOptions.none(), true);

    assertThat(names(inspection)).contains("customer_customer_demo");
  }

  private static Inspection strict(String fixture) {
    return new DdlInspector().inspect(FIXTURES.resolve(fixture), NestingOptions.none(), false);
  }

  private static List<String> names(Inspection inspection) {
    return inspection.structures().stream().map(DataStructure::getName).toList();
  }

  /** Writes every structure with the normal writer and loads it back with the real parser. */
  private static void assertLoadable(Inspection inspection, Path dir) {
    StructureYamlWriter writer = new StructureYamlWriter();
    for (DataStructure structure : inspection.structures()) {
      Map<String, String> comments =
          inspection.comments().getOrDefault(structure.getName(), Map.of());
      writer.write(structure, dir, true, comments);
      DataStructure loaded =
          new DataStructureParser().parse(dir.resolve(structure.getName() + ".yaml"));
      assertThat(loaded.getData().keySet())
          .as("fields of %s", structure.getName())
          .containsExactlyElementsOf(structure.getData().keySet());
    }
  }
}
