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
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schema-shape edge cases: required/optional, circular refs, name collisions, odd property names.
 */
class OpenApiInspectorSchemaShapeTest {

  private static final String HEADER =
      """
      openapi: 3.0.3
      info:
        title: Test
        version: "1.0"
      components:
        schemas:
      """;

  @Test
  void shouldEmitRequiredAndOptionalFieldsIdentically(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              Pet:
                type: object
                required: [name, age]
                properties:
                  name:
                    type: string
                    maxLength: 20
                  age:
                    type: integer
                    minimum: 0
                    maximum: 30
                  nickname:
                    type: string
                    maxLength: 20
                  weight:
                    type: integer
                    minimum: 0
                    maximum: 30
            """);

    // spec §6 Q5 / §8: required/optional is ignored, every field is always emitted
    assertThat(datatypesOf(inspection, "pet"))
        .containsExactly(
            Map.entry("name", "char[1..20]"),
            Map.entry("age", "int[0..30]"),
            Map.entry("nickname", "char[1..20]"),
            Map.entry("weight", "int[0..30]"));
  }

  @Test
  void shouldTerminateOnMutuallyRecursiveRefs(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              A:
                type: object
                properties:
                  b:
                    $ref: '#/components/schemas/B'
                  label:
                    type: string
                    maxLength: 5
              B:
                type: object
                properties:
                  a:
                    $ref: '#/components/schemas/A'
            """);

    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .containsExactly("a", "b");
    assertThat(datatypesOf(inspection, "a"))
        .containsExactly(Map.entry("b", "object[b]"), Map.entry("label", "char[1..5]"));
    assertThat(datatypesOf(inspection, "b")).containsExactly(Map.entry("a", "object[a]"));
  }

  @Test
  void shouldTerminateOnSelfReferencingSchemaAndArrayOfSelf(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              TreeNode:
                type: object
                properties:
                  parent:
                    $ref: '#/components/schemas/TreeNode'
                  children:
                    type: array
                    minItems: 0
                    maxItems: 3
                    items:
                      $ref: '#/components/schemas/TreeNode'
            """);

    assertThat(datatypesOf(inspection, "tree_node"))
        .containsExactly(
            Map.entry("parent", "object[tree_node]"),
            Map.entry("children", "array[object[tree_node], 0..3]"));
  }

  @Test
  void shouldFailWhenSchemaNamesCollideAfterSnakeCasing(@TempDir Path dir) {
    // LineItem and line_item would both become line_item.yaml (one silently lost) and $ref
    // resolution could not tell them apart, so inspect stops and names the clash.
    assertThatThrownBy(
            () ->
                inspect(
                    dir,
                    """
                      LineItem:
                        type: object
                        properties:
                          sku:
                            type: string
                      line_item:
                        type: object
                        properties:
                          quantity:
                            type: integer
                      Order:
                        type: object
                        properties:
                          id:
                            type: integer
                    """))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("[LineItem, line_item] -> line_item")
        .hasMessageNotContaining("Order");
  }

  @Test
  void shouldInspectSchemasWhoseSnakeNamesAreDistinct(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              LineItem:
                type: object
                properties:
                  sku:
                    type: string
              LineItems:
                type: object
                properties:
                  count:
                    type: integer
            """);

    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .containsExactlyInAnyOrder("line_item", "line_items");
  }

  @Test
  void shouldKeepYamlReservedWordsAndDottedPropertyNamesAsExactFieldKeys(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              Odd:
                type: object
                properties:
                  'yes':
                    type: boolean
                  'no':
                    type: boolean
                  'null':
                    type: integer
                    minimum: 1
                    maximum: 2
                  'true':
                    type: string
                    maxLength: 3
                  'on':
                    type: boolean
                  '~':
                    type: integer
                    minimum: 1
                    maximum: 2
                  '123':
                    type: integer
                    minimum: 1
                    maximum: 2
                  'a.b':
                    type: string
                    maxLength: 4
                  'user.address.city':
                    type: string
                    maxLength: 9
            """);

    Map<String, String> expected =
        Map.of(
            "yes", "boolean",
            "no", "boolean",
            "null", "int[1..2]",
            "true", "char[1..3]",
            "on", "boolean",
            "~", "int[1..2]",
            "123", "int[1..2]",
            "a.b", "char[1..4]");
    assertThat(datatypesOf(inspection, "odd"))
        .containsAllEntriesOf(expected)
        .containsEntry("user.address.city", "char[1..9]")
        .hasSize(9);
  }

  @Test
  void shouldRoundTripReservedAndDottedFieldNamesThroughWrittenYaml(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              Odd:
                type: object
                properties:
                  'yes':
                    type: boolean
                  'null':
                    type: integer
                    minimum: 1
                    maximum: 2
                  'on':
                    type: boolean
                  'a.b':
                    type: string
                    maxLength: 4
            """);
    Path out = dir.resolve("out");
    new StructureYamlWriter().write(inspection.structures().get(0), out, true, Map.of());

    DataStructure parsed = new DataStructureParser().parse(out.resolve("odd.yaml"));

    assertThat(parsed.getData().keySet()).containsExactly("yes", "null", "on", "a.b");
    assertThat(parsed.getData().get("yes").getDatatype()).isEqualTo("boolean");
    assertThat(parsed.getData().get("null").getDatatype()).isEqualTo("int[1..2]");
    assertThat(parsed.getData().get("on").getDatatype()).isEqualTo("boolean");
    assertThat(parsed.getData().get("a.b").getDatatype()).isEqualTo("char[1..4]");
  }

  @Test
  void shouldWarnAndSkipSchemaWithoutProperties(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
              Status:
                type: string
                enum: [A, B]
              Real:
                type: object
                properties:
                  id:
                    type: integer
                    minimum: 1
                    maximum: 5
            """);

    assertThat(inspection.structures()).extracting(DataStructure::getName).containsExactly("real");
    assertThat(inspection.warnings()).anyMatch(w -> w.contains("Status") && w.contains("skipped"));
  }

  @Test
  void shouldFlagPropertyLevelAllOfAsUnrecognizedType(@TempDir Path dir) throws IOException {
    // OpenAPI idiom: allOf with a single $ref to attach a description. The documented
    // property-level fallback is a flagged placeholder (spec §10), never a silent guess.
    Inspection inspection =
        inspect(
            dir,
            """
              Owner:
                type: object
                properties:
                  id:
                    type: integer
                    minimum: 1
                    maximum: 5
              Pet:
                type: object
                properties:
                  owner:
                    allOf:
                      - $ref: '#/components/schemas/Owner'
                    description: the owner
            """);

    assertThat(datatypesOf(inspection, "pet")).containsEntry("owner", "char[1..50]");
    assertThat(inspection.comments().get("pet"))
        .containsEntry("owner", "unrecognized source type, defaulted — verify");
  }

  private Inspection inspect(Path dir, String schemasYaml) throws IOException {
    Path spec = dir.resolve("api.yaml");
    Files.writeString(spec, HEADER + schemasYaml.replaceAll("(?m)^(?=.)", "  "));
    return new OpenApiInspector().inspect(spec);
  }
}
