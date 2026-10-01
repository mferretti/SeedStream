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

package com.datagenerator.schema.parser;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.core.util.LogUtils;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses data structure definition YAML files into DataStructure objects. Validates the structure
 * using Jakarta Bean Validation.
 */
@Slf4j
public class DataStructureParser extends AbstractYamlParser<DataStructure> {

  private static final Pattern NAME = Pattern.compile(TypeParser.IDENT);

  /**
   * Parse a data structure definition from a YAML file.
   *
   * @param filePath Path to the YAML file
   * @return Parsed and validated DataStructure
   * @throws com.datagenerator.schema.exception.SchemaParseException if parsing or validation fails
   */
  public DataStructure parse(Path filePath) {
    log.debug("Parsing data structure from: {}", filePath);
    DataStructure structure = parseFile(filePath, DataStructure.class, "data structure");
    requireNameMatchesFile(structure.getName(), filePath);
    if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
      log.trace(
          "Parsed structure: name={}, fields={}, geolocation={}",
          structure.getName(),
          structure.getData().keySet(),
          structure.getGeolocation());
    }
    log.info("Successfully parsed data structure: {}", structure.getName());
    return structure;
  }

  /**
   * A record definition's {@code name} is how it is referenced ({@code object[name]}, the job's
   * root) and resolved to {@code <name>.yaml}, so it must be a plain identifier equal to its own
   * file name. A mismatch used to fail later with a confusing error, and a path-like name made
   * {@code execute} generate from a different file than the job named.
   */
  private static void requireNameMatchesFile(String name, Path filePath) {
    Path fileName = filePath.getFileName();
    String expected = fileName == null ? "" : fileName.toString().replaceFirst("\\.ya?ml$", "");
    if (!NAME.matcher(name).matches()) {
      throw new SchemaParseException(
          "Validation failed for %s: name: '%s' must be a lowercase identifier ([a-z_][a-z0-9_]*)"
              .formatted(filePath, name));
    }
    if (!name.equals(expected)) {
      throw new SchemaParseException(
          "Validation failed for %s: name: '%s' must match the file name ('%s')"
              .formatted(filePath, name, expected));
    }
  }
}
