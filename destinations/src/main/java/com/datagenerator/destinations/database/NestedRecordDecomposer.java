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

package com.datagenerator.destinations.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursively decomposes a generated nested record into an ordered flat list of table-insert
 * operations.
 *
 * <p><b>Decomposition rules:</b>
 *
 * <ul>
 *   <li>Scalar fields (not {@code Map} or {@code List<Map>}) → included in the parent flat record
 *   <li>{@code Map} field → single child-table INSERT; field key = child table name
 *   <li>{@code List<Map>} field → N child-table INSERTs; field key = child table name
 *   <li>Empty {@code List} fields are ignored (no child inserts generated)
 * </ul>
 *
 * <p><b>Ordering:</b> The returned list is in depth-first order — parent record first, then its
 * children in field-declaration order, then grandchildren, and so on. This ensures parent rows are
 * always inserted before their child rows, satisfying FK constraints.
 *
 * <p><b>FK injection:</b> When a {@link ParentContext} is provided, its FK column ( {@code
 * {tableName}_id}) is injected into the flat fields of the current record <em>before</em> it is
 * added to the result list.
 *
 * <p><b>FK propagation:</b> Each level builds its own {@link ParentContext} from its {@code id}
 * field and passes it to its children. Children only receive their immediate parent's ID — the root
 * ID does not bleed through to grandchildren.
 *
 * <p><b>Thread safety:</b> Stateless — safe for concurrent use.
 */
public class NestedRecordDecomposer {

  /**
   * When {@code true}, a {@code {parent_table}_id} column is automatically injected into each child
   * record. When {@code false}, no injection occurs — use this when child structures already carry
   * the FK via {@code ref[parent.field]}.
   */
  private final boolean injectParentFk;

  public NestedRecordDecomposer(boolean injectParentFk) {
    this.injectParentFk = injectParentFk;
  }

  /** Creates a decomposer with parent FK injection enabled (default behaviour). */
  public NestedRecordDecomposer() {
    this(true);
  }

  /**
   * A flat record ready for a single table INSERT.
   *
   * @param tableName the target table name
   * @param fields scalar fields (with FK already injected if applicable)
   */
  public record TableRecord(String tableName, Map<String, Object> fields) {}

  /**
   * Decomposes a potentially nested record into an ordered list of table-insert operations.
   *
   * @param record the generated record (may contain nested {@code Map} or {@code List<Map>} values)
   * @param tableName the table to insert the flat portion of this record into
   * @param parentCtx FK context from the immediate parent ({@code null} for root records)
   * @return depth-first ordered list of {@link TableRecord} entries ready to INSERT
   */
  public List<TableRecord> decompose(
      Map<String, Object> record, String tableName, ParentContext parentCtx) {

    List<TableRecord> result = new ArrayList<>();
    Map<String, Object> flat = new LinkedHashMap<>();
    Map<String, List<Map<String, Object>>> childrenByField = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : record.entrySet()) {
      classifyEntry(entry.getKey(), entry.getValue(), flat, childrenByField);
    }

    if (injectParentFk && parentCtx != null) {
      flat.put(parentCtx.fkColumnName(), parentCtx.parentId());
    }

    result.add(new TableRecord(tableName, flat));

    Object myId = flat.get("id");
    ParentContext myCtx =
        (injectParentFk && myId != null) ? new ParentContext(tableName, myId) : null;

    for (Map.Entry<String, List<Map<String, Object>>> childEntry : childrenByField.entrySet()) {
      String childTable = childEntry.getKey();
      for (Map<String, Object> child : childEntry.getValue()) {
        result.addAll(decompose(child, childTable, myCtx));
      }
    }

    return result;
  }

  private void classifyEntry(
      String key,
      Object value,
      Map<String, Object> flat,
      Map<String, List<Map<String, Object>>> childrenByField) {
    if (value instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      Map<String, Object> child = (Map<String, Object>) value;
      childrenByField.put(key, List.of(new LinkedHashMap<>(child)));
    } else if (value instanceof List<?> list) {
      classifyList(key, list, flat, childrenByField);
    } else {
      flat.put(key, value);
    }
  }

  private void classifyList(
      String key,
      List<?> list,
      Map<String, Object> flat,
      Map<String, List<Map<String, Object>>> childrenByField) {
    if (list.isEmpty()) {
      // Empty list — skip entirely. Cannot determine element type, and a Java List
      // cannot be bound as a JDBC parameter. No child inserts are produced.
    } else if (list.get(0) instanceof Map) {
      // Non-empty list of Maps → child table records
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> children = (List<Map<String, Object>>) list;
      // Defensive copy so FK injection doesn't mutate the original record
      List<Map<String, Object>> copies = new ArrayList<>(children.size());
      for (Map<String, Object> child : children) {
        copies.add(new LinkedHashMap<>(child));
      }
      childrenByField.put(key, copies);
    } else {
      // List of scalars — treat as a regular scalar value (e.g. array[char[]])
      flat.put(key, list);
    }
  }
}
