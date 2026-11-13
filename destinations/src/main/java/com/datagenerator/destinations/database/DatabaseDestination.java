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

import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.destinations.AbstractDestination;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.destinations.retry.RetryPolicy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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

  /** Vendor identifier quote and metadata, read lazily once from the connection metadata. */
  private String vendorQuote;

  /** Lower-cased words from the vendor's getSQLKeywords() list. */
  private Set<String> vendorKeywords;

  private boolean storesLowerCase;
  private boolean storesUpperCase;

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

  /**
   * The failure of a flat-mode batch insert, once one has happened. Later {@link #flush()} and
   * {@link #close()} calls rethrow it instead of running the failed batch again: with {@code
   * auto_commit} the batch's good rows are already committed, so a re-run would duplicate them.
   */
  private DestinationException batchFailure;

  // --- Common state ---
  private long totalInserted = 0;

  /**
   * Tables already truncated in this run. Guards {@link #truncateIfNeeded(String)} so each table is
   * emptied at most once, before its first insert. Only populated when {@code
   * truncate_before_insert} is enabled.
   */
  private final Set<String> truncatedTables = new HashSet<>();

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
    validateTruncateDialect();
    retryPolicy.execute(
        "open database connection to " + JdbcUrlRedactor.redactJdbcCredentials(config.getJdbcUrl()),
        this::openConnection);
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

      // MySQL converts TIMESTAMP columns on write using the connection's session time_zone, which
      // defaults to SYSTEM — the MySQL server host's own OS zone, not necessarily UTC — so the same
      // seed stored different instants depending on how the target server was configured (issue
      // #218). Pin the session zone to UTC on every pooled connection so the stored instant depends
      // only on the value, not the server. Offset '+00:00' needs no server time-zone tables. Only
      // MySQL applies this session conversion.
      if (config.getJdbcUrl() != null && config.getJdbcUrl().startsWith("jdbc:mysql:")) {
        hikariConfig.setConnectionInitSql("SET time_zone = '+00:00'");
      }

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
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      e -> typeParser.parse(e.getValue()),
                      (a, b) -> a,
                      LinkedHashMap::new));
      log.debug("Built field schema with {} typed fields", schema.size());

      // Decide flat-vs-nested from the declared schema up front, rather than from the first
      // written record's runtime shape. A field such as `array[object[..], 0..N]` can legally
      // emit an empty list for the first record (stripped by stripEmptyLists()), which would
      // otherwise cause runtime detection to lock flat mode and crash on a later, populated
      // record (issue #283).
      if (hasNestedFieldInSchema(schema)) {
        nestedMode = true;
        decomposer = new NestedRecordDecomposer(config.isInjectParentFk());
        log.info(
            "Nested schema detected — switching to multi-table decomposition mode (table={})",
            config.getTableName());
      }
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

    // Auto-detect nested mode from the first record's runtime shape, but only when no schema
    // was supplied at construction — a schema-driven decision was already made in open() and
    // must not be overridden or re-derived from a single record's values (issue #283).
    if (schema == null && columnNames == null && !nestedMode && hasNestedFields(effectiveRecord)) {
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
      // open() may have allocated a connection/pool before failing prior to setting isOpen —
      // release any such partially-opened resources. closeQuietly() is null-safe.
      closeQuietly();
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
      value = {"SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING", "SQL_INJECTION_JDBC"},
      justification =
          "Table name and column names go through quoteTable()/quoteColumn(): an allowlist of "
              + "letters, digits and _ $ # @ - space rejects everything else (quotes, ';', '\\', "
              + "control characters, ...) before this point; names are never escaped")
  private void initializeStatement(Map<String, Object> firstRecord) {
    truncateIfNeeded(config.getTableName());
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
    if (batchFailure != null) {
      throw new DestinationException(
          "An earlier batch insert into table "
              + config.getTableName()
              + " failed; nothing more is written or committed",
          batchFailure);
    }
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
      batchFailure =
          new DestinationException(
              "Failed to execute batch insert into table: " + config.getTableName(), e);
      batch.clear();
      discardFailedBatch();
      throw batchFailure;
    }
  }

  /** Drops the statement's pending batch and rolls back what the failed batch left uncommitted. */
  private void discardFailedBatch() {
    try {
      insertStatement.clearBatch();
      if (!STRATEGY_AUTO_COMMIT.equals(config.getTransactionStrategy())) {
        connection.rollback();
      }
    } catch (SQLException e) {
      log.warn("Could not roll back after a failed batch: {}", e.getMessage());
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

  /**
   * Declared-schema counterpart of {@link #hasNestedFields(Map)}. Returns {@code true} if any field
   * is a nested {@code object[...]} or an {@code array[object[...], ...]}, regardless of what any
   * single record's runtime values happen to contain (issue #283).
   */
  private static boolean hasNestedFieldInSchema(Map<String, DataType> schema) {
    return schema.values().stream().anyMatch(DatabaseDestination::isNestedType);
  }

  private static boolean isNestedType(DataType type) {
    if (type instanceof ObjectType) {
      return true;
    }
    return type instanceof ArrayType arrayType && arrayType.getElementType() instanceof ObjectType;
  }

  /**
   * A null {@code object[...]} / {@code array[object[...]]} field (nullable) has no child row, so
   * it must not reach the decomposer as a column of the parent. Uses the declared root schema, so
   * only the root record's own nested fields are covered (child records' nested fields are not).
   */
  private Map<String, Object> withoutNullChildren(Map<String, Object> data) {
    if (schema == null) {
      return data;
    }
    Map<String, Object> cleaned = null;
    for (Map.Entry<String, Object> e : data.entrySet()) {
      DataType declared = schema.get(e.getKey());
      if (e.getValue() == null && declared != null && isNestedType(declared)) {
        if (cleaned == null) {
          cleaned = new LinkedHashMap<>(data);
        }
        cleaned.remove(e.getKey());
      }
    }
    return cleaned == null ? data : cleaned;
  }

  private void writeNested(Map<String, Object> data) {
    List<NestedRecordDecomposer.TableRecord> tableRecords =
        decomposer.decompose(withoutNullChildren(data), config.getTableName(), null);

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
  }) // identifiers are allowlist-validated, then quoted when needed, by quoteTable()/quoteColumn()
  @SuppressFBWarnings(
      value = {
        "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
        "SQL_INJECTION_JDBC",
        "OBL_UNSATISFIED_OBLIGATION"
      },
      justification =
          "Table name and column names go through quoteTable()/quoteColumn() (allowlist, never "
              + "escaping); PreparedStatement is stored in nestedStatements immediately after creation — "
              + "no resource leak path exists")
  private void initNestedStatementIfAbsent(String tableName, Map<String, Object> firstRecord) {
    truncateIfNeeded(tableName);
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
   * SQL words reserved by every vendor: always quoted verbatim. Vendor keywords come from metadata.
   */
  private static final Set<String> FIXED_RESERVED_WORDS =
      Set.of(
          ("order,group,user,select,table,from,where,insert,update,delete,create,drop,alter,join,"
                  + "on,and,or,not,null,by,as,in,into,values,set,union,having,distinct,grant,"
                  + "primary,foreign,references,check,default,constraint")
              .split(","));

  /** A plain identifier: the only form emitted unquoted, exactly as before #446. */
  private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");

  /** Allowlist for one identifier part. Excludes every quote, ';', '.', '\\' and control char. */
  private static final Pattern SAFE_IDENTIFIER_PART =
      Pattern.compile("[\\p{L}\\p{N}\\p{M}_$#@ -]+");

  /**
   * Quotes a possibly schema-qualified table name (1 to 3 dot-separated parts), each part per
   * {@link #quotePart}.
   */
  private String quoteTable(String name) {
    if (name == null || name.isEmpty()) {
      throw new DestinationException("SQL identifier must not be null or empty");
    }
    String[] parts = name.split("\\.", -1);
    if (parts.length > 3) {
      throw illegal(name);
    }
    return Arrays.stream(parts).map(part -> quotePart(part, name)).collect(Collectors.joining("."));
  }

  /** Quotes a column name; a dot is not allowed (a field is not a qualified name). */
  private String quoteColumn(String name) {
    if (name == null || name.isEmpty()) {
      throw new DestinationException("SQL identifier must not be null or empty");
    }
    return quotePart(name, name);
  }

  /**
   * Allowlist check, then: a plain part stays unquoted unless it is a fixed reserved word or a
   * vendor keyword (a vendor keyword is folded to the database's stored case, see {@link #fold});
   * any other part is quoted verbatim. Quote characters are never allowed in a part, so nothing is
   * escaped.
   */
  private String quotePart(String part, String fullName) {
    if (part.isEmpty()
        || !SAFE_IDENTIFIER_PART.matcher(part).matches()
        || part.startsWith(" ")
        || part.endsWith(" ")) {
      throw illegal(part.isEmpty() ? fullName : part);
    }
    loadVendorMetadata();
    String lower = part.toLowerCase(Locale.ROOT);
    String text = part;
    if (PLAIN_IDENTIFIER.matcher(part).matches() && !config.isQuoteIdentifiers()) {
      boolean fixed = FIXED_RESERVED_WORDS.contains(lower);
      if (!fixed && !vendorKeywords.contains(lower)) {
        return part;
      }
      // Without a quote string a vendor keyword can only go out unquoted, as it works today.
      if (!fixed && vendorQuote.isEmpty()) {
        return part;
      }
      if (!fixed) {
        text = fold(part);
      }
    }
    if (vendorQuote.isEmpty()) {
      throw new DestinationException(
          "Identifier '"
              + part
              + "' needs quoting but the JDBC driver reports no identifier quote string");
    }
    return vendorQuote + text + vendorQuote;
  }

  /** Case the database stores unquoted names in; a vendor keyword is quoted in that case. */
  private String fold(String part) {
    // Quoted names are case-sensitive: match the case an unquoted name would fold to.
    if (storesLowerCase) {
      return part.toLowerCase(Locale.ROOT);
    }
    return storesUpperCase ? part.toUpperCase(Locale.ROOT) : part;
  }

  private static DestinationException illegal(String name) {
    return new DestinationException(
        "SQL identifier contains illegal characters (allowed: letters, digits, _ $ # @ - and inner"
            + " spaces; tables may be schema-qualified with up to 3 dot-separated parts): '"
            + name
            + "'");
  }

  /**
   * Reads the quote string, keywords and case storage once. Each call is read on its own: one that
   * fails falls back to a safe default and is logged, so the others still apply.
   */
  private void loadVendorMetadata() {
    if (vendorQuote != null) {
      return;
    }
    DatabaseMetaData meta = readOr("database metadata", null, connection::getMetaData);
    String quote = "";
    String keywordList = "";
    boolean lower = false;
    boolean upper = false;
    if (meta != null) {
      // Objects.toString: stubbed or minimal drivers may return null
      quote =
          readOr(
                  "identifier quote string",
                  "",
                  () -> Objects.toString(meta.getIdentifierQuoteString(), ""))
              .strip();
      keywordList = readOr("SQL keywords", "", () -> Objects.toString(meta.getSQLKeywords(), ""));
      lower = readOr("lower-case storage", false, meta::storesLowerCaseIdentifiers);
      upper = readOr("upper-case storage", false, meta::storesUpperCaseIdentifiers);
    }
    vendorKeywords =
        Arrays.stream(keywordList.split(","))
            .map(w -> w.strip().toLowerCase(Locale.ROOT))
            .filter(w -> !w.isEmpty())
            .collect(Collectors.toSet());
    storesLowerCase = lower;
    storesUpperCase = upper;
    vendorQuote = quote;
  }

  /** A JDBC metadata call that may throw. */
  @FunctionalInterface
  private interface MetadataRead<T> {
    T read() throws SQLException;
  }

  /**
   * Runs one metadata call; on SQLException logs what could not be read and returns the fallback.
   */
  private static <T> T readOr(String what, T fallback, MetadataRead<T> read) {
    try {
      return read.read();
    } catch (SQLException e) {
      log.warn(
          "Could not read {} from the JDBC driver, using a safe default: {}", what, e.getMessage());
      return fallback;
    }
  }

  /**
   * Empties {@code tableName} and every table that references it (transitively) before its first
   * insert, when {@code truncate_before_insert} is enabled. Idempotent per run via {@link
   * #truncatedTables}. Same meaning on every vendor; the mechanism differs:
   *
   * <ul>
   *   <li>PostgreSQL / Oracle: {@code TRUNCATE TABLE ... CASCADE}, with {@code restart_identity}
   *       {@code TRUNCATE TABLE ... RESTART IDENTITY CASCADE} (PostgreSQL only). TRUNCATE
   *       participates in the active transaction on PostgreSQL.
   *   <li>MySQL / MariaDB: foreign-key checks are switched off for the duration, the dependents are
   *       found through {@link DatabaseMetaData#getExportedKeys}, and each table gets {@code
   *       TRUNCATE TABLE} (with {@code restart_identity}, resetting {@code AUTO_INCREMENT}) or
   *       {@code DELETE FROM} (keeping it). TRUNCATE is DDL on MySQL: it commits implicitly.
   *   <li>SQL Server: no CASCADE exists, so the dependents are {@code DELETE}d children first; a
   *       foreign-key cycle between distinct tables is rejected. With {@code restart_identity} each
   *       used identity column is reseeded so the next row gets the identity seed.
   * </ul>
   *
   * The dependents are discovered at truncate time (first write), not in {@link #open()}.
   */
  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(
      value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
      justification = IDENTIFIERS_ALLOWLISTED)
  private void truncateIfNeeded(String tableName) {
    if (!config.isTruncateBeforeInsert() || truncatedTables.contains(tableName)) {
      return;
    }
    String safeTable = quoteTable(tableName);
    String url = config.getJdbcUrl();
    try {
      if (isMySql(url)) {
        truncateMySql(tableName);
      } else if (isSqlServer(url)) {
        truncateSqlServer(tableName);
      } else {
        String identityClause = config.isRestartIdentity() ? " RESTART IDENTITY" : "";
        execute("TRUNCATE TABLE " + safeTable + identityClause + " CASCADE");
      }
      truncatedTables.add(tableName);
      log.warn(
          "Truncated table '{}' before insert (truncate_before_insert=true, restart_identity={})",
          tableName,
          config.isRestartIdentity());
    } catch (SQLException e) {
      throw new DestinationException("Failed to truncate table: " + tableName, e);
    }
  }

  private static final String IDENTIFIERS_ALLOWLISTED =
      "Every identifier in the SQL went through quoteTable()/quotePart(): an allowlist of letters, "
          + "digits and _ $ # @ - space rejects everything else; names are never escaped";

  private static boolean isMySql(String url) {
    return url != null && (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:"));
  }

  private static boolean isSqlServer(String url) {
    return url != null && url.startsWith("jdbc:sqlserver:");
  }

  /** A table as reported by (or resolved for) JDBC metadata; catalog and schema may be null. */
  private record TableRef(String catalog, String schema, String name) {}

  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = IDENTIFIERS_ALLOWLISTED)
  private void execute(String sql) throws SQLException {
    try (Statement st = connection.createStatement()) {
      st.executeUpdate(sql); // nosemgrep
    }
  }

  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(
      value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
      justification = IDENTIFIERS_ALLOWLISTED)
  private void truncateMySql(String tableName) throws SQLException {
    Map<String, TableRef> tables = new LinkedHashMap<>();
    collectDependents(resolveTable(tableName, true), tables, new HashMap<>(), true);
    execute("SET FOREIGN_KEY_CHECKS=0");
    try {
      for (TableRef t : tables.values()) {
        String sql = config.isRestartIdentity() ? "TRUNCATE TABLE " : "DELETE FROM ";
        execute(sql + quoteRef(t, true));
      }
    } finally {
      execute("SET FOREIGN_KEY_CHECKS=1");
    }
  }

  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(
      value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
      justification = IDENTIFIERS_ALLOWLISTED)
  private void truncateSqlServer(String tableName) throws SQLException {
    Map<String, TableRef> tables = new LinkedHashMap<>();
    Map<String, Set<String>> children = new HashMap<>();
    collectDependents(resolveTable(tableName, false), tables, children, false);
    // Post-order over the dependents graph: every table comes after all tables referencing it.
    List<String> order = new ArrayList<>();
    Map<String, Boolean> done = new HashMap<>(); // false = in progress, true = finished
    for (String key : tables.keySet()) {
      orderChildrenFirst(key, tables, children, done, new ArrayList<>(), order, tableName);
    }
    for (String key : order) {
      execute("DELETE FROM " + quoteRef(tables.get(key), false));
    }
    if (config.isRestartIdentity()) {
      for (String key : order) {
        reseedIdentity(quoteRef(tables.get(key), false));
      }
    }
  }

  private void orderChildrenFirst(
      String key,
      Map<String, TableRef> tables,
      Map<String, Set<String>> children,
      Map<String, Boolean> done,
      List<String> path,
      List<String> order,
      String tableName) {
    Boolean state = done.get(key);
    if (Boolean.TRUE.equals(state)) {
      return;
    }
    if (Boolean.FALSE.equals(state)) {
      String cycle =
          path.subList(path.indexOf(key), path.size()).stream()
              .map(k -> tables.get(k).name())
              .collect(Collectors.joining(", "));
      throw new DestinationException(
          "Failed to truncate table: "
              + tableName
              + ". Foreign keys form a cycle between tables ["
              + cycle
              + "]: SQL Server cannot empty them in one pass. Empty these tables out of band.");
    }
    done.put(key, false);
    path.add(key);
    for (String child : children.getOrDefault(key, Set.of())) {
      orderChildrenFirst(child, tables, children, done, path, order, tableName);
    }
    path.remove(path.size() - 1);
    done.put(key, true);
    order.add(key);
  }

  /**
   * Reseeds the identity column of {@code safeTable}, if it has one that was ever used, so the next
   * row gets the identity seed. {@code RESEED 0} blindly would hand out 0 on a never-used table,
   * hence the {@code last_value IS NULL} check and {@code seed - increment}.
   */
  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings(
      value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
      justification = IDENTIFIERS_ALLOWLISTED)
  private void reseedIdentity(String safeTable) throws SQLException {
    long reseedTo;
    try (PreparedStatement ps =
        connection.prepareStatement(
            "SELECT CAST(seed_value AS BIGINT) - CAST(increment_value AS BIGINT) "
                + "FROM sys.identity_columns WHERE object_id = OBJECT_ID(?) "
                + "AND last_value IS NOT NULL")) {
      ps.setString(1, safeTable);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return; // no identity column, or never used: the next row already gets the seed
        }
        reseedTo = rs.getLong(1);
      }
    }
    // safeTable is allowlisted and contains no single quote
    execute("DBCC CHECKIDENT ('" + safeTable + "', RESEED, " + reseedTo + ")");
  }

  /** Maps a (possibly qualified) table name to catalog/schema/name for the vendor. */
  private TableRef resolveTable(String tableName, boolean mysql) throws SQLException {
    String[] p = tableName.split("\\.", -1);
    if (mysql) {
      return switch (p.length) {
        case 1 -> new TableRef(connection.getCatalog(), null, p[0]);
        case 2 -> new TableRef(p[0], null, p[1]);
        default -> throw illegal(tableName);
      };
    }
    String defaultSchema = connection.getSchema() == null ? "dbo" : connection.getSchema();
    return switch (p.length) {
      case 1 -> new TableRef(connection.getCatalog(), defaultSchema, p[0]);
      case 2 -> new TableRef(connection.getCatalog(), p[0], p[1]);
      default -> new TableRef(p[0], p[1], p[2]);
    };
  }

  /** Quoted, qualified name; every part goes through the {@link #quotePart} allowlist. */
  private String quoteRef(TableRef t, boolean mysql) {
    // MySQL: database.table. SQL Server: database.schema.table, so a table qualified with another
    // database is never confused with a same-named table in the connection's database.
    List<String> parts = new ArrayList<>();
    if (t.catalog() != null) {
      parts.add(t.catalog());
    }
    if (!mysql && t.schema() != null) {
      parts.add(t.schema());
    }
    parts.add(t.name());
    return parts.stream().map(p -> quotePart(p, t.name())).collect(Collectors.joining("."));
  }

  /**
   * Breadth-first walk of {@link DatabaseMetaData#getExportedKeys} from {@code target}: fills
   * {@code tables} with the target and everything that references it, transitively, and {@code
   * children} with the referencing tables of each. A self-reference is only a visited table. SQL
   * Server names are case-insensitive, so its keys are lower-cased.
   */
  private void collectDependents(
      TableRef target,
      Map<String, TableRef> tables,
      Map<String, Set<String>> children,
      boolean mysql)
      throws SQLException {
    DatabaseMetaData md = connection.getMetaData();
    List<TableRef> queue = new ArrayList<>(List.of(target));
    tables.put(refKey(target, mysql), target);
    for (int i = 0; i < queue.size(); i++) {
      TableRef parent = queue.get(i);
      String parentKey = refKey(parent, mysql);
      try (ResultSet rs =
          md.getExportedKeys(parent.catalog(), mysql ? null : parent.schema(), parent.name())) {
        while (rs.next()) {
          TableRef child =
              new TableRef(
                  rs.getString("FKTABLE_CAT"),
                  rs.getString("FKTABLE_SCHEM"),
                  rs.getString("FKTABLE_NAME"));
          String childKey = refKey(child, mysql);
          if (!childKey.equals(parentKey)) {
            children.computeIfAbsent(parentKey, k -> new LinkedHashSet<>()).add(childKey);
          }
          if (tables.putIfAbsent(childKey, child) == null) {
            queue.add(child);
          }
        }
      }
    }
  }

  private static String refKey(TableRef t, boolean mysql) {
    String key = t.catalog() + "." + t.schema() + "." + t.name();
    return mysql ? key : key.toLowerCase(Locale.ROOT);
  }

  private String buildInsertSql(String tableName, List<String> columns) {
    String safeTable = quoteTable(tableName);
    String cols = columns.stream().map(this::quoteColumn).collect(Collectors.joining(", "));
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

  /**
   * Fail fast when {@code truncate_before_insert} is requested against a dialect {@link
   * #truncateIfNeeded(String)} cannot empty: supported are PostgreSQL, Oracle, MySQL/MariaDB and
   * SQL Server. {@code restart_identity} is rejected on Oracle (no such TRUNCATE clause); on the
   * others it restarts identity/serial/AUTO_INCREMENT. Rejecting here beats failing at first
   * insert.
   */
  private void validateTruncateDialect() {
    if (!config.isTruncateBeforeInsert()) {
      return;
    }
    String url = config.getJdbcUrl();
    if (url == null) {
      return; // openConnection() will fail on the missing URL with a clearer message.
    }
    boolean oracle = url.startsWith("jdbc:oracle:");
    if (!oracle && !url.startsWith("jdbc:postgresql:") && !isMySql(url) && !isSqlServer(url)) {
      throw new DestinationException(
          "truncate_before_insert is supported on PostgreSQL, MySQL/MariaDB, SQL Server and Oracle. "
              + "JDBC URL '"
              + JdbcUrlRedactor.redactJdbcCredentials(url)
              + "' targets an unsupported dialect. Remove truncate_before_insert or truncate the "
              + "table out of band.");
    }
    if (config.isRestartIdentity() && oracle) {
      throw new DestinationException(
          "restart_identity is not supported on Oracle: 'TRUNCATE TABLE ... RESTART IDENTITY' is "
              + "PostgreSQL only (MySQL and SQL Server restart identity natively). JDBC URL '"
              + JdbcUrlRedactor.redactJdbcCredentials(url)
              + "' is Oracle. Disable restart_identity.");
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
