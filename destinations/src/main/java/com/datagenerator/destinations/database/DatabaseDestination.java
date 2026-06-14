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
import com.datagenerator.destinations.AbstractDestination;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.destinations.retry.RetryPolicy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
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
 *   <li>Column names are derived from data field names; aliases are already resolved by generators
 * </ul>
 *
 * <p><b>Stage 2 — nested auto-decomposition:</b>
 *
 * <p>When the first written data contains a nested {@code Map} or {@code List<Map>} field, the
 * destination automatically switches to nested mode. In nested mode:
 *
 * <ul>
 *   <li>Each nested {@code object[X]} field → one child-table INSERT into a table named after the
 *       YAML field key
 *   <li>Each {@code array[object[X], min..max]} field → N child-table INSERTs
 *   <li>FK column {@code {parent_table}_id} is injected into every child data automatically
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
 * <p><b>INSERT statement:</b> Built lazily from the first data's key set. One statement per table
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
public class DatabaseDestination extends AbstractDestination {

  private static final String STRATEGY_PER_BATCH = "per_batch";
  private static final String STRATEGY_PER_JOB = "per_job";
  private static final String STRATEGY_AUTO_COMMIT = "auto_commit";

  private final DatabaseDestinationConfig config;
  private final RetryPolicy retryPolicy;

  /**
   * Optional raw YAML type strings for DataType-aware JDBC binding (Option B).
   *
   * <p>When non-null, parsed into {@link #schema} during {@link #open()}. When null, falls back to
   * the instanceof-based Option A.
   */
  private final Map<String, String> rawFieldTypes;

  /**
   * Non-null only when injected via the package-private test constructor. When set, {@code open()}
   * uses this DataSource instead of creating a HikariCP pool.
   */
  private final DataSource injectedDataSource;

  /** Parsed schema built from {@link #rawFieldTypes} in {@link #open()}. Null for Option A. */
  private Map<String, DataType> schema;

  private DataSource dataSource;
  private Connection connection;

  // --- Flat mode state ---
  private PreparedStatement insertStatement;
  private List<String> columnNames;
  private final List<Map<String, Object>> batch;

  // --- Nested mode state ---
  private boolean nestedMode = false;
  private NestedRecordDecomposer decomposer;

  /**
   * Per-table PreparedStatements, initialised lazily on the first data for each table. Key = table
   * name.
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

  /**
   * Create a database destination with the given configuration (Option A — no schema).
   *
   * @param config connection and batch settings
   */
  public DatabaseDestination(DatabaseDestinationConfig cfg) {
    this(cfg, (Map<String, String>) null);
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
  public DatabaseDestination(DatabaseDestinationConfig cfg, Map<String, String> rawFieldTypes) {
    this.config = cfg;
    this.rawFieldTypes =
        rawFieldTypes != null ? Collections.unmodifiableMap(new HashMap<>(rawFieldTypes)) : null;
    this.injectedDataSource = null;
    this.retryPolicy = RetryPolicy.of(cfg.getMaxRetries(), cfg.getRetryDelayMs());
    this.batch = new ArrayList<>(cfg.getBatchSize());
  }

  /** Package-private: injects a DataSource for unit testing (bypasses HikariCP). */
  DatabaseDestination(DatabaseDestinationConfig cfg, DataSource injectedDataSource) {
    this.config = cfg;
    this.rawFieldTypes = null;
    this.injectedDataSource = injectedDataSource;
    this.retryPolicy = RetryPolicy.of(cfg.getMaxRetries(), cfg.getRetryDelayMs());
    this.batch = new ArrayList<>(cfg.getBatchSize());
  }

  @Override
  public void open() {
    if (isOpen) {
      log.warn("Database destination already open");
      return;
    }

    validateTransactionStrategy(config.getTransactionStrategy());
    retryPolicy.execute("open database connection to " + config.getJdbcUrl(), this::openConnection);
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void openConnection() throws SQLException {
    if (injectedDataSource != null) {
      connection = injectedDataSource.getConnection();
    } else {
      // Close any pool left over from a previous failed attempt before creating a new one.
      if (dataSource instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          log.warn("Failed to close stale DataSource before retry", e);
        }
        dataSource = null;
      }

      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setJdbcUrl(config.getJdbcUrl());
      hikariConfig.setUsername(config.getUsername());
      hikariConfig.setPassword(config.getPassword());
      hikariConfig.setMaximumPoolSize(config.getPoolSize());
      hikariConfig.setAutoCommit(STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy()));

      dataSource = new HikariDataSource(hikariConfig);
      connection = dataSource.getConnection();
    }

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
  }

  @Override
  public void write(Map<String, Object> data) {
    requireOpen("Database");

    // Strip empty List fields before routing. An empty list produces no JDBC value (flat mode)
    // and no child records (nested mode). Stripping here prevents the flat-mode validator from
    // rejecting a data that simply has an empty array[object[...]] field.
    Map<String, Object> effectiveRecord = stripEmptyLists(data);

    // Auto-detect nested mode on the first data
    if (columnNames == null && !nestedMode && hasNestedFields(effectiveRecord)) {
      nestedMode = true;
      decomposer = new NestedRecordDecomposer(config.isInjectParentFk());
      log.info(
          "Nested data detected — switching to multi-table decomposition mode (table={})",
          config.getTableName());
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

  private void writeFlatRecord(Map<String, Object> data) {
    validateFlatRecord(data);

    if (columnNames == null) {
      initializeStatement(data);
    }

    batch.add(data);

    if (batch.size() >= config.getBatchSize()) {
      flushBatch();
    }
  }

  private void validateFlatRecord(Map<String, Object> data) {
    for (Map.Entry<String, Object> entry : data.entrySet()) {
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

  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(
      value = "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
      justification =
          "Table name and column names are validated by validateIdentifier(); "
              + "any identifier with non-alphanumeric/underscore chars is rejected before reaching this point")
  private void initializeStatement(Map<String, Object> firstRecord) {
    columnNames = new ArrayList<>(firstRecord.keySet());
    String sql = buildInsertSql(config.getTableName(), columnNames);
    try {
      insertStatement = connection.prepareStatement(sql); // nosemgrep
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
      for (Map<String, Object> data : batch) {
        for (int i = 0; i < columnNames.size(); i++) {
          String col = columnNames.get(i);
          Object value = data.get(col);
          DataType fieldType = schema != null ? schema.get(col) : null;
          if (fieldType != null) {
            JdbcTypeMapper.bind(insertStatement, i + 1, value, fieldType);
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

  private Map<String, Object> stripEmptyLists(Map<String, Object> data) {
    boolean hasEmpty = false;
    for (Object value : data.values()) {
      if (value instanceof List<?> list && list.isEmpty()) {
        hasEmpty = true;
        break;
      }
    }
    if (!hasEmpty) return data;
    Map<String, Object> cleaned = new LinkedHashMap<>(data);
    cleaned.entrySet().removeIf(e -> e.getValue() instanceof List<?> l && l.isEmpty());
    return cleaned;
  }

  private boolean hasNestedFields(Map<String, Object> data) {
    for (Object value : data.values()) {
      if (value instanceof Map) return true;
      if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map)
        return true;
    }
    return false;
  }

  private void writeNested(Map<String, Object> data) {
    List<NestedRecordDecomposer.TableRecord> tableRecords =
        decomposer.decompose(data, config.getTableName(), null);

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

  @SuppressWarnings({
    "SqlSourceToSinkFlow",
    "java:S2077"
  }) // identifiers are ANSI-quoted and validated by quoteIdentifier()
  @SuppressFBWarnings(
      value = {
        "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
        "OBL_UNSATISFIED_OBLIGATION"
      },
      justification =
          "Table name and column names are validated by validateIdentifier(); "
              + "PreparedStatement is stored in nestedStatements immediately after creation — "
              + "no resource leak path exists")
  private void initNestedStatementIfAbsent(String tableName, Map<String, Object> firstRecord) {
    if (nestedStatements.containsKey(tableName)) return;

    List<String> cols = new ArrayList<>(firstRecord.keySet());
    nestedColumnNames.put(tableName, cols);
    String sql = buildInsertSql(tableName, cols);

    try {
      PreparedStatement ps = connection.prepareStatement(sql); // nosemgrep
      nestedStatements.put(tableName, ps);
      log.debug("Prepared nested INSERT for table '{}': {}", tableName, sql);
    } catch (SQLException e) {
      throw new DestinationException("Failed to prepare nested INSERT for table: " + tableName, e);
    }
  }

  /**
   * Validates a SQL identifier against a strict allowlist pattern to prevent SQL injection.
   *
   * <p>Permits only standard SQL identifiers: letters, digits, underscores, and dollar signs,
   * starting with a letter or underscore. Rejects anything else (spaces, quotes, semicolons, etc.)
   * before it can reach the statement builder.
   *
   * @throws DestinationException if the identifier contains unsafe characters
   */
  static String validateIdentifier(String name) {
    if (name == null || name.isEmpty()) {
      throw new DestinationException("SQL identifier must not be null or empty");
    }
    if (!name.matches("[a-zA-Z_][a-zA-Z0-9_$]*")) {
      throw new DestinationException(
          "SQL identifier contains illegal characters (only letters, digits, _ and $ allowed): '"
              + name
              + "'");
    }
    return name;
  }

  private static String buildInsertSql(String tableName, List<String> columns) {
    String safeTable = validateIdentifier(tableName);
    String cols =
        columns.stream()
            .map(DatabaseDestination::validateIdentifier)
            .collect(Collectors.joining(", "));
    String placeholders = "?,".repeat(columns.size());
    placeholders = placeholders.substring(0, placeholders.length() - 1);
    return "INSERT INTO " + safeTable + " (" + cols + ") VALUES (" + placeholders + ")";
  }

  private void executeNestedInsert(String tableName, Map<String, Object> data) {
    PreparedStatement ps = nestedStatements.get(tableName);
    List<String> cols = nestedColumnNames.get(tableName);

    try {
      for (int i = 0; i < cols.size(); i++) {
        JdbcTypeMapper.bind(ps, i + 1, data.get(cols.get(i)));
      }
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new DestinationException("Failed to insert nested data into table: " + tableName, e);
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

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
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
    if (dataSource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        log.warn("Failed to close DataSource", e);
      }
    }
  }
}
