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

import static org.assertj.core.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NestedRecordDecomposer}.
 *
 * <p>Covers: flat records, single nested object, nested arrays, FK injection, multi-level
 * recursion, and edge cases (empty arrays, missing id field).
 */
class NestedRecordDecomposerTest {

  private NestedRecordDecomposer decomposer;

  @BeforeEach
  void setUp() {
    decomposer = new NestedRecordDecomposer();
  }

  // --- Flat records ---

  @Test
  void shouldReturnSingleTableRecordForFlatInput() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 1);
    record.put("name", "Alice");
    record.put("active", true);

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(record, "users", null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).tableName()).isEqualTo("users");
    assertThat(result.get(0).fields()).containsEntry("id", 1).containsEntry("name", "Alice");
  }

  @Test
  void shouldInjectParentFkIntoFlatRecord() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 99);
    record.put("amount", 19.99);

    ParentContext parentCtx = new ParentContext("order", 42);
    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(record, "line_items", parentCtx);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).fields())
        .containsEntry("id", 99)
        .containsEntry("amount", 19.99)
        .containsEntry("order_id", 42);
  }

  // --- Single nested object (Map field) ---

  @Test
  void shouldDecomposeNestedObjectIntoTwoTableRecords() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("id", 200);
    address.put("city", "Rome");

    Map<String, Object> customer = new LinkedHashMap<>();
    customer.put("id", 10);
    customer.put("name", "Mario");
    customer.put("address", address);

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(customer, "customer", null);

    assertThat(result).hasSize(2);

    NestedRecordDecomposer.TableRecord parentRow = result.get(0);
    assertThat(parentRow.tableName()).isEqualTo("customer");
    assertThat(parentRow.fields()).containsOnlyKeys("id", "name");
    assertThat(parentRow.fields()).containsEntry("id", 10);

    NestedRecordDecomposer.TableRecord childRow = result.get(1);
    assertThat(childRow.tableName()).isEqualTo("address");
    assertThat(childRow.fields())
        .containsEntry("id", 200)
        .containsEntry("city", "Rome")
        .containsEntry("customer_id", 10); // FK injected
  }

  // --- Array of nested objects ---

  @Test
  void shouldDecomposeArrayIntoOneParentAndNChildRecords() {
    Map<String, Object> item1 = new LinkedHashMap<>();
    item1.put("id", 101);
    item1.put("product", "Widget");

    Map<String, Object> item2 = new LinkedHashMap<>();
    item2.put("id", 102);
    item2.put("product", "Gadget");

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 7);
    order.put("status", "PENDING");
    order.put("line_items", List.of(item1, item2));

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(order, "order", null);

    assertThat(result).hasSize(3);

    assertThat(result.get(0).tableName()).isEqualTo("order");
    assertThat(result.get(0).fields()).containsOnlyKeys("id", "status");

    assertThat(result.get(1).tableName()).isEqualTo("line_items");
    assertThat(result.get(1).fields())
        .containsEntry("id", 101)
        .containsEntry("order_id", 7); // FK from parent

    assertThat(result.get(2).tableName()).isEqualTo("line_items");
    assertThat(result.get(2).fields()).containsEntry("id", 102).containsEntry("order_id", 7);
  }

  // --- Depth-first ordering ---

  @Test
  void shouldOrderParentBeforeChildrenAlways() {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", 501);

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 50);
    order.put("line_items", List.of(item));

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(order, "order", null);

    assertThat(result.get(0).tableName()).isEqualTo("order"); // parent first
    assertThat(result.get(1).tableName()).isEqualTo("line_items"); // child after
  }

  // --- Multi-level nesting (3 levels) ---

  @Test
  void shouldDecomposeThreeLevelNestingCorrectly() {
    Map<String, Object> attribute = new LinkedHashMap<>();
    attribute.put("id", 9001);
    attribute.put("name", "color");
    attribute.put("value", "red");

    Map<String, Object> lineItem = new LinkedHashMap<>();
    lineItem.put("id", 1001);
    lineItem.put("product", "Widget");
    lineItem.put("attributes", List.of(attribute));

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 42);
    order.put("date", "2024-06-15");
    order.put("line_items", List.of(lineItem));

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(order, "order", null);

    assertThat(result).hasSize(3);

    // Level 1: order
    assertThat(result.get(0).tableName()).isEqualTo("order");
    assertThat(result.get(0).fields()).doesNotContainKey("order_id");

    // Level 2: line_items with FK to order
    assertThat(result.get(1).tableName()).isEqualTo("line_items");
    assertThat(result.get(1).fields())
        .containsEntry("id", 1001)
        .containsEntry("order_id", 42)
        .doesNotContainKey("attributes");

    // Level 3: attributes with FK to line_items (NOT to order — immediate parent only)
    assertThat(result.get(2).tableName()).isEqualTo("attributes");
    assertThat(result.get(2).fields())
        .containsEntry("id", 9001)
        .containsEntry("line_items_id", 1001) // immediate parent FK
        .doesNotContainKey("order_id"); // root ID must not bleed through
  }

  // --- FK isolation: immediate parent only ---

  @Test
  void shouldInjectImmediateParentFkOnlyNotRootFk() {
    Map<String, Object> grandchild = new LinkedHashMap<>();
    grandchild.put("id", 3000);
    grandchild.put("label", "tag");

    Map<String, Object> child = new LinkedHashMap<>();
    child.put("id", 200);
    child.put("tags", List.of(grandchild));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", 10);
    root.put("children", List.of(child));

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(root, "root", null);

    NestedRecordDecomposer.TableRecord grandchildRow = result.get(2);
    assertThat(grandchildRow.tableName()).isEqualTo("tags");
    assertThat(grandchildRow.fields())
        .containsEntry("children_id", 200) // immediate parent FK
        .doesNotContainKey("root_id"); // root ID must not appear
  }

  // --- Edge cases ---

  @Test
  void shouldProduceOnlyParentRowWhenArrayIsEmpty() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 1);
    order.put("line_items", List.of()); // empty array

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(order, "order", null);

    // Empty array → no children → only parent row
    assertThat(result).hasSize(1);
    assertThat(result.get(0).tableName()).isEqualTo("order");
    assertThat(result.get(0).fields()).containsOnlyKeys("id"); // line_items excluded
  }

  @Test
  void shouldNotInjectFkWhenParentHasNoIdField() {
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("name", "item");

    Map<String, Object> parent = new LinkedHashMap<>();
    parent.put("title", "no-id-parent"); // no "id" field
    parent.put("children", List.of(child));

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(parent, "parent", null);

    assertThat(result).hasSize(2);
    // Child has no FK injected because parent has no "id"
    assertThat(result.get(1).fields()).doesNotContainKey("parent_id");
  }

  @Test
  void shouldNotMutateOriginalRecord() {
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("id", 55);
    List<Map<String, Object>> children = List.of(child);

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 1);
    record.put("items", children);

    decomposer.decompose(record, "parent", null);

    // Original child must not have FK injected into it
    assertThat(child).doesNotContainKey("parent_id");
    assertThat(child).containsOnlyKeys("id");
  }

  @Test
  void shouldHandleMultipleChildrenInArray() {
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", i * 100);
      item.put("name", "item-" + i);
      items.add(item);
    }

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 99);
    order.put("line_items", items);

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(order, "order", null);

    assertThat(result).hasSize(6); // 1 order + 5 line_items
    assertThat(result.subList(1, 6))
        .allSatisfy(
            r -> {
              assertThat(r.tableName()).isEqualTo("line_items");
              assertThat(r.fields()).containsEntry("order_id", 99);
            });
  }
}
