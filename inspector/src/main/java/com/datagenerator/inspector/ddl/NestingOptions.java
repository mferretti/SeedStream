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

import com.datagenerator.inspector.InspectorException;
import java.util.Locale;

/**
 * Controls DDL foreign-key inversion: whether {@code 1:n}/{@code 1:1} FKs are turned into nested
 * {@code array[object[child], min..max]} / {@code object[child]} fields on the parent structure
 * instead of the default flat {@code ref[parent.col]}. See {@code docs/INSPECT-NESTING-PLAN.md}.
 *
 * @param mode nesting strategy
 * @param defaultMin lower bound for a synthesized array when the source gives no hint
 * @param defaultMax upper bound for a synthesized array when the source gives no hint
 */
public record NestingOptions(Mode mode, int defaultMin, int defaultMax) {

  /** Nesting strategy. */
  public enum Mode {
    /** Today's behavior: every FK stays a flat {@code ref[parent.col]}. */
    NONE,
    /** Invert FKs into nesting where safe; keep flat refs on cycles / M:N / shared children. */
    AUTO,
    /** Aggressive: nest every invertible FK, error on a true cycle instead of falling back. */
    ALL
  }

  private static final int DEFAULT_MIN = 1;
  private static final int DEFAULT_MAX = 10;

  /** Default (no nesting). */
  public static NestingOptions none() {
    return new NestingOptions(Mode.NONE, DEFAULT_MIN, DEFAULT_MAX);
  }

  /** True when foreign keys should be inverted into nested documents. */
  public boolean enabled() {
    return mode != Mode.NONE;
  }

  /**
   * Parses the {@code --nest} flag value ({@code auto|all|none}, empty → {@code auto}) and a {@code
   * --nest-default-count} of the form {@code min..max}.
   *
   * @throws InspectorException on an unrecognized mode or malformed count range
   */
  public static NestingOptions parse(String nestValue, String defaultCount) {
    Mode mode = parseMode(nestValue);
    int min = DEFAULT_MIN;
    int max = DEFAULT_MAX;
    if (defaultCount != null && !defaultCount.isBlank()) {
      int sep = defaultCount.indexOf("..");
      if (sep < 0) {
        throw new InspectorException(
            "Invalid --nest-default-count '" + defaultCount + "' (expected min..max)");
      }
      min = parseBound(defaultCount.substring(0, sep), defaultCount);
      max = parseBound(defaultCount.substring(sep + 2), defaultCount);
      if (min < 0 || max < min) {
        throw new InspectorException(
            "Invalid --nest-default-count '" + defaultCount + "' (need 0 <= min <= max)");
      }
    }
    return new NestingOptions(mode, min, max);
  }

  private static Mode parseMode(String nestValue) {
    String v = nestValue == null ? "" : nestValue.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "", "auto" -> Mode.AUTO;
      case "all" -> Mode.ALL;
      case "none" -> Mode.NONE;
      default ->
          throw new InspectorException(
              "Invalid --nest '" + nestValue + "' (expected auto|all|none)");
    };
  }

  private static int parseBound(String raw, String original) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new InspectorException(
          "Invalid --nest-default-count '" + original + "' (expected integer bounds)", e);
    }
  }
}
