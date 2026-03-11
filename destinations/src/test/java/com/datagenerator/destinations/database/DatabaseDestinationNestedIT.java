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

import com.datagenerator.destinations.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration tests for nested auto-decomposition in {@link DatabaseDestination} (Stage 2).
 *
 * <p>Uses a real PostgreSQL container. Tests validate that nested records are correctly decomposed
 * into multi-table INSERTs with FK injection, depth-first ordering, and correct row counts.
 *
 * <p>Schema: {@code order} → {@code line_items} → {@code attributes} (3 levels max).
 *
 * <p>Run with: {@code ./gradlew :destinations:integrationTest}
 */
class DatabaseDestinationNestedIT extends IntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private static final String CREATE_ORDERS =
      """
      CREATE TABLE orders (
        id     INT,
        status VARCHAR(50)
      )
      """;

  private static final String CREATE_LINE_ITEMS =
      """
      CREATE TABLE line_items (
        id        INT,
        product   VARCHAR(255),
        quantity  INT,
        orders_id INT
      )
      """;

  private static final String CREATE_ATTRIBUTES =
      """
      CREATE TABLE attributes (
        id            INT,
        name          VARCHAR(255),
        value         VARCHAR(255),
        line_items_id INT
      )
      """;

  private Connection verify;

  @BeforeEach
  void setUp() throws SQLException {
    verify =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = verify.createStatement()) {
      st.execute(CREATE_ORDERS);
      st.execute(CREATE_LINE_ITEMS);
      st.execute(CREATE_ATTRIBUTES);
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verify.createStatement()) {
      st.execute("DROP TABLE IF EXISTS attributes");
      st.execute("DROP TABLE IF EXISTS line_items");
      st.execute("DROP TABLE IF EXISTS orders");
    }
    verify.close();
  }

  // --- Helpers ---

  private DatabaseDestinationConfig config() {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(postgres.getJdbcUrl())
        .username(postgres.getUsername())
        .password(postgres.getPassword())
        .tableName("orders")
        .batchSize(100)
        .build();
  }

  private DatabaseDestinationConfig config(int batchSize, String strategy) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(postgres.getJdbcUrl())
        .username(postgres.getUsername())
        .password(postgres.getPassword())
        .tableName("orders")
        .batchSize(batchSize)
        .transactionStrategy(strategy)
        .build();
  }

  private Map<String, Object> orderRecord(int id, String status, List<Map<String, Object>> items) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", id);
    record.put("status", status);
    record.put("line_items", items);
    return record;
  }

  private Map<String, Object> lineItemRecord(int id, String product, int qty) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", id);
    item.put("product", product);
    item.put("quantity", qty);
    return item;
  }

  private Map<String, Object> lineItemWithAttributes(
      int id, String product, int qty, List<Map<String, Object>> attrs) {
    Map<String, Object> item = lineItemRecord(id, product, qty);
    item.put("attributes", attrs);
    return item;
  }

  private Map<String, Object> attributeRecord(int id, String name, String value) {
    Map<String, Object> attr = new LinkedHashMap<>();
    attr.put("id", id);
    attr.put("name", name);
    attr.put("value", value);
    return attr;
  }

  private int countRows(String table) throws SQLException {
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private int countRowsWhere(String table, String whereClause) throws SQLException {
    try (Statement st = verify.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT COUNT(*) FROM \"" + table + "\" WHERE " + whereClause)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  // --- Tests ---

  @Test
  void shouldInsertOrderAndLineItemsIntoSeparateTables() throws SQLException {
    Map<String, Object> order =
        orderRecord(
            1,
            "PENDING",
            List.of(lineItemRecord(101, "Widget", 2), lineItemRecord(102, "Gadget", 1)));

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(order);
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(1);
    assertThat(countRows("line_items")).isEqualTo(2);
  }

  @Test
  void shouldInjectCorrectFkIntoLineItems() throws SQLException {
    Map<String, Object> order =
        orderRecord(42, "CONFIRMED", List.of(lineItemRecord(201, "Widget", 3)));

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(order);
      dest.flush();
    }

    assertThat(countRowsWhere("line_items", "orders_id = 42")).isEqualTo(1);
  }

  @Test
  void shouldHandleOrderWithNoLineItems() throws SQLException {
    Map<String, Object> order = orderRecord(10, "DRAFT", List.of());

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(order);
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(1);
    assertThat(countRows("line_items")).isEqualTo(0);
  }

  @Test
  void shouldInsertMultipleOrdersWithCorrectFks() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(
          orderRecord(
              1, "PENDING", List.of(lineItemRecord(11, "A", 1), lineItemRecord(12, "B", 2))));
      dest.write(orderRecord(2, "SHIPPED", List.of(lineItemRecord(21, "C", 5))));
      dest.write(orderRecord(3, "DELIVERED", List.of()));
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(3);
    assertThat(countRows("line_items")).isEqualTo(3);
    assertThat(countRowsWhere("line_items", "orders_id = 1")).isEqualTo(2);
    assertThat(countRowsWhere("line_items", "orders_id = 2")).isEqualTo(1);
    assertThat(countRowsWhere("line_items", "orders_id = 3")).isEqualTo(0);
  }

  @Test
  void shouldInsertThreeLevelNestingWithCorrectFkChain() throws SQLException {
    Map<String, Object> attr1 = attributeRecord(9001, "color", "red");
    Map<String, Object> attr2 = attributeRecord(9002, "size", "M");
    Map<String, Object> item = lineItemWithAttributes(501, "T-shirt", 1, List.of(attr1, attr2));
    Map<String, Object> order = orderRecord(100, "NEW", List.of(item));

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(order);
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(1);
    assertThat(countRows("line_items")).isEqualTo(1);
    assertThat(countRows("attributes")).isEqualTo(2);

    // FK chain: attributes.line_items_id must point to line_items.id (501)
    assertThat(countRowsWhere("attributes", "line_items_id = 501")).isEqualTo(2);

    // Grandchild must NOT have orders_id — only immediate parent FK
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM attributes LIMIT 1")) {
      rs.next();
      assertThat(rs.getString("name")).isIn("color", "size");
      assertThat(rs.getInt("line_items_id")).isEqualTo(501);
    }
  }

  @Test
  void shouldInsert100OrdersWithVariableLineItemCounts() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      int totalLineItems = 0;
      for (int i = 1; i <= 100; i++) {
        int itemCount = (i % 5) + 1; // 1 to 5 items per order
        List<Map<String, Object>> items = new ArrayList<>();
        for (int j = 0; j < itemCount; j++) {
          items.add(lineItemRecord(i * 100 + j, "product-" + j, j + 1));
        }
        dest.write(orderRecord(i, "PENDING", items));
        totalLineItems += itemCount;
      }
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(100);
    // 100 orders × avg 3 items = 300 line items (1+2+3+4+5 repeating = 3 avg)
    assertThat(countRows("line_items")).isEqualTo(300);
  }

  @Test
  void shouldCommitParentAndChildrenTogetherWithPerBatchStrategy() throws SQLException {
    Map<String, Object> order = orderRecord(77, "BATCH_TEST", List.of(lineItemRecord(771, "X", 1)));

    try (DatabaseDestination dest = new DatabaseDestination(config(10, "per_batch"))) {
      dest.open();
      dest.write(order);
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(1);
    assertThat(countRows("line_items")).isEqualTo(1);
    assertThat(countRowsWhere("line_items", "orders_id = 77")).isEqualTo(1);
  }

  @Test
  void shouldCommitAllRecordsAtEndWithPerJobStrategy() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(100, "per_job"))) {
      dest.open();
      for (int i = 1; i <= 5; i++) {
        dest.write(orderRecord(i, "JOB_TEST", List.of(lineItemRecord(i * 10, "item", 1))));
      }
      dest.flush(); // triggers per_job commit
    }

    assertThat(countRows("orders")).isEqualTo(5);
    assertThat(countRows("line_items")).isEqualTo(5);
  }

  @Test
  void shouldWorkWithAutoCommitStrategy() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(100, "auto_commit"))) {
      dest.open();
      dest.write(orderRecord(55, "AUTO", List.of(lineItemRecord(551, "P", 2))));
      dest.flush();
    }

    assertThat(countRows("orders")).isEqualTo(1);
    assertThat(countRows("line_items")).isEqualTo(1);
  }

  @Test
  void shouldFlushRemainingRecordsOnClose() throws SQLException {
    // Write fewer than batchSize records; close() must commit them
    try (DatabaseDestination dest = new DatabaseDestination(config(100, "per_batch"))) {
      dest.open();
      dest.write(orderRecord(1, "A", List.of(lineItemRecord(11, "X", 1))));
      dest.write(orderRecord(2, "B", List.of(lineItemRecord(21, "Y", 2))));
      // No explicit flush — relies on close()
    }

    assertThat(countRows("orders")).isEqualTo(2);
    assertThat(countRows("line_items")).isEqualTo(2);
  }
}
