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

import com.datagenerator.schema.exception.SchemaParseException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Resolves where a job's structure files live. */
@Slf4j
final class JobPaths {

  private static final String CONFIG_STRUCTURES = "config/structures";

  private JobPaths() {}

  /**
   * Outcome of structures-directory resolution.
   *
   * @param structuresPath absolute, normalized directory the structure file is loaded from
   * @param fellBack true only when the inferred sibling dir lacked the file and {@code
   *     <currentDir>/config/structures} was used instead
   * @param tried the structure directories examined, in order (sibling first, then config fallback)
   */
  record StructuresResolution(Path structuresPath, boolean fellBack, List<Path> tried) {}

  /**
   * Resolves the structures directory: explicit {@code structuresPath} is resolved against {@code
   * currentDir} unchanged; otherwise a job in a dir named {@code jobs} uses its sibling {@code
   * structures} (job path made absolute and normalized against {@code currentDir}, no symlink
   * resolution), falling back to {@code <currentDir>/config/structures} with a WARN log when the
   * sibling lacks {@code source}; a job dir not named {@code jobs} uses {@code
   * <currentDir>/config/structures} without existence checks. Throws (message lists every location
   * tried) when the inferred sibling and the config fallback both lack {@code source}.
   */
  static StructuresResolution resolveStructuresPath(
      Path jobFile, String explicitStructuresPath, String source, Path currentDir) {
    Path cwd = currentDir.toAbsolutePath();
    if (explicitStructuresPath != null) {
      return new StructuresResolution(cwd.resolve(explicitStructuresPath), false, List.of());
    }
    Path configDir = cwd.resolve(CONFIG_STRUCTURES);
    Path jobDir = cwd.resolve(jobFile).normalize().getParent();
    if (jobDir == null
        || jobDir.getFileName() == null
        || !jobDir.getFileName().toString().equals("jobs")) {
      return new StructuresResolution(configDir, false, List.of());
    }
    Path sibling = jobDir.getParent().resolve("structures");
    if (Files.exists(sibling.resolve(source))) {
      return new StructuresResolution(sibling, false, List.of(sibling));
    }
    Path fallbackFile = configDir.resolve(source);
    if (Files.exists(fallbackFile)) {
      log.warn(
          "Structure file {} not found in inferred dir {}; using {} instead."
              + " Set structures_path to choose explicitly.",
          fallbackFile,
          sibling,
          configDir);
      return new StructuresResolution(configDir, true, List.of(sibling, configDir));
    }
    throw new SchemaParseException(
        "data structure file not found: "
            + source
            + " (tried "
            + sibling.resolve(source)
            + " and "
            + fallbackFile
            + ")");
  }
}
