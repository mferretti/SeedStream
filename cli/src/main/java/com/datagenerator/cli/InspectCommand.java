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
import com.datagenerator.inspector.openapi.OpenApiInspector;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Reads an existing schema and emits ready-to-use SeedStream structure YAML files.
 *
 * <p>v1 supports OpenAPI 3.x specs (JSON/YAML). See {@code docs/INSPECT-V1-SPEC.md}.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * datagenerator inspect api.yaml --output config/structures/
 * datagenerator inspect api.json --output config/structures/ --force
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
    description = "Generate SeedStream structure YAML from an OpenAPI spec",
    mixinStandardHelpOptions = true)
public class InspectCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Schema file to inspect (OpenAPI 3.x JSON/YAML)")
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
      description = "Input format: openapi (default). Reserved for future DDL support.")
  private String format = "openapi";

  @Override
  @SuppressWarnings("java:S106")
  public Integer call() {
    if (!"openapi".equalsIgnoreCase(format)) {
      log.error("Unsupported --format '{}'. v1 supports: openapi", format);
      return 2;
    }

    try {
      Inspection inspection = new OpenApiInspector().inspect(inputFile);
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
}
