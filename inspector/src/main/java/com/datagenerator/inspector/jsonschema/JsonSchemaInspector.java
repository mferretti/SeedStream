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

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.inspector.SchemaTypeMapper;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a standalone JSON Schema (Draft 7 / 2020-12) and maps it to SeedStream {@link
 * DataStructure} files: the root object schema plus every entry under {@code $defs} / {@code
 * definitions}. Reuses {@link SchemaTypeMapper} for per-property mapping (the same mapper the
 * OpenAPI inspector uses).
 *
 * <p><b>Composition/conditionals.</b> Because this is a data <em>generator</em>, the fields of
 * every branch are emitted: {@code allOf}/{@code oneOf}/{@code anyOf}/{@code if}/{@code
 * then}/{@code else}/{@code dependentSchemas} subschemas are merged (union, first field wins) into
 * one record so nothing that could appear is dropped. {@code allOf} is an exact merge; the lossy
 * branches drop their conditional relationship, so those fields get a {@code # review} comment and
 * the structure a warning (the union record may not validate against the source). See {@code
 * docs/INSPECT-V1-SPEC.md} §10.
 */
public class JsonSchemaInspector {

  private static final String SCHEMA_PREFIX = "schema '";

  private final SchemaTypeMapper mapper = new SchemaTypeMapper();

  /** Inspects a JSON Schema file and returns the structures plus diagnostics. */
  public Inspection inspect(Path schemaFile) {
    JsonNode root = readSchema(schemaFile);

    List<DataStructure> structures = new ArrayList<>();
    Map<String, Map<String, String>> comments = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();
    Map<String, String> fakerSuggestions = new LinkedHashMap<>();

    JsonNode rootProperties = root.path("properties");
    boolean hasRootObject = rootProperties.isObject() && !rootProperties.isEmpty();
    JsonNode defs = root.path("$defs");
    JsonNode definitions = root.path("definitions");
    boolean hasDefs = defs.isObject() || definitions.isObject();

    if (!hasRootObject && !hasDefs) {
      throw new InspectorException(
          "No object schema, $defs, or definitions found in "
              + schemaFile
              + " — not a recognizable JSON Schema");
    }

    if (hasRootObject) {
      DataStructure structure =
          toStructure(rootName(root, schemaFile), root, root, comments, warnings, fakerSuggestions);
      if (structure != null) {
        structures.add(structure);
      }
    }
    addDefs(defs, root, structures, comments, warnings, fakerSuggestions);
    addDefs(definitions, root, structures, comments, warnings, fakerSuggestions);

    return new Inspection(structures, comments, warnings, fakerSuggestions);
  }

  private void addDefs(
      JsonNode defs,
      JsonNode root,
      List<DataStructure> structures,
      Map<String, Map<String, String>> comments,
      List<String> warnings,
      Map<String, String> fakerSuggestions) {
    if (!defs.isObject()) {
      return;
    }
    for (Map.Entry<String, JsonNode> entry : defs.properties()) {
      DataStructure structure =
          toStructure(
              Names.toSnakeCase(entry.getKey()),
              entry.getValue(),
              root,
              comments,
              warnings,
              fakerSuggestions);
      if (structure != null) {
        structures.add(structure);
      }
    }
  }

  private DataStructure toStructure(
      String name,
      JsonNode schemaNode,
      JsonNode root,
      Map<String, Map<String, String>> comments,
      List<String> warnings,
      Map<String, String> fakerSuggestions) {
    Map<String, JsonNode> properties = effectiveProperties(schemaNode, root);
    if (properties.isEmpty()) {
      warnings.add(SCHEMA_PREFIX + name + "' has no properties — skipped");
      return null;
    }

    Set<String> branchFields = JsonSchemaGaps.conditionallyConstrainedFields(schemaNode);
    Map<String, FieldDefinition> data = new LinkedHashMap<>();
    Map<String, String> fieldComments = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
      String fieldName = property.getKey();
      JsonNode fieldNode = property.getValue();

      Optional<String> gap = JsonSchemaGaps.unsupported(fieldNode, name);
      if (gap.isPresent()) {
        fieldComments.put(fieldName, "review: " + gap.get());
        warnings.add(SCHEMA_PREFIX + name + "' field '" + fieldName + "': " + gap.get());
        data.put(fieldName, new FieldDefinition(Defaults.STRING, null));
        continue;
      }

      MappedType mapped = mapper.map(fieldName, fieldNode);
      if (mapped.flagged()) {
        fieldComments.put(fieldName, mapped.comment());
      }
      data.put(fieldName, new FieldDefinition(mapped.datatype(), null));

      suggestRegex(fieldName, fieldNode, fakerSuggestions, fieldComments);
      if (branchFields.contains(fieldName)) {
        fieldComments.putIfAbsent(
            fieldName,
            "review: merged from a conditional/composition branch — relationship not enforced");
      }
    }

    flagSchemaLevelGaps(name, schemaNode, warnings);

    if (!fieldComments.isEmpty()) {
      comments.put(name, fieldComments);
    }
    return new DataStructure(name, null, data);
  }

  /**
   * A {@code string} with a {@code pattern} has no inline SeedStream type. Record a regex custom
   * type keyed by the field name (the CLI writes them to {@code inspect-faker-types.yaml}) and hint
   * the field so a rerun with {@code --faker-types} resolves it by name.
   */
  private void suggestRegex(
      String fieldName,
      JsonNode fieldNode,
      Map<String, String> fakerSuggestions,
      Map<String, String> fieldComments) {
    if (!"string".equals(fieldNode.path("type").asText("")) || !fieldNode.hasNonNull("pattern")) {
      return;
    }
    String pattern = fieldNode.get("pattern").asText();
    String typeName = Names.toSnakeCase(fieldName);
    fakerSuggestions.putIfAbsent(typeName, "regex:" + pattern);
    fieldComments.putIfAbsent(
        fieldName,
        "review: pattern '"
            + pattern
            + "' — add inspect-faker-types.yaml via --faker-types to generate matching values");
  }

  /**
   * Warns when the structure carries a lossy schema-level relationship ({@code oneOf}/{@code if}/…)
   * whose branches were merged but whose constraint can no longer be enforced.
   */
  private void flagSchemaLevelGaps(String name, JsonNode schemaNode, List<String> warnings) {
    List<String> objectGaps = JsonSchemaGaps.unsupportedObjectKeywords(schemaNode);
    if (objectGaps.isEmpty()) {
      return;
    }
    warnings.add(
        SCHEMA_PREFIX
            + name
            + "' has schema-level "
            + String.join(", ", objectGaps)
            + " — branch fields were merged into one record (union); the conditional/polymorphic"
            + " relationship is NOT enforced, so generated records may not validate against the"
            + " source. Review the # review fields.");
  }

  /**
   * Collects the effective property set of an object schema: its own {@code properties} plus those
   * merged in from {@code allOf}/{@code oneOf}/{@code anyOf}/{@code if}/{@code then}/{@code else}/
   * {@code dependentSchemas} branches and local {@code $ref}s, first-declared wins. A visited-set
   * on resolved {@code $ref}s guards against recursion.
   */
  private Map<String, JsonNode> effectiveProperties(JsonNode schemaNode, JsonNode root) {
    Map<String, JsonNode> out = new LinkedHashMap<>();
    collectProperties(schemaNode, root, out, new HashSet<>());
    return out;
  }

  private void collectProperties(
      JsonNode node, JsonNode root, Map<String, JsonNode> out, Set<String> visitedRefs) {
    if (node == null || !node.isObject()) {
      return;
    }
    JsonNode properties = node.get("properties");
    if (properties != null && properties.isObject()) {
      properties.properties().forEach(e -> out.putIfAbsent(e.getKey(), e.getValue()));
    }
    JsonNode ref = node.get("$ref");
    if (ref != null
        && ref.isTextual()
        && ref.asText().startsWith("#/")
        && visitedRefs.add(ref.asText())) {
      collectProperties(resolvePointer(root, ref.asText()), root, out, visitedRefs);
    }
    for (String keyword : List.of("allOf", "oneOf", "anyOf")) {
      JsonNode array = node.get(keyword);
      if (array != null && array.isArray()) {
        array.forEach(branch -> collectProperties(branch, root, out, visitedRefs));
      }
    }
    for (String keyword : List.of("if", "then", "else")) {
      collectProperties(node.get(keyword), root, out, visitedRefs);
    }
    JsonNode dependent = node.get("dependentSchemas");
    if (dependent != null && dependent.isObject()) {
      dependent.forEach(branch -> collectProperties(branch, root, out, visitedRefs));
    }
  }

  /** Resolves a local JSON pointer ({@code #/$defs/Foo}) against the document root, or null. */
  private JsonNode resolvePointer(JsonNode root, String pointer) {
    JsonNode current = root;
    for (String rawSegment : pointer.substring(2).split("/")) {
      if (current == null) {
        return null;
      }
      String segment = rawSegment.replace("~1", "/").replace("~0", "~");
      current = current.get(segment);
    }
    return current;
  }

  /**
   * Derives the root structure name: {@code title} → {@code $id} last segment → file name stem, run
   * through the shared safe-name guard so it can never redirect a later write.
   */
  private String rootName(JsonNode root, Path schemaFile) {
    String candidate = null;
    if (root.hasNonNull("title")) {
      candidate = root.get("title").asText();
    } else if (root.hasNonNull("$id")) {
      String id = root.get("$id").asText();
      candidate = stripSchemaSuffix(id.substring(id.lastIndexOf('/') + 1));
    }
    if (candidate == null || candidate.isBlank()) {
      Path fileName = schemaFile.getFileName();
      candidate = stripSchemaSuffix(fileName == null ? "" : fileName.toString());
    }
    return Names.requireSafeStructureName(Names.toSnakeCase(candidate));
  }

  private String stripSchemaSuffix(String name) {
    return name.replaceFirst("(?i)\\.schema\\.json$", "").replaceFirst("(?i)\\.(json|ya?ml)$", "");
  }

  private JsonNode readSchema(Path schemaFile) {
    ObjectMapper objectMapper =
        schemaFile.toString().endsWith(".json") ? new ObjectMapper() : new YAMLMapper();
    try {
      return objectMapper.readTree(schemaFile.toFile());
    } catch (IOException e) {
      throw new InspectorException("Failed to read JSON Schema: " + schemaFile, e);
    }
  }
}
