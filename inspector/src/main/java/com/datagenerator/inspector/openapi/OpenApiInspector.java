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

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an OpenAPI 3.x spec and maps every {@code components.schemas} object to a SeedStream {@link
 * DataStructure}. See {@code docs/INSPECT-V1-SPEC.md}.
 */
public class OpenApiInspector {

  private final OpenApiTypeMapper mapper = new OpenApiTypeMapper();

  /** Inspects an OpenAPI spec file and returns the structures plus diagnostics. */
  public Inspection inspect(Path specFile) {
    JsonNode root = readSpec(specFile);
    JsonNode schemas = root.path("components").path("schemas");
    if (!schemas.isObject()) {
      throw new InspectorException(
          "No components.schemas found in " + specFile + " — not a recognizable OpenAPI 3.x spec");
    }

    List<DataStructure> structures = new ArrayList<>();
    List<String> inferred = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (Map.Entry<String, JsonNode> entry : schemas.properties()) {
      DataStructure structure = toStructure(entry.getKey(), entry.getValue(), inferred, warnings);
      if (structure != null) {
        structures.add(structure);
      }
    }

    return new Inspection(structures, inferred, warnings);
  }

  private DataStructure toStructure(
      String schemaName, JsonNode schemaNode, List<String> inferred, List<String> warnings) {
    String name = Names.toSnakeCase(schemaName);
    JsonNode properties = schemaNode.path("properties");
    if (!properties.isObject() || properties.isEmpty()) {
      warnings.add("schema '" + schemaName + "' has no properties — skipped");
      return null;
    }

    Map<String, FieldDefinition> data = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> property : properties.properties()) {
      String fieldName = property.getKey();
      MappedType mapped = mapper.map(fieldName, property.getValue());
      if (mapped.inferred()) {
        inferred.add(name + "." + fieldName);
      }
      data.put(fieldName, new FieldDefinition(mapped.datatype(), null));
    }

    return new DataStructure(name, null, data);
  }

  private JsonNode readSpec(Path specFile) {
    ObjectMapper objectMapper =
        specFile.toString().endsWith(".json") ? new ObjectMapper() : new YAMLMapper();
    try {
      return objectMapper.readTree(specFile.toFile());
    } catch (IOException e) {
      throw new InspectorException("Failed to read OpenAPI spec: " + specFile, e);
    }
  }
}
