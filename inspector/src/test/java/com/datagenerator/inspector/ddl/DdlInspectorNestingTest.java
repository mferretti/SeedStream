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

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdlInspectorNestingTest {

  private static final String CHAIN =
      """
      CREATE TABLE customer (
        id   BIGINT PRIMARY KEY,
        name VARCHAR(120)
      );
      CREATE TABLE invoice (
        id          BIGINT PRIMARY KEY,
        customer_id BIGINT,
        total       DECIMAL(10,2),
        CONSTRAINT fk_cust FOREIGN KEY (customer_id) REFERENCES customer(id)
      );
      CREATE TABLE invoice_item (
        id          BIGINT PRIMARY KEY,
        invoice_id  BIGINT,
        description VARCHAR(200),
        CONSTRAINT fk_inv FOREIGN KEY (invoice_id) REFERENCES invoice(id)
      );
      """;

  @Test
  void defaultInspectKeepsFlatRefs(@TempDir Path dir) throws IOException {
    Inspection flat = inspect(dir, CHAIN, NestingOptions.none());
    assertThat(datatypes(flat, "invoice")).containsEntry("customer_id", "ref[customer.id]");
    assertThat(datatypes(flat, "customer")).doesNotContainKey("invoices");
  }

  @Test
  void nestInvertsFksIntoNestedArrays(@TempDir Path dir) throws IOException {
    Inspection nested = inspect(dir, CHAIN, NestingOptions.parse("auto", null));

    assertThat(datatypes(nested, "customer"))
        .containsEntry("invoices", "array[object[invoice], 1..10]");
    assertThat(datatypes(nested, "invoice"))
        .containsEntry("invoice_items", "array[object[invoice_item], 1..10]")
        .doesNotContainKey("customer_id");
    assertThat(datatypes(nested, "invoice_item")).doesNotContainKey("invoice_id");
  }

  @Test
  void uniqueForeignKeyNestsAsObject(@TempDir Path dir) throws IOException {
    String oneToOne =
        """
        CREATE TABLE app_user (
          id   BIGINT PRIMARY KEY,
          name VARCHAR(80)
        );
        CREATE TABLE profile (
          id      BIGINT PRIMARY KEY,
          user_id BIGINT UNIQUE,
          bio     VARCHAR(200),
          CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES app_user(id)
        );
        """;
    Inspection nested = inspect(dir, oneToOne, NestingOptions.parse("auto", null));

    assertThat(datatypes(nested, "app_user")).containsEntry("profile", "object[profile]");
    assertThat(datatypes(nested, "profile")).doesNotContainKey("user_id");
  }

  @Test
  void everyNestedDatatypeParses(@TempDir Path dir) throws IOException {
    Inspection nested = inspect(dir, CHAIN, NestingOptions.parse("auto", null));
    TypeParser parser = new TypeParser();
    for (DataStructure structure : nested.structures()) {
      structure
          .getData()
          .values()
          .forEach(
              f ->
                  assertThatCode(() -> parser.parse(f.getDatatype()))
                      .as("nested datatype must parse: %s", f.getDatatype())
                      .doesNotThrowAnyException());
    }
  }

  private Inspection inspect(Path dir, String ddl, NestingOptions opts) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return new DdlInspector().inspect(sql, opts);
  }

  private Map<String, String> datatypes(Inspection inspection, String structureName) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(structureName))
            .findFirst()
            .orElseThrow();
    return structure.getData().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype(), (a, b) -> a));
  }
}
