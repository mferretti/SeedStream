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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * MySQL integration tests for {@code truncate_before_insert} / {@code restart_identity} (#454): the
 * table and every table that references it (transitively) are emptied, FKs stay enforced.
 */
class DatabaseTruncateMySqlIT extends IntegrationTest {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private Connection verify;

  @BeforeEach
  void setUp() throws SQLException {
    verify =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    try (Statement st = verify.createStatement()) {
      st.execute("CREATE TABLE parent (id INT PRIMARY KEY, name VARCHAR(50))");
      st.execute(
          "CREATE TABLE child (id INT PRIMARY KEY, parent_id INT NOT NULL, "
              + "FOREIGN KEY (parent_id) REFERENCES parent(id))");
      st.execute(
          "CREATE TABLE grandchild (id INT PRIMARY KEY, child_id INT NOT NULL, "
              + "FOREIGN KEY (child_id) REFERENCES child(id))");
      st.execute(
          "CREATE TABLE tree (id INT PRIMARY KEY, parent_id INT NULL, "
              + "FOREIGN KEY (parent_id) REFERENCES tree(id))");
      st.execute("CREATE TABLE ident (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
      st.execute(
          "CREATE TABLE idchild (id INT AUTO_INCREMENT PRIMARY KEY, parent_id INT NOT NULL, "
              + "FOREIGN KEY (parent_id) REFERENCES parent(id))");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verify.createStatement()) {
      st.execute("SET FOREIGN_KEY_CHECKS=0");
      for (String t : new String[] {"idchild", "grandchild", "child", "tree", "ident", "parent"}) {
        st.execute("DROP TABLE IF EXISTS " + t);
      }
      st.execute("SET FOREIGN_KEY_CHECKS=1");
    }
    verify.close();
  }

  private DatabaseDestinationConfig config(String table, boolean restartIdentity) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(mysql.getJdbcUrl())
        .username(mysql.getUsername())
        .password(mysql.getPassword())
        .tableName(table)
        .truncateBeforeInsert(true)
        .restartIdentity(restartIdentity)
        .build();
  }

  private void exec(String... sql) throws SQLException {
    try (Statement st = verify.createStatement()) {
      for (String s : sql) {
        st.execute(s);
      }
    }
  }

  private List<Integer> ints(String sql) throws SQLException {
    List<Integer> out = new ArrayList<>();
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getInt(1));
      }
    }
    return out;
  }

  private void prefillFamily() throws SQLException {
    exec(
        "INSERT INTO parent (id, name) VALUES (99, 'stale')",
        "INSERT INTO child (id, parent_id) VALUES (98, 99)",
        "INSERT INTO grandchild (id, child_id) VALUES (97, 98)");
  }

  private void runParentJob() {
    try (DatabaseDestination dest = new DatabaseDestination(config("parent", false))) {
      dest.open();
      dest.write(Map.of("id", 1, "name", "Alice"));
      dest.write(Map.of("id", 2, "name", "Bob"));
      dest.flush();
    }
  }

  private void runIdentityJob(boolean restartIdentity) {
    try (DatabaseDestination dest = new DatabaseDestination(config("ident", restartIdentity))) {
      dest.open();
      dest.write(Map.of("name", "a"));
      dest.write(Map.of("name", "b"));
      dest.write(Map.of("name", "c"));
      dest.flush();
    }
  }

  private void prefillIdent() throws SQLException {
    for (int i = 0; i < 5; i++) {
      exec("INSERT INTO ident (name) VALUES ('old')");
    }
  }

  @Test
  void shouldEmptyParentChildAndGrandchildWhenTruncatingParent() throws SQLException {
    prefillFamily();

    runParentJob();

    assertThat(ints("SELECT id FROM parent ORDER BY id")).containsExactly(1, 2);
    assertThat(ints("SELECT id FROM child")).isEmpty();
    assertThat(ints("SELECT id FROM grandchild")).isEmpty();
  }

  @Test
  void shouldStillEnforceForeignKeysAfterTruncate() throws SQLException {
    prefillFamily();
    runParentJob();

    // Fresh connection: an orphan must be rejected
    assertThatThrownBy(() -> exec("INSERT INTO child (id, parent_id) VALUES (1, 12345)"))
        .isInstanceOf(SQLException.class);

    // A second job on the same JDBC target must also see enforced FKs
    try (DatabaseDestination dest = new DatabaseDestination(config("child", false))) {
      dest.open();
      assertThatThrownBy(
              () -> {
                dest.write(Map.of("id", 2, "parent_id", 54321));
                dest.flush();
              })
          .isInstanceOf(DestinationException.class);
    }
  }

  @Test
  void shouldEmptySelfReferencingTable() throws SQLException {
    exec(
        "INSERT INTO tree (id, parent_id) VALUES (1, NULL)",
        "INSERT INTO tree (id, parent_id) VALUES (2, 1)",
        "INSERT INTO tree (id, parent_id) VALUES (3, 2)");

    try (DatabaseDestination dest = new DatabaseDestination(config("tree", false))) {
      dest.open();
      dest.write(Map.of("id", 10));
      dest.flush();
    }

    assertThat(ints("SELECT id FROM tree")).containsExactly(10);
  }

  @Test
  void shouldRestartIdentityWhenRestartIdentityTrue() throws SQLException {
    prefillIdent();

    runIdentityJob(true);

    assertThat(ints("SELECT id FROM ident ORDER BY id")).containsExactly(1, 2, 3);
  }

  @Test
  void shouldContinueIdentityWhenRestartIdentityFalse() throws SQLException {
    prefillIdent();

    runIdentityJob(false);

    assertThat(ints("SELECT id FROM ident ORDER BY id")).containsExactly(6, 7, 8);
  }

  @Test
  void shouldRestartIdentityOnEmptiedChildTablesWhenRestartIdentityTrue() throws SQLException {
    exec("INSERT INTO parent (id, name) VALUES (1, 'p')");
    for (int i = 0; i < 4; i++) {
      exec("INSERT INTO idchild (parent_id) VALUES (1)");
    }

    try (DatabaseDestination dest = new DatabaseDestination(config("parent", true))) {
      dest.open();
      dest.write(Map.of("id", 5, "name", "new"));
      dest.flush();
    }
    exec("INSERT INTO idchild (parent_id) VALUES (5)");

    assertThat(ints("SELECT id FROM idchild")).containsExactly(1);
  }

  @Test
  void shouldProduceSameContentsWhenReseededTwice() throws SQLException {
    prefillFamily();

    runParentJob();
    List<Integer> first = ints("SELECT id FROM parent ORDER BY id");
    runParentJob();

    assertThat(first).containsExactly(1, 2);
    assertThat(ints("SELECT id FROM parent ORDER BY id")).isEqualTo(first);
    assertThat(ints("SELECT id FROM child")).isEmpty();
  }
}
