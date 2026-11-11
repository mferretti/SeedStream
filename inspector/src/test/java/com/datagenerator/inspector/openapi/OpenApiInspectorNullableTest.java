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

package com.datagenerator.inspector.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #492: OpenAPI 3.0 {@code nullable: true} and 3.1 {@code type: [.., "null"]} give 0.05. */
class OpenApiInspectorNullableTest {

  private static final String SPEC_30 =
      """
      openapi: 3.0.3
      info: {title: T, version: "1"}
      components:
        schemas:
          Person:
            type: object
            required: [name]
            properties:
              name:
                type: string
              nick:
                type: string
                nullable: true
              age:
                type: integer
                nullable: true
              notNullable:
                type: string
                nullable: false
              plainOptional:
                type: string
      """;

  private static final String SPEC_31 =
      """
      openapi: 3.1.0
      info: {title: T, version: "1"}
      components:
        schemas:
          Person:
            type: object
            properties:
              nick:
                type: [string, "null"]
              age:
                type: [integer, "null"]
              name:
                type: string
              plainOptional:
                type: string
      """;

  private static Map<String, Double> nullables(Inspection in) {
    DataStructure person =
        in.structures().stream()
            .filter(s -> s.getName().equals("person"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no person structure"));
    Map<String, Double> result = new LinkedHashMap<>();
    person.getData().forEach((field, def) -> result.put(field, def.getNullable()));
    return result;
  }

  private static Inspection inspect(Path dir, String spec) throws IOException {
    Path file = dir.resolve("api.yaml");
    Files.writeString(file, spec);
    return new OpenApiInspector().inspect(file);
  }

  @Test
  void shouldMarkOpenApi30NullableTrueAndNothingElse(@TempDir Path dir) throws IOException {
    assertThat(nullables(inspect(dir, SPEC_30)))
        .containsEntry("nick", 0.05)
        .containsEntry("age", 0.05)
        .containsEntry("name", null)
        .containsEntry("notNullable", null)
        .containsEntry("plainOptional", null);
  }

  @Test
  void shouldMarkOpenApi31TypeArrayWithNullAndNotOptionalProperties(@TempDir Path dir)
      throws IOException {
    assertThat(nullables(inspect(dir, SPEC_31)))
        .containsEntry("nick", 0.05)
        .containsEntry("age", 0.05)
        .containsEntry("name", null)
        .containsEntry("plainOptional", null);
  }
}
