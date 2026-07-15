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

import com.datagenerator.inspector.Names;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detects JSON Schema constructs that have no clean SeedStream equivalent. These are flagged with a
 * {@code # review} comment plus a warning rather than mapped to a misleading structure — false
 * confidence is worse than an explicit gap (see issue #89, {@code docs/INSPECT-V1-SPEC.md}).
 */
public final class JsonSchemaGaps {

  private JsonSchemaGaps() {}

  private static final String ONE_OF = "oneOf";
  private static final String ANY_OF = "anyOf";
  private static final String ALL_OF = "allOf";
  private static final List<String> COMPOSITION = List.of(ONE_OF, ANY_OF, ALL_OF);

  /**
   * Returns a human-readable review reason if a single <em>property</em> schema uses a construct
   * that cannot be mapped to one datatype, otherwise empty. A property occupies a single field
   * slot, so composition/conditionals on it (unlike at the structure level, where sibling fields
   * can be merged) have no representation.
   *
   * @param node the property schema node
   * @param currentStructure the snake_case name of the structure being built, for recursive-{@code
   *     $ref} detection (may be null)
   */
  public static Optional<String> unsupported(JsonNode node, String currentStructure) {
    if (node == null || !node.isObject()) {
      return Optional.empty();
    }
    for (String keyword : COMPOSITION) {
      if (node.has(keyword)) {
        return Optional.of(keyword + " schema composition — no SeedStream equivalent, map by hand");
      }
    }
    if (node.has("if") || node.has("then") || node.has("else")) {
      return Optional.of("if/then/else conditional schema — no SeedStream equivalent, map by hand");
    }
    if (node.has("patternProperties")) {
      return Optional.of("patternProperties (open map) — no SeedStream equivalent, map by hand");
    }
    JsonNode additional = node.get("additionalProperties");
    if (additional != null && additional.isBoolean() && additional.booleanValue()) {
      return Optional.of(
          "additionalProperties: true (open map) — no SeedStream equivalent, map by hand");
    }
    if (node.has("const")) {
      return Optional.of("const value — no SeedStream equivalent, map by hand");
    }
    if (node.has("not")) {
      return Optional.of("not schema — no SeedStream equivalent, map by hand");
    }
    JsonNode items = node.get("items");
    if (items != null && items.isArray()) {
      return Optional.of(
          "tuple-form items (array of schemas) — no SeedStream equivalent, map by hand");
    }
    return refGap(node, currentStructure);
  }

  private static Optional<String> refGap(JsonNode node, String currentStructure) {
    JsonNode ref = node.get("$ref");
    if (ref == null || !ref.isTextual()) {
      return Optional.empty();
    }
    String pointer = ref.asText();
    if (!pointer.startsWith("#")) {
      return Optional.of(
          "external $ref '" + pointer + "' — only local $defs/definitions supported, map by hand");
    }
    if (currentStructure != null) {
      String target = Names.toSnakeCase(pointer.substring(pointer.lastIndexOf('/') + 1));
      if (currentStructure.equals(target)) {
        return Optional.of(
            "recursive $ref '" + pointer + "' — verify generation depth (circular refs fail fast)");
      }
    }
    return Optional.empty();
  }

  /**
   * Schema-level keywords whose <em>relationship</em> can't be represented after the structure is
   * flattened, so their presence warrants a warning. Note {@code allOf} is deliberately absent: an
   * {@code allOf} of object schemas is an exact merge (the common "extends" idiom) and is honoured,
   * not flagged. The lossy branches ({@code oneOf}/{@code anyOf}/{@code if}/…) still have their
   * fields merged in, but the record may then not validate — hence the warning.
   */
  private static final List<String> OBJECT_LEVEL =
      List.of(
          ONE_OF,
          ANY_OF,
          "if",
          "then",
          "else",
          "not",
          "dependentSchemas",
          "dependentRequired",
          "patternProperties");

  /**
   * Detects schema-level (whole-object, not per-property) constructs whose relationship is not
   * enforced after flattening. Returns the keywords present, in declaration order; empty when none.
   */
  public static List<String> unsupportedObjectKeywords(JsonNode schemaNode) {
    if (schemaNode == null || !schemaNode.isObject()) {
      return List.of();
    }
    List<String> found = new ArrayList<>();
    for (String keyword : OBJECT_LEVEL) {
      if (schemaNode.has(keyword)) {
        found.add(keyword);
      }
    }
    JsonNode additional = schemaNode.get("additionalProperties");
    if (additional != null && additional.isBoolean() && additional.booleanValue()) {
      found.add("additionalProperties:true");
    }
    return found;
  }

  /**
   * Property names merged in from a <em>lossy</em> branch ({@code oneOf}/{@code anyOf}/{@code
   * if}/{@code then}/{@code else}/{@code dependentSchemas}) — their conditional/polymorphic
   * relationship is dropped once flattened, so the affected fields get an inline review comment.
   * {@code allOf} is excluded: its merge is exact and needs no comment.
   */
  public static Set<String> conditionallyConstrainedFields(JsonNode schemaNode) {
    if (schemaNode == null || !schemaNode.isObject()) {
      return Set.of();
    }
    Set<String> names = new LinkedHashSet<>();
    for (String keyword : List.of("if", "then", "else")) {
      collectBranchProps(schemaNode.get(keyword), names);
    }
    for (String keyword : List.of(ONE_OF, ANY_OF)) {
      JsonNode array = schemaNode.get(keyword);
      if (array != null && array.isArray()) {
        array.forEach(branch -> collectBranchProps(branch, names));
      }
    }
    JsonNode dependent = schemaNode.get("dependentSchemas");
    if (dependent != null && dependent.isObject()) {
      dependent.forEach(branch -> collectBranchProps(branch, names));
    }
    return names;
  }

  private static void collectBranchProps(JsonNode branch, Set<String> names) {
    if (branch == null || !branch.isObject()) {
      return;
    }
    JsonNode props = branch.get("properties");
    if (props != null && props.isObject()) {
      props.fieldNames().forEachRemaining(names::add);
    }
  }
}
