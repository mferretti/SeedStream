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
import java.time.Instant;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * PostgreSQL-only integration test pinning timestamp writes to UTC.
 *
 * <p>Regression guard for a cross-machine determinism break: {@code setTimestamp()} without a
 * calendar renders the instant in the JVM's default zone, so the same seed wrote different
 * wall-clock values into a {@code TIMESTAMP} column depending on where the job ran. The CI seeding
 * fixture caught it as a fingerprint mismatch between a CEST developer machine and a UTC runner.
 */
class DatabaseTimestampTimeZonePostgresIT extends IntegrationTest {

  private static final String TABLE_EVENTS = "events";

  /** Deliberately not UTC and not the developer's zone: UTC+14, and no DST. */
  private static final String FAR_ZONE = "Pacific/Kiritimati";

  private static final Instant INSTANT = Instant.parse("2024-06-15T10:30:00Z");

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
      st.execute("CREATE TABLE events (id INT PRIMARY KEY, occurred_at TIMESTAMP)");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS events");
    }
    verifyConnection.close();
  }

  private DatabaseDestinationConfig config() {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(postgres.getJdbcUrl())
        .username(postgres.getUsername())
        .password(postgres.getPassword())
        .tableName(TABLE_EVENTS)
        .build();
  }

  private void writeEvent(int id) {
    try (DatabaseDestination dest = new DatabaseDestination(config())) {
      dest.open();
      dest.write(Map.of("id", id, "occurred_at", INSTANT));
      dest.flush();
    }
  }

  /** Reads the stored wall clock as text, so the assertion is not re-interpreted by the client. */
  private String storedWallClock(int id) throws SQLException {
    try (Statement st = verifyConnection.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT to_char(occurred_at, 'YYYY-MM-DD HH24:MI:SS') AS wc "
                    + "FROM events WHERE id = "
                    + id)) {
      assertThat(rs.next()).isTrue();
      return rs.getString("wc");
    }
  }

  @Test
  void shouldStoreTimestampAsUtcWallClockUnderAnyDefaultTimeZone() throws SQLException {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(FAR_ZONE));
      writeEvent(1);

      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      writeEvent(2);
    } finally {
      TimeZone.setDefault(original);
    }

    // Same instant, two very different JVM zones → one stored value, the UTC wall clock
    assertThat(storedWallClock(1)).isEqualTo("2024-06-15 10:30:00");
    assertThat(storedWallClock(2)).isEqualTo(storedWallClock(1));
  }
}
