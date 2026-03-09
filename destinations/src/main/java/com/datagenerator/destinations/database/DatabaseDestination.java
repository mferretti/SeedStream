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

import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.DestinationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes generated records to a relational database via JDBC using batch inserts.
 *
 * <p><b>Stage 1 limitations:</b>
 *
 * <ul>
 *   <li>Flat structures only — nested objects and arrays are rejected at write time
 *   <li>Table must already exist (no DDL generation)
 *   <li>Column names are derived from record field names; aliases are already resolved by
 *       generators
 * </ul>
 *
 * <p><b>Architecture:</b>
 *
 * <ol>
 *   <li>{@link #open()} — initialises HikariCP pool and acquires a long-lived connection
 *   <li>{@link #write(Map)} — validates record, accumulates batch; flushes when batch is full
 *   <li>{@link #flush()} — executes remaining batch and commits per transaction strategy
 *   <li>{@link #close()} — flushes, closes statement, connection, and pool
 * </ol>
 *
 * <p><b>INSERT statement:</b> Built from the first record's key set ({@link
 * java.util.LinkedHashMap} order preserved by generators) and reused for all subsequent records in
 * the job.
 *
 * <p><b>Transaction strategies:</b>
 *
 * <ul>
 *   <li>{@code per_batch} — commits after each full batch (default)
 *   <li>{@code per_job} — single commit at end of job (all-or-nothing)
 *   <li>{@code auto_commit} — JDBC auto-commit, no explicit transaction management
 * </ul>
 *
 * <p><b>Thread safety:</b> Not thread-safe. The {@link
 * com.datagenerator.core.engine.GenerationEngine} uses a single writer thread, so this is safe in
 * normal usage.
 */
@Slf4j
public class DatabaseDestination implements DestinationAdapter {

  private static final String STRATEGY_PER_BATCH = "per_batch";
  private static final String STRATEGY_PER_JOB = "per_job";
  private static final String STRATEGY_AUTO_COMMIT = "auto_commit";

  private final DatabaseDestinationConfig config;

  private HikariDataSource dataSource;
  private Connection connection;
  private PreparedStatement insertStatement;
  private List<String> columnNames;
  private final List<Map<String, Object>> batch;
  private long totalInserted = 0;
  private boolean isOpen = false;

  /**
   * Create a database destination with the given configuration.
   *
   * @param config connection and batch settings
   */
  public DatabaseDestination(DatabaseDestinationConfig config) {
    this.config = config;
    this.batch = new ArrayList<>(config.getBatchSize());
  }

  @Override
  public void open() {
    if (isOpen) {
      log.warn("Database destination already open");
      return;
    }

    validateTransactionStrategy(config.getTransactionStrategy());

    try {
      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setJdbcUrl(config.getJdbcUrl());
      hikariConfig.setUsername(config.getUsername());
      hikariConfig.setPassword(config.getPassword());
      hikariConfig.setMaximumPoolSize(config.getPoolSize());
      // Let HikariCP manage auto-commit based on strategy
      hikariConfig.setAutoCommit(STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy()));

      dataSource = new HikariDataSource(hikariConfig);
      connection = dataSource.getConnection();

      if (!STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy())) {
        connection.setAutoCommit(false);
      }

      isOpen = true;
      log.info(
          "Opened database destination: table={}, strategy={}, batchSize={}",
          config.getTableName(),
          config.getTransactionStrategy(),
          config.getBatchSize());

    } catch (SQLException e) {
      throw new DestinationException(
          "Failed to open database connection to: " + config.getJdbcUrl(), e);
    }
  }

  @Override
  public void write(Map<String, Object> record) {
    if (!isOpen) {
      throw new DestinationException("Database destination not open. Call open() first.");
    }

    validateFlatRecord(record);

    if (columnNames == null) {
      initializeStatement(record);
    }

    batch.add(record);

    if (batch.size() >= config.getBatchSize()) {
      flushBatch();
    }
  }

  @Override
  public void flush() {
    if (!isOpen) {
      log.warn("Cannot flush: database destination not open");
      return;
    }

    flushBatch();

    if (STRATEGY_PER_JOB.equals(config.getTransactionStrategy())) {
      try {
        connection.commit();
        log.debug("Committed per-job transaction ({} total records)", totalInserted);
      } catch (SQLException e) {
        throw new DestinationException("Failed to commit per-job transaction", e);
      }
    }
  }

  @Override
  public void close() {
    if (!isOpen) {
      log.debug("Database destination already closed");
      return;
    }

    try {
      flush();
    } finally {
      closeQuietly();
    }

    log.info(
        "Closed database destination: table={}, totalInserted={}",
        config.getTableName(),
        totalInserted);
  }

  @Override
  public String getDestinationType() {
    return "database";
  }

  // --- Private helpers ---

  private void validateTransactionStrategy(String strategy) {
    if (!STRATEGY_PER_BATCH.equals(strategy)
        && !STRATEGY_PER_JOB.equals(strategy)
        && !STRATEGY_AUTO_COMMIT.equals(strategy)) {
      throw new DestinationException(
          "Unknown transaction_strategy '"
              + strategy
              + "'. Valid values: per_batch, per_job, auto_commit");
    }
  }

  private void validateFlatRecord(Map<String, Object> record) {
    for (Map.Entry<String, Object> entry : record.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map) {
        throw new DestinationException(
            "Database destination (Stage 1) does not support nested objects. "
                + "Field '"
                + entry.getKey()
                + "' contains a nested object. Use flat structures only.");
      }
      if (value instanceof List) {
        throw new DestinationException(
            "Database destination (Stage 1) does not support arrays. "
                + "Field '"
                + entry.getKey()
                + "' contains an array. Use flat structures only.");
      }
    }
  }

  private void initializeStatement(Map<String, Object> firstRecord) {
    columnNames = new ArrayList<>(firstRecord.keySet());

    String columns = String.join(", ", columnNames);
    String placeholders = columnNames.stream().map(c -> "?").collect(Collectors.joining(", "));
    String sql =
        "INSERT INTO " + config.getTableName() + " (" + columns + ") VALUES (" + placeholders + ")";

    try {
      insertStatement = connection.prepareStatement(sql);
      log.debug("Prepared INSERT statement: {}", sql);
    } catch (SQLException e) {
      throw new DestinationException("Failed to prepare INSERT statement: " + sql, e);
    }
  }

  private void flushBatch() {
    if (batch.isEmpty()) {
      return;
    }

    try {
      for (Map<String, Object> record : batch) {
        for (int i = 0; i < columnNames.size(); i++) {
          Object value = record.get(columnNames.get(i));
          JdbcTypeMapper.bind(insertStatement, i + 1, value);
        }
        insertStatement.addBatch();
      }

      insertStatement.executeBatch();
      insertStatement.clearBatch();

      if (STRATEGY_PER_BATCH.equals(config.getTransactionStrategy())) {
        connection.commit();
      }

      totalInserted += batch.size();
      log.debug("Flushed batch of {} records (total: {})", batch.size(), totalInserted);
      batch.clear();

    } catch (SQLException e) {
      throw new DestinationException(
          "Failed to execute batch insert into table: " + config.getTableName(), e);
    }
  }

  private void closeQuietly() {
    isOpen = false;
    try {
      if (insertStatement != null) {
        insertStatement.close();
      }
    } catch (SQLException e) {
      log.warn("Failed to close PreparedStatement", e);
    }
    try {
      if (connection != null) {
        connection.close();
      }
    } catch (SQLException e) {
      log.warn("Failed to close Connection", e);
    }
    if (dataSource != null) {
      dataSource.close();
    }
  }
}
