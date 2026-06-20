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
import com.datagenerator.inspector.ddl.NestingOptions;
import com.datagenerator.inspector.openapi.OpenApiInspector;
import com.datagenerator.inspector.protobuf.ProtobufInspector;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.CustomTypeConfigLoader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
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
  private static final String FORMAT_PROTOBUF = "protobuf";

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

  @Option(
      names = {"--best-effort"},
      description =
          "DDL only: emit the parseable subset and warn on tables that fail to parse, instead of "
              + "aborting. Default: any unparseable CREATE TABLE fails the whole inspection so no "
              + "partial, FK-broken output is written.")
  private boolean bestEffort;

  @Option(
      names = {"--nest"},
      arity = "0..1",
      fallbackValue = "auto",
      description =
          "DDL only: invert 1:n/1:1 foreign keys into nested array[object[child]]/object[child]. "
              + "auto (default when flag given) keeps cycles/M:N/shared children flat; all errors "
              + "on a true cycle; none is the default when the flag is absent.")
  private String nest;

  @Option(
      names = {"--nest-default-count"},
      description =
          "DDL only: multiplicity min..max for synthesized nested arrays when the schema gives no "
              + "hint (default: 1..10).")
  private String nestDefaultCount;

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
    if (nest != null && !"ddl".equals(resolved)) {
      log.warn(
          "--nest ignored for OpenAPI input (nesting comes from the spec's native $ref/array)");
    }

    try {
      Inspection inspection;
      if ("ddl".equals(resolved)) {
        inspection = new DdlInspector().inspect(inputFile, resolveNesting(resolved), bestEffort);
      } else if (FORMAT_PROTOBUF.equals(resolved)) {
        inspection = new ProtobufInspector().inspect(inputFile);
      } else {
        inspection = new OpenApiInspector().inspect(inputFile);
      }
      StructureYamlWriter writer = new StructureYamlWriter();

      int written = 0;
      int skipped = 0;
      for (DataStructure structure : inspection.structures()) {
        Map<String, String> comments =
            inspection.comments().getOrDefault(structure.getName(), Map.of());
        if (writer.write(structure, outputDir, force, comments)) {
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

      log.info(
          "inspect complete: {} written, {} skipped, {} fields flagged for review (commented)",
          written,
          skipped,
          inspection.flaggedCount());
      return 0;
    } catch (InspectorException e) {
      log.error("inspect failed: {}", e.getMessage());
      return 2;
    }
  }

  /** Resolves nesting options from the CLI flags ({@code none} when {@code --nest} is absent). */
  private NestingOptions resolveNesting(String resolved) {
    if (nest == null || !"ddl".equals(resolved)) {
      return NestingOptions.none();
    }
    return NestingOptions.parse(nest, nestDefaultCount);
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
      case "openapi", "ddl", FORMAT_PROTOBUF -> requested;
      case "auto" -> detectFormat();
      default -> {
        log.error("Unsupported --format '{}'. Supported: openapi, ddl, protobuf", format);
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
    if (fileName.endsWith(".desc")
        || fileName.endsWith(".binpb")
        || fileName.endsWith(".protoset")) {
      return FORMAT_PROTOBUF;
    }
    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".json")) {
      return "openapi";
    }
    log.error(
        "Cannot auto-detect format for '{}'. Use --format openapi|ddl|protobuf",
        inputFile.getFileName());
    return null;
  }
}
