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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.ddl.NestingPlanner.ForeignKeyRef;
import com.datagenerator.inspector.ddl.NestingPlanner.TableInfo;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NestingPlannerTest {

  private final NestingPlanner planner = new NestingPlanner();
  private final NestingOptions auto = NestingOptions.parse("auto", null);

  @Test
  void invertsOneToManyChainIntoNestedArrays() {
    TableInfo customer = table("customer", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo invoice =
        table(
            "invoice",
            1,
            data("id", "int[1..999999]", "customer_id", "ref[customer.id]"),
            pk("id"),
            set(),
            fks(fk("customer_id", "customer", "id")));
    TableInfo item =
        table(
            "invoice_item",
            2,
            data("id", "int[1..999999]", "invoice_id", "ref[invoice.id]"),
            pk("id"),
            set(),
            fks(fk("invoice_id", "invoice", "id")));

    Inspection result = planner.plan(List.of(customer, invoice, item), auto);

    assertThat(datatypes(result, "customer"))
        .containsEntry("invoices", "array[object[invoice], 1..10]");
    assertThat(datatypes(result, "invoice"))
        .containsEntry("invoice_items", "array[object[invoice_item], 1..10]")
        .doesNotContainKey("customer_id");
    assertThat(datatypes(result, "invoice_item")).doesNotContainKey("invoice_id");
  }

  @Test
  void uniqueForeignKeyBecomesObjectNotArray() {
    TableInfo user = table("user", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo profile =
        table(
            "profile",
            1,
            data("id", "int[1..999999]", "user_id", "ref[user.id]"),
            pk("id"),
            set("user_id"),
            fks(fk("user_id", "user", "id")));

    Inspection result = planner.plan(List.of(user, profile), auto);

    assertThat(datatypes(result, "user")).containsEntry("profile", "object[profile]");
    assertThat(datatypes(result, "profile")).doesNotContainKey("user_id");
  }

  @Test
  void usesConfiguredDefaultCount() {
    TableInfo customer = table("customer", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo invoice =
        table(
            "invoice",
            1,
            data("id", "int[1..999999]", "customer_id", "ref[customer.id]"),
            pk("id"),
            set(),
            fks(fk("customer_id", "customer", "id")));

    Inspection result =
        planner.plan(List.of(customer, invoice), NestingOptions.parse("auto", "2..5"));

    assertThat(datatypes(result, "customer"))
        .containsEntry("invoices", "array[object[invoice], 2..5]");
  }

  @Test
  void selfReferenceStaysFlat() {
    TableInfo employee =
        table(
            "employee",
            0,
            data("id", "int[1..999999]", "manager_id", "ref[employee.id]"),
            pk("id"),
            set(),
            fks(fk("manager_id", "employee", "id")));

    Inspection result = planner.plan(List.of(employee), auto);

    assertThat(datatypes(result, "employee")).containsEntry("manager_id", "ref[employee.id]");
    assertThat(result.warnings()).anyMatch(w -> w.contains("self-referencing"));
  }

  @Test
  void twoNodeCycleBreaksAtBackEdgeUnderAuto() {
    TableInfo a =
        table(
            "a",
            0,
            data("id", "int[1..999999]", "b_id", "ref[b.id]"),
            pk("id"),
            set(),
            fks(fk("b_id", "b", "id")));
    TableInfo b =
        table(
            "b",
            1,
            data("id", "int[1..999999]", "a_id", "ref[a.id]"),
            pk("id"),
            set(),
            fks(fk("a_id", "a", "id")));

    Inspection result = planner.plan(List.of(a, b), auto);

    // exactly one direction nests; the back-edge stays a flat ref
    long refCount =
        result.structures().stream()
            .flatMap(s -> s.getData().values().stream())
            .filter(f -> f.getDatatype().startsWith("ref["))
            .count();
    assertThat(refCount).isEqualTo(1);
    assertThat(result.warnings()).anyMatch(w -> w.contains("cycle"));
  }

  @Test
  void twoNodeCycleErrorsUnderAll() {
    TableInfo a =
        table(
            "a",
            0,
            data("id", "int[1..999999]", "b_id", "ref[b.id]"),
            pk("id"),
            set(),
            fks(fk("b_id", "b", "id")));
    TableInfo b =
        table(
            "b",
            1,
            data("id", "int[1..999999]", "a_id", "ref[a.id]"),
            pk("id"),
            set(),
            fks(fk("a_id", "a", "id")));

    assertThatThrownBy(() -> planner.plan(List.of(a, b), NestingOptions.parse("all", null)))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("cycle");
  }

  @Test
  void manyToManyJunctionIsNotNested() {
    TableInfo student = table("student", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo course = table("course", 1, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo enrollment =
        table(
            "enrollment",
            2,
            data("student_id", "ref[student.id]", "course_id", "ref[course.id]"),
            pk("student_id", "course_id"),
            set(),
            fks(fk("student_id", "student", "id"), fk("course_id", "course", "id")));

    Inspection result = planner.plan(List.of(student, course, enrollment), auto);

    assertThat(datatypes(result, "enrollment"))
        .containsEntry("student_id", "ref[student.id]")
        .containsEntry("course_id", "ref[course.id]");
    assertThat(datatypes(result, "student")).doesNotContainKey("enrollments");
    assertThat(result.warnings()).anyMatch(w -> w.contains("junction"));
  }

  @Test
  void sharedChildEmbedsIntoFirstParentOnly() {
    TableInfo order = table("orders", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo product = table("product", 1, data("id", "int[1..999999]"), pk("id"), set(), fks());
    TableInfo line =
        table(
            "line_item",
            2,
            data(
                "id", "int[1..999999]",
                "order_id", "ref[orders.id]",
                "product_id", "ref[product.id]"),
            pk("id"),
            set(),
            fks(fk("order_id", "orders", "id"), fk("product_id", "product", "id")));

    Inspection result = planner.plan(List.of(order, product, line), auto);

    // embedded under the first-declared parent (orders), flat ref kept to product
    assertThat(datatypes(result, "orders")).containsKey("line_items");
    assertThat(datatypes(result, "product")).doesNotContainKey("line_items");
    assertThat(datatypes(result, "line_item"))
        .doesNotContainKey("order_id")
        .containsEntry("product_id", "ref[product.id]");
    assertThat(result.warnings()).anyMatch(w -> w.contains("kept flat"));
  }

  @Test
  void compositeForeignKeyStaysFlat() {
    TableInfo parent =
        table(
            "parent",
            0,
            data("a", "int[1..999999]", "b", "int[1..999999]"),
            pk("a", "b"),
            set(),
            fks());
    ForeignKeyRef composite = new ForeignKeyRef(List.of("pa", "pb"), "parent", List.of("a", "b"));
    TableInfo child =
        table(
            "child",
            1,
            data("id", "int[1..999999]", "pa", "int[1..999999]", "pb", "int[1..999999]"),
            pk("id"),
            set(),
            List.of(composite));

    Inspection result = planner.plan(List.of(parent, child), auto);

    assertThat(datatypes(result, "parent")).doesNotContainKey("children");
    assertThat(result.warnings()).anyMatch(w -> w.contains("composite"));
  }

  @Test
  void nestedFieldNameCollisionGetsSetSuffix() {
    // parent already has a column literally named "invoices"
    TableInfo customer =
        table(
            "customer",
            0,
            data("id", "int[1..999999]", "invoices", "char[1..50]"),
            pk("id"),
            set(),
            fks());
    TableInfo invoice =
        table(
            "invoice",
            1,
            data("id", "int[1..999999]", "customer_id", "ref[customer.id]"),
            pk("id"),
            set(),
            fks(fk("customer_id", "customer", "id")));

    Inspection result = planner.plan(List.of(customer, invoice), auto);

    assertThat(datatypes(result, "customer"))
        .containsEntry("invoices", "char[1..50]")
        .containsEntry("invoices_set", "array[object[invoice], 1..10]");
    assertThat(result.warnings()).anyMatch(w -> w.contains("collides"));
  }

  @Test
  void noneModeIsNotInvokedButPlannerIsPureWhenNoFks() {
    TableInfo orphan = table("orphan", 0, data("id", "int[1..999999]"), pk("id"), set(), fks());
    Inspection result = planner.plan(List.of(orphan), auto);
    assertThat(datatypes(result, "orphan")).containsOnlyKeys("id");
    assertThat(result.warnings()).isEmpty();
  }

  // --- helpers ---

  private static TableInfo table(
      String name,
      int order,
      LinkedHashMap<String, FieldDefinition> data,
      Set<String> pk,
      Set<String> unique,
      List<ForeignKeyRef> fks) {
    return new TableInfo(name, data, new LinkedHashMap<>(), pk, unique, fks, order);
  }

  private static LinkedHashMap<String, FieldDefinition> data(String... pairs) {
    LinkedHashMap<String, FieldDefinition> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(pairs[i], new FieldDefinition(pairs[i + 1], null));
    }
    return map;
  }

  private static Set<String> pk(String... cols) {
    return Set.of(cols);
  }

  private static Set<String> set(String... cols) {
    return Set.of(cols);
  }

  private static List<ForeignKeyRef> fks(ForeignKeyRef... refs) {
    return List.of(refs);
  }

  private static ForeignKeyRef fk(String localCol, String refTable, String refCol) {
    return new ForeignKeyRef(List.of(localCol), refTable, List.of(refCol));
  }

  private static Map<String, String> datatypes(Inspection result, String structureName) {
    DataStructure structure =
        result.structures().stream()
            .filter(s -> s.getName().equals(structureName))
            .findFirst()
            .orElseThrow();
    return structure.getData().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype(), (a, b) -> a));
  }
}
