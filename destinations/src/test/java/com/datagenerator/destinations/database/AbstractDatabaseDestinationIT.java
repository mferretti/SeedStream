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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Integration tests for {@link DatabaseDestination} against any JDBC database provided by
 * subclasses.
 *
 * <p>Uses the passport structure (Stage 1 — flat fields only) to exercise all supported JDBC type
 * mappings: {@code VARCHAR}, {@code DATE}, {@code TIMESTAMP}, and {@code enum} values as strings.
 *
 * <p>Run with: {@code ./gradlew :destinations:integrationTest}
 */
abstract class AbstractDatabaseDestinationIT extends IntegrationTest {

  // "doc_number" not "number": NUMBER is a reserved word in Oracle, and identifiers are
  // emitted unquoted, so a reserved-word column name fails (ORA-03050) on strict dialects.
  private static final String FIELD_NUMBER = "doc_number";
  private static final String FIELD_FIRST_NAME = "first_name";
  private static final String FIELD_LAST_NAME = "last_name";
  private static final String FIELD_FULL_NAME = "full_name";
  private static final String FIELD_NATIONALITY = "nationality";
  private static final String FIELD_PLACE_OF_BIRTH = "place_of_birth";
  private static final String FIELD_ISSUE_DATE = "issue_date";
  private static final String FIELD_EXPIRY_DATE = "expiry_date";
  private static final String FIELD_AUTHORITY = "authority";
  private static final String FIELD_ISSUED_AT = "issued_at";
  private static final String PASSPORT_AB = "AB123456";
  private static final String TABLE_PASSPORTS = "passports";

  /** UTC+14 and no DST — as far from UTC as a JVM default zone gets. */
  private static final String FAR_ZONE = "Pacific/Kiritimati";

  private static final Instant ISSUED_AT = Instant.parse("2024-06-15T10:30:00Z");

  private Connection verifyConnection;

  protected abstract JdbcDatabaseContainer<?> container();

  /**
   * Passport DDL. Built per-run rather than held as a constant because the timestamp column type is
   * dialect-dependent — see {@link #timestampColumnType()}.
   */
  @SuppressFBWarnings(
      "VA_FORMAT_STRING_USES_NEWLINE") // text block newlines are intentional DDL line endings
  private String createPassportsTable() {
    return """
        CREATE TABLE passports (
          doc_number     VARCHAR(9),
          first_name     VARCHAR(255),
          last_name      VARCHAR(255),
          full_name      VARCHAR(255),
          dob            DATE,
          nationality    VARCHAR(255),
          place_of_birth VARCHAR(255),
          issue_date     DATE,
          expiry_date    DATE,
          authority      VARCHAR(255),
          sex            VARCHAR(5),
          issued_at      %s
        )
        """
        .formatted(timestampColumnType());
  }

  /**
   * SQL type for a wall-clock timestamp column — one with no time-zone semantics of its own.
   *
   * <p>Standard {@code TIMESTAMP} everywhere except the two dialects where that name means
   * something else: SQL Server's {@code TIMESTAMP} is a rowversion (not a datetime at all), and
   * MySQL's applies a server-side session-zone conversion on write and read, which would mask what
   * {@link #shouldStoreTimestampInUtcRegardlessOfDefaultTimeZone()} asserts about the client-side
   * binding. Both override this with their plain datetime type.
   */
  protected String timestampColumnType() {
    return "TIMESTAMP";
  }

  protected String jdbcUrl() {
    return container().getJdbcUrl();
  }

  protected String username() {
    return container().getUsername();
  }

  protected String password() {
    return container().getPassword();
  }

  @BeforeEach
  void setUp() throws SQLException {
    verifyConnection = DriverManager.getConnection(jdbcUrl(), username(), password());
    try (Statement st = verifyConnection.createStatement()) {
      st.execute(createPassportsTable());
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
        .jdbcUrl(jdbcUrl())
        .username(username())
        .password(password())
        .tableName(TABLE_PASSPORTS)
        .batchSize(batchSize)
        .build();
  }

  @SuppressWarnings("java:S107")
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
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(FIELD_NUMBER, number);
    data.put(FIELD_FIRST_NAME, firstName);
    data.put(FIELD_LAST_NAME, lastName);
    data.put(FIELD_FULL_NAME, fullName);
    data.put("dob", dob);
    data.put(FIELD_NATIONALITY, nationality);
    data.put(FIELD_PLACE_OF_BIRTH, placeOfBirth);
    data.put(FIELD_ISSUE_DATE, issueDate);
    data.put(FIELD_EXPIRY_DATE, expiryDate);
    data.put(FIELD_AUTHORITY, authority);
    data.put("sex", sex);
    return data;
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
        sexFor(index));
  }

  private int countRows() throws SQLException {
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM passports")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private String sexFor(int index) {
    if (index % 3 == 0) return "M";
    if (index % 3 == 1) return "F";
    return "X";
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
    Map<String, Object> data =
        passportRecord(
            PASSPORT_AB,
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
      dest.write(data);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT doc_number, first_name, last_name, sex FROM passports")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(FIELD_NUMBER)).isEqualTo(PASSPORT_AB);
      assertThat(rs.getString(FIELD_FIRST_NAME)).isEqualTo("Alice");
      assertThat(rs.getString(FIELD_LAST_NAME)).isEqualTo("Smith");
      assertThat(rs.getString("sex")).isEqualTo("F");
    }
  }

  @Test
  void shouldPersistDateFieldsCorrectly() throws SQLException {
    LocalDate dob = LocalDate.of(1975, 11, 30);
    LocalDate issueDate = LocalDate.of(2019, 5, 1);
    LocalDate expiryDate = LocalDate.of(2029, 5, 1);

    Map<String, Object> data =
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
      dest.write(data);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT dob, issue_date, expiry_date FROM passports")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getDate("dob")).isEqualTo(Date.valueOf(dob));
      assertThat(rs.getDate(FIELD_ISSUE_DATE)).isEqualTo(Date.valueOf(issueDate));
      assertThat(rs.getDate(FIELD_EXPIRY_DATE)).isEqualTo(Date.valueOf(expiryDate));
    }
  }

  // -------------------------------------------------------------------------
  // Timestamps — written in UTC, whatever zone the JVM happens to be in
  // -------------------------------------------------------------------------

  /**
   * Writes one row carrying {@code issued_at} while the JVM default zone is {@code zoneId}, which
   * is the only thing that differs between a developer laptop and a CI runner.
   *
   * @param schema declared field types for schema-aware binding, or null for runtime inference
   */
  private void writeIssuedAtUnderZone(
      String docNumber, Object issuedAt, String zoneId, Map<String, String> schema) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put(FIELD_NUMBER, docNumber);
    row.put(FIELD_ISSUED_AT, issuedAt);

    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
      try (DatabaseDestination dest =
          schema == null
              ? new DatabaseDestination(config())
              : new DatabaseDestination(config(), schema)) {
        dest.open();
        dest.write(row);
        dest.flush();
      }
    } finally {
      TimeZone.setDefault(original);
    }
  }

  /**
   * Reads the stored value back interpreted in UTC, so the assertion depends on the wall clock that
   * was written and not on the zone in force at read time. Binding without a UTC calendar stored a
   * wall clock shifted by the writer's offset, which surfaces here as a different instant.
   */
  private Timestamp readIssuedAtInUtc(String docNumber) throws SQLException {
    try (PreparedStatement ps =
        verifyConnection.prepareStatement("SELECT issued_at FROM passports WHERE doc_number = ?")) {
      ps.setString(1, docNumber);
      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).isTrue();
        return rs.getTimestamp(FIELD_ISSUED_AT, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
      }
    }
  }

  @Test
  void shouldStoreTimestampInUtcRegardlessOfDefaultTimeZone() throws SQLException {
    // One instant, written as if from two machines 14 hours apart
    writeIssuedAtUnderZone("TS000001", ISSUED_AT, FAR_ZONE, null);
    writeIssuedAtUnderZone("TS000002", ISSUED_AT, "UTC", null);

    Timestamp expected = Timestamp.from(ISSUED_AT);
    assertThat(readIssuedAtInUtc("TS000001")).isEqualTo(expected);
    assertThat(readIssuedAtInUtc("TS000002")).isEqualTo(expected);
  }

  @Test
  void shouldStoreTimestampInUtcWithSchemaAwareBinding() throws SQLException {
    // Option B: ISO-8601 String coerced through the declared timestamp type
    writeIssuedAtUnderZone("TS000003", ISSUED_AT.toString(), FAR_ZONE, timestampSchema());

    assertThat(readIssuedAtInUtc("TS000003")).isEqualTo(Timestamp.from(ISSUED_AT));
  }

  private Map<String, String> timestampSchema() {
    return Map.of(
        FIELD_NUMBER,
        "char[8..9]",
        FIELD_ISSUED_AT,
        "timestamp[2020-01-01T00:00:00..2030-12-31T23:59:59]");
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
            .jdbcUrl(jdbcUrl())
            .username(username())
            .password(password())
            .tableName(TABLE_PASSPORTS)
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
            .jdbcUrl(jdbcUrl())
            .username(username())
            .password(password())
            .tableName(TABLE_PASSPORTS)
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
      st.execute(createPassportsTable().replace(TABLE_PASSPORTS, "alt_passports"));
    }

    DatabaseDestinationConfig altConfig =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(jdbcUrl())
            .username(username())
            .password(password())
            .tableName("alt_passports")
            .batchSize(10)
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(altConfig)) {
      dest.open();
      dest.write(samplePassport(1));
      dest.flush();
    }

    // Original passports table must be empty
    assertThat(countRows()).isZero();

    // Alt table must have 1 data
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
  void shouldHandleNestedObjectViaStage2Decomposition() throws SQLException {
    // Stage 2: nested objects are auto-decomposed; no exception is thrown.
    // The child table "address" must exist for the insert to succeed.
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("CREATE TABLE address (id INT, city VARCHAR(255), passports_id VARCHAR(9))");
    }

    Map<String, Object> address = new LinkedHashMap<>();
    address.put("id", 1);
    address.put("city", "Rome");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put(FIELD_NUMBER, PASSPORT_AB);
    data.put("address", address);

    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      assertThatCode(() -> dest.write(data)).doesNotThrowAnyException();
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS address");
    }
  }

  // -------------------------------------------------------------------------
  // Option B — schema-aware binding against real database
  // -------------------------------------------------------------------------

  @Test
  void shouldInsertPassportRecordsWithSchema() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(), passportSchema())) {
      dest.open();
      for (int i = 0; i < 5; i++) {
        dest.write(samplePassport(i));
      }
      dest.flush();
    }

    assertThat(countRows()).isEqualTo(5);
  }

  @Test
  void shouldCoerceStringToDateWithSchema() throws SQLException {
    // Pass ISO-8601 String for date fields instead of LocalDate — schema triggers coercion
    Map<String, Object> recordWithStringDates = new LinkedHashMap<>();
    recordWithStringDates.put(FIELD_NUMBER, "ZZ000001");
    recordWithStringDates.put(FIELD_FIRST_NAME, "Test");
    recordWithStringDates.put(FIELD_LAST_NAME, "User");
    recordWithStringDates.put(FIELD_FULL_NAME, "Test User");
    recordWithStringDates.put("dob", "1990-06-15"); // String, not LocalDate
    recordWithStringDates.put(FIELD_NATIONALITY, "Italy");
    recordWithStringDates.put(FIELD_PLACE_OF_BIRTH, "Rome");
    recordWithStringDates.put(FIELD_ISSUE_DATE, "2020-03-01"); // String, not LocalDate
    recordWithStringDates.put(FIELD_EXPIRY_DATE, "2030-03-01"); // String, not LocalDate
    recordWithStringDates.put(FIELD_AUTHORITY, "Ministry of Interior");
    recordWithStringDates.put("sex", "M");

    try (DatabaseDestination dest = new DatabaseDestination(config(), passportSchema())) {
      dest.open();
      dest.write(recordWithStringDates);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT dob, issue_date FROM passports")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getDate("dob")).isEqualTo(Date.valueOf(LocalDate.of(1990, 6, 15)));
      assertThat(rs.getDate(FIELD_ISSUE_DATE)).isEqualTo(Date.valueOf(LocalDate.of(2020, 3, 1)));
    }
  }

  // --- Schema helper ---

  /**
   * Raw YAML type strings for the passport table columns used in this test class.
   *
   * <p>Keys use alias names (doc_number, dob, authority) to match the data field names produced by
   * {@link #samplePassport(int)} and {@link #passportRecord}, which mirror the DB column names.
   */
  private Map<String, String> passportSchema() {
    return Map.ofEntries(
        Map.entry(FIELD_NUMBER, "char[8..9]"),
        Map.entry(FIELD_FIRST_NAME, FIELD_FIRST_NAME),
        Map.entry(FIELD_LAST_NAME, FIELD_LAST_NAME),
        Map.entry(FIELD_FULL_NAME, FIELD_FULL_NAME),
        Map.entry("dob", "date[1950-01-01..2006-12-31]"),
        Map.entry(FIELD_NATIONALITY, "country"),
        Map.entry(FIELD_PLACE_OF_BIRTH, "city"),
        Map.entry(FIELD_ISSUE_DATE, "date[2015-01-01..2024-12-31]"),
        Map.entry(FIELD_EXPIRY_DATE, "date[2025-01-01..2034-12-31]"),
        Map.entry(FIELD_AUTHORITY, "company"),
        Map.entry("sex", "enum[M,F,X]"));
  }
}
