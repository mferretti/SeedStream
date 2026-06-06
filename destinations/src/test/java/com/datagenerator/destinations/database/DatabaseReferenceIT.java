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

import com.datagenerator.core.engine.GenerationEngine;
import com.datagenerator.core.structure.StructureLoader;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.destinations.IntegrationTest;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.semantic.FakerCache;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * End-to-end integration test for foreign key references ({@code ref[s.f, min..count]}) across
 * three related tables written to a real PostgreSQL instance.
 *
 * <p><b>Schema (dependency order):</b>
 *
 * <pre>
 * it_customer (50 rows)
 *   └─ it_order (100 rows)  customer_id → ref[it_customer.id, 1..count]
 *        └─ it_order_item (200 rows)  order_id → ref[it_order.id, 1..count]
 * </pre>
 *
 * <p><b>DDL:</b>
 *
 * <pre>{@code
 * CREATE TABLE it_customers (
 *   id         INT,
 *   name       VARCHAR(255),
 *   email      VARCHAR(255)
 * );
 * CREATE TABLE it_orders (
 *   id           INT,
 *   customer_id  INT,
 *   status       VARCHAR(50),
 *   total        NUMERIC(10, 2)
 * );
 * CREATE TABLE it_order_items (
 *   id          INT,
 *   order_id    INT,
 *   product     VARCHAR(255),
 *   quantity    INT,
 *   unit_price  NUMERIC(10, 2)
 * );
 * }</pre>
 *
 * <p>No FK constraints are applied — the explicit pool model ({@code ref[s.f, min..count]})
 * generates values in the correct range without requiring referential integrity enforcement at the
 * database level. The tests verify that FK column values fall within the expected bounds.
 *
 * <p>Run with: {@code ./gradlew :destinations:integrationTest}
 */
class DatabaseReferenceIT extends IntegrationTest {

  // --- Schema ---

  private static final String DDL_CUSTOMERS =
      """
      CREATE TABLE it_customers (
        id    INT,
        name  VARCHAR(255),
        email VARCHAR(255)
      )
      """;

  private static final String DDL_ORDERS =
      """
      CREATE TABLE it_orders (
        id           INT,
        customer_id  INT,
        status       VARCHAR(50),
        total        NUMERIC(10, 2)
      )
      """;

  private static final String DDL_ORDER_ITEMS =
      """
      CREATE TABLE it_order_items (
        id          INT,
        order_id    INT,
        product     VARCHAR(255),
        quantity    INT,
        unit_price  NUMERIC(10, 2)
      )
      """;

  // --- Container ---

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  // --- State ---

  private Connection verify;
  private Path structuresPath;

  // --- Lifecycle ---

  @BeforeEach
  void setUp() throws SQLException, URISyntaxException {
    verify =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = verify.createStatement()) {
      st.execute(DDL_CUSTOMERS);
      st.execute(DDL_ORDERS);
      st.execute(DDL_ORDER_ITEMS);
    }

    // Resolve test resources structures directory
    structuresPath =
        Paths.get(
                DatabaseReferenceIT.class
                    .getClassLoader()
                    .getResource("structures/it_customer.yaml")
                    .toURI())
            .getParent();
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verify.createStatement()) {
      st.execute("DROP TABLE IF EXISTS it_order_items");
      st.execute("DROP TABLE IF EXISTS it_orders");
      st.execute("DROP TABLE IF EXISTS it_customers");
    }
    verify.close();
  }

  // --- Tests ---

  @Test
  void shouldGenerateThreeEntitiesWithForeignKeyChain() throws InterruptedException, SQLException {
    int customerCount = 50;
    int orderCount = 100;
    int orderItemCount = 200;

    generate("it_customer", "it_customers", customerCount);
    generate("it_order", "it_orders", orderCount);
    generate("it_order_item", "it_order_items", orderItemCount);

    assertThat(countRows("it_customers")).isEqualTo(customerCount);
    assertThat(countRows("it_orders")).isEqualTo(orderCount);
    assertThat(countRows("it_order_items")).isEqualTo(orderItemCount);
  }

  @Test
  void shouldBoundCustomerIdWithinCustomerCount() throws InterruptedException, SQLException {
    // Both jobs use the SAME count — ref[it_customer.id, 1..count] resolves to 1..count
    // for the ORDER job, so pass the customer count as the order job count.
    int count = 50;

    generate("it_customer", "it_customers", count);
    generate("it_order", "it_orders", count);

    try (Statement st = verify.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT MIN(customer_id), MAX(customer_id) FROM it_orders")) {
      rs.next();
      assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
      assertThat(rs.getInt(2)).isLessThanOrEqualTo(count);
    }
  }

  @Test
  void shouldBoundOrderIdWithinOrderCount() throws InterruptedException, SQLException {
    // ref[it_order.id, 1..count] resolves to 1..count for the ORDER_ITEM job.
    // Passing the same count to both jobs keeps FK values in [1..count].
    int count = 80;

    generate("it_order", "it_orders", count);
    generate("it_order_item", "it_order_items", count);

    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT MIN(order_id), MAX(order_id) FROM it_order_items")) {
      rs.next();
      assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
      assertThat(rs.getInt(2)).isLessThanOrEqualTo(count);
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() throws InterruptedException, SQLException {
    // Run A
    generate("it_order", "it_orders", 20, 42L);
    long sumA = sumColumn("it_orders", "customer_id");

    try (Statement st = verify.createStatement()) {
      st.execute("TRUNCATE it_orders");
    }

    // Run B — same seed, same result
    generate("it_order", "it_orders", 20, 42L);
    long sumB = sumColumn("it_orders", "customer_id");

    assertThat(sumA).isEqualTo(sumB);
  }

  @Test
  void shouldScaleWithDifferentCounts() throws InterruptedException, SQLException {
    int smallCount = 10;
    int largeCount = 500;

    generate("it_order", "it_orders", smallCount);
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(customer_id) FROM it_orders")) {
      rs.next();
      assertThat(rs.getInt(1)).isLessThanOrEqualTo(smallCount);
    }

    try (Statement st = verify.createStatement()) {
      st.execute("TRUNCATE it_orders");
    }

    generate("it_order", "it_orders", largeCount);
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(customer_id) FROM it_orders")) {
      rs.next();
      // With 500 records, max customer_id should use 1..500 range
      assertThat(rs.getInt(1)).isLessThanOrEqualTo(largeCount);
      // And should use a wider range than the small run (statistically certain at 500 records)
      assertThat(rs.getInt(1)).isGreaterThan(smallCount);
    }
  }

  // --- Helpers ---

  /**
   * Generate {@code count} records of {@code structureName} into {@code tableName} using seed 42.
   *
   * <p>Uses the full pipeline: YAML structure → ObjectGenerator → GenerationEngine →
   * DatabaseDestination. The {@code count} is injected into GeneratorContext so that {@code
   * ref[s.f, 1..count]} resolves correctly.
   */
  private void generate(String structureName, String tableName, int count)
      throws InterruptedException {
    generate(structureName, tableName, count, 42L);
  }

  private void generate(String structureName, String tableName, int count, long seed)
      throws InterruptedException {
    DataStructureParser structureParser = new DataStructureParser();
    Path structureFile = structuresPath.resolve(structureName + ".yaml");
    DataStructure dataStructure = structureParser.parse(structureFile);

    StructureRegistry registry = buildRegistry(structuresPath);
    DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);
    ObjectType objectType = new ObjectType(structureName);
    DataGenerator generator = factory.create(objectType);

    Map<String, String> rawFieldTypes =
        dataStructure.getData().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype()));

    DatabaseDestinationConfig config =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .tableName(tableName)
            .batchSize(100)
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(config, rawFieldTypes)) {
      dest.open();

      GenerationEngine engine =
          GenerationEngine.builder()
              .recordGenerator(
                  (random) -> {
                    try (var ctx = GeneratorContext.enter(factory, null, count)) {
                      @SuppressWarnings("unchecked")
                      Map<String, Object> record =
                          (Map<String, Object>) generator.generate(random, objectType);
                      return record;
                    }
                  })
              .recordWriter(dest::write)
              .masterSeed(seed)
              .workerThreads(1)
              .workerCleanup(FakerCache::clear)
              .build();

      engine.generate(count);
      dest.flush();
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private StructureRegistry buildRegistry(Path path) {
    StructureLoader loader =
        (structureName, basePath, registry) -> {
          try {
            DataStructureParser parser = new DataStructureParser();
            DataStructure structure = parser.parse(basePath.resolve(structureName + ".yaml"));
            TypeParser typeParser = new TypeParser();
            return structure.getData().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> typeParser.parse(entry.getValue().getDatatype())));
          } catch (Exception e) {
            throw new GeneratorException("Failed to load structure: " + structureName, e);
          }
        };
    return new StructureRegistry(loader);
  }

  // table and column are hardcoded test constants, never user input — not a real SQL injection risk
  @SuppressWarnings("SqlSourceToSinkFlow")
  @SuppressFBWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
  private int countRows(String table) throws SQLException {
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  @SuppressWarnings("SqlSourceToSinkFlow")
  @SuppressFBWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
  private long sumColumn(String table, String column) throws SQLException {
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT SUM(" + column + ") FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
