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

  private static final String TABLE_PARENT = "parent";
  private static final String COL_PARENT_ID = "parent_id";
  private static final String FIELD_CHILDREN = "children";
  private static final String TABLE_ORDER = "order";
  private static final String TABLE_LINE_ITEMS = "line_items";
  private static final String TABLE_ATTRIBUTES = "attributes";
  private static final String COL_ORDER_ID = "order_id";
  private static final String FIELD_PRODUCT = "product";

  private NestedRecordDecomposer decomposer;

  @BeforeEach
  void setUp() {
    decomposer = new NestedRecordDecomposer();
  }

  // --- Flat records ---

  @Test
  void shouldReturnSingleTableRecordForFlatInput() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 1);
    data.put("name", "Alice");
    data.put("active", true);

    List<NestedRecordDecomposer.TableRecord> result = decomposer.decompose(data, "users", null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).tableName()).isEqualTo("users");
    assertThat(result.get(0).fields()).containsEntry("id", 1).containsEntry("name", "Alice");
  }

  @Test
  void shouldInjectParentFkIntoFlatRecord() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 99);
    data.put("amount", 19.99);

    ParentContext parentCtx = new ParentContext(TABLE_ORDER, 42);
    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(data, TABLE_LINE_ITEMS, parentCtx);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).fields())
        .containsEntry("id", 99)
        .containsEntry("amount", 19.99)
        .containsEntry(COL_ORDER_ID, 42);
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
    item1.put(FIELD_PRODUCT, "Widget");

    Map<String, Object> item2 = new LinkedHashMap<>();
    item2.put("id", 102);
    item2.put(FIELD_PRODUCT, "Gadget");

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 7);
    order.put("status", "PENDING");
    order.put(TABLE_LINE_ITEMS, List.of(item1, item2));

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(order, TABLE_ORDER, null);

    assertThat(result).hasSize(3);

    assertThat(result.get(0).tableName()).isEqualTo(TABLE_ORDER);
    assertThat(result.get(0).fields()).containsOnlyKeys("id", "status");

    assertThat(result.get(1).tableName()).isEqualTo(TABLE_LINE_ITEMS);
    assertThat(result.get(1).fields())
        .containsEntry("id", 101)
        .containsEntry(COL_ORDER_ID, 7); // FK from parent

    assertThat(result.get(2).tableName()).isEqualTo(TABLE_LINE_ITEMS);
    assertThat(result.get(2).fields()).containsEntry("id", 102).containsEntry(COL_ORDER_ID, 7);
  }

  // --- Depth-first ordering ---

  @Test
  void shouldOrderParentBeforeChildrenAlways() {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", 501);

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 50);
    order.put(TABLE_LINE_ITEMS, List.of(item));

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(order, TABLE_ORDER, null);

    assertThat(result.get(0).tableName()).isEqualTo(TABLE_ORDER); // parent first
    assertThat(result.get(1).tableName()).isEqualTo(TABLE_LINE_ITEMS); // child after
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
    lineItem.put(FIELD_PRODUCT, "Widget");
    lineItem.put(TABLE_ATTRIBUTES, List.of(attribute));

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", 42);
    order.put("date", "2024-06-15");
    order.put(TABLE_LINE_ITEMS, List.of(lineItem));

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(order, TABLE_ORDER, null);

    assertThat(result).hasSize(3);

    // Level 1: order
    assertThat(result.get(0).tableName()).isEqualTo(TABLE_ORDER);
    assertThat(result.get(0).fields()).doesNotContainKey(COL_ORDER_ID);

    // Level 2: line_items with FK to order
    assertThat(result.get(1).tableName()).isEqualTo(TABLE_LINE_ITEMS);
    assertThat(result.get(1).fields())
        .containsEntry("id", 1001)
        .containsEntry(COL_ORDER_ID, 42)
        .doesNotContainKey(TABLE_ATTRIBUTES);

    // Level 3: attributes with FK to line_items (NOT to order — immediate parent only)
    assertThat(result.get(2).tableName()).isEqualTo(TABLE_ATTRIBUTES);
    assertThat(result.get(2).fields())
        .containsEntry("id", 9001)
        .containsEntry("line_items_id", 1001) // immediate parent FK
        .doesNotContainKey(COL_ORDER_ID); // root ID must not bleed through
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
    root.put(FIELD_CHILDREN, List.of(child));

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
    order.put(TABLE_LINE_ITEMS, List.of()); // empty array

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(order, TABLE_ORDER, null);

    // Empty array → no children → only parent row
    assertThat(result).hasSize(1);
    assertThat(result.get(0).tableName()).isEqualTo(TABLE_ORDER);
    assertThat(result.get(0).fields()).containsOnlyKeys("id"); // line_items excluded
  }

  @Test
  void shouldNotInjectFkWhenParentHasNoIdField() {
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("name", "item");

    Map<String, Object> parent = new LinkedHashMap<>();
    parent.put("title", "no-id-parent"); // no "id" field
    parent.put(FIELD_CHILDREN, List.of(child));

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(parent, TABLE_PARENT, null);

    assertThat(result).hasSize(2);
    // Child has no FK injected because parent has no "id"
    assertThat(result.get(1).fields()).doesNotContainKey(COL_PARENT_ID);
  }

  @Test
  void shouldNotMutateOriginalRecord() {
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("id", 55);
    List<Map<String, Object>> children = List.of(child);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 1);
    data.put("items", children);

    decomposer.decompose(data, TABLE_PARENT, null);

    // Original child must not have FK injected into it
    assertThat(child).doesNotContainKey(COL_PARENT_ID).containsOnlyKeys("id");
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
    order.put(TABLE_LINE_ITEMS, items);

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(order, TABLE_ORDER, null);

    assertThat(result).hasSize(6); // 1 order + 5 line_items
    assertThat(result.subList(1, 6))
        .allSatisfy(
            r -> {
              assertThat(r.tableName()).isEqualTo(TABLE_LINE_ITEMS);
              assertThat(r.fields()).containsEntry(COL_ORDER_ID, 99);
            });
  }

  // --- injectParentFk=false ---

  @Test
  void shouldNotInjectParentFkWhenFlagIsFalse() {
    NestedRecordDecomposer noInject = new NestedRecordDecomposer(false);

    Map<String, Object> book = new LinkedHashMap<>();
    book.put("id", 1);
    book.put("author_id", 42); // set explicitly via ref[parent.id]

    Map<String, Object> author = new LinkedHashMap<>();
    author.put("id", 42);
    author.put("books", List.of(book));

    List<NestedRecordDecomposer.TableRecord> result =
        noInject.decompose(author, "lib_authors", null);

    assertThat(result).hasSize(2);
    NestedRecordDecomposer.TableRecord bookRecord = result.get(1);
    assertThat(bookRecord.tableName()).isEqualTo("books");
    assertThat(bookRecord.fields()).containsEntry("author_id", 42);
    assertThat(bookRecord.fields()).doesNotContainKey("lib_authors_id");
  }

  @Test
  void shouldNotBuildParentContextForChildrenWhenFlagIsFalse() {
    NestedRecordDecomposer noInject = new NestedRecordDecomposer(false);

    Map<String, Object> child = new LinkedHashMap<>();
    child.put("value", "x");

    Map<String, Object> parent = new LinkedHashMap<>();
    parent.put("id", 1);
    parent.put(FIELD_CHILDREN, List.of(child));

    List<NestedRecordDecomposer.TableRecord> result =
        noInject.decompose(parent, TABLE_PARENT, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(1).fields()).doesNotContainKey(COL_PARENT_ID);
  }

  @Test
  void shouldDefaultToInjectParentFkTrue() {
    // Default constructor injects FK (regression guard)
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("name", "child");

    Map<String, Object> parent = new LinkedHashMap<>();
    parent.put("id", 7);
    parent.put("child", child);

    List<NestedRecordDecomposer.TableRecord> result =
        decomposer.decompose(parent, TABLE_PARENT, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(1).fields()).containsEntry(COL_PARENT_ID, 7);
  }
}
