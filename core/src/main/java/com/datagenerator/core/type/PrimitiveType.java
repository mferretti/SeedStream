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

package com.datagenerator.core.type;

import java.util.Locale;
import lombok.Value;

/**
 * Represents primitive data types with optional range constraints. Supports: char, int, decimal,
 * boolean, date, timestamp.
 *
 * <p><b>Note:</b> Semantic types (name, email, address, etc.) are now handled by {@link
 * CustomDatafakerType} via {@link com.datagenerator.core.registry.DatafakerRegistry}.
 */
@Value
public class PrimitiveType implements DataType {
  Kind kind;
  String minValue; // String to support dates, numbers, etc.
  String maxValue;

  /**
   * Primitive type kinds - only true primitives with range constraints.
   *
   * <p>For semantic types (name, email, company, etc.), use {@link CustomDatafakerType} instead.
   */
  public enum Kind {
    /** Character string type with length range. */
    CHAR,
    /** Integer type with numeric range. */
    INT,
    /** Decimal number type with numeric range. */
    DECIMAL,
    /** Boolean type (true/false). */
    BOOLEAN,
    /** Date type with date range. */
    DATE,
    /** Timestamp type with datetime range. */
    TIMESTAMP
  }

  @Override
  public String describe() {
    if (kind == Kind.BOOLEAN) {
      return "boolean";
    }
    if (minValue == null || maxValue == null) {
      return kind.name().toLowerCase(Locale.ROOT);
    }
    return kind.name().toLowerCase(Locale.ROOT) + "[" + minValue + ".." + maxValue + "]";
  }
}
