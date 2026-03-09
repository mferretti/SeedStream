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

import com.datagenerator.destinations.DestinationException;
import com.datagenerator.destinations.IntegrationTest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration tests for {@link DatabaseDestination} against a real PostgreSQL instance.
 *
 * <p>Uses the passport structure (Stage 1 — flat fields only) to exercise all supported JDBC type
 * mappings: {@code VARCHAR}, {@code DATE}, and {@code enum} values as strings.
 *
 * <p>Run with: {@code ./gradlew :destinations:integrationTest}
 */
class DatabaseDestinationIT extends IntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private static final String CREATE_PASSPORTS_TABLE =
      """
      CREATE TABLE passports (
        number         VARCHAR(9),
        first_name     VARCHAR(255),
        last_name      VARCHAR(255),
        full_name      VARCHAR(255),
        dob            DATE,
        nationality    VARCHAR(255),
        place_of_birth VARCHAR(255),
        issue_date     DATE,
        expiry_date    DATE,
        authority      VARCHAR(255),
        sex            VARCHAR(5)
      )
      """;

  private Connection verifyConnection;

  @BeforeEach
  void setUp() throws SQLException {
    verifyConnection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = verifyConnection.createStatement()) {
      st.execute(CREATE_PASSPORTS_TABLE);
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS passports");
    }
    verifyConnection.close();
  }

  // --- Helpers ---

  private DatabaseDestinationConfig config() {
    return config(1000);
  }

  private DatabaseDestinationConfig config(int batchSize) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(postgres.getJdbcUrl())
        .username(postgres.getUsername())
        .password(postgres.getPassword())
        .tableName("passports")
        .batchSize(batchSize)
        .build();
  }

  private Map<String, Object> passportRecord(
      String number,
      String firstName,
      String lastName,
      String fullName,
      LocalDate dob,
      String nationality,
      String placeOfBirth,
      LocalDate issueDate,
      LocalDate expiryDate,
      String authority,
      String sex) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("number", number);
    record.put("first_name", firstName);
    record.put("last_name", lastName);
    record.put("full_name", fullName);
    record.put("dob", dob);
    record.put("nationality", nationality);
    record.put("place_of_birth", placeOfBirth);
    record.put("issue_date", issueDate);
    record.put("expiry_date", expiryDate);
    record.put("authority", authority);
    record.put("sex", sex);
    return record;
  }

  private Map<String, Object> samplePassport(int index) {
    return passportRecord(
        String.format("P%08d", index), // always 9 chars (P + 8 digits)
        "Jane",
        "Doe",
        "Jane Doe",
        LocalDate.of(1985, 3, 12),
        "United States",
        "New York",
        LocalDate.of(2020, 1, 15),
        LocalDate.of(2030, 1, 15),
        "US Department of State",
        index % 3 == 0 ? "M" : index % 3 == 1 ? "F" : "X");
  }

  private int countRows() throws SQLException {
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM passports")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  // --- Tests ---

  @Test
  void shouldInsertPassportRecords() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      for (int i = 0; i < 10; i++) {
        dest.write(samplePassport(i));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(10);
  }

  @Test
  void shouldPersistCorrectFieldValues() throws SQLException {
    Map<String, Object> record =
        passportRecord(
            "AB123456",
            "Alice",
            "Smith",
            "Alice Smith",
            LocalDate.of(1990, 6, 21),
            "Canada",
            "Toronto",
            LocalDate.of(2022, 3, 10),
            LocalDate.of(2032, 3, 10),
            "Passport Canada",
            "F");

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(record);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM passports")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("number")).isEqualTo("AB123456");
      assertThat(rs.getString("first_name")).isEqualTo("Alice");
      assertThat(rs.getString("last_name")).isEqualTo("Smith");
      assertThat(rs.getString("sex")).isEqualTo("F");
    }
  }

  @Test
  void shouldPersistDateFieldsCorrectly() throws SQLException {
    LocalDate dob = LocalDate.of(1975, 11, 30);
    LocalDate issueDate = LocalDate.of(2019, 5, 1);
    LocalDate expiryDate = LocalDate.of(2029, 5, 1);

    Map<String, Object> record =
        passportRecord(
            "ZX987654",
            "Bob",
            "Jones",
            "Bob Jones",
            dob,
            "UK",
            "London",
            issueDate,
            expiryDate,
            "HM Passport Office",
            "M");

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(record);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT dob, issue_date, expiry_date FROM passports")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getDate("dob")).isEqualTo(Date.valueOf(dob));
      assertThat(rs.getDate("issue_date")).isEqualTo(Date.valueOf(issueDate));
      assertThat(rs.getDate("expiry_date")).isEqualTo(Date.valueOf(expiryDate));
    }
  }

  @Test
  void shouldInsertAcrossMultipleBatches() throws SQLException {
    // batchSize=10, writing 35 records → 3 full batches + 1 partial
    try (DatabaseDestination dest = new DatabaseDestination(config(10))) {
      dest.open();
      for (int i = 0; i < 35; i++) {
        dest.write(samplePassport(i));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(35);
  }

  @Test
  void shouldFlushPartialBatchOnClose() throws SQLException {
    // Write fewer records than batch size — close() must flush the remainder
    try (DatabaseDestination dest = new DatabaseDestination(config(100))) {
      dest.open();
      dest.write(samplePassport(1));
      dest.write(samplePassport(2));
      dest.write(samplePassport(3));
      // no explicit flush() — relies on close()
    }

    assertThat(countRows()).isEqualTo(3);
  }

  @Test
  void shouldCommitWithPerJobStrategy() throws SQLException {
    DatabaseDestinationConfig perJobConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .tableName("passports")
            .batchSize(5)
            .transactionStrategy("per_job")
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(perJobConfig)) {
      dest.open();
      for (int i = 0; i < 12; i++) {
        dest.write(samplePassport(i));
      }
      dest.flush(); // triggers per_job commit
    }

    assertThat(countRows()).isEqualTo(12);
  }

  @Test
  void shouldWorkWithAutoCommitStrategy() throws SQLException {
    DatabaseDestinationConfig autoCommitConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .tableName("passports")
            .batchSize(5)
            .transactionStrategy("auto_commit")
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(autoCommitConfig)) {
      dest.open();
      for (int i = 0; i < 8; i++) {
        dest.write(samplePassport(i));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(8);
  }

  @Test
  void shouldRespectTableNameOverride() throws SQLException {
    // Create an alternate table
    try (Statement st = verifyConnection.createStatement()) {
      st.execute(CREATE_PASSPORTS_TABLE.replace("passports", "alt_passports"));
    }

    DatabaseDestinationConfig altConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .tableName("alt_passports")
            .batchSize(10)
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(altConfig)) {
      dest.open();
      dest.write(samplePassport(1));
      dest.flush();
    }

    // Original passports table must be empty
    assertThat(countRows()).isEqualTo(0);

    // Alt table must have 1 record
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM alt_passports")) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }

    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS alt_passports");
    }
  }

  @Test
  void shouldRejectNestedObjectWithClearError() {
    Map<String, Object> invalidRecord = new LinkedHashMap<>();
    invalidRecord.put("number", "AB123456");
    invalidRecord.put("address", Map.of("city", "Rome")); // nested — Stage 1 not supported

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      assertThatThrownBy(() -> dest.write(invalidRecord))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("nested objects")
          .hasMessageContaining("address");
    }
  }
}
