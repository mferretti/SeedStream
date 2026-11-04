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
import static com.datagenerator.inspector.WrittenStructureSupport.yamlOf;
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end string length handling (#452 phase 1) through the JSON Schema inspector. */
class JsonSchemaInspectorStringLengthTest {

  // Concatenated strings rather than a text block: google-java-format mangles a text block whose
  // first content line begins with '{'.
  private static final String SCHEMA =
      "{"
          + "  \"title\": \"Account\","
          + "  \"type\": \"object\","
          + "  \"properties\": {"
          + "    \"code\": { \"type\": \"string\", \"minLength\": 4, \"maxLength\": 6 },"
          + "    \"note\": { \"type\": \"string\", \"minLength\": 3 },"
          + "    \"optionalTag\": { \"type\": \"string\", \"minLength\": 0, \"maxLength\": 5 },"
          + "    \"token\": { \"type\": \"string\", \"format\": \"uuid\", \"maxLength\": 20 },"
          + "    \"contact\": { \"type\": \"string\", \"format\": \"email\", \"maxLength\": 20 },"
          + "    \"id\": { \"type\": \"string\", \"format\": \"uuid\" },"
          + "    \"sku\": { \"type\": \"string\", \"pattern\": \"^[A-Z]{3}$\" }"
          + "  }"
          + "}";

  @Test
  void shouldCarryMinLengthIntoWrittenStructureAndLoadItWithRealParser(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, SCHEMA);

    DataStructure loaded = writeAndLoad(inspection, "account", dir.resolve("out"));

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("code", "char[4..6]")
        .containsEntry("note", "char[3..50]")
        .containsEntry("optionalTag", "char[0..5]")
        .containsEntry("token", "char[1..20]")
        .containsEntry("contact", "char[1..20]")
        .containsEntry("id", "uuid");
    assertThat(loaded.getData().get("code").getDatatype()).isEqualTo("char[4..6]");
    assertThat(loaded.getData().get("note").getDatatype()).isEqualTo("char[3..50]");
    assertThat(loaded.getData().get("optionalTag").getDatatype()).isEqualTo("char[0..5]");
  }

  @Test
  void shouldWriteReviewCommentsForUuidAndEmailThatCannotFit(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, SCHEMA);
    Path out = dir.resolve("out");
    writeAndLoad(inspection, "account", out);

    Map<String, String> comments = inspection.comments().getOrDefault("account", Map.of());

    assertThat(comments.get("token")).contains("uuid format cannot fit maxLength 20");
    assertThat(comments.get("contact")).contains("email values can exceed maxLength 20");
    assertThat(comments).doesNotContainKeys("code", "note", "optionalTag", "id");
    assertThat(yamlOf("account", out))
        .contains("char[1..20]  # uuid format cannot fit maxLength 20")
        .contains("char[1..20]  # email values can exceed maxLength 20");
  }

  @Test
  void shouldKeepPatternFieldUnchangedByLengthHandling(@TempDir Path dir) throws IOException {
    // guard, passes today: a pattern-only string keeps the default range and the regex suggestion
    Inspection inspection = inspect(dir, SCHEMA);

    assertThat(datatypesOf(inspection, "account")).containsEntry("sku", "char[1..50]");
    assertThat(inspection.fakerTypeSuggestions()).containsEntry("sku", "regex:^[A-Z]{3}$");
    assertThat(inspection.comments().get("account").get("sku")).contains("pattern");
  }

  @Test
  void shouldWarnNamingTheFieldWhenMinLengthExceedsMaxLength(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            "{"
                + "  \"title\": \"Account\","
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"brokenField\": { \"type\": \"string\", \"minLength\": 10,"
                + " \"maxLength\": 5 },"
                + "    \"fine\": { \"type\": \"string\", \"minLength\": 1, \"maxLength\": 5 }"
                + "  }"
                + "}");

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("brokenField", "char[1..5]")
        .containsEntry("fine", "char[1..5]");
    assertThat(inspection.warnings()).anyMatch(w -> w.contains("brokenField"));
    assertThat(inspection.warnings()).noneMatch(w -> w.contains("'fine'"));
    assertThat(inspection.comments().get("account")).containsKey("brokenField");
    writeAndLoad(inspection, "account", dir.resolve("out"));
  }

  private Inspection inspect(Path dir, String schema) throws IOException {
    Path file = dir.resolve("account.json");
    Files.writeString(file, schema);
    return new JsonSchemaInspector().inspect(file);
  }
}
