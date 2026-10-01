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

/**
 * Commit/rollback visibility semantics of {@link DatabaseDestination}, observed from a second,
 * independent JDBC connection (H2 in-memory, READ_COMMITTED).
 */
@SuppressWarnings("java:S2068") // H2 in-memory test DB — empty password is the default
class DatabaseDestinationTransactionTest {

  private static final String JDBC_URL = "jdbc:h2:mem:txsemantics;DB_CLOSE_DELAY=-1";
  private static final String TABLE = "tx_items";
  private static final String PER_BATCH = "per_batch";
  private static final String PER_JOB = "per_job";
  private static final String AUTO_COMMIT = "auto_commit";

  private Connection observer;

  @BeforeEach
  @SuppressWarnings("java:S2115")
  void setUp() throws SQLException {
    observer = DriverManager.getConnection(JDBC_URL, "sa", ""); // nosemgrep
    observer.setAutoCommit(true);
    try (Statement st = observer.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + TABLE);
      st.execute("CREATE TABLE " + TABLE + " (id INT PRIMARY KEY, name VARCHAR(50) NOT NULL)");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = observer.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + TABLE);
    }
    observer.close();
  }

  private DatabaseDestinationConfig config(String strategy, int batchSize) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(JDBC_URL)
        .username("sa")
        .password("")
        .tableName(TABLE)
        .batchSize(batchSize)
        .transactionStrategy(strategy)
        .build();
  }

  private static Map<String, Object> row(int id, String name) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("name", name);
    return m;
  }

  private List<Integer> visibleIds() throws SQLException {
    List<Integer> ids = new ArrayList<>();
    try (Statement st = observer.createStatement();
        ResultSet rs = st.executeQuery("SELECT id FROM " + TABLE + " ORDER BY id")) {
      while (rs.next()) {
        ids.add(rs.getInt(1));
      }
    }
    return ids;
  }

  // --- visibility ---

  @Test
  void shouldMakeCompletedBatchVisibleBeforeCloseWhenPerBatch() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 3))) {
      dest.open();
      for (int i = 1; i <= 4; i++) {
        dest.write(row(i, "n" + i));
      }

      // batch of 3 filled and committed; the 4th is still buffered in memory
      assertThat(visibleIds()).containsExactly(1, 2, 3);

      dest.flush();
      assertThat(visibleIds()).containsExactly(1, 2, 3, 4);
    }
  }

  @Test
  void shouldKeepPartialBatchInvisibleUntilFlushWhenPerBatch() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 5))) {
      dest.open();
      dest.write(row(1, "a"));
      dest.write(row(2, "b"));

      assertThat(visibleIds()).isEmpty();

      dest.flush();
      assertThat(visibleIds()).containsExactly(1, 2);
    }
  }

  @Test
  void shouldKeepEverythingInvisibleUntilFlushWhenPerJob() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(PER_JOB, 2))) {
      dest.open();
      for (int i = 1; i <= 5; i++) {
        dest.write(row(i, "n" + i));
      }

      // 2 full batches have been executed on the connection but must not be committed
      assertThat(visibleIds()).isEmpty();

      dest.flush();
      assertThat(visibleIds()).containsExactly(1, 2, 3, 4, 5);
    }
  }

  @Test
  void shouldCommitPerJobDataOnCloseWithoutExplicitFlush() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_JOB, 10));
    dest.open();
    dest.write(row(1, "a"));
    assertThat(visibleIds()).isEmpty();

    dest.close();

    assertThat(visibleIds()).containsExactly(1);
  }

  @Test
  void shouldMakeBatchVisibleImmediatelyWhenAutoCommit() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(AUTO_COMMIT, 2))) {
      dest.open();
      dest.write(row(1, "a"));
      dest.write(row(2, "b"));

      assertThat(visibleIds()).containsExactly(1, 2);
    }
  }

  // --- failing batch ---

  @Test
  void shouldKeepEarlierBatchesAndDropFailedBatchWhenPerBatch() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 3));
    dest.open();
    for (int i = 1; i <= 3; i++) {
      dest.write(row(i, "ok" + i)); // batch 1: committed
    }
    dest.write(row(10, "fine"));
    dest.write(row(10, "dup-pk")); // duplicate primary key inside batch 2

    Map<String, Object> nextRow = row(11, "x");
    assertThatThrownBy(() -> dest.write(nextRow))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("Failed to execute batch insert into table: " + TABLE)
        .hasCauseInstanceOf(SQLException.class);

    // the failed batch must not leak, neither now nor after the destination is torn down
    assertThat(visibleIds()).containsExactly(1, 2, 3);
    assertThatThrownBy(dest::close).isInstanceOf(DestinationException.class);
    assertThat(visibleIds()).containsExactly(1, 2, 3);
  }

  @Test
  void shouldRollBackEverythingWhenBatchFailsAndPerJob() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_JOB, 2));
    dest.open();
    dest.write(row(1, "a"));
    dest.write(row(2, "b")); // batch 1 executed, uncommitted
    dest.write(row(3, "c"));

    Map<String, Object> dupRow = row(3, "dup");
    assertThatThrownBy(() -> dest.write(dupRow))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining(TABLE);
    assertThatThrownBy(dest::close).isInstanceOf(DestinationException.class);

    assertThat(visibleIds()).as("per_job is all-or-nothing").isEmpty();
  }

  @Test
  void shouldSurfaceNotNullViolationAsDestinationExceptionWithSqlCause() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 2));
    dest.open();
    dest.write(row(1, "a"));

    Map<String, Object> bad = row(2, "ignored");
    bad.put("name", null);
    assertThatThrownBy(() -> dest.write(bad))
        .isInstanceOf(DestinationException.class)
        .hasRootCauseInstanceOf(SQLException.class)
        .rootCause()
        .hasMessageContaining("NAME");

    assertThat(visibleIds()).isEmpty();
    assertThatThrownBy(dest::close).isInstanceOf(DestinationException.class);
    assertThat(visibleIds()).isEmpty();
  }

  @Test
  void shouldNotCommitFailedBatchRowsWhenFlushedAgainAfterFailure() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 2));
    dest.open();
    dest.write(row(1, "a"));
    Map<String, Object> dup = row(1, "dup");
    assertThatThrownBy(() -> dest.write(dup)).isInstanceOf(DestinationException.class);

    // A retry of the same poisoned batch must fail again, not silently half-commit.
    assertThatThrownBy(dest::flush).isInstanceOf(DestinationException.class);

    assertThat(visibleIds()).isEmpty();
    assertThatThrownBy(dest::close).isInstanceOf(DestinationException.class);
    assertThat(visibleIds()).isEmpty();
  }

  // --- lifecycle ---

  @Test
  void shouldTolerateDoubleClose() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10));
    dest.open();
    dest.write(row(1, "a"));

    dest.close();
    dest.close();

    assertThat(visibleIds()).containsExactly(1);
  }

  @Test
  void shouldRejectWriteAfterClose() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10));
    dest.open();
    dest.write(row(1, "a"));
    dest.close();

    Map<String, Object> nextRecord = row(2, "b");
    assertThatThrownBy(() -> dest.write(nextRecord))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("not open");
    assertThat(visibleIds()).containsExactly(1);
  }

  @Test
  void shouldIgnoreFlushAfterCloseWithoutWritingBufferedRows() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10));
    dest.open();
    dest.write(row(1, "a"));
    dest.close();

    dest.flush();

    assertThat(visibleIds()).containsExactly(1);
  }

  @Test
  void shouldNotLoseDataOrDuplicateWhenOpenedTwice() throws SQLException {
    try (DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10))) {
      dest.open();
      dest.write(row(1, "a"));
      dest.open(); // warns and returns; must not reset state
      dest.write(row(2, "b"));
      dest.flush();

      assertThat(visibleIds()).containsExactly(1, 2);
    }
  }

  // --- identifier validation ---

  @Test
  void shouldRejectInjectionInTableName() throws SQLException {
    DatabaseDestinationConfig cfg =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username("sa")
            .password("")
            .tableName("users; DROP TABLE " + TABLE)
            .build();

    DatabaseDestination dest = new DatabaseDestination(cfg);
    dest.open();
    Map<String, Object> testRow = row(1, "a");
    assertThatThrownBy(() -> dest.write(testRow))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("illegal characters")
        .hasMessageContaining("DROP TABLE");
    dest.close();

    assertThat(visibleIds()).isEmpty(); // table still exists and is queryable
  }

  @Test
  void shouldRejectQuoteInColumnName() {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10));
    dest.open();
    Map<String, Object> bad = new LinkedHashMap<>();
    bad.put("a\"b", 1);

    assertThatThrownBy(() -> dest.write(bad))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("illegal characters")
        .hasMessageContaining("a\"b");
    dest.close();
  }

  @Test
  void shouldRejectInjectionInColumnName() throws SQLException {
    DatabaseDestination dest = new DatabaseDestination(config(PER_BATCH, 10));
    dest.open();
    Map<String, Object> bad = new LinkedHashMap<>();
    bad.put("id) VALUES (1); DROP TABLE " + TABLE + "; --", 1);

    assertThatThrownBy(() -> dest.write(bad)).isInstanceOf(DestinationException.class);
    dest.close();

    assertThat(visibleIds()).isEmpty();
  }

  @Test
  void shouldRejectUnsafeIdentifiers() {
    for (String bad :
        List.of("", "1abc", "a b", "a-b", "a;b", "a'b", "a\"b", "tbl.col", "x\n", "über")) {
      assertThatThrownBy(() -> DatabaseDestination.validateIdentifier(bad))
          .as("identifier '%s'", bad)
          .isInstanceOf(DestinationException.class);
    }
    assertThatThrownBy(() -> DatabaseDestination.validateIdentifier(null))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void shouldAcceptSafeIdentifiersUnchanged() {
    for (String ok : List.of("users", "_x", "A1_b$c", "T")) {
      assertThat(DatabaseDestination.validateIdentifier(ok)).isEqualTo(ok);
    }
  }
}
