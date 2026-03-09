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

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.destinations.DestinationException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DatabaseDestination using H2 in-memory database.
 *
 * <p>H2 is used instead of Testcontainers for fast, dependency-free unit testing. Integration tests
 * against real PostgreSQL/MySQL use Testcontainers (see DatabaseDestinationIT).
 */
class DatabaseDestinationTest {

  private static final String JDBC_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
  private static final String USERNAME = "sa";
  private static final String PASSWORD = "";

  private Connection h2Connection;

  @BeforeEach
  void setUp() throws SQLException {
    h2Connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
    try (Statement st = h2Connection.createStatement()) {
      st.execute(
          "CREATE TABLE IF NOT EXISTS users ("
              + "id INT, "
              + "name VARCHAR(255), "
              + "active BOOLEAN"
              + ")");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = h2Connection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS users");
    }
    h2Connection.close();
  }

  private DatabaseDestinationConfig config() {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(JDBC_URL)
        .username(USERNAME)
        .password(PASSWORD)
        .tableName("users")
        .batchSize(10)
        .build();
  }

  private Map<String, Object> record(int id, String name, boolean active) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", id);
    record.put("name", name);
    record.put("active", active);
    return record;
  }

  @Test
  void shouldInsertSingleRecord() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(record(1, "Alice", true));
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(1);
    assertThat(fetchName(1)).isEqualTo("Alice");
  }

  @Test
  void shouldInsertMultipleRecordsInBatch() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      for (int i = 1; i <= 25; i++) {
        dest.write(record(i, "User " + i, i % 2 == 0));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(25);
  }

  @Test
  void shouldFlushPartialBatchOnClose() throws SQLException {
    // batchSize=10 but only 3 records written — must flush on close
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(record(1, "Alice", true));
      dest.write(record(2, "Bob", false));
      dest.write(record(3, "Carol", true));
      // close() without explicit flush() — should still persist all 3
    }

    assertThat(countRows()).isEqualTo(3);
  }

  @Test
  void shouldInsertExactlyBatchSizeRecords() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      for (int i = 1; i <= 10; i++) { // exactly batchSize
        dest.write(record(i, "User " + i, true));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(10);
  }

  @Test
  void shouldFailFastOnNestedObject() {
    Map<String, Object> nestedRecord = new LinkedHashMap<>();
    nestedRecord.put("id", 1);
    nestedRecord.put("address", Map.of("city", "Rome")); // nested — not allowed in Stage 1

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      assertThatThrownBy(() -> dest.write(nestedRecord))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("nested objects")
          .hasMessageContaining("address");
    }
  }

  @Test
  void shouldFailFastOnArrayField() {
    Map<String, Object> arrayRecord = new LinkedHashMap<>();
    arrayRecord.put("id", 1);
    arrayRecord.put("tags", List.of("a", "b")); // array — not allowed in Stage 1

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      assertThatThrownBy(() -> dest.write(arrayRecord))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("arrays")
          .hasMessageContaining("tags");
    }
  }

  @Test
  void shouldThrowWhenWritingBeforeOpen() {
    DatabaseDestination dest = new DatabaseDestination(config());

    assertThatThrownBy(() -> dest.write(record(1, "Alice", true)))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("not open");

    dest.close(); // should be a no-op
  }

  @Test
  void shouldRejectUnknownTransactionStrategy() {
    DatabaseDestinationConfig badConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .tableName("users")
            .transactionStrategy("invalid_strategy")
            .build();

    DatabaseDestination dest = new DatabaseDestination(badConfig);
    assertThatThrownBy(dest::open)
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("transaction_strategy")
        .hasMessageContaining("invalid_strategy");
    dest.close();
  }

  @Test
  void shouldWorkWithPerJobTransactionStrategy() throws SQLException {
    DatabaseDestinationConfig perJobConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .tableName("users")
            .batchSize(10)
            .transactionStrategy("per_job")
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(perJobConfig)) {
      dest.open();
      for (int i = 1; i <= 15; i++) {
        dest.write(record(i, "User " + i, true));
      }
      dest.flush(); // triggers per_job commit
    }

    assertThat(countRows()).isEqualTo(15);
  }

  @Test
  void shouldWorkWithAutoCommitTransactionStrategy() throws SQLException {
    DatabaseDestinationConfig autoCommitConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .tableName("users")
            .batchSize(10)
            .transactionStrategy("auto_commit")
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(autoCommitConfig)) {
      dest.open();
      for (int i = 1; i <= 5; i++) {
        dest.write(record(i, "User " + i, true));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(5);
  }

  // -------------------------------------------------------------------------
  // Option B — schema-aware binding
  // -------------------------------------------------------------------------

  @Test
  void shouldInsertWithSchemaUsingTypedBinding() throws SQLException {
    Map<String, DataType> schema =
        Map.of(
            "id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "9999"),
            "name", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "255"),
            "active", new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));

    try (DatabaseDestination dest = new DatabaseDestination(config(), schema)) {
      dest.open();
      dest.write(record(1, "Alice", true));
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(1);
    assertThat(fetchName(1)).isEqualTo("Alice");
  }

  @Test
  void shouldCoerceStringValueToIntWhenSchemaDeclaresIntType() throws SQLException {
    // Simulates a Datafaker numeric type returning a String (e.g. age, quantity)
    Map<String, DataType> schema =
        Map.of(
            "id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "9999"),
            "name", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "255"),
            "active", new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));

    Map<String, Object> recordWithStringId = new LinkedHashMap<>();
    recordWithStringId.put("id", "7"); // String instead of Integer — coercion expected
    recordWithStringId.put("name", "Bob");
    recordWithStringId.put("active", true);

    try (DatabaseDestination dest = new DatabaseDestination(config(), schema)) {
      dest.open();
      dest.write(recordWithStringId);
      dest.flush();
    }

    // Verify the record was inserted and id was correctly bound as INT
    try (Statement st = h2Connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT id FROM users WHERE name = 'Bob'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt("id")).isEqualTo(7);
    }
  }

  @Test
  void shouldHandleNullWithTypedSchemaForCorrectSqlType() throws SQLException {
    Map<String, DataType> schema =
        Map.of(
            "id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "9999"),
            "name", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "255"),
            "active", new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));

    Map<String, Object> recordWithNull = new LinkedHashMap<>();
    recordWithNull.put("id", 5);
    recordWithNull.put("name", null); // null with schema → setNull(Types.VARCHAR)
    recordWithNull.put("active", false);

    try (DatabaseDestination dest = new DatabaseDestination(config(), schema)) {
      dest.open();
      dest.write(recordWithNull);
      dest.flush();
    }

    try (Statement st = h2Connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT name FROM users WHERE id = 5")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isNull();
    }
  }

  @Test
  void shouldFallBackToOptionAForFieldsNotInSchema() throws SQLException {
    // Schema only covers 'id' — 'name' and 'active' fall back to Option A instanceof binding
    Map<String, DataType> partialSchema =
        Map.of("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "9999"));

    try (DatabaseDestination dest = new DatabaseDestination(config(), partialSchema)) {
      dest.open();
      dest.write(record(3, "Carol", true));
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(1);
    assertThat(fetchName(3)).isEqualTo("Carol");
  }

  @Test
  void shouldBindEnumFieldAsStringWithSchema() throws SQLException {
    // Create a table with a status column
    try (Statement st = h2Connection.createStatement()) {
      st.execute("CREATE TABLE IF NOT EXISTS statuses (id INT, status VARCHAR(20))");
    }

    DatabaseDestinationConfig statusConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .tableName("statuses")
            .batchSize(10)
            .build();

    Map<String, DataType> schema =
        Map.of(
            "id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "9999"),
            "status", new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING")));

    Map<String, Object> statusRecord = new LinkedHashMap<>();
    statusRecord.put("id", 1);
    statusRecord.put("status", "ACTIVE");

    try (DatabaseDestination dest = new DatabaseDestination(statusConfig, schema)) {
      dest.open();
      dest.write(statusRecord);
      dest.flush();
    }

    try (Statement st = h2Connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT status FROM statuses WHERE id = 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("status")).isEqualTo("ACTIVE");
    }

    try (Statement st = h2Connection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS statuses");
    }
  }

  // --- Helpers ---

  private int countRows() throws SQLException {
    try (Statement st = h2Connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private String fetchName(int id) throws SQLException {
    try (Statement st = h2Connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT name FROM users WHERE id = " + id)) {
      rs.next();
      return rs.getString("name");
    }
  }
}
