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

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.DestinationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes generated records to a relational database via JDBC using batch inserts.
 *
 * <p><b>Stage 1 — flat structures:</b>
 *
 * <ul>
 *   <li>Flat structures only — nested objects and arrays are silently auto-detected and switch the
 *       destination to Stage 2 nested mode (see below)
 *   <li>Table must already exist (no DDL generation)
 *   <li>Column names are derived from record field names; aliases are already resolved by
 *       generators
 * </ul>
 *
 * <p><b>Stage 2 — nested auto-decomposition:</b>
 *
 * <p>When the first written record contains a nested {@code Map} or {@code List<Map>} field, the
 * destination automatically switches to nested mode. In nested mode:
 *
 * <ul>
 *   <li>Each nested {@code object[X]} field → one child-table INSERT into a table named after the
 *       YAML field key
 *   <li>Each {@code array[object[X], min..max]} field → N child-table INSERTs
 *   <li>FK column {@code {parent_table}_id} is injected into every child record automatically
 *   <li>Inserts are depth-first: parent row is always written before its children
 *   <li>Works recursively at any nesting depth
 * </ul>
 *
 * <p><b>Architecture:</b>
 *
 * <ol>
 *   <li>{@link #open()} — initialises HikariCP pool and acquires a long-lived connection
 *   <li>{@link #write(Map)} — auto-detects flat vs nested on first call; routes accordingly
 *   <li>{@link #flush()} — flushes remaining flat batch; commits per strategy
 *   <li>{@link #close()} — flushes, closes all statements, connection, and pool
 * </ol>
 *
 * <p><b>INSERT statement:</b> Built lazily from the first record's key set. One statement per table
 * in nested mode (cached in {@link #nestedStatements}).
 *
 * <p><b>Transaction strategies:</b>
 *
 * <ul>
 *   <li>{@code per_batch} — commits after each batch (flat: {@code batch_size} records; nested:
 *       {@code batch_size} root records)
 *   <li>{@code per_job} — single commit at end of job (all-or-nothing)
 *   <li>{@code auto_commit} — JDBC auto-commit, no explicit transaction management
 * </ul>
 *
 * <p><b>Schema (Option B):</b> When a raw field-type map ({@code Map<String, String>}) is provided
 * at construction, it is parsed via {@link TypeParser} inside {@link #open()} and used for accurate
 * JDBC type binding in flat mode only. Nested child records always use Option A (runtime {@code
 * instanceof}) binding.
 *
 * <p><b>Thread safety:</b> Not thread-safe. The {@link
 * com.datagenerator.core.engine.GenerationEngine} uses a single writer thread.
 */
@Slf4j
public class DatabaseDestination implements DestinationAdapter {

  private static final String STRATEGY_PER_BATCH = "per_batch";
  private static final String STRATEGY_PER_JOB = "per_job";
  private static final String STRATEGY_AUTO_COMMIT = "auto_commit";

  private final DatabaseDestinationConfig config;

  /**
   * Optional raw YAML type strings for DataType-aware JDBC binding (Option B).
   *
   * <p>When non-null, parsed into {@link #schema} during {@link #open()}. When null, falls back to
   * the instanceof-based Option A.
   */
  private final Map<String, String> rawFieldTypes;

  /** Parsed schema built from {@link #rawFieldTypes} in {@link #open()}. Null for Option A. */
  private Map<String, DataType> schema;

  private HikariDataSource dataSource;
  private Connection connection;

  // --- Flat mode state ---
  private PreparedStatement insertStatement;
  private List<String> columnNames;
  private final List<Map<String, Object>> batch;

  // --- Nested mode state ---
  private boolean nestedMode = false;
  private NestedRecordDecomposer decomposer;

  /**
   * Per-table PreparedStatements, initialised lazily on the first record for each table. Key =
   * table name.
   */
  private final Map<String, PreparedStatement> nestedStatements = new LinkedHashMap<>();

  /**
   * Per-table column name lists, initialised alongside {@link #nestedStatements}. Key = table name.
   */
  private final Map<String, List<String>> nestedColumnNames = new LinkedHashMap<>();

  /**
   * Count of root-level records written in nested mode since the last commit. Used to honour {@code
   * batch_size} for the per_batch transaction strategy.
   */
  private int nestedRecordCount = 0;

  // --- Common state ---
  private long totalInserted = 0;
  private boolean isOpen = false;

  /**
   * Create a database destination with the given configuration (Option A — no schema).
   *
   * @param config connection and batch settings
   */
  public DatabaseDestination(DatabaseDestinationConfig config) {
    this(config, null);
  }

  /**
   * Create a database destination with schema-aware JDBC binding (Option B).
   *
   * <p>The raw field-type map uses the same YAML type syntax as structure definitions (e.g. {@code
   * "int[1..999]"}, {@code "date[2020-01-01..2025-12-31]"}, {@code "enum[M,F,X]"}). It is parsed
   * into a {@code Map<String, DataType>} inside {@link #open()} after the connection is
   * established.
   *
   * <p>Option B binding applies only to flat-mode root fields. Nested child records always use
   * Option A (runtime type inference).
   *
   * @param config connection and batch settings
   * @param rawFieldTypes YAML type strings keyed by field name; null to use Option A
   */
  public DatabaseDestination(DatabaseDestinationConfig config, Map<String, String> rawFieldTypes) {
    this.config = config;
    this.rawFieldTypes = rawFieldTypes;
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
      hikariConfig.setAutoCommit(STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy()));

      dataSource = new HikariDataSource(hikariConfig);
      connection = dataSource.getConnection();

      if (!STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy())) {
        connection.setAutoCommit(false);
      }

      if (rawFieldTypes != null) {
        TypeParser typeParser = new TypeParser();
        schema =
            rawFieldTypes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> typeParser.parse(e.getValue())));
        log.debug("Built field schema with {} typed fields", schema.size());
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

    // Strip empty List fields before routing. An empty list produces no JDBC value (flat mode)
    // and no child records (nested mode). Stripping here prevents the flat-mode validator from
    // rejecting a record that simply has an empty array[object[...]] field.
    Map<String, Object> effectiveRecord = stripEmptyLists(record);

    // Auto-detect nested mode on the first record
    if (columnNames == null && !nestedMode) {
      if (hasNestedFields(effectiveRecord)) {
        nestedMode = true;
        decomposer = new NestedRecordDecomposer();
        log.info(
            "Nested record detected — switching to multi-table decomposition mode (table={})",
            config.getTableName());
      }
    }

    if (nestedMode) {
      writeNested(effectiveRecord);
    } else {
      writeFlatRecord(effectiveRecord);
    }
  }

  @Override
  public void flush() {
    if (!isOpen) {
      log.warn("Cannot flush: database destination not open");
      return;
    }

    if (nestedMode) {
      flushNestedPendingCommit();
    } else {
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

  // --- Private: flat mode ---

  private void writeFlatRecord(Map<String, Object> record) {
    validateFlatRecord(record);

    if (columnNames == null) {
      initializeStatement(record);
    }

    batch.add(record);

    if (batch.size() >= config.getBatchSize()) {
      flushBatch();
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
          String col = columnNames.get(i);
          Object value = record.get(col);
          if (schema != null && schema.containsKey(col)) {
            JdbcTypeMapper.bind(insertStatement, i + 1, value, schema.get(col));
          } else {
            JdbcTypeMapper.bind(insertStatement, i + 1, value);
          }
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

  // --- Private: nested mode ---

  private Map<String, Object> stripEmptyLists(Map<String, Object> record) {
    boolean hasEmpty = false;
    for (Object value : record.values()) {
      if (value instanceof List<?> list && list.isEmpty()) {
        hasEmpty = true;
        break;
      }
    }
    if (!hasEmpty) return record;
    Map<String, Object> cleaned = new LinkedHashMap<>(record);
    cleaned.entrySet().removeIf(e -> e.getValue() instanceof List<?> l && l.isEmpty());
    return cleaned;
  }

  private boolean hasNestedFields(Map<String, Object> record) {
    for (Object value : record.values()) {
      if (value instanceof Map) return true;
      if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map)
        return true;
    }
    return false;
  }

  private void writeNested(Map<String, Object> record) {
    List<NestedRecordDecomposer.TableRecord> tableRecords =
        decomposer.decompose(record, config.getTableName(), null);

    for (NestedRecordDecomposer.TableRecord tr : tableRecords) {
      initNestedStatementIfAbsent(tr.tableName(), tr.fields());
      executeNestedInsert(tr.tableName(), tr.fields());
      totalInserted++;
    }

    nestedRecordCount++;

    if (STRATEGY_PER_BATCH.equals(config.getTransactionStrategy())
        && nestedRecordCount >= config.getBatchSize()) {
      try {
        connection.commit();
        log.debug(
            "Committed per-batch nested transaction ({} root records, {} total rows)",
            nestedRecordCount,
            totalInserted);
        nestedRecordCount = 0;
      } catch (SQLException e) {
        throw new DestinationException("Failed to commit per-batch nested transaction", e);
      }
    }
  }

  private void initNestedStatementIfAbsent(String tableName, Map<String, Object> firstRecord) {
    if (nestedStatements.containsKey(tableName)) return;

    List<String> cols = new ArrayList<>(firstRecord.keySet());
    nestedColumnNames.put(tableName, cols);

    String columns = String.join(", ", cols);
    String placeholders = cols.stream().map(c -> "?").collect(Collectors.joining(", "));
    String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      nestedStatements.put(tableName, ps);
      log.debug("Prepared nested INSERT for table '{}': {}", tableName, sql);
    } catch (SQLException e) {
      throw new DestinationException("Failed to prepare nested INSERT for table: " + tableName, e);
    }
  }

  private void executeNestedInsert(String tableName, Map<String, Object> record) {
    PreparedStatement ps = nestedStatements.get(tableName);
    List<String> cols = nestedColumnNames.get(tableName);

    try {
      for (int i = 0; i < cols.size(); i++) {
        JdbcTypeMapper.bind(ps, i + 1, record.get(cols.get(i)));
      }
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new DestinationException("Failed to insert nested record into table: " + tableName, e);
    }
  }

  private void flushNestedPendingCommit() {
    if (STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy()) || nestedRecordCount == 0) {
      return;
    }
    try {
      connection.commit();
      log.debug(
          "Committed {} remaining nested root records on flush (total rows: {})",
          nestedRecordCount,
          totalInserted);
      nestedRecordCount = 0;
    } catch (SQLException e) {
      throw new DestinationException("Failed to commit remaining nested records on flush", e);
    }
  }

  // --- Private: cleanup ---

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

  private void closeQuietly() {
    isOpen = false;

    // Close flat mode statement
    try {
      if (insertStatement != null) {
        insertStatement.close();
      }
    } catch (SQLException e) {
      log.warn("Failed to close flat PreparedStatement", e);
    }

    // Close nested mode statements
    for (Map.Entry<String, PreparedStatement> entry : nestedStatements.entrySet()) {
      try {
        entry.getValue().close();
      } catch (SQLException e) {
        log.warn("Failed to close nested PreparedStatement for table '{}'", entry.getKey(), e);
      }
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
