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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Name utilities: tokenization and snake_case conversion shared by the inspector. */
public final class Names {

  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
  private static final Pattern ACRONYM_BOUNDARY = Pattern.compile("([A-Z]+)([A-Z][a-z])");
  private static final Pattern SEPARATORS = Pattern.compile("[_\\-\\s]+");
  private static final Pattern SPACE = Pattern.compile(" ");

  private Names() {}

  /**
   * Converts a schema or field name to snake_case. Handles camelCase, PascalCase, kebab-case and
   * already-snake_case inputs.
   *
   * <p>Examples: {@code LineItem -> line_item}, {@code firstName -> first_name}, {@code postal-code
   * -> postal_code}.
   */
  public static String toSnakeCase(String name) {
    if (name == null || name.isBlank()) {
      return name;
    }
    return String.join("_", tokenize(name));
  }

  /**
   * Validates that a structure name is a single safe path segment (CWE-22 guard). The name is
   * derived from an untrusted source (schema key/title/{@code $id}/file name), so a value like
   * {@code ../../etc/x} or one containing a path separator must not be allowed to redirect a later
   * file write. Shared by {@code StructureYamlWriter} (emitted-file naming) and the inspectors
   * (root-schema naming) so the two can't diverge.
   *
   * @return the name unchanged when safe
   * @throws InspectorException if the name is null/blank or not a plain single path segment
   */
  public static String requireSafeStructureName(String name) {
    if (name == null
        || name.isBlank()
        || name.indexOf('/') >= 0
        || name.indexOf('\\') >= 0
        || name.indexOf('\0') >= 0
        || name.contains("..")) {
      throw new InspectorException(
          "Refusing structure with unsafe name '" + name + "' (must be a plain file name)");
    }
    return name;
  }

  /**
   * Splits a name into lowercase word tokens on camelCase boundaries and on {@code _}/{@code
   * -}/whitespace separators. Returns an empty list for blank input.
   */
  public static List<String> tokenize(String name) {
    if (name == null || name.isBlank()) {
      return List.of();
    }
    String spaced = CAMEL_BOUNDARY.matcher(name).replaceAll("$1 $2"); // camelCase boundary
    spaced = ACRONYM_BOUNDARY.matcher(spaced).replaceAll("$1 $2"); // ACRONYMWord boundary
    spaced =
        SEPARATORS.matcher(spaced).replaceAll(" ").trim().toLowerCase(Locale.ROOT); // separators
    return Arrays.stream(SPACE.split(spaced)).filter(s -> !s.isBlank()).toList();
  }
}
