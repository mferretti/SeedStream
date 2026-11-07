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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code quote_identifiers} in a job's {@code conf} reaches the database destination through the
 * real job parser ({@code execute}), observed by reading rows back from H2 (#446).
 */
@SuppressWarnings("java:S2068") // H2 in-memory test DB — empty password is the default
class ExecuteCommandQuoteIdentifiersTest {

  private static final String H2_URL = "jdbc:h2:mem:clitest446;DB_CLOSE_DELAY=-1";
  private static final String QUOTED_TABLE = "\"Orders\"";

  @TempDir Path tempDir;

  private Connection observer;

  @BeforeEach
  @SuppressWarnings("java:S2115")
  void setUp() throws SQLException, IOException {
    observer = DriverManager.getConnection(H2_URL, "sa", ""); // nosemgrep
    try (Statement st = observer.createStatement()) {
      st.execute("DROP ALL OBJECTS");
      st.execute("CREATE TABLE " + QUOTED_TABLE + " (\"id\" INT, \"Name\" VARCHAR(20))");
    }
    Path structures = tempDir.resolve("structures");
    Files.createDirectories(structures);
    Files.writeString(
        structures.resolve("orders.yaml"),
        String.join(
            "\n",
            "name: orders",
            "data:",
            "  id:",
            "    datatype: \"int[1..1000]\"",
            "  Name:",
            "    datatype: \"char[3..10]\"",
            ""));
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = observer.createStatement()) {
      st.execute("DROP ALL OBJECTS");
    }
    observer.close();
  }

  private Path job(String... extraConf) throws IOException {
    Path jobFile = tempDir.resolve("job.yaml");
    String conf =
        String.join(
            "\n",
            "source: orders.yaml",
            "type: database",
            "structures_path: " + tempDir.resolve("structures").toAbsolutePath(),
            "seed:",
            "  type: embedded",
            "  value: 42",
            "conf:",
            "  jdbc_url: " + H2_URL,
            "  username: sa",
            "  password: \"\"",
            "  table: Orders",
            "  transaction_strategy: auto_commit",
            "  max_retries: 1",
            String.join("\n", extraConf),
            "");
    Files.writeString(jobFile, conf);
    return jobFile;
  }

  private String rowCount() throws SQLException {
    try (Statement st = observer.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + QUOTED_TABLE)) {
      rs.next();
      return rs.getString(1);
    }
  }

  @Test
  void shouldQuoteIdentifiersWhenJobConfSetsQuoteIdentifiersTrue() throws Exception {
    CliTestSupport.Result r =
        CliTestSupport.run(
            "execute", "--job", job("  quote_identifiers: true").toString(), "--count", "3");

    assertThat(r.exit()).as(r.err()).isZero();
    assertThat(rowCount()).isEqualTo("3");
  }

  @Test
  void guardShouldNotQuoteIdentifiersWhenJobConfOmitsQuoteIdentifiers() throws Exception {
    CliTestSupport.Result r =
        CliTestSupport.run("execute", "--job", job().toString(), "--count", "3");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(rowCount()).isEqualTo("0");
  }

  @Test
  void guardShouldNotQuoteIdentifiersWhenJobConfSetsQuoteIdentifiersFalse() throws Exception {
    CliTestSupport.Result r =
        CliTestSupport.run(
            "execute", "--job", job("  quote_identifiers: false").toString(), "--count", "3");

    assertThat(r.exit()).isEqualTo(1);
    assertThat(rowCount()).isEqualTo("0");
  }
}
