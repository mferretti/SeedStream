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

package com.datagenerator.benchmarks;

import com.datagenerator.destinations.database.DatabaseDestination;
import com.datagenerator.destinations.database.DatabaseDestinationConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for database insert throughput.
 *
 * <p><b>Goal:</b> Quantify the effect of batch size and transaction strategy on INSERT throughput,
 * and measure the overhead of nested auto-decomposition vs flat inserts.
 *
 * <p><b>Scenarios:</b>
 *
 * <ul>
 *   <li>Flat insert — 1 INSERT per {@code write()} call into {@code benchmark_flat}
 *   <li>Nested insert — 3 INSERTs per {@code write()} call (1 parent + 2 children) via {@link
 *       com.datagenerator.destinations.database.NestedRecordDecomposer}
 * </ul>
 *
 * <p><b>Parameters:</b>
 *
 * <ul>
 *   <li>{@code batchSize}: 100 / 500 / 1000 / 5000 — JDBC batch accumulation size
 *   <li>{@code transactionStrategy}: {@code per_batch} (commit at batch boundary) / {@code
 *       auto_commit} (per-statement commit)
 * </ul>
 *
 * <p><b>Note:</b> {@code per_job} strategy is excluded because it keeps a single open transaction
 * across iterations; the TRUNCATE issued at iteration teardown would deadlock against it.
 *
 * <p><b>Prerequisites — Option 1: Docker (Recommended)</b>
 *
 * <pre>
 * docker run -d --name pg-benchmark \
 *   -e POSTGRES_DB=benchmark \
 *   -e POSTGRES_USER=benchmark \
 *   -e POSTGRES_PASSWORD=benchmark \
 *   -p 5432:5432 \
 *   postgres:17-alpine
 * </pre>
 *
 * <p><b>Prerequisites — Option 2: Existing PostgreSQL</b>
 *
 * <pre>
 * psql -U postgres -c "CREATE DATABASE benchmark;"
 * psql -U postgres -c "CREATE USER benchmark WITH PASSWORD 'benchmark';"
 * psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE benchmark TO benchmark;"
 * </pre>
 *
 * <p><b>Run all database benchmarks:</b>
 *
 * <pre>
 * ./gradlew :benchmarks:jmh -Pjmh.includes=".*DatabaseBenchmark.*"
 * </pre>
 *
 * <p><b>Run with custom connection settings:</b>
 *
 * <pre>
 * ./gradlew :benchmarks:jmh \
 *   -Pjmh.includes=".*DatabaseBenchmark.*" \
 *   -Djvm.args="-Ddb.url=jdbc:postgresql://myhost:5432/mydb -Ddb.user=myuser -Ddb.password=secret"
 * </pre>
 *
 * <p><b>Run flat inserts only (skip nested decomposition overhead):</b>
 *
 * <pre>
 * ./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkFlatInsert.*"
 * </pre>
 *
 * <p><b>Skip database benchmarks (when PostgreSQL is unavailable):</b>
 *
 * <pre>
 * ./gradlew :benchmarks:jmh -Pjmh.excludes=".*DatabaseBenchmark.*"
 * </pre>
 *
 * <p><b>Cleanup Docker container:</b>
 *
 * <pre>
 * docker stop pg-benchmark &amp;&amp; docker rm pg-benchmark
 * </pre>
 *
 * <p><b>Expected results (local PostgreSQL on developer hardware):</b>
 *
 * <ul>
 *   <li>Flat, per_batch, batchSize=1000: ~3,000–5,000 records/sec
 *   <li>Flat, auto_commit: ~500–1,500 records/sec (commit-per-record overhead)
 *   <li>Nested, per_batch, batchSize=1000: ~1,200–2,000 records/sec (3× INSERT fanout)
 *   <li>Nested, auto_commit: ~200–600 records/sec
 * </ul>
 *
 * <p>See {@code docs/PERFORMANCE.md} for measured results on reference hardware.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
@State(Scope.Benchmark)
public class DatabaseBenchmark {

  // -----------------------------------------------------------------------
  // Parameters — 4 × 2 = 8 configurations per benchmark method (16 total)
  // -----------------------------------------------------------------------

  @Param({"100", "500", "1000", "5000"})
  private int batchSize;

  @Param({"per_batch", "auto_commit"})
  private String transactionStrategy;

  // -----------------------------------------------------------------------
  // Connection settings (overridable via system properties)
  // -----------------------------------------------------------------------

  private static final String JDBC_URL =
      System.getProperty("db.url", "jdbc:postgresql://localhost:5432/benchmark");
  private static final String DB_USER = System.getProperty("db.user", "benchmark");
  private static final String DB_PASS = System.getProperty("db.password", "benchmark");

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------

  private DatabaseDestination flatDestination;
  private DatabaseDestination nestedDestination;

  /** Pre-built flat record — {@code id} field is overwritten per call. */
  private Map<String, Object> flatRecord;

  /**
   * Pre-built nested record — {@code id} field is overwritten per call; child items are shared and
   * mutated by {@link com.datagenerator.destinations.database.NestedRecordDecomposer} (FK
   * injection).
   */
  private Map<String, Object> nestedRecord;

  private AtomicLong flatIdCounter;
  private AtomicLong nestedIdCounter;

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------

  @Setup(Level.Trial)
  public void setup() throws Exception {
    flatIdCounter = new AtomicLong(0);
    nestedIdCounter = new AtomicLong(0);

    createSchema();

    flatDestination = buildDestination("benchmark_flat");
    flatDestination.open();

    // Nested mode is auto-detected by DatabaseDestination on first write()
    nestedDestination = buildDestination("benchmark_order");
    nestedDestination.open();

    flatRecord = buildFlatRecord();
    nestedRecord = buildNestedRecord();

    System.out.printf(
        "[DB BENCHMARK] Setup complete: batchSize=%d, strategy=%s%n",
        batchSize, transactionStrategy);
  }

  /** Flush pending batches and truncate all benchmark tables between iterations. */
  @TearDown(Level.Iteration)
  public void flushAndTruncate() throws Exception {
    if (flatDestination != null) {
      flatDestination.flush();
    }
    if (nestedDestination != null) {
      nestedDestination.flush();
    }

    try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE order_items");
      stmt.execute("TRUNCATE TABLE benchmark_order");
      stmt.execute("TRUNCATE TABLE benchmark_flat");
    }

    flatIdCounter.set(0);
    nestedIdCounter.set(0);
  }

  @TearDown(Level.Trial)
  public void tearDown() throws Exception {
    closeQuietly(flatDestination, "flat");
    closeQuietly(nestedDestination, "nested");
    dropSchema();

    System.out.printf(
        "[DB BENCHMARK] Teardown complete: batchSize=%d, strategy=%s%n",
        batchSize, transactionStrategy);
  }

  // -----------------------------------------------------------------------
  // Benchmarks
  // -----------------------------------------------------------------------

  /**
   * Flat insert — 1 INSERT per call into {@code benchmark_flat}.
   *
   * <p>Measures raw INSERT throughput with JDBC batching and the configured transaction strategy.
   * Use this as the baseline for comparing against nested inserts.
   */
  @Benchmark
  public void benchmarkFlatInsert() {
    flatRecord.put("id", flatIdCounter.getAndIncrement());
    flatDestination.write(flatRecord);
  }

  /**
   * Nested insert — 3 INSERTs per call (1 parent + 2 children).
   *
   * <p>Measures the combined throughput of parent and child-table inserts via {@link
   * com.datagenerator.destinations.database.NestedRecordDecomposer}. The reported ops/sec is in
   * terms of logical records written, not raw SQL statements.
   *
   * <p>Table layout:
   *
   * <ul>
   *   <li>{@code benchmark_order} — parent (id, customer_name, total_amount)
   *   <li>{@code order_items} — children (benchmark_order_id, product, quantity, price)
   * </ul>
   */
  @Benchmark
  public void benchmarkNestedInsert() {
    nestedRecord.put("id", nestedIdCounter.getAndIncrement());
    nestedDestination.write(nestedRecord);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private DatabaseDestination buildDestination(String tableName) {
    return new DatabaseDestination(
        DatabaseDestinationConfig.builder()
            .jdbcUrl(JDBC_URL)
            .username(DB_USER)
            .password(DB_PASS)
            .tableName(tableName)
            .batchSize(batchSize)
            .transactionStrategy(transactionStrategy)
            .poolSize(1)
            .build());
  }

  private void createSchema() throws Exception {
    try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS benchmark_flat ("
              + "id BIGINT PRIMARY KEY, "
              + "name VARCHAR(100), "
              + "email VARCHAR(100), "
              + "age INT, "
              + "amount NUMERIC(10,2), "
              + "created_at TIMESTAMP WITH TIME ZONE)");
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS benchmark_order ("
              + "id BIGINT PRIMARY KEY, "
              + "customer_name VARCHAR(100), "
              + "total_amount NUMERIC(10,2))");
      // No standalone PK on child table — benchmark_order_id + row order is sufficient here
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS order_items ("
              + "benchmark_order_id BIGINT, "
              + "product VARCHAR(100), "
              + "quantity INT, "
              + "price NUMERIC(10,2))");
    }
  }

  private void dropSchema() {
    try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS order_items");
      stmt.execute("DROP TABLE IF EXISTS benchmark_order");
      stmt.execute("DROP TABLE IF EXISTS benchmark_flat");
    } catch (Exception e) {
      System.err.println("[DB BENCHMARK] Warning: failed to drop schema: " + e.getMessage());
    }
  }

  private static Map<String, Object> buildFlatRecord() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 0L);
    record.put("name", "Alexandra Martinez");
    record.put("email", "alexandra.martinez@example.com");
    record.put("age", 38);
    record.put("amount", new BigDecimal("249.99"));
    record.put("created_at", Instant.parse("2026-03-10T09:00:00Z"));
    return record;
  }

  private static Map<String, Object> buildNestedRecord() {
    Map<String, Object> item1 = new LinkedHashMap<>();
    item1.put("product", "Widget Pro");
    item1.put("quantity", 2);
    item1.put("price", new BigDecimal("89.99"));

    Map<String, Object> item2 = new LinkedHashMap<>();
    item2.put("product", "Gadget Plus");
    item2.put("quantity", 1);
    item2.put("price", new BigDecimal("149.99"));

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", 0L);
    record.put("customer_name", "Alexandra Martinez");
    record.put("total_amount", new BigDecimal("329.97"));
    record.put("order_items", List.of(item1, item2));
    return record;
  }

  private static void closeQuietly(DatabaseDestination destination, String label) {
    if (destination == null) {
      return;
    }
    try {
      destination.close();
    } catch (Exception e) {
      System.err.printf("[DB BENCHMARK] Error closing %s destination: %s%n", label, e.getMessage());
    }
  }
}
