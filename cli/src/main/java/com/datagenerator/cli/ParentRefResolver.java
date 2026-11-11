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

import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ParentUniqueReferenceType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.core.type.SerialType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.datagenerator.schema.parser.DataStructureParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns {@code ref[t.c]}, {@code ref[t.c, unique]} and {@code ref[t.c, unique=g]} into the types
 * the user could have written by hand ({@code ref[t.c, lo..hi]}, {@code ref[t.c, lo..hi, unique]})
 * from the parent key's declared type and the parent's row count (#442). It runs while a structure
 * is loaded, before unique layouts are computed, so the resolved types flow through the existing
 * generators and checks unchanged. Explicit forms are never touched and no parent is loaded for
 * them.
 */
final class ParentRefResolver {
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final String OPT = "--counts";

  private final Map<String, Long> counts;
  private final Path structuresPath;
  private final TypeParser typeParser = new TypeParser();

  ParentRefResolver(Map<String, Long> rowCounts, Path structuresDir) {
    this.counts = rowCounts;
    this.structuresPath = structuresDir;
  }

  /** Returns {@code fields} with every count-resolved reference replaced; same order. */
  Map<String, DataType> resolve(String structureName, Map<String, DataType> fields) {
    Map<String, DataType> out = new LinkedHashMap<>();
    fields.forEach((name, type) -> out.put(name, resolveType(structureName, name, type)));
    return out;
  }

  private DataType resolveType(String structure, String field, DataType type) {
    return switch (type) {
      case ReferenceType r when r.getMin() == null -> {
        long[] pool =
            pool(structure, field, r.describe(), r.getTargetStructure(), r.getTargetField());
        yield new ReferenceType(
            r.getTargetStructure(), r.getTargetField(), pool[0], pool[1], false);
      }
      case ParentUniqueReferenceType p -> {
        long[] pool =
            pool(structure, field, p.describe(), p.getTargetStructure(), p.getTargetField());
        yield new UniqueType(
            p.getGroup(),
            pool[0],
            pool[1],
            false,
            p.getTargetStructure() + "." + p.getTargetField());
      }
      case ArrayType a ->
          new ArrayType(
              resolveType(structure, field, a.getElementType()),
              a.getMinLength(),
              a.getMaxLength());
      case null, default -> type;
    };
  }

  /** The {@code [lo, hi]} pool of the parent key given the parent's row count. */
  private long[] pool(String structure, String field, String ref, String parent, String key) {
    String label = ref + " in " + structure + "." + field;
    String parentKey = parent + "." + key;
    if (parent.equals(structure)) {
      throw new IllegalArgumentException(label + ": self-references are not supported yet (#213)");
    }
    Long rows = counts.get(parent);
    if (rows == null) {
      throw new IllegalArgumentException(
          "%s: no row count for '%s' — add it to the job's counts file or pass --counts %s=N"
              .formatted(label, parent, parent));
    }
    DataType keyType = parentKeyType(label, parent, key);
    long n = rows;
    return switch (keyType) {
      case SerialType s -> {
        long last = lastValue(s.getMin(), n, label, parentKey, s.describe(), parent);
        if (s.isBounded() && last > s.getMax()) {
          throw tooSmall(label, parentKey, s.describe(), parent, n);
        }
        yield new long[] {s.getMin(), last};
      }
      case UniqueType u when u.getGroup() == null && u.isMaxIsCount() && u.getMin() == 1 ->
          new long[] {1, n};
      case UniqueType u when u.getGroup() == null && !u.isMaxIsCount() -> {
        // compare the last value N rows need with max: max - min + 1 can overflow a long
        long last = lastValue(u.getMin(), n, label, parentKey, u.describe(), parent);
        if (u.getMax() > last) {
          throw new IllegalArgumentException(
              ("%s: %s is %s but %s has %d rows, so only some of its values exist."
                      + " Use serial or unique[1..count] on %s, or an explicit ref[%s, 1..N].")
                  .formatted(label, parentKey, u.describe(), parent, n, parentKey, parentKey));
        }
        if (u.getMax() < last) {
          throw tooSmall(label, parentKey, u.describe(), parent, n);
        }
        yield new long[] {u.getMin(), u.getMax()};
      }
      case null, default ->
          throw new IllegalArgumentException(
              "%s: %s is %s, which cannot be referenced without a range. Use serial,"
                      .formatted(label, parentKey, keyType.describe())
                  + " unique[1..count] or a fixed unique range on it, or an explicit"
                  + " ref[%s, lo..hi].".formatted(parentKey));
    };
  }

  /** {@code min + n - 1}, the value of the parent's last row; too small when it overflows. */
  private static long lastValue(
      long min, long n, String label, String parentKey, String type, String parent) {
    try {
      return Math.addExact(min, n - 1);
    } catch (ArithmeticException e) {
      throw tooSmall(label, parentKey, type, parent, n);
    }
  }

  private DataType parentKeyType(String label, String parent, String key) {
    Path file = structuresPath.resolve(parent + ".yaml");
    if (!Files.exists(file)) {
      throw new IllegalArgumentException(
          "%s: parent structure '%s' not found (%s)".formatted(label, parent, file));
    }
    DataStructure structure = new DataStructureParser().parse(file);
    FieldDefinition def = structure.getData().get(key);
    if (def == null) {
      throw new IllegalArgumentException(
          "%s: field '%s.%s' not found in structure '%s'".formatted(label, parent, key, parent));
    }
    if (def.getNullable() != null && def.getNullable() > 0) {
      throw new IllegalArgumentException(
          "%s: '%s.%s' is nullable (%s) but a referenced parent key must never be null"
              .formatted(label, parent, key, def.getNullable()));
    }
    return typeParser.parse(def.getDatatype());
  }

  private static IllegalArgumentException tooSmall(
      String label, String parentKey, String type, String parent, long n) {
    return new IllegalArgumentException(
        "%s: %s is %s, which cannot hold the %d rows of '%s'"
            .formatted(label, parentKey, type, n, parent));
  }

  /** Reads a counts file: a map of structure name to positive row count. */
  static Map<String, Long> readCountsFile(Path file) {
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("counts file not found: " + file);
    }
    JsonNode root;
    try {
      root = YAML.readTree(Files.readString(file));
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "cannot read counts file " + file + ": " + e.getMessage(), e);
    }
    Map<String, Long> out = new HashMap<>();
    if (root == null || root.isMissingNode() || root.isNull()) {
      return out;
    }
    if (!root.isObject()) {
      throw new IllegalArgumentException(
          "counts file " + file + " must be a map of structure name to row count");
    }
    root.fields()
        .forEachRemaining(
            e -> {
              JsonNode v = e.getValue();
              if (!v.isIntegralNumber() || !v.canConvertToLong() || v.asLong() < 1) {
                throw new IllegalArgumentException(
                    "counts file %s: entry '%s' must be a positive integer, got %s"
                        .formatted(file, e.getKey(), v));
              }
              out.put(e.getKey(), v.asLong());
            });
    return out;
  }

  /** Parses {@code --counts name=N,name=N}. */
  static Map<String, Long> parseCliCounts(String spec) {
    Map<String, Long> out = new HashMap<>();
    for (String pair : spec.split(",", -1)) {
      int eq = pair.indexOf('=');
      if (eq < 1) {
        throw new IllegalArgumentException("%s: '%s' is not name=N".formatted(OPT, pair.trim()));
      }
      String name = pair.substring(0, eq).trim();
      String value = pair.substring(eq + 1).trim();
      try {
        long n = Long.parseLong(value);
        if (n < 1) {
          throw new NumberFormatException(value);
        }
        out.put(name, n);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "%s: entry '%s' must be a positive integer, got '%s'".formatted(OPT, name, value), e);
      }
    }
    return out;
  }
}
