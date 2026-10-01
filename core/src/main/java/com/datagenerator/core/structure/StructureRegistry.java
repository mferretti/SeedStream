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

package com.datagenerator.core.structure;

import com.datagenerator.core.exception.CircularReferenceException;
import com.datagenerator.core.exception.TypeParseException;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.UniqueType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for loaded data structures with circular reference detection. Maintains a cache of
 * parsed structures and tracks loading stack to detect cycles.
 */
public class StructureRegistry {
  private final Map<String, Map<String, DataType>> structureCache = new ConcurrentHashMap<>();
  private final StructureLoader loader;
  private final ThreadLocal<Deque<String>> loadingStack = ThreadLocal.withInitial(ArrayDeque::new);

  private final long jobCount;

  public StructureRegistry(StructureLoader structureLoader) {
    this(structureLoader, 0L);
  }

  /**
   * @param jobCount the job's record count, used to resolve {@code unique[min..count]}; 0 = unknown
   */
  public StructureRegistry(StructureLoader structureLoader, long jobCount) {
    this.loader = structureLoader;
    this.jobCount = jobCount;
  }

  /**
   * Load a structure by name, detecting circular references.
   *
   * @param structureName the name of the structure to load
   * @param structuresPath the base path for structure files
   * @return map of field names to DataType objects
   * @throws CircularReferenceException if a circular reference is detected
   */
  public Map<String, DataType> loadStructure(String structureName, Path structuresPath) {
    // Check cache first
    if (structureCache.containsKey(structureName)) {
      return structureCache.get(structureName);
    }

    // Check for circular reference
    Deque<String> stack = loadingStack.get();
    if (stack.contains(structureName)) {
      List<String> cycle = new ArrayList<>(stack);
      cycle.add(structureName);
      throw new CircularReferenceException(
          "Circular reference detected: " + String.join(" → ", cycle));
    }

    // Add to stack and load
    stack.push(structureName);
    try {
      Map<String, DataType> fields =
          resolveUniqueGroups(
              structureName, loader.load(structureName, structuresPath, this), jobCount);
      structureCache.put(structureName, fields);
      return fields;
    } finally {
      stack.pop();
      if (stack.isEmpty()) {
        loadingStack.remove(); // Clean up thread-local
      }
    }
  }

  /**
   * Resolves {@link UniqueType} layouts: groups unique fields by explicit group (or field name),
   * computes domain, mixed-radix divisors, Feistel half-bits and group hash, and returns a copy of
   * the map (same order) with each unique field replaced by its resolved copy.
   *
   * <p>Members of each unique group are ordered by field name (natural String order) when computing
   * divisors, not by map iteration order, ensuring divisors are stable across different map
   * insertion orders and load paths where iteration order may differ.
   *
   * @throws TypeParseException if a group's domain overflows a long
   */
  public static Map<String, DataType> resolveUniqueGroups(
      String structureName, Map<String, DataType> fields) {
    return resolveUniqueGroups(structureName, fields, 0L);
  }

  /**
   * As {@link #resolveUniqueGroups(String, Map)}, resolving {@code min..count} ranges against
   * {@code jobCount}.
   *
   * @throws TypeParseException if a {@code ..count} range is used and jobCount is unknown (<= 0) or
   *     smaller than the range minimum
   */
  public static Map<String, DataType> resolveUniqueGroups(
      String structureName, Map<String, DataType> fields, long jobCount) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    fields.forEach(
        (name, type) -> {
          if (type instanceof UniqueType u) {
            groups
                .computeIfAbsent(u.getGroup() != null ? u.getGroup() : name, g -> new ArrayList<>())
                .add(name);
          }
        });
    if (groups.isEmpty()) {
      return fields;
    }
    Map<String, DataType> resolved = new LinkedHashMap<>(fields);
    groups.forEach(
        (group, names) -> resolveGroup(structureName, group, names, fields, jobCount, resolved));
    return resolved;
  }

  private static void resolveGroup(
      String structureName,
      String group,
      List<String> names,
      Map<String, DataType> fields,
      long jobCount,
      Map<String, DataType> resolved) {
    String key = structureName + "." + group;
    long domain = 1;
    for (String n : names) {
      UniqueType u = (UniqueType) fields.get(n);
      try {
        domain = Math.multiplyExact(domain, sizeOf(structureName, n, u, jobCount));
      } catch (ArithmeticException e) {
        throw new TypeParseException("unique group domain too large: " + key);
      }
    }
    long hash = fnv1a64(key);
    int h = halfBits(domain);
    // Sort members by field name to ensure divisors are computed in a stable order,
    // independent of map iteration order (which can vary across loads).
    List<String> sortedNames = names.stream().sorted().toList();
    Map<String, Long> divisorMap = new HashMap<>();
    long divisor = domain;
    for (String n : sortedNames) {
      divisor /= sizeOf(structureName, n, (UniqueType) fields.get(n), jobCount);
      divisorMap.put(n, divisor);
    }
    // Update resolved map in original field order, but use divisors from sorted names
    for (String n : names) {
      UniqueType u = (UniqueType) fields.get(n);
      long size = sizeOf(structureName, n, u, jobCount);
      long max = u.isMaxIsCount() ? jobCount : u.getMax();
      resolved.put(n, u.withLayout(max, key, hash, domain, divisorMap.get(n), size, h));
    }
  }

  private static long sizeOf(String structureName, String field, UniqueType u, long jobCount) {
    if (!u.isMaxIsCount()) {
      return Math.addExact(Math.subtractExact(u.getMax(), u.getMin()), 1);
    }
    if (jobCount <= 0) {
      throw new TypeParseException(
          "%s in %s.%s needs the job --count; it is not available here"
              .formatted(u.describe(), structureName, field));
    }
    if (jobCount < u.getMin()) {
      throw new TypeParseException(
          "%s in %s.%s is empty: --count (%d) is below the range minimum (%d)"
              .formatted(u.describe(), structureName, field, jobCount, u.getMin()));
    }
    try {
      return Math.addExact(Math.subtractExact(jobCount, u.getMin()), 1);
    } catch (ArithmeticException e) {
      throw new TypeParseException("unique group domain too large: " + structureName + "." + field);
    }
  }

  /** Stable 64-bit FNV-1a over the UTF-8 bytes of {@code s}. */
  public static long fnv1a64(String s) {
    long h = 0xcbf29ce484222325L;
    for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
      h = (h ^ (b & 0xff)) * 0x100000001b3L;
    }
    return h;
  }

  /** Smallest h >= 1 such that 4^h >= domain. */
  public static int halfBits(long domain) {
    int h = 1;
    while (h < 32 && (1L << (2 * h)) < domain) {
      h++;
    }
    return h;
  }

  /**
   * Validate all object references in a structure (recursive). Ensures all referenced structures
   * can be loaded without circular references.
   *
   * @param fields the fields to validate
   * @param structuresPath the base path for structure files
   */
  public void validateReferences(Map<String, DataType> fields, Path structuresPath) {
    for (DataType type : fields.values()) {
      validateType(type, structuresPath);
    }
  }

  private void validateType(DataType type, Path structuresPath) {
    if (type instanceof ObjectType objectType) {
      // This will trigger loading and cycle detection
      loadStructure(objectType.getStructureName(), structuresPath);
    } else if (type instanceof ArrayType arrayType) {
      validateType(arrayType.getElementType(), structuresPath);
    }
  }

  /** Clear the cache (useful for testing). */
  public void clearCache() {
    structureCache.clear();
  }
}
