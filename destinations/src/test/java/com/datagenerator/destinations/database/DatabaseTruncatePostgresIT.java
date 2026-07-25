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
 * PostgreSQL-only integration tests for {@code truncate_before_insert}.
 *
 * <p>Kept separate from {@link AbstractDatabaseDestinationIT} (which runs across all four DBs)
 * because the feature emits {@code TRUNCATE TABLE ... CASCADE}, which is PostgreSQL/Oracle syntax.
 * MySQL and SQL Server are intentionally out of scope for this feature.
 */
class DatabaseTruncatePostgresIT extends IntegrationTest {

  private static final String TABLE_ORDERS = "orders";
  private static final String TABLE_LINES = "order_lines";
  private static final String TABLE_IDENTITY = "identity_orders";

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private Connection verifyConnection;

  @BeforeEach
  void setUp() throws SQLException {
    verifyConnection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("CREATE TABLE orders (id INT PRIMARY KEY, customer VARCHAR(255))");
      st.execute(
          "CREATE TABLE order_lines ("
              + "id INT, sku VARCHAR(255), orders_id INT REFERENCES orders(id))");
      st.execute(
          "CREATE TABLE identity_orders ("
              + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, customer VARCHAR(255))");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS order_lines");
      st.execute("DROP TABLE IF EXISTS orders");
      st.execute("DROP TABLE IF EXISTS identity_orders");
    }
    verifyConnection.close();
  }

  private DatabaseDestinationConfig config(String table, boolean truncate) {
    return config(table, truncate, false);
  }

  private DatabaseDestinationConfig config(
      String table, boolean truncate, boolean restartIdentity) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(postgres.getJdbcUrl())
        .username(postgres.getUsername())
        .password(postgres.getPassword())
        .tableName(table)
        .truncateBeforeInsert(truncate)
        .restartIdentity(restartIdentity)
        .build();
  }

  private Map<String, Object> order(int id, String customer) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id);
    data.put("customer", customer);
    return data;
  }

  private int countOrders() throws SQLException {
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM orders")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private int countOrderLines() throws SQLException {
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM order_lines")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  @Test
  void shouldReplaceExistingRowsWhenTruncateEnabled() throws SQLException {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("INSERT INTO orders (id, customer) VALUES (99, 'stale')");
    }
    assertThat(countOrders()).isEqualTo(1);

    try (DatabaseDestination dest = new DatabaseDestination(config(TABLE_ORDERS, true))) {
      dest.open();
      dest.write(order(1, "Alice"));
      dest.write(order(2, "Bob"));
      dest.flush();
    }

    // Stale row gone; only the two fresh rows remain
    assertThat(countOrders()).isEqualTo(2);
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT customer FROM orders WHERE id = 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("customer")).isEqualTo("Alice");
    }
  }

  @Test
  void shouldKeepExistingRowsWhenTruncateDisabled() throws SQLException {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("INSERT INTO orders (id, customer) VALUES (99, 'stale')");
    }

    try (DatabaseDestination dest = new DatabaseDestination(config(TABLE_ORDERS, false))) {
      dest.open();
      dest.write(order(1, "Alice"));
      dest.flush();
    }

    // Existing row survives — append semantics
    assertThat(countOrders()).isEqualTo(2);
  }

  @Test
  void shouldCascadeTruncateNestedFkChildTables() throws SQLException {
    // Pre-populate parent + FK child with stale data
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("INSERT INTO orders (id, customer) VALUES (99, 'stale')");
      st.execute("INSERT INTO order_lines (id, sku, orders_id) VALUES (5, 'OLD', 99)");
    }
    assertThat(countOrderLines()).isEqualTo(1);

    // Nested record → orders row + order_lines child row; CASCADE must clear the stale child
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("id", 10);
    line.put("sku", "NEW");
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("id", 1);
    nested.put("customer", "Alice");
    nested.put(TABLE_LINES, line);

    try (DatabaseDestination dest = new DatabaseDestination(config(TABLE_ORDERS, true))) {
      dest.open();
      dest.write(nested);
      dest.flush();
    }

    assertThat(countOrders()).isEqualTo(1);
    assertThat(countOrderLines()).isEqualTo(1);
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT sku FROM order_lines")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("sku")).isEqualTo("NEW");
    }
  }

  // --- restart_identity ---

  private void seedIdentityOrders(boolean restartIdentity) {
    try (DatabaseDestination dest =
        new DatabaseDestination(config(TABLE_IDENTITY, true, restartIdentity))) {
      dest.open();
      dest.write(Map.of("customer", "Alice"));
      dest.write(Map.of("customer", "Bob"));
      dest.flush();
    }
  }

  private List<Long> identityOrderIds() throws SQLException {
    List<Long> ids = new ArrayList<>();
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT id FROM identity_orders ORDER BY id")) {
      while (rs.next()) {
        ids.add(rs.getLong("id"));
      }
    }
    return ids;
  }

  @Test
  void shouldRestartIdentitySequenceOnReseedWhenEnabled() throws SQLException {
    seedIdentityOrders(true);
    assertThat(identityOrderIds()).containsExactly(1L, 2L);

    // Reseed: the sequence must restart, so the same dense key range comes back
    seedIdentityOrders(true);
    assertThat(identityOrderIds()).containsExactly(1L, 2L);
  }

  @Test
  void shouldKeepClimbingIdentitySequenceWhenRestartIdentityDisabled() throws SQLException {
    seedIdentityOrders(false);
    assertThat(identityOrderIds()).containsExactly(1L, 2L);

    // Plain TRUNCATE leaves the sequence untouched — ids continue from the high-water mark
    seedIdentityOrders(false);
    assertThat(identityOrderIds()).containsExactly(3L, 4L);
  }
}
