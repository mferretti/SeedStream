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

class JsonSchemaInspectorTest {

  // JSON kept as concatenated strings rather than a text block: google-java-format mangles a
  // text block whose first content line begins with '{'.
  private static final String SCHEMA =
      "{"
          + "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\","
          + "  \"title\": \"Order\","
          + "  \"type\": \"object\","
          + "  \"properties\": {"
          + "    \"id\": { \"type\": \"string\", \"format\": \"uuid\" },"
          + "    \"total\": { \"type\": \"number\", \"minimum\": 0, \"maximum\": 5000 },"
          + "    \"status\": { \"type\": \"string\", \"enum\": [\"NEW\", \"SHIPPED\"] },"
          + "    \"lines\": {"
          + "      \"type\": \"array\", \"minItems\": 1, \"maxItems\": 5,"
          + "      \"items\": { \"$ref\": \"#/$defs/LineItem\" }"
          + "    },"
          + "    \"kind\": { \"oneOf\": [ { \"type\": \"string\" }, { \"type\": \"integer\" } ] }"
          + "  },"
          + "  \"$defs\": {"
          + "    \"LineItem\": {"
          + "      \"type\": \"object\","
          + "      \"properties\": {"
          + "        \"sku\": { \"type\": \"string\", \"maxLength\": 20 },"
          + "        \"qty\": { \"type\": \"integer\", \"minimum\": 1 }"
          + "      }"
          + "    }"
          + "  },"
          + "  \"definitions\": {"
          + "    \"LegacyRef\": {"
          + "      \"type\": \"object\","
          + "      \"properties\": { \"code\": { \"type\": \"string\" } }"
          + "    }"
          + "  }"
          + "}";

  @Test
  void shouldEmitRootDefsAndDefinitionsAsStructures(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "order.schema.json", SCHEMA);

    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .containsExactlyInAnyOrder("order", "line_item", "legacy_ref");
  }

  @Test
  void shouldMapRootPropertiesIncludingLocalRef(@TempDir Path dir) throws IOException {
    Map<String, String> order = datatypesOf(inspect(dir, "order.schema.json", SCHEMA), "order");

    assertThat(order)
        .containsEntry("id", "uuid")
        .containsEntry("total", "decimal[0..5000]")
        .containsEntry("status", "enum[NEW,SHIPPED]")
        // $ref #/$defs/LineItem resolves to the snake_case structure name
        .containsEntry("lines", "array[object[line_item], 1..5]");
  }

  @Test
  void shouldMapNestedDefProperties(@TempDir Path dir) throws IOException {
    Map<String, String> lineItem =
        datatypesOf(inspect(dir, "order.schema.json", SCHEMA), "line_item");

    assertThat(lineItem).containsEntry("sku", "char[1..20]").containsEntry("qty", "int[1..999999]");
  }

  @Test
  void shouldFlagUnsupportedConstructWithReviewCommentAndWarning(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, "order.schema.json", SCHEMA);

    // oneOf has no SeedStream equivalent: placeholder datatype + review comment + warning
    assertThat(datatypesOf(inspection, "order")).containsEntry("kind", "char[1..50]");
    assertThat(inspection.comments().get("order").get("kind"))
        .startsWith("review:")
        .contains("oneOf");
    assertThat(inspection.warnings()).anyMatch(w -> w.contains("kind") && w.contains("oneOf"));
    assertThat(inspection.flaggedCount()).isPositive();
  }

  @Test
  void shouldNameRootFromIdWhenNoTitle(@TempDir Path dir) throws IOException {
    String schema =
        "{"
            + "  \"$id\": \"https://example.com/schemas/Address.schema.json\","
            + "  \"type\": \"object\","
            + "  \"properties\": { \"city\": { \"type\": \"string\" } }"
            + "}";
    Inspection inspection = inspect(dir, "whatever.json", schema);

    assertThat(inspection.structures()).extracting(DataStructure::getName).contains("address");
  }

  @Test
  void shouldNameRootFromFileNameWhenNoTitleOrId(@TempDir Path dir) throws IOException {
    String schema =
        "{ \"type\": \"object\", \"properties\": { \"city\": { \"type\": \"string\" } } }";
    Inspection inspection = inspect(dir, "customer.schema.json", schema);

    assertThat(inspection.structures()).extracting(DataStructure::getName).contains("customer");
  }

  @Test
  void shouldFlagRootLevelConditionalSchema(@TempDir Path dir) throws IOException {
    // Mirrors json-schema.org's "Conditional Validation with If-Else" example: the if/then/else
    // sits at the root, beside properties — it must not be silently dropped.
    String schema =
        "{"
            + "  \"title\": \"Membership\","
            + "  \"type\": \"object\","
            + "  \"properties\": {"
            + "    \"isMember\": { \"type\": \"boolean\" },"
            + "    \"membershipNumber\": { \"type\": \"string\" }"
            + "  },"
            + "  \"if\": { \"properties\": { \"isMember\": { \"const\": true } } },"
            + "  \"then\": { \"properties\": { \"membershipNumber\": { \"minLength\": 10 } } },"
            + "  \"else\": { \"properties\": { \"membershipNumber\": { \"minLength\": 15 } } }"
            + "}";
    Inspection inspection = inspect(dir, "membership.schema.json", schema);

    assertThat(inspection.warnings()).anyMatch(w -> w.contains("schema-level") && w.contains("if"));
    // the conditionally-constrained field gets an inline review comment
    assertThat(inspection.comments().get("membership").get("membershipNumber"))
        .startsWith("review:")
        .contains("conditional");
    assertThat(inspection.flaggedCount()).isPositive();
  }

  @Test
  void shouldMergeAllOfWithoutWarning(@TempDir Path dir) throws IOException {
    // allOf of object schemas is an exact merge (the "extends" idiom) — union the fields, no
    // warning.
    String schema =
        "{"
            + "  \"title\": \"Account\","
            + "  \"type\": \"object\","
            + "  \"properties\": { \"id\": { \"type\": \"integer\" } },"
            + "  \"allOf\": ["
            + "    { \"properties\": { \"owner\": { \"type\": \"string\" } } },"
            + "    { \"properties\": { \"active\": { \"type\": \"boolean\" } } }"
            + "  ]"
            + "}";
    Inspection inspection = inspect(dir, "account.schema.json", schema);

    assertThat(datatypesOf(inspection, "account")).containsKeys("id", "owner", "active");
    assertThat(inspection.warnings()).noneMatch(w -> w.contains("schema-level"));
  }

  @Test
  void shouldUnionOneOfVariantsAndWarn(@TempDir Path dir) throws IOException {
    // oneOf variants are unioned so every possible field is emitted; the relationship is flagged.
    String schema =
        "{"
            + "  \"title\": \"Shape\","
            + "  \"type\": \"object\","
            + "  \"properties\": { \"kind\": { \"type\": \"string\" } },"
            + "  \"oneOf\": ["
            + "    { \"properties\": { \"radius\": { \"type\": \"number\" } } },"
            + "    { \"properties\": { \"side\": { \"type\": \"number\" } } }"
            + "  ]"
            + "}";
    Inspection inspection = inspect(dir, "shape.schema.json", schema);

    assertThat(datatypesOf(inspection, "shape")).containsKeys("kind", "radius", "side");
    assertThat(inspection.warnings())
        .anyMatch(w -> w.contains("schema-level") && w.contains("oneOf"));
    assertThat(inspection.comments().get("shape")).containsKey("radius").containsKey("side");
  }

  @Test
  void shouldSuggestRegexFakerTypeForPatternedField(@TempDir Path dir) throws IOException {
    String schema =
        "{"
            + "  \"title\": \"Code\","
            + "  \"type\": \"object\","
            + "  \"properties\": { \"sku\": { \"type\": \"string\", \"pattern\": \"^[A-Z]{3}$\" } }"
            + "}";
    Inspection inspection = inspect(dir, "code.schema.json", schema);

    assertThat(inspection.fakerTypeSuggestions()).containsEntry("sku", "regex:^[A-Z]{3}$");
    assertThat(inspection.comments().get("code").get("sku")).contains("pattern");
  }

  @Test
  void shouldFailWhenNoObjectSchemaOrDefs(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("scalar.schema.json");
    Files.writeString(file, "{ \"$schema\": \"x\", \"type\": \"string\" }");
    JsonSchemaInspector inspector = new JsonSchemaInspector();

    assertThatThrownBy(() -> inspector.inspect(file))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("not a recognizable JSON Schema");
  }

  private Inspection inspect(Path dir, String fileName, String content) throws IOException {
    Path file = dir.resolve(fileName);
    Files.writeString(file, content);
    return new JsonSchemaInspector().inspect(file);
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
