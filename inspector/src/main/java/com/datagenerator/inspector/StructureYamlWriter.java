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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a {@link DataStructure} to a {@code {name}.yaml} file under the output directory. Existing
 * files are skipped unless {@code force} is set — never silently clobbered (§7). Field order
 * matches the SeedStream convention: {@code name}, {@code geolocation}, then {@code data}.
 *
 * <p>Review comments for flagged fields are appended inline to that field's {@code datatype:} line.
 * The document body is serialized by Jackson (correct quoting/escaping); only the comment text is
 * appended afterwards, matched positionally so a repeated datatype value is never mis-tagged.
 */
public class StructureYamlWriter {

  private static final Pattern FIELD_KEY_LINE = Pattern.compile("^ {2}(\\w+):\\s*$");
  private static final String DATATYPE_LINE_PREFIX = "    datatype:";

  private final YAMLMapper yaml =
      new YAMLMapper(
          YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());

  /**
   * Writes one structure with optional inline review comments (field name → comment text). Returns
   * {@code true} if the file was written, {@code false} if it already existed and {@code force} was
   * not set.
   */
  public boolean write(
      DataStructure structure, Path outputDir, boolean force, Map<String, String> comments) {
    Path file = safeOutputFile(outputDir, structure.getName());
    if (Files.exists(file) && !force) {
      return false;
    }
    try {
      Files.createDirectories(outputDir);
      String body = yaml.writeValueAsString(toOrderedMap(structure));
      Files.writeString(file, annotate(body, comments == null ? Map.of() : comments));
      return true;
    } catch (IOException e) {
      throw new InspectorException("Failed to write structure: " + file, e);
    }
  }

  /**
   * Resolves the output file for {@code name} under {@code outputDir}, rejecting any name that
   * could escape the directory (CWE-22). The structure name comes from an untrusted spec (OpenAPI
   * schema key / SQL table name), so a value like {@code ../../etc/cron.d/x} or an absolute path
   * must not be allowed to redirect the write. The name is required to be a single safe path
   * segment, and the normalized result must stay strictly under the normalized output directory.
   *
   * @throws InspectorException if the name is unsafe or escapes {@code outputDir}
   */
  private static Path safeOutputFile(Path outputDir, String name) {
    Names.requireSafeStructureName(name);
    Path base = outputDir.normalize();
    Path file = base.resolve(name + ".yaml").normalize();
    if (!file.startsWith(base) || file.equals(base)) {
      throw new InspectorException(
          "Refusing to write structure '" + name + "' — resolves outside output directory " + base);
    }
    return file;
  }

  /** Appends {@code # comment} to the {@code datatype:} line of each commented field. */
  private String annotate(String body, Map<String, String> comments) {
    if (comments.isEmpty()) {
      return body;
    }
    String[] lines = body.split("\n", -1);
    StringBuilder out = new StringBuilder(body.length() + 64);
    String currentField = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      Matcher keyMatch = FIELD_KEY_LINE.matcher(line);
      if (keyMatch.matches()) {
        currentField = keyMatch.group(1);
      } else if (currentField != null
          && line.startsWith(DATATYPE_LINE_PREFIX)
          && comments.containsKey(currentField)) {
        line = line + "  # " + comments.get(currentField);
        currentField = null;
      }
      out.append(line);
      if (i < lines.length - 1) {
        out.append('\n');
      }
    }
    return out.toString();
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
