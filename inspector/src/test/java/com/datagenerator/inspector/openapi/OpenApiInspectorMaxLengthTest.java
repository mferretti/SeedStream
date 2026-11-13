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

/** #452 phase 2 / #440: a name-hinted string with {@code maxLength} keeps its hint and a cap. */
class OpenApiInspectorMaxLengthTest {

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
              city:
                type: string
                maxLength: 15
              country:
                type: string
                minLength: 1
                maxLength: 15
              address:
                type: string
                minLength: 0
                maxLength: 60
              street:
                type: string
                minLength: 3
                maxLength: 20
              nick:
                type: string
                maxLength: 30
              status:
                type: string
                enum: [IT, FR]
                maxLength: 2
              contact:
                type: string
                format: email
                maxLength: 20
              backupContact:
                type: string
                format: email
                minLength: 12
                maxLength: 254
              token:
                type: string
                format: uuid
                maxLength: 20
              zip:
                type: string
      """;

  @Test
  void shouldKeepHintAndCapForHintedStringWithMaxLengthAndNoRealMinLength(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account"))
        .containsEntry("city", "city")
        .containsEntry("country", "country")
        .containsEntry("address", "address");
    Map<String, FieldDefinition> data = data(inspection);
    assertThat(data.get("city").getMaxLength()).isEqualTo(15);
    assertThat(data.get("country").getMaxLength()).isEqualTo(15);
    assertThat(data.get("address").getMaxLength()).isEqualTo(60);
  }

  @Test
  void shouldNeverEmitLengthUnitBytes(@TempDir Path dir) throws IOException {
    Map<String, FieldDefinition> data = data(inspect(dir));

    assertThat(data.values()).noneMatch(FieldDefinition::isLengthInBytes);
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
  void shouldLetEnumWinOverHintAndCap(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("status", "enum[IT,FR]");
    assertThat(data(inspection).get("status").getMaxLength()).isNull();
  }

  @Test
  void shouldKeepHintWithoutCapWhenNoMaxLength(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("zip", "postal_code");
    assertThat(data(inspection).get("zip").getMaxLength()).isNull();
  }

  @Test
  void shouldEmitEmailWithCapWithoutReviewNoteWhenMaxLengthIsBelowTheEmailFloor(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("contact", "email");
    assertThat(data(inspection).get("contact").getMaxLength()).isEqualTo(20);
    assertThat(inspection.comments().getOrDefault("account", Map.of()))
        .doesNotContainKey("contact");
  }

  @Test
  void shouldKeepCharRangeWithNoteWhenEmailMinLengthMisfits(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir);

    assertThat(datatypesOf(inspection, "account")).containsEntry("backupContact", "char[12..254]");
    assertThat(data(inspection).get("backupContact").getMaxLength()).isNull();
    assertThat(inspection.comments().get("account").get("backupContact")).contains("email");
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
  void shouldWriteCapRightAfterDatatypeAndLoadItWithRealParser(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir);
    Path out = dir.resolve("out");

    DataStructure loaded = writeAndLoad(inspection, "account", out);

    assertThat(loaded.getData().get("city").getMaxLength()).isEqualTo(15);
    assertThat(loaded.getData().get("contact").getMaxLength()).isEqualTo(20);
    assertThat(yamlOf("account", out)).contains("max_length: 15").doesNotContain("length_unit");
  }

  private static Map<String, FieldDefinition> data(Inspection inspection) {
    return inspection.structures().stream()
        .filter(s -> s.getName().equals("account"))
        .findFirst()
        .orElseThrow()
        .getData();
  }

  private Inspection inspect(Path dir) throws IOException {
    Path file = dir.resolve("api.yaml");
    Files.writeString(file, SPEC);
    return new OpenApiInspector().inspect(file);
  }
}
