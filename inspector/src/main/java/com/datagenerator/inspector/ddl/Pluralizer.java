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

package com.datagenerator.inspector.ddl;

import java.util.Locale;
import java.util.Set;

/**
 * Minimal English pluralizer for synthesized nested-array field names: a {@code 1:n} child table
 * {@code invoice_item} becomes the parent field {@code invoice_items}. Only the final word of a
 * {@code snake_case} name is inflected so {@code invoice_item -> invoice_items}, not {@code
 * invoices_item}. This is heuristic, not linguistically complete — irregular plurals are out of
 * scope (the user can rename the field). See {@code docs/INSPECT-NESTING-PLAN.md} §5.
 */
public final class Pluralizer {

  private Pluralizer() {}

  private static final Set<String> SIBILANT_SUFFIXES = Set.of("s", "x", "z", "ch", "sh");
  private static final String VOWELS = "aeiou";

  /** Pluralizes a {@code snake_case} table name by inflecting its last token. */
  public static String pluralize(String snakeName) {
    if (snakeName == null || snakeName.isBlank()) {
      return snakeName;
    }
    int lastUnderscore = snakeName.lastIndexOf('_');
    String prefix = lastUnderscore < 0 ? "" : snakeName.substring(0, lastUnderscore + 1);
    String word = snakeName.substring(lastUnderscore + 1);
    return prefix + inflect(word);
  }

  private static String inflect(String word) {
    String lower = word.toLowerCase(Locale.ROOT);
    if (endsWithConsonantY(lower)) {
      return word.substring(0, word.length() - 1) + "ies";
    }
    if (SIBILANT_SUFFIXES.stream().anyMatch(lower::endsWith)) {
      return word + "es";
    }
    return word + "s";
  }

  private static boolean endsWithConsonantY(String lower) {
    if (!lower.endsWith("y") || lower.length() < 2) {
      return false;
    }
    char beforeY = lower.charAt(lower.length() - 2);
    return VOWELS.indexOf(beforeY) < 0;
  }
}
