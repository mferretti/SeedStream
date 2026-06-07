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

  private static final Pattern PRIMITIVE_PATTERN =
      Pattern.compile("^(char|int|decimal|date|timestamp)\\[([^\\]]+?)\\.\\.([^\\]]+)\\]$");
  private static final Pattern ENUM_PATTERN = Pattern.compile("^enum\\[(.*)\\]$");
  private static final Pattern OBJECT_PATTERN = Pattern.compile("^object\\[([a-z_]+)\\]$");
  private static final Pattern ARRAY_PATTERN =
      Pattern.compile("^array\\[(.+),\\s*(-?\\d+)\\.\\.(-?\\d+)\\]$");
  private static final Pattern REF_PATTERN = Pattern.compile("^ref\\[([a-z_]+)\\.([a-z_]+)\\]$");

  private static final Pattern REF_RANGE_PATTERN =
      Pattern.compile("^ref\\[([a-z_]+)\\.([a-z_]+),\\s*(-?\\d+)\\.\\.(-?\\d+)\\]$");

  private static final Pattern REF_COUNT_PATTERN =
      Pattern.compile("^ref\\[([a-z_]+)\\.([a-z_]+),\\s*(-?\\d+)\\.\\.count\\]$");

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

    Matcher m;
    if ((m = PRIMITIVE_PATTERN.matcher(trimmed)).matches()) return parsePrimitive(m);
    if ((m = ENUM_PATTERN.matcher(trimmed)).matches()) return parseEnum(m, typeString);
    if ((m = OBJECT_PATTERN.matcher(trimmed)).matches()) return new ObjectType(m.group(1));
    if ((m = REF_COUNT_PATTERN.matcher(trimmed)).matches()) return parseRefCount(m);
    if ((m = REF_RANGE_PATTERN.matcher(trimmed)).matches()) return parseRefRange(m, typeString);
    if ((m = REF_PATTERN.matcher(trimmed)).matches())
      return new ReferenceType(m.group(1), m.group(2), null, null, false);
    if ((m = ARRAY_PATTERN.matcher(trimmed)).matches()) return parseArray(m, typeString);

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

  private static DataType parseRefCount(Matcher m) {
    return new ReferenceType(m.group(1), m.group(2), Long.parseLong(m.group(3)), null, true);
  }

  private static DataType parseRefRange(Matcher m, String typeString) {
    long min = Long.parseLong(m.group(3));
    long max = Long.parseLong(m.group(4));
    if (min > max) {
      throw new TypeParseException(
          "Invalid ref range: min (%d) > max (%d) in: %s".formatted(min, max, typeString));
    }
    return new ReferenceType(m.group(1), m.group(2), min, max, false);
  }

  private DataType parseArray(Matcher m, String typeString) {
    int minLength = Integer.parseInt(m.group(2));
    int maxLength = Integer.parseInt(m.group(3));
    if (minLength < 0 || maxLength < minLength) {
      throw new TypeParseException(
          "Invalid array length constraints: min=" + minLength + ", max=" + maxLength);
    }
    return new ArrayType(parse(m.group(1).trim()), minLength, maxLength);
  }
}
