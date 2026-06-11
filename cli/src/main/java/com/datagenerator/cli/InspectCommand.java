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

package com.datagenerator.cli;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.inspector.ddl.DdlInspector;
import com.datagenerator.inspector.openapi.OpenApiInspector;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.CustomTypeConfigLoader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Reads an existing schema and emits ready-to-use SeedStream structure YAML files.
 *
 * <p>Supports OpenAPI 3.x specs (JSON/YAML) and SQL DDL ({@code .sql}). Format is auto-detected
 * from the file extension and can be overridden with {@code --format}. See {@code
 * docs/INSPECT-V1-SPEC.md}.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * datagenerator inspect api.yaml --output config/structures/
 * datagenerator inspect schema.sql --output config/structures/ --force
 * datagenerator inspect spec.yaml --format ddl
 * </pre>
 *
 * <p><b>Exit codes:</b>
 *
 * <ul>
 *   <li>0 — structures emitted
 *   <li>2 — read or mapping error
 * </ul>
 */
@Slf4j
@Command(
    name = "inspect",
    description = "Generate SeedStream structure YAML from an OpenAPI spec or SQL DDL",
    mixinStandardHelpOptions = true)
public class InspectCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Schema file to inspect (OpenAPI 3.x JSON/YAML or .sql)")
  private Path inputFile;

  @Option(
      names = {"-o", "--output"},
      description = "Output directory for generated structure YAML (default: ${DEFAULT-VALUE})")
  private Path outputDir = Path.of("config/structures");

  @Option(
      names = {"--force"},
      description = "Overwrite existing structure files (default: skip them)")
  private boolean force;

  @Option(
      names = {"--format"},
      description = "Input format: openapi | ddl. Default: auto-detect from file extension.")
  private String format = "auto";

  @Option(
      names = {"--faker-types"},
      description =
          "YAML config of custom Datafaker types to register before inspection, so name hints "
              + "can target them (see docs/INSPECT-V1-SPEC.md).")
  private Path fakerTypes;

  @Override
  @SuppressWarnings("java:S106")
  public Integer call() {
    String resolved = resolveFormat();
    if (resolved == null) {
      return 2;
    }
    if (!loadCustomTypes()) {
      return 2;
    }

    try {
      Inspection inspection =
          "ddl".equals(resolved)
              ? new DdlInspector().inspect(inputFile)
              : new OpenApiInspector().inspect(inputFile);
      StructureYamlWriter writer = new StructureYamlWriter();

      int written = 0;
      int skipped = 0;
      for (DataStructure structure : inspection.structures()) {
        if (writer.write(structure, outputDir, force)) {
          written++;
          log.info("wrote {}/{}.yaml", outputDir, structure.getName());
        } else {
          skipped++;
          log.warn(
              "skipped {}/{}.yaml (exists — use --force to overwrite)",
              outputDir,
              structure.getName());
        }
      }

      inspection.warnings().forEach(log::warn);
      inspection
          .inferredFields()
          .forEach(
              field -> log.warn("inferred default for {} — review the generated range", field));

      log.info(
          "inspect complete: {} written, {} skipped, {} fields inferred",
          written,
          skipped,
          inspection.inferredFields().size());
      return 0;
    } catch (InspectorException e) {
      log.error("inspect failed: {}", e.getMessage());
      return 2;
    }
  }

  /** Loads the optional custom Datafaker types config. Returns false on failure. */
  private boolean loadCustomTypes() {
    if (fakerTypes == null) {
      return true;
    }
    try {
      new CustomTypeConfigLoader().load(fakerTypes);
      return true;
    } catch (SchemaParseException e) {
      log.error("inspect failed: {}", e.getMessage());
      return false;
    }
  }

  /** Resolves the effective format, auto-detecting from extension. Returns null on error. */
  private String resolveFormat() {
    String requested = format.toLowerCase(Locale.ROOT);
    return switch (requested) {
      case "openapi", "ddl" -> requested;
      case "auto" -> detectFormat();
      default -> {
        log.error("Unsupported --format '{}'. Supported: openapi, ddl", format);
        yield null;
      }
    };
  }

  private String detectFormat() {
    Path name = inputFile.getFileName();
    String fileName = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".sql")) {
      return "ddl";
    }
    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".json")) {
      return "openapi";
    }
    log.error(
        "Cannot auto-detect format for '{}'. Use --format openapi|ddl", inputFile.getFileName());
    return null;
  }
}
