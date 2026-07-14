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
 * ./gradlew :benchmarks:jmh -PjmhSuite=database
 * </pre>
 *
 * <p><b>Run with custom connection settings:</b>
 *
 * <pre>
 * ./gradlew :benchmarks:jmh -PjmhSuite=database \
 *   -Djvm.args="-Ddb.url=jdbc:postgresql://myhost:5432/mydb -Ddb.user=myuser -Ddb.password=secret"
 * </pre>
 *
 * <p><b>Note:</b> {@code -Pjmh.includes} and {@code -Pjmh.excludes} are silently ignored by
 * me.champeau.jmh 0.7.3 — passing them runs the FULL suite anyway. Earlier revisions of this
 * javadoc documented {@code -Pjmh.excludes=".*DatabaseBenchmark.*"} as a way to skip these
 * benchmarks when PostgreSQL is unavailable; it does not work. Use {@code -PjmhSuite} to select a
 * suite instead. With no database reachable, this class fails fast on connection and the rest of
 * the run proceeds.
 *
 * <p><b>Cleanup Docker container:</b>
 *
 * <pre>
 * docker stop pg-benchmark &amp;&amp; docker rm pg-benchmark
 * </pre>
 *
 * <p><b>Measured results</b> (14 Jul 2026, PostgreSQL 17.9-alpine in Docker on localhost —
 * shared-memory socket, so effectively zero network latency; a remote DB will be far slower):
 *
 * <ul>
 *   <li>Flat, per_batch, batchSize=5000: ~83,500 ops/sec (batchSize=100: ~56,000)
 *   <li>Flat, auto_commit: ~55,000–75,000 ops/sec — barely worse than per_batch for single INSERTs
 *   <li>Nested, per_batch, batchSize=5000: ~2,900 ops/sec (3× INSERT fanout + decomposition)
 *   <li>Nested, auto_commit: ~120–770 ops/sec — up to <b>24× slower</b> than per_batch. Do not use
 *       it for nested writes
 * </ul>
 *
 * <p>Error margins are wide at the default JMH config (batch=100/per_batch reports ±119,838 on
 * 56,165). Trust the ordering, not the third significant figure.
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

  /**
   * Pre-built flat data — no {@code id} field; {@code benchmark_flat.id} is {@code BIGSERIAL}
   * (auto-generated). This avoids the reference-aliasing trap: {@code DatabaseDestination} stores
   * map references in its batch list, so reusing the same map with a mutated id would cause all
   * batch entries to share the final id value, producing duplicate key violations.
   */
  private Map<String, Object> flatRecord;

  /**
   * Pre-built nested data — {@code id} field is overwritten per call. Safe because {@link
   * com.datagenerator.destinations.database.NestedRecordDecomposer} creates new maps during
   * decomposition, so each batch entry holds an independent copy.
   */
  private Map<String, Object> nestedRecord;

  private AtomicLong nestedIdCounter;

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------

  @SuppressWarnings("java:S106")
  @Setup(Level.Trial)
  public void setup() throws Exception {
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

    nestedIdCounter.set(0);
  }

  @SuppressWarnings("java:S106")
  @TearDown(Level.Trial)
  public void tearDown() {
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

  private void createSchema() throws java.sql.SQLException {
    try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement()) {
      // Drop first to guarantee a clean slate — previous interrupted runs may leave stale data
      stmt.execute("DROP TABLE IF EXISTS order_items");
      stmt.execute("DROP TABLE IF EXISTS benchmark_order");
      stmt.execute("DROP TABLE IF EXISTS benchmark_flat");
      stmt.execute(
          "CREATE TABLE benchmark_flat ("
              + "id BIGSERIAL PRIMARY KEY, "
              + "name VARCHAR(100), "
              + "email VARCHAR(100), "
              + "age INT, "
              + "amount NUMERIC(10,2), "
              + "created_at TIMESTAMP WITH TIME ZONE)");
      stmt.execute(
          "CREATE TABLE benchmark_order ("
              + "id BIGINT PRIMARY KEY, "
              + "customer_name VARCHAR(100), "
              + "total_amount NUMERIC(10,2))");
      // No standalone PK on child table — benchmark_order_id + row order is sufficient here
      stmt.execute(
          "CREATE TABLE order_items ("
              + "benchmark_order_id BIGINT, "
              + "product VARCHAR(100), "
              + "quantity INT, "
              + "price NUMERIC(10,2))");
    }
  }

  @SuppressWarnings({"PMD.AvoidCatchingGenericException", "java:S106"})
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
    // No "id": benchmark_flat.id is BIGSERIAL; reusing the same map avoids reference-aliasing.
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Alexandra Martinez");
    data.put("email", "alexandra.martinez@example.com");
    data.put("age", 38);
    data.put("amount", new BigDecimal("249.99"));
    data.put("created_at", Instant.parse("2026-03-10T09:00:00Z"));
    return data;
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

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 0L);
    data.put("customer_name", "Alexandra Martinez");
    data.put("total_amount", new BigDecimal("329.97"));
    data.put("order_items", List.of(item1, item2));
    return data;
  }

  @SuppressWarnings({"PMD.AvoidCatchingGenericException", "java:S106"})
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
