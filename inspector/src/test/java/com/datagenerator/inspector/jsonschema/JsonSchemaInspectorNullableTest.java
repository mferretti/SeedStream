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

package com.datagenerator.inspector.jsonschema;

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

/** #492: JSON Schema {@code type: [.., "null"]} gives {@code nullable: 0.05}. */
class JsonSchemaInspectorNullableTest {

  // concatenated strings: google-java-format mangles a text block starting with '{'
  private static final String SCHEMA =
      "{"
          + "\"$schema\": \"https://json-schema.org/draft/2020-12/schema\","
          + "\"title\": \"Person\","
          + "\"type\": \"object\","
          + "\"required\": [\"name\"],"
          + "\"properties\": {"
          + "  \"name\": { \"type\": \"string\" },"
          + "  \"nick\": { \"type\": [\"string\", \"null\"] },"
          + "  \"age\": { \"type\": [\"null\", \"integer\"] },"
          + "  \"plainOptional\": { \"type\": \"string\" }"
          + "}"
          + "}";

  @Test
  void shouldMarkTypeArraysContainingNullAndNotRequiredOrOptionalProperties(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve("person.schema.json");
    Files.writeString(file, SCHEMA);
    Inspection in = new JsonSchemaInspector().inspect(file);

    DataStructure person =
        in.structures().stream()
            .filter(s -> s.getName().equals("person"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no person structure"));
    Map<String, Double> rates = new LinkedHashMap<>();
    person.getData().forEach((field, def) -> rates.put(field, def.getNullable()));

    assertThat(rates)
        .containsEntry("nick", 0.05)
        .containsEntry("age", 0.05)
        .containsEntry("name", null)
        .containsEntry("plainOptional", null);
  }
}
