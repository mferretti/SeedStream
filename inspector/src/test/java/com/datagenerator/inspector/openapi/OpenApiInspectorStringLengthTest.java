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

/** End-to-end string length handling (#452 phase 1) through the OpenAPI inspector. */
class OpenApiInspectorStringLengthTest {

  private static final String SPEC =
      """
      openapi: 3.0.3
      info:
        title: Test
        version: "1.0"
      components:
        schemas:
          Account:
            type: object
            properties:
              code:
                type: string
                minLength: 4
                maxLength: 6
              note:
                type: string
                minLength: 3
              optionalTag:
                type: string
                minLength: 0
                maxLength: 5
              token:
                type: string
                format: uuid
                maxLength: 20
              contact:
                type: string
                format: email
                maxLength: 20
              id:
                type: string
                format: uuid
      """;

  @Test
  void shouldCarryMinLengthIntoWrittenStructureAndLoadItWithRealParser(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, SPEC);
    Path out = dir.resolve("out");

    DataStructure loaded = writeAndLoad(inspection, "account", out);

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("code", "char[4..6]")
        .containsEntry("note", "char[3..50]")
        .containsEntry("optionalTag", "char[0..5]")
        .containsEntry("token", "char[1..20]")
        .containsEntry("contact", "email")
        .containsEntry("id", "uuid");
    assertThat(loaded.getData().get("code").getDatatype()).isEqualTo("char[4..6]");
    assertThat(loaded.getData().get("note").getDatatype()).isEqualTo("char[3..50]");
    assertThat(loaded.getData().get("optionalTag").getDatatype()).isEqualTo("char[0..5]");
    assertThat(inspection.warnings()).isEmpty();
  }

  @Test
  void shouldWriteReviewCommentsForUuidAndEmailThatCannotFit(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, SPEC);
    Path out = dir.resolve("out");
    writeAndLoad(inspection, "account", out);

    Map<String, String> comments = inspection.comments().getOrDefault("account", Map.of());

    assertThat(comments.get("token")).contains("uuid format cannot fit maxLength 20");
    // #452 phase 2: an email that cannot fit is now email + max_length, no review note
    assertThat(comments).doesNotContainKey("contact");
    assertThat(comments).doesNotContainKeys("code", "note", "optionalTag", "id");
    assertThat(yamlOf("account", out))
        .contains("char[1..20]\"  # uuid format cannot fit maxLength 20")
        .doesNotContain("email values can exceed");
  }

  @Test
  void shouldWarnNamingTheFieldWhenMinLengthExceedsMaxLength(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            openapi: 3.0.3
            info:
              title: Test
              version: "1.0"
            components:
              schemas:
                Account:
                  type: object
                  properties:
                    brokenField:
                      type: string
                      minLength: 10
                      maxLength: 5
                    fine:
                      type: string
                      minLength: 1
                      maxLength: 5
            """);

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("brokenField", "char[1..5]")
        .containsEntry("fine", "char[1..5]");
    assertThat(inspection.warnings()).anyMatch(w -> w.contains("brokenField"));
    assertThat(inspection.warnings()).noneMatch(w -> w.contains("'fine'"));
    assertThat(inspection.comments().get("account")).containsKey("brokenField");
    writeAndLoad(inspection, "account", dir.resolve("out"));
  }

  @Test
  void shouldWarnExactlyOnceForPlainFieldWithMinLengthAboveMaxLength(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, schemaWith("{type: string, minLength: 10, maxLength: 5}"));

    assertThat(inspection.warnings()).filteredOn(w -> w.contains("brokenField")).hasSize(1);
  }

  @Test
  void shouldWarnAndCommentForArrayOfStringsWithMinLengthAboveMaxLength(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir, schemaWith("{type: array, items: {type: string, minLength: 10, maxLength: 5}}"));

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("brokenField", "array[char[1..5], 1..10]");
    assertThat(inspection.warnings()).filteredOn(w -> w.contains("brokenField")).hasSize(1);
    assertThat(inspection.comments().get("account").get("brokenField")).contains("minLength");
  }

  @Test
  void shouldWarnAndNotInvertRangeWhenMaxLengthIsZero(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, schemaWith("{type: string, minLength: 3, maxLength: 0}"));

    assertThat(datatypesOf(inspection, "account")).containsEntry("brokenField", "char[0..0]");
    assertThat(inspection.warnings()).filteredOn(w -> w.contains("brokenField")).hasSize(1);
    assertThat(inspection.comments().get("account")).containsKey("brokenField");
  }

  private static String schemaWith(String brokenFieldSchema) {
    return """
        openapi: 3.0.3
        info:
          title: Test
          version: "1.0"
        components:
          schemas:
            Account:
              type: object
              properties:
                brokenField: BROKEN_FIELD_SCHEMA
        """
        .replace("BROKEN_FIELD_SCHEMA", brokenFieldSchema);
  }

  private Inspection inspect(Path dir, String spec) throws IOException {
    Path file = dir.resolve("api.yaml");
    Files.writeString(file, spec);
    return new OpenApiInspector().inspect(file);
  }
}
