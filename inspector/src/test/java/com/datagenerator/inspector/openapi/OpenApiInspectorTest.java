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

class OpenApiInspectorTest {

  private static final String SPEC =
      """
      openapi: 3.0.3
      info:
        title: Test
        version: "1.0"
      components:
        schemas:
          Customer:
            type: object
            properties:
              id:
                type: string
                format: uuid
              userEmail:
                type: string
              emailVerifiedAt:
                type: string
                format: date-time
              firstName:
                type: string
              age:
                type: integer
                minimum: 18
                maximum: 120
              balance:
                type: number
              status:
                type: string
                enum: [ACTIVE, CLOSED]
              nickname:
                type: string
                maxLength: 30
              orders:
                type: array
                minItems: 1
                maxItems: 5
                items:
                  $ref: '#/components/schemas/Order'
          Order:
            type: object
            properties:
              total:
                type: number
                minimum: 0.0
                maximum: 5000.0
      """;

  @Test
  void shouldMapOpenApiSchemaToSeedStreamStructures(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .contains("customer", "order");

    Map<String, String> customer = datatypesOf(inspection, "customer");
    assertThat(customer)
        .containsEntry("id", "datafaker[internet.uuid]")
        .containsEntry("userEmail", "datafaker[internet.emailAddress]")
        .containsEntry("firstName", "datafaker[name.firstName]")
        .containsEntry("age", "int[18..120]")
        .containsEntry("status", "enum[ACTIVE,CLOSED]")
        .containsEntry("nickname", "char[1..30]")
        .containsEntry("orders", "array[object[order], 1..5]");
  }

  @Test
  void shouldLetFormatWinOverNameHint(@TempDir Path dir) throws IOException {
    // emailVerifiedAt contains the 'email' token but has format date-time -> timestamp wins
    assertThat(datatypesOf(inspect(dir), "customer"))
        .containsEntry("emailVerifiedAt", "timestamp[now-1y..now]");
  }

  @Test
  void shouldFlagUnboundedNumberAsInferred(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "customer"))
        .containsEntry("balance", "decimal[0.0..9999.99]");
    assertThat(inspection.inferredFields()).contains("customer.balance");
  }

  @Test
  void shouldFailOnSpecWithoutComponentSchemas(@TempDir Path dir) throws IOException {
    Path spec = dir.resolve("empty.yaml");
    Files.writeString(spec, "openapi: 3.0.3\ninfo:\n  title: x\n  version: \"1\"\n");

    assertThatThrownBy(() -> new OpenApiInspector().inspect(spec))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("components.schemas");
  }

  private Inspection inspect(Path dir) throws IOException {
    Path spec = dir.resolve("api.yaml");
    Files.writeString(spec, SPEC);
    return new OpenApiInspector().inspect(spec);
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
