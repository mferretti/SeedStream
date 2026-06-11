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
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdlInspectorTest {

  private static final String DDL =
      """
      CREATE TABLE customers (
        id            BIGINT PRIMARY KEY,
        email         VARCHAR(255),
        full_name     VARCHAR(120),
        nickname      VARCHAR(40),
        is_active     BOOLEAN,
        signup_date   DATE,
        created_at    TIMESTAMP,
        balance       DECIMAL(10,2),
        bio           TEXT
      );

      CREATE TABLE orders (
        id          BIGINT PRIMARY KEY,
        customer_id BIGINT,
        product_id  BIGINT REFERENCES products(sku),
        CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
      );
      """;

  @Test
  void shouldMapDdlColumnTypes(@TempDir Path dir) throws IOException {
    Map<String, String> customer = datatypesOf(inspect(dir), "customers");

    assertThat(customer)
        .containsEntry("email", "datafaker[internet.emailAddress]")
        .containsEntry("nickname", "char[1..40]")
        .containsEntry("is_active", "boolean")
        .containsEntry("signup_date", "date[2020-01-01..2030-12-31]")
        .containsEntry("created_at", "timestamp[now-1y..now]")
        .containsEntry("balance", "decimal[0.0..9999.99]")
        .containsEntry("bio", "char[1..500]")
        .containsEntry("id", "int[1..999999]");
  }

  @Test
  void shouldMapTableLevelForeignKeyToRef(@TempDir Path dir) throws IOException {
    assertThat(datatypesOf(inspect(dir), "orders"))
        .containsEntry("customer_id", "ref[customers.id]");
  }

  @Test
  void shouldMapInlineForeignKeyToRef(@TempDir Path dir) throws IOException {
    assertThat(datatypesOf(inspect(dir), "orders"))
        .containsEntry("product_id", "ref[products.sku]");
  }

  @Test
  void shouldFlagUnboundedNumericAsInferred(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);
    assertThat(inspection.inferredFields())
        .contains("customers.balance", "customers.id", "customers.bio");
  }

  @Test
  void shouldFailWhenNoCreateTable(@TempDir Path dir) throws IOException {
    Path sql = dir.resolve("noddl.sql");
    Files.writeString(sql, "SELECT 1;");

    assertThatThrownBy(() -> new DdlInspector().inspect(sql))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("No CREATE TABLE");
  }

  private Inspection inspect(Path dir) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, DDL);
    return new DdlInspector().inspect(sql);
  }

  private Map<String, String> datatypesOf(Inspection inspection, String structureName) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(structureName))
            .findFirst()
            .orElseThrow();
    return structure.getData().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype(), (a, b) -> a));
  }
}
