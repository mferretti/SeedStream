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

package com.datagenerator.inspector;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@link DataStructure} to a {@code {name}.yaml} file under the output directory. Existing
 * files are skipped unless {@code force} is set — never silently clobbered (§7). Field order
 * matches the SeedStream convention: {@code name}, {@code geolocation}, then {@code data}.
 */
public class StructureYamlWriter {

  private final YAMLMapper yaml =
      new YAMLMapper(
          YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());

  /**
   * Writes one structure. Returns {@code true} if the file was written, {@code false} if it already
   * existed and {@code force} was not set.
   */
  public boolean write(DataStructure structure, Path outputDir, boolean force) {
    Path file = outputDir.resolve(structure.getName() + ".yaml");
    if (Files.exists(file) && !force) {
      return false;
    }
    try {
      Files.createDirectories(outputDir);
      yaml.writeValue(file.toFile(), toOrderedMap(structure));
      return true;
    } catch (IOException e) {
      throw new InspectorException("Failed to write structure: " + file, e);
    }
  }

  private Map<String, Object> toOrderedMap(DataStructure structure) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("name", structure.getName());
    if (structure.getGeolocation() != null) {
      root.put("geolocation", structure.getGeolocation());
    }
    Map<String, Object> data = new LinkedHashMap<>();
    structure.getData().forEach((field, def) -> data.put(field, fieldToMap(def)));
    root.put("data", data);
    return root;
  }

  private Map<String, Object> fieldToMap(FieldDefinition def) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("datatype", def.getDatatype());
    if (def.getAlias() != null) {
      field.put("alias", def.getAlias());
    }
    return field;
  }
}
