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

import static com.datagenerator.inspector.InspectionTestSupport.datatypesOf;
import static com.datagenerator.inspector.WrittenStructureSupport.writeAndLoad;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #452 phase 2 / #440 through the JSON Schema inspector. */
class JsonSchemaInspectorMaxLengthTest {

  // Concatenated strings rather than a text block: google-java-format mangles a text block whose
  // first content line begins with '{'.
  private static final String SCHEMA =
      "{"
          + "  \"title\": \"Account\","
          + "  \"type\": \"object\","
          + "  \"properties\": {"
          + "    \"city\": { \"type\": \"string\", \"maxLength\": 15 },"
          + "    \"country\": { \"type\": \"string\", \"minLength\": 1, \"maxLength\": 15 },"
          + "    \"street\": { \"type\": \"string\", \"minLength\": 3, \"maxLength\": 20 },"
          + "    \"nick\": { \"type\": \"string\", \"maxLength\": 30 },"
          + "    \"contact\": { \"type\": \"string\", \"format\": \"email\", \"maxLength\": 20 },"
          + "    \"token\": { \"type\": \"string\", \"format\": \"uuid\", \"maxLength\": 20 }"
          + "  }"
          + "}";

  @Test
  void shouldKeepHintAndCapForHintedStringWithMaxLength(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("city", "city")
        .containsEntry("country", "country");
    assertThat(data(inspection).get("city").getMaxLength()).isEqualTo(15);
    assertThat(data(inspection).get("country").getMaxLength()).isEqualTo(15);
    assertThat(data(inspection).values()).noneMatch(FieldDefinition::isLengthInBytes);
  }

  @Test
  void shouldKeepCharRangeWhenMinLengthAboveOneOrFieldIsNotHinted(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("street", "char[3..20]")
        .containsEntry("nick", "char[1..30]");
    assertThat(data(inspection).get("street").getMaxLength()).isNull();
    assertThat(data(inspection).get("nick").getMaxLength()).isNull();
  }

  @Test
  void shouldEmitEmailWithCapAndNoReviewNoteWhenMaxLengthIsBelowTheEmailFloor(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("contact", "email");
    assertThat(data(inspection).get("contact").getMaxLength()).isEqualTo(20);
    assertThat(inspection.comments().getOrDefault("account", Map.of()))
        .doesNotContainKey("contact");
  }

  @Test
  void shouldKeepCharRangeWithNoteForUuidThatCannotFit(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("token", "char[1..20]");
    assertThat(data(inspection).get("token").getMaxLength()).isNull();
    assertThat(inspection.comments().get("account").get("token"))
        .contains("uuid format cannot fit maxLength 20");
  }

  @Test
  void shouldWriteCapsThatTheRealParserLoads(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    DataStructure loaded = writeAndLoad(inspection, "account", dir.resolve("out"));

    assertThat(loaded.getData().get("city").getMaxLength()).isEqualTo(15);
    assertThat(loaded.getData().get("contact").getMaxLength()).isEqualTo(20);
  }

  private static Map<String, FieldDefinition> data(Inspection inspection) {
    return inspection.structures().stream()
        .filter(s -> s.getName().equals("account"))
        .findFirst()
        .orElseThrow()
        .getData();
  }

  private Inspection inspect(Path dir) throws IOException {
    Path file = dir.resolve("account.json");
    Files.writeString(file, SCHEMA);
    return new JsonSchemaInspector().inspect(file);
  }
}
