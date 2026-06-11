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

package com.datagenerator.inspector;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.inspector.ddl.DdlInspector;
import com.datagenerator.inspector.openapi.OpenApiInspector;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the contract that every datatype the inspector emits is accepted by the engine's {@link
 * TypeParser}. This is what caught the {@code datafaker[...]} bracket bug: such strings parse-fail.
 */
class TypeParserRoundTripTest {

  private static final String OPENAPI =
      """
      openapi: 3.0.3
      info: {title: t, version: "1"}
      components:
        schemas:
          Person:
            type: object
            properties:
              id: {type: string, format: uuid}
              contactEmail: {type: string}
              firstName: {type: string}
              homeCity: {type: string}
              website: {type: string}
              age: {type: integer, minimum: 0, maximum: 120}
              score: {type: number}
              active: {type: boolean}
              born: {type: string, format: date}
              tags:
                type: array
                items: {type: string}
      """;

  private static final String DDL =
      """
      CREATE TABLE accounts (
        id BIGINT,
        email VARCHAR(255),
        phone VARCHAR(20),
        city VARCHAR(80),
        balance DECIMAL(10,2),
        active BOOLEAN,
        created_at TIMESTAMP,
        bio TEXT
      );
      """;

  @Test
  void everyOpenApiDatatypeParses(@TempDir Path dir) throws IOException {
    Path spec = dir.resolve("api.yaml");
    Files.writeString(spec, OPENAPI);
    assertAllParse(new OpenApiInspector().inspect(spec).structures());
  }

  @Test
  void everyDdlDatatypeParses(@TempDir Path dir) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, DDL);
    assertAllParse(new DdlInspector().inspect(sql).structures());
  }

  private void assertAllParse(Iterable<DataStructure> structures) {
    TypeParser parser = new TypeParser();
    for (DataStructure structure : structures) {
      for (FieldDefinition field : structure.getData().values()) {
        String datatype = field.getDatatype();
        assertThatCode(() -> parser.parse(datatype))
            .as("emitted datatype must be parseable: %s", datatype)
            .doesNotThrowAnyException();
      }
    }
  }
}
