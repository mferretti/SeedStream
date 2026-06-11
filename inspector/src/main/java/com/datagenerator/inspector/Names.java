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

/** Name utilities: tokenization and snake_case conversion shared by the inspector. */
public final class Names {

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
   * Splits a name into lowercase word tokens on camelCase boundaries and on {@code _}/{@code
   * -}/whitespace separators. Returns an empty list for blank input.
   */
  public static List<String> tokenize(String name) {
    if (name == null || name.isBlank()) {
      return List.of();
    }
    String spaced =
        name.replaceAll("([a-z0-9])([A-Z])", "$1 $2") // camelCase boundary
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2") // ACRONYMWord boundary
            .replaceAll("[_\\-\\s]+", " ") // separators
            .trim()
            .toLowerCase(Locale.ROOT);
    return Arrays.stream(spaced.split(" ")).filter(s -> !s.isBlank()).toList();
  }
}
