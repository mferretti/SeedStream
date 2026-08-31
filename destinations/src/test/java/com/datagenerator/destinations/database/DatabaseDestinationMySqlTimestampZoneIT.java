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

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.destinations.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Regression test for issue #218: MySQL {@code TIMESTAMP} columns drift because the server converts
 * the UTC wall clock {@link JdbcTypeMapper} sends on write using the connection's session {@code
 * time_zone}, and that session zone defaults to {@code SYSTEM} — the MySQL server host's own OS
 * time zone, which is production-dependent and not guaranteed to be UTC (a self-hosted server or a
 * developer's local MySQL commonly runs on local time).
 *
 * <p>The MySQL container's OS time zone is forced to {@code Pacific/Kiritimati} (UTC+14, no DST —
 * as far from UTC as a zone gets) via the {@code TZ} container env var, which is what {@code
 * SYSTEM} resolves to server-side without the fix and is what actually reproduces the drift. The
 * JVM default zone is forced to the same hostile zone for realism (matching a developer laptop
 * outside UTC), though the client-side binding is already zone-naive since #80 and does not itself
 * depend on it. Toggling {@link TimeZone#setDefault(TimeZone)} per-class is safe only because these
 * integration tests run sequentially (no JUnit parallel execution is configured for this module).
 *
 * <p>Unlike {@link AbstractDatabaseDestinationIT}, this test declares a real {@code TIMESTAMP}
 * column (not {@code DATETIME}) and does <b>not</b> pin {@code connectionTimeZone=UTC} on the JDBC
 * URL — pinning it there would mask the server-side drift this test exists to catch. It relies
 * solely on {@link DatabaseDestination#open()} issuing {@code SET time_zone = '+00:00'} via Hikari
 * {@code connectionInitSql}.
 *
 * <p>Verification reads {@code UNIX_TIMESTAMP(issued_at)}, which MySQL always evaluates against the
 * column's true stored UTC epoch regardless of the reading session's zone — unlike {@code SELECT
 * issued_at}, which would itself be re-converted on the way out and could coincidentally match.
 */
class DatabaseDestinationMySqlTimestampZoneIT extends IntegrationTest {

  private static final String TABLE_NAME = "ts_probe";
  private static final Instant ISSUED_AT = Instant.parse("2024-06-15T10:30:00Z");
  private static final long EXPECTED_EPOCH_SECONDS = 1718447400L;

  private static TimeZone originalDefaultZone;

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass")
          .withEnv("TZ", "Pacific/Kiritimati");

  private Connection verifyConnection;

  @BeforeAll
  static void pinHostileJvmZoneForRealism() {
    originalDefaultZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
  }

  @AfterAll
  static void restoreJvmZone() {
    TimeZone.setDefault(originalDefaultZone);
  }

  @BeforeEach
  void setUp() throws Exception {
    // Deliberately NOT adding connectionTimeZone=UTC here — see class javadoc.
    verifyConnection =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("CREATE TABLE " + TABLE_NAME + " (issued_at TIMESTAMP NULL)");
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    try (Statement st = verifyConnection.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
    }
    verifyConnection.close();
  }

  @Test
  void shouldStoreMySqlTimestampAsTrueUtcEpochUnderHostileServerZone() throws Exception {
    DatabaseDestinationConfig config =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(mysql.getJdbcUrl())
            .username(mysql.getUsername())
            .password(mysql.getPassword())
            .tableName(TABLE_NAME)
            .batchSize(1)
            .build();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("issued_at", ISSUED_AT);

    try (DatabaseDestination dest = new DatabaseDestination(config)) {
      dest.open();
      dest.write(row);
      dest.flush();
    }

    try (Statement st = verifyConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT UNIX_TIMESTAMP(issued_at) FROM " + TABLE_NAME)) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong(1)).isEqualTo(EXPECTED_EPOCH_SECONDS);
    }
  }
}
