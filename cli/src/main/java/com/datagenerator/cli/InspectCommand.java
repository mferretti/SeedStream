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

import com.datagenerator.inspector.FakerTypeSuggestionsWriter;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.inspector.ddl.DdlInspector;
import com.datagenerator.inspector.ddl.NestingOptions;
import com.datagenerator.inspector.jsonschema.JsonSchemaInspector;
import com.datagenerator.inspector.openapi.OpenApiInspector;
import com.datagenerator.inspector.protobuf.ProtobufInspector;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.CustomTypeConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
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
 * <p>Supports OpenAPI 3.x specs (JSON/YAML), standalone JSON Schema ({@code .schema.json} or a
 * {@code $schema}/{@code $defs} root), SQL DDL ({@code .sql}), and compiled Protobuf descriptor
 * sets. Format is auto-detected from the file extension/content and can be overridden with {@code
 * --format}. See {@code docs/INSPECT-V1-SPEC.md}.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * datagenerator inspect api.yaml --output config/structures/
 * datagenerator inspect payload.schema.json --output config/structures/
 * datagenerator inspect schema.sql --output config/structures/ --force
 * datagenerator inspect spec.yaml --format jsonschema
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
    description =
        "Generate SeedStream structure YAML from an OpenAPI spec, JSON Schema, SQL DDL, or Protobuf",
    mixinStandardHelpOptions = true)
public class InspectCommand implements Callable<Integer> {
  private static final String FORMAT_PROTOBUF = "protobuf";
  private static final String FORMAT_OPENAPI = "openapi";
  private static final String FORMAT_JSONSCHEMA = "jsonschema";

  @Parameters(
      index = "0",
      description =
          "Schema file to inspect: OpenAPI 3.x (.yaml/.yml/.json), standalone JSON Schema"
              + " (.schema.json or a $schema/$defs root), SQL DDL (.sql), or compiled Protobuf"
              + " descriptor set (.desc/.binpb/.protoset)")
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
      description =
          "Input format: openapi | jsonschema | ddl | protobuf. Default: auto-detect from file"
              + " extension/content.")
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
          "--nest ignored for {} input (nesting comes from the schema's native $ref/array)",
          resolved);
    }

    try {
      Inspection inspection;
      if ("ddl".equals(resolved)) {
        inspection = new DdlInspector().inspect(inputFile, resolveNesting(resolved), bestEffort);
      } else if (FORMAT_PROTOBUF.equals(resolved)) {
        inspection = new ProtobufInspector().inspect(inputFile);
      } else if (FORMAT_JSONSCHEMA.equals(resolved)) {
        inspection = new JsonSchemaInspector().inspect(inputFile);
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
      writeFakerTypeSuggestions(inspection.fakerTypeSuggestions());

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

  /**
   * Writes discovered regex/custom-type suggestions to a companion {@code
   * inspect-faker-types.yaml}, never clobbering an existing file or the {@code --faker-types}
   * input. On a skip the suggestions are logged so the user can still act on them.
   */
  private void writeFakerTypeSuggestions(Map<String, String> suggestions) {
    FakerTypeSuggestionsWriter.Outcome outcome =
        new FakerTypeSuggestionsWriter().write(suggestions, outputDir, force, fakerTypes);
    switch (outcome.result()) {
      case WRITTEN ->
          log.info(
              "wrote {} ({} suggested type(s)) — rerun with --faker-types {} to apply",
              outcome.target(),
              suggestions.size(),
              outcome.target());
      case SKIPPED_EXISTS ->
          log.warn(
              "{} exists — not overwritten (use --force). Suggested types: {}",
              outcome.target(),
              suggestions);
      case SKIPPED_IS_INPUT ->
          log.warn(
              "not writing suggestions over the --faker-types input {}. Suggested types: {}",
              outcome.target(),
              suggestions);
      default -> {
        // NONE (or any future outcome): nothing to write
      }
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
      case FORMAT_OPENAPI, "ddl", FORMAT_PROTOBUF, FORMAT_JSONSCHEMA -> requested;
      case "auto" -> detectFormat();
      default -> {
        log.error(
            "Unsupported --format '{}'. Supported: openapi, jsonschema, ddl, protobuf", format);
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
    if (fileName.endsWith(".schema.json")) {
      return FORMAT_JSONSCHEMA;
    }
    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".json")) {
      // .json/.yaml is ambiguous between OpenAPI and standalone JSON Schema — peek the root.
      return peekJsonOrYamlFormat();
    }
    log.error(
        "Cannot auto-detect format for '{}'. Use --format openapi|jsonschema|ddl|protobuf",
        inputFile.getFileName());
    return null;
  }

  /**
   * Disambiguates a {@code .json}/{@code .yaml} input by its root keys: an OpenAPI document has
   * {@code openapi}/{@code swagger}; a standalone JSON Schema has {@code $schema}/{@code
   * $defs}/{@code definitions}. Defaults to OpenAPI (the historical behaviour) when neither is
   * present or the file cannot be parsed, so detection never hard-fails here.
   */
  private String peekJsonOrYamlFormat() {
    try {
      ObjectMapper reader =
          inputFile.toString().toLowerCase(Locale.ROOT).endsWith(".json")
              ? new ObjectMapper()
              : new YAMLMapper();
      JsonNode root = reader.readTree(inputFile.toFile());
      if (root == null || !root.isObject()) {
        return FORMAT_OPENAPI;
      }
      if (root.has(FORMAT_OPENAPI) || root.has("swagger")) {
        return FORMAT_OPENAPI;
      }
      if (root.has("$schema") || root.has("$defs") || root.has("definitions")) {
        return FORMAT_JSONSCHEMA;
      }
    } catch (IOException e) {
      log.debug("format peek failed for {}, defaulting to openapi: {}", inputFile, e.getMessage());
    }
    return FORMAT_OPENAPI;
  }
}
