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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the {@code counts.yaml} skeleton (every table at a placeholder count) next to the
 * structures.
 */
public class CountsFileWriter {

  /** Fixed file name, written under the inspect output directory. */
  public static final String FILE_NAME = "counts.yaml";

  private static final int PLACEHOLDER = 100;

  /** Outcome of a write attempt. */
  public enum Result {
    /** Nothing to write (no tables). */
    NONE,
    /** File written. */
    WRITTEN,
    /** Skipped: file already exists and {@code force} was not set. */
    SKIPPED_EXISTS
  }

  /** Where the file resolved to, and what happened. */
  public record Outcome(Result result, Path target) {}

  public Outcome write(List<String> tables, Path outputDir, boolean force) {
    Path target = outputDir.resolve(FILE_NAME).normalize();
    if (tables == null || tables.isEmpty()) {
      return new Outcome(Result.NONE, target);
    }
    if (Files.exists(target) && !force) {
      return new Outcome(Result.SKIPPED_EXISTS, target);
    }
    String body =
        "# Row counts per table, read by jobs through `counts: counts.yaml` (path relative to the job file)\n"
            + "# and by bare ref[table.column] fields. Placeholder values: set them to your real table"
            + " sizes.\n"
            + tables.stream().map(t -> t + ": " + PLACEHOLDER + "\n").collect(Collectors.joining());
    try {
      Files.createDirectories(outputDir);
      Files.writeString(target, body);
      return new Outcome(Result.WRITTEN, target);
    } catch (IOException e) {
      throw new InspectorException("Failed to write " + target, e);
    }
  }
}
