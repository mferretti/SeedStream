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

import java.nio.file.Path;
import java.util.List;

/** Resolves where a job's structure files live (signature-only stub; see JobPathsTest). */
final class JobPaths {

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
    throw new UnsupportedOperationException("not implemented");
  }
}
