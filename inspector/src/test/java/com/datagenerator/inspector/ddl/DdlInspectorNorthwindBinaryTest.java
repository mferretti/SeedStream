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
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #439: the real Northwind exports that have binary columns (PostgreSQL bytea, SQL Server image)
 * lose exactly those columns; the Oracle and MySQL exports declare them as VARCHAR and are covered
 * by the other Northwind tests.
 */
class DdlInspectorNorthwindBinaryTest {

  private static final Path ROOT = Path.of("src/test/resources/northwind");

  private static Inspection inspect(String relative, boolean bestEffort) {
    return new DdlInspector().inspect(ROOT.resolve(relative), NestingOptions.none(), bestEffort);
  }

  @Test
  void shouldOmitPictureAndPhotoFromPostgresDump(@TempDir Path dir) {
    Inspection in = inspect("postgres/northwind-pg-dump.sql", false);

    assertThat(datatypesOf(in, "categories"))
        .doesNotContainKey("picture")
        .containsKeys("category_id", "category_name", "description");
    assertThat(datatypesOf(in, "employees"))
        .doesNotContainKey("photo")
        .containsKeys("employee_id", "last_name", "photo_path")
        .containsEntry("photo_path", "char[1..255]");
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("categories.picture: ") && w.contains("omitted"))
        .anyMatch(w -> w.startsWith("employees.photo: ") && w.contains("omitted"));
    loadAll(in, dir);
  }

  @Test
  void shouldOmitPictureAndPhotoFromPostgresSource() {
    Inspection in = inspect("postgres/northwind-pg-source.sql", false);

    assertThat(datatypesOf(in, "categories"))
        .doesNotContainKey("picture")
        .containsKey("description");
    assertThat(datatypesOf(in, "employees")).doesNotContainKey("photo").containsKey("photo_path");
  }

  @Test
  void shouldOmitPictureAndPhotoFromSqlServerExport() {
    Inspection in = inspect("sqlserver/instnwnd-schema.sql", true);

    assertThat(datatypesOf(in, "categories"))
        .doesNotContainKey("Picture")
        .containsKeys("CategoryID", "CategoryName", "Description");
    assertThat(datatypesOf(in, "employees"))
        .doesNotContainKey("Photo")
        .containsKeys("EmployeeID", "LastName", "PhotoPath")
        .containsEntry("PhotoPath", "char[1..255]");
    assertThat(in.warnings())
        .anyMatch(w -> w.startsWith("categories.Picture: ") && w.contains("omitted"))
        .anyMatch(w -> w.startsWith("employees.Photo: ") && w.contains("omitted"));
  }

  @Test
  void shouldNotMentionBinaryColumnsForOracleAndMysqlExports() {
    assertThat(inspect("oracle/northwind-oracle-schema.sql", true).warnings())
        .noneMatch(w -> w.contains("#439"));
    assertThat(inspect("mysql/northwind-mysql-schema.sql", true).warnings())
        .noneMatch(w -> w.contains("#439"));
  }
}
