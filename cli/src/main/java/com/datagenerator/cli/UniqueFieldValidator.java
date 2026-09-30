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

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.SerialType;
import com.datagenerator.core.type.UniqueType;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Startup checks for {@code unique[...]} fields. Runs before the destination is opened, because
 * opening may truncate tables.
 */
final class UniqueFieldValidator {
  private final StructureRegistry registry;
  private final Path structuresPath;
  private final long count;

  UniqueFieldValidator(StructureRegistry registry, Path structuresPath, long count) {
    this.registry = registry;
    this.structuresPath = structuresPath;
    this.count = count;
  }

  /**
   * @throws IllegalArgumentException if a unique field sits under an array, or a group's domain is
   *     smaller than the record count
   */
  void validate(String structureName) {
    walk(structureName, false);
  }

  private void walk(String structureName, boolean underArray) {
    Map<String, DataType> fields = registry.loadStructure(structureName, structuresPath);
    for (Map.Entry<String, DataType> e : fields.entrySet()) {
      visit(structureName, e.getKey(), e.getValue(), underArray);
    }
    checkGroups(structureName, fields);
  }

  private void visit(String structureName, String field, DataType type, boolean underArray) {
    if ((type instanceof UniqueType || type instanceof SerialType) && underArray) {
      throw new IllegalArgumentException(
          (type instanceof SerialType ? "serial" : "unique[...]")
              + " field '"
              + structureName
              + "."
              + field
              + "' is inside array[...]; array elements share their record's index so values"
              + " would collide. Use it only on top-level or 1:1 object[...] fields.");
    } else if (type instanceof ObjectType o) {
      walk(o.getStructureName(), underArray);
    } else if (type instanceof ArrayType a) {
      visit(structureName, field, a.getElementType(), true);
    }
  }

  private void checkGroups(String structureName, Map<String, DataType> fields) {
    Map<String, Map<String, UniqueType>> byGroup = new LinkedHashMap<>();
    fields.forEach(
        (name, type) -> {
          if (type instanceof UniqueType u) {
            byGroup.computeIfAbsent(u.getGroupKey(), g -> new LinkedHashMap<>()).put(name, u);
          }
        });
    byGroup.forEach(
        (key, members) -> {
          UniqueType first = members.values().iterator().next();
          if (count <= first.getDomain()) {
            return;
          }
          throw new IllegalArgumentException(message(key, members, first.getDomain()));
        });
  }

  private String message(String groupKey, Map<String, UniqueType> members, long domain) {
    if (members.size() == 1 && members.values().iterator().next().getGroup() == null) {
      Map.Entry<String, UniqueType> e = members.entrySet().iterator().next();
      UniqueType u = e.getValue();
      if (u.isMaxIsCount() && u.getMin() > 1) {
        return String.format(
            Locale.ROOT,
            "unique[%s] range %d..count holds %,d values but --count is %,d."
                + " Use 1..count or a wider fixed range.",
            e.getKey(),
            u.getMin(),
            domain,
            count);
      }
      return String.format(
          Locale.ROOT,
          "unique[%s] range %d..%d holds %,d values but --count is %,d."
              + " Widen the range to at least %d..%d.",
          e.getKey(),
          u.getMin(),
          u.getMax(),
          domain,
          count,
          u.getMin(),
          u.getMin() + count - 1);
    }
    String group = groupKey.substring(groupKey.indexOf('.') + 1);
    String parts =
        members.entrySet().stream()
            .map(e -> e.getKey() + " " + e.getValue().getMin() + ".." + e.getValue().getMax())
            .collect(Collectors.joining(" × "));
    return String.format(
        Locale.ROOT,
        "unique group '%s' (%s) holds %,d combinations but --count is %,d.",
        group,
        parts,
        domain,
        count);
  }
}
