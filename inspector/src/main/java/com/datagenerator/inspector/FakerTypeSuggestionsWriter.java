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

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes inspector-suggested custom Datafaker types (e.g. regex patterns discovered on schema
 * fields) to a companion {@code inspect-faker-types.yaml} the user can feed back via {@code
 * --faker-types}. Written in the same shape {@code CustomTypeConfigLoader} reads ({@code types:}
 * map of name → expression).
 *
 * <p>Never clobbers: an existing file is left untouched unless {@code force} is set, and the target
 * is refused outright if it is the very {@code --faker-types} input the run was given — so a
 * hand-tuned config can't be silently overwritten. Callers log the suggestions on a skip so nothing
 * is lost.
 */
public class FakerTypeSuggestionsWriter {

  /** Fixed companion file name, written under the inspect output directory. */
  public static final String FILE_NAME = "inspect-faker-types.yaml";

  /** Outcome of a write attempt. */
  public enum Result {
    /** Nothing to write (no suggestions). */
    NONE,
    /** File written. */
    WRITTEN,
    /** Skipped: file already exists and {@code force} was not set. */
    SKIPPED_EXISTS,
    /** Skipped: target is the same file passed as {@code --faker-types} input. */
    SKIPPED_IS_INPUT
  }

  private final YAMLMapper yaml =
      new YAMLMapper(
          YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());

  /**
   * Writes {@code suggestions} (type name → expression, e.g. {@code regex:...}) to {@link
   * #FILE_NAME} under {@code outputDir}.
   *
   * @param fakerTypesInput the {@code --faker-types} input path, or null; the target is refused if
   *     it resolves to this file
   * @return where the target resolved to, plus the outcome
   */
  public Outcome write(
      Map<String, String> suggestions, Path outputDir, boolean force, Path fakerTypesInput) {
    Path target = outputDir.resolve(FILE_NAME).normalize();
    if (suggestions == null || suggestions.isEmpty()) {
      return new Outcome(Result.NONE, target);
    }
    if (fakerTypesInput != null && target.equals(fakerTypesInput.normalize())) {
      return new Outcome(Result.SKIPPED_IS_INPUT, target);
    }
    if (Files.exists(target) && !force) {
      return new Outcome(Result.SKIPPED_EXISTS, target);
    }
    try {
      Files.createDirectories(outputDir);
      Files.writeString(target, render(suggestions));
      return new Outcome(Result.WRITTEN, target);
    } catch (IOException e) {
      throw new InspectorException("Failed to write " + target, e);
    }
  }

  /** Serializes the suggestions to the {@code types:} YAML shape (correct quoting/escaping). */
  private String render(Map<String, String> suggestions) throws IOException {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("types", new LinkedHashMap<>(suggestions));
    return yaml.writeValueAsString(root);
  }

  /** Where the companion file resolved to, and what happened. */
  public record Outcome(Result result, Path target) {}
}
