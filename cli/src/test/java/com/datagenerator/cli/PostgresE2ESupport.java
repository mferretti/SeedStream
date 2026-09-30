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

package com.datagenerator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import picocli.CommandLine;

/** Shared PostgreSQL container and in-process CLI helpers for the slow end-to-end tests. */
@Tag("integration")
@Tag("slow")
@Testcontainers
abstract class PostgresE2ESupport {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  /** Runs {@code execute} in-process and returns the picocli exit code. */
  static int execute(Path job, String... extraArgs) {
    List<String> args = new ArrayList<>(List.of("--job", job.toString()));
    args.addAll(List.of(extraArgs));
    return new CommandLine(new ExecuteCommand()).execute(args.toArray(String[]::new));
  }

  /** Database-destination job for {@code table}; flat mode, connection points at the container. */
  static String databaseJob(String structuresDir, String source, String table, long seed) {
    return String.join(
        "\n",
        "source: " + source,
        "type: database",
        "structures_path: " + structuresDir,
        "seed:",
        "  type: embedded",
        "  value: " + seed,
        "conf:",
        "  jdbc_url: \"" + postgres.getJdbcUrl() + "\"",
        "  username: \"" + postgres.getUsername() + "\"",
        "  password: \"" + postgres.getPassword() + "\"",
        "  table: \"" + table + "\"",
        "  batch_size: 500",
        "  transaction_strategy: per_batch",
        "");
  }

  static void writeString(Path file, String content) throws IOException {
    Files.writeString(file, content);
  }

  static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  static void runSql(String sql) throws SQLException {
    try (Connection c = connect();
        Statement st = c.createStatement()) {
      st.execute(sql); // nosemgrep: test-only, hard-coded SQL on throwaway container
    }
  }

  static String queryString(String sql) throws SQLException {
    try (Connection c = connect();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) { // nosemgrep: test-only, hard-coded SQL
      assertThat(rs.next()).as(sql).isTrue();
      return rs.getString(1);
    }
  }

  static long count(String sql) throws SQLException {
    return Long.parseLong(queryString(sql));
  }
}
