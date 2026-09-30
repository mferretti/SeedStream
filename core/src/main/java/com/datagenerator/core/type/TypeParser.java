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

import com.datagenerator.core.exception.TypeParseException;
import com.datagenerator.core.registry.DatafakerRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses datatype strings from YAML into DataType objects. Supports all type syntax: primitives,
 * enums, objects, arrays, references.
 */
public class TypeParser {
  /** Constructs a new TypeParser instance. */
  public TypeParser() {
    // Default constructor
  }

  // Structure, field and group names: lowercase snake_case, digits allowed after the first
  // character (inspect keeps them, e.g. Item2 -> item2, package v1, #355).
  private static final String IDENT = "[a-z_][a-z0-9_]*";

  private static final Pattern PRIMITIVE_PATTERN =
      Pattern.compile(
          "^(char|int|decimal|date|timestamp)\\[((?:(?!\\.\\.)[^\\]])++)\\.\\.([^\\]]++)\\]$");
  private static final Pattern ENUM_PATTERN = Pattern.compile("^enum\\[(.*)\\]$");
  private static final Pattern OBJECT_PATTERN = Pattern.compile("^object\\[(" + IDENT + ")\\]$");
  private static final Pattern ARRAY_PATTERN =
      Pattern.compile("^array\\[(.+),\\s*(-?\\d+)\\.\\.(-?\\d+)\\]$");
  private static final Pattern UNIQUE_PATTERN =
      Pattern.compile("^unique\\[(?:(" + IDENT + ")\\s*,\\s*)?(-?\\d+)\\.\\.(-?\\d+|count)\\]$");
  private static final Pattern SERIAL_PATTERN = Pattern.compile("^serial(?:\\[(-?\\d+)\\])?$");
  private static final Pattern REF_UNIQUE_PATTERN =
      Pattern.compile(
          "^ref\\[("
              + IDENT
              + ")\\.("
              + IDENT
              + "),\\s*(-?\\d+)\\.\\.(-?\\d+|count),\\s*unique(?:=("
              + IDENT
              + "))?\\]$");
  private static final Pattern PARENT_REF_PATTERN =
      Pattern.compile("^ref\\[parent\\.(" + IDENT + ")\\]$");

  private static final Pattern REF_PATTERN =
      Pattern.compile("^ref\\[(" + IDENT + ")\\.(" + IDENT + ")\\]$");

  private static final Pattern REF_RANGE_PATTERN =
      Pattern.compile("^ref\\[(" + IDENT + ")\\.(" + IDENT + "),\\s*(-?\\d+)\\.\\.(-?\\d+)\\]$");

  private static final Pattern REF_COUNT_PATTERN =
      Pattern.compile("^ref\\[(" + IDENT + ")\\.(" + IDENT + "),\\s*(-?\\d+)\\.\\.count\\]$");

  /**
   * Parse a datatype string into a DataType object.
   *
   * @param typeString the type string (e.g., "char[3..15]", "array[int[1..100], 5..10]")
   * @return the parsed DataType
   * @throws TypeParseException if the type string is invalid
   */
  public DataType parse(String typeString) {
    if (typeString == null || typeString.isBlank()) {
      throw new TypeParseException("Type string cannot be null or empty");
    }
    String trimmed = typeString.trim();
    if ("boolean".equals(trimmed)) return new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);

    Matcher m = PRIMITIVE_PATTERN.matcher(trimmed);
    if (m.matches()) return parsePrimitive(m);
    m = ENUM_PATTERN.matcher(trimmed);
    if (m.matches()) return parseEnum(m, typeString);
    m = OBJECT_PATTERN.matcher(trimmed);
    if (m.matches()) return new ObjectType(m.group(1));
    m = PARENT_REF_PATTERN.matcher(trimmed);
    if (m.matches()) return new ParentReferenceType(m.group(1));
    m = REF_UNIQUE_PATTERN.matcher(trimmed);
    if (m.matches())
      return parseUnique(
          m.group(5), m.group(3), m.group(4), m.group(1) + "." + m.group(2), typeString);
    m = REF_COUNT_PATTERN.matcher(trimmed);
    if (m.matches()) return parseRefCount(m, typeString);
    m = REF_RANGE_PATTERN.matcher(trimmed);
    if (m.matches()) return parseRefRange(m, typeString);
    m = REF_PATTERN.matcher(trimmed);
    if (m.matches()) return new ReferenceType(m.group(1), m.group(2), null, null, false);
    m = ARRAY_PATTERN.matcher(trimmed);
    if (m.matches()) return parseArray(m, typeString);

    m = UNIQUE_PATTERN.matcher(trimmed);
    if (m.matches()) return parseUnique(m.group(1), m.group(2), m.group(3), null, typeString);
    m = SERIAL_PATTERN.matcher(trimmed);
    if (m.matches()) return parseSerial(m, typeString);

    if (DatafakerRegistry.isRegistered(trimmed)) {
      return new CustomDatafakerType(DatafakerRegistry.getCanonicalName(trimmed));
    }
    throw new TypeParseException("Unsupported type syntax: " + typeString);
  }

  private static DataType parsePrimitive(Matcher m) {
    return new PrimitiveType(
        PrimitiveType.Kind.valueOf(m.group(1).toUpperCase(Locale.ROOT)),
        m.group(2).trim(),
        m.group(3).trim());
  }

  private static DataType parseEnum(Matcher m, String typeString) {
    List<String> values =
        Arrays.stream(m.group(1).trim().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    if (values.isEmpty()) {
      throw new TypeParseException("Enum must have at least one value: %s".formatted(typeString));
    }
    return new EnumType(values);
  }

  private static DataType parseRefCount(Matcher m, String typeString) {
    return new ReferenceType(
        m.group(1), m.group(2), parseLongBound(m.group(3), typeString), null, true);
  }

  private static DataType parseRefRange(Matcher m, String typeString) {
    long min = parseLongBound(m.group(3), typeString);
    long max = parseLongBound(m.group(4), typeString);
    if (min > max) {
      throw new TypeParseException(
          "Invalid ref range: min (%d) > max (%d) in: %s".formatted(min, max, typeString));
    }
    return new ReferenceType(m.group(1), m.group(2), min, max, false);
  }

  private static DataType parseUnique(
      String group, String minStr, String maxStr, String refTarget, String typeString) {
    try {
      long min = Long.parseLong(minStr);
      boolean isCount = "count".equals(maxStr);
      long max = isCount ? 0 : Long.parseLong(maxStr);
      if (!isCount && min > max) {
        throw new TypeParseException(
            "Invalid unique range: min (%d) > max (%d) in: %s".formatted(min, max, typeString));
      }
      return new UniqueType(group, min, max, isCount, refTarget);
    } catch (NumberFormatException e) {
      throw new TypeParseException("Invalid unique range in: " + typeString);
    }
  }

  private static DataType parseSerial(Matcher m, String typeString) {
    try {
      return new SerialType(m.group(1) == null ? 1L : Long.parseLong(m.group(1)));
    } catch (NumberFormatException e) {
      throw new TypeParseException("Invalid serial start in: " + typeString);
    }
  }

  private DataType parseArray(Matcher m, String typeString) {
    int minLength = parseIntBound(m.group(2), typeString);
    int maxLength = parseIntBound(m.group(3), typeString);
    if (minLength < 0 || maxLength < minLength) {
      throw new TypeParseException(
          "Invalid array length constraints: min=" + minLength + ", max=" + maxLength);
    }
    return new ArrayType(parse(m.group(1).trim()), minLength, maxLength);
  }

  // The patterns only admit digits, so a NumberFormatException here always means overflow (#346).
  private static long parseLongBound(String value, String typeString) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new TypeParseException(
          "Numeric bound %s is out of range (max %d) in: %s"
              .formatted(value, Long.MAX_VALUE, typeString),
          e);
    }
  }

  private static int parseIntBound(String value, String typeString) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new TypeParseException(
          "Numeric bound %s is out of range (max %d) in: %s"
              .formatted(value, Integer.MAX_VALUE, typeString),
          e);
    }
  }
}
