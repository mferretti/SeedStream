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
 * End-to-end integration test for the author → books relationship using {@code ref[parent.id]}.
 *
 * <p>Each lib_author record embeds a list of books ({@code array[object[lib_book], 1..5]}). The
 * {@code DatabaseDestination} auto-decomposes the nested structure into two tables: {@code
 * lib_authors} and {@code books}. Each book's {@code author_id} is populated via {@code
 * ref[parent.id]}, which copies the actual id from the enclosing author record — guaranteeing
 * referential integrity without a separate generation pass.
 *
 * <p>Run with: {@code ./gradlew :destinations:integrationTest --tests "*LibraryForeignKeyIT"}
 */
class LibraryForeignKeyIT extends IntegrationTest {

  private static final int AUTHOR_COUNT = 20;
  private static final int BOOKS_PER_AUTHOR_MIN = 1;
  private static final int BOOKS_PER_AUTHOR_MAX = 5;
  private static final long SEED = 42L;
  private static final String TABLE_BOOKS = "books";

  private static final String DDL_AUTHORS =
      """
      CREATE TABLE lib_authors (
        id           INT,
        first_name   VARCHAR(255),
        last_name    VARCHAR(255),
        birthdate    DATE,
        nationality  VARCHAR(255),
        email        VARCHAR(255)
      )
      """;

  private static final String DDL_BOOKS =
      """
      CREATE TABLE books (
        id             INT,
        title          TEXT,
        isbn           VARCHAR(20),
        genre          VARCHAR(20),
        pages          INT,
        language       VARCHAR(10),
        price          NUMERIC(10, 2),
        published_date DATE,
        author_id      INT
      )
      """;

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("librarydb")
          .withUsername("testuser")
          .withPassword("testpass");

  private Connection verify;
  private Path structuresPath;

  @BeforeEach
  void setUp() throws SQLException, URISyntaxException {
    verify =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = verify.createStatement()) {
      st.execute(DDL_AUTHORS);
      st.execute(DDL_BOOKS);
    }
    structuresPath =
        Paths.get(
                LibraryForeignKeyIT.class
                    .getClassLoader()
                    .getResource("structures/lib_author.yaml")
                    .toURI())
            .getParent();
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Statement st = verify.createStatement()) {
      st.execute("DROP TABLE IF EXISTS books");
      st.execute("DROP TABLE IF EXISTS lib_authors");
    }
    verify.close();
  }

  @Test
  void shouldWriteAuthorsAndEmbeddedBooksToSeparateTables()
      throws InterruptedException, SQLException {
    generate(AUTHOR_COUNT, SEED);

    assertThat(countRows("lib_authors")).isEqualTo(AUTHOR_COUNT);
    assertThat(countRows(TABLE_BOOKS))
        .isGreaterThanOrEqualTo(AUTHOR_COUNT * BOOKS_PER_AUTHOR_MIN)
        .isLessThanOrEqualTo(AUTHOR_COUNT * BOOKS_PER_AUTHOR_MAX);
  }

  @Test
  void shouldSetBookAuthorIdToActualParentAuthorId() throws InterruptedException, SQLException {
    generate(AUTHOR_COUNT, SEED);

    // author_id values must be within the author id range
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT MIN(author_id), MAX(author_id) FROM books")) {
      rs.next();
      assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
      assertThat(rs.getInt(2)).isLessThanOrEqualTo(20);
    }

    // every book's author_id must reference an actual generated author
    try (Statement st = verify.createStatement();
        ResultSet rs =
            st.executeQuery(
                """
                SELECT COUNT(*) FROM books b
                WHERE NOT EXISTS (
                  SELECT 1 FROM lib_authors a WHERE a.id = b.author_id
                )
                """)) {
      rs.next();
      assertThat(rs.getInt(1)).as("orphan books (no matching author)").isZero();
    }
  }

  @Test
  void shouldGenerateDeterministicBookCountWithSameSeed()
      throws InterruptedException, SQLException {
    generate(AUTHOR_COUNT, SEED);
    int bookCount1 = countRows(TABLE_BOOKS);

    try (Statement st = verify.createStatement()) {
      st.execute("TRUNCATE lib_authors, books");
    }

    generate(AUTHOR_COUNT, SEED);
    int bookCount2 = countRows(TABLE_BOOKS);

    assertThat(bookCount1).isEqualTo(bookCount2);
  }

  // --- Helpers ---

  private void generate(int count, long seed) throws InterruptedException {
    DataStructureParser structureParser = new DataStructureParser();
    DataStructure dataStructure = structureParser.parse(structuresPath.resolve("lib_author.yaml"));

    StructureRegistry registry = buildRegistry(structuresPath);
    DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);
    ObjectType objectType = new ObjectType("lib_author");
    DataGenerator generator = factory.create(objectType);

    Map<String, String> rawFieldTypes =
        dataStructure.getData().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype()));

    DatabaseDestinationConfig config =
        DatabaseDestinationConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .tableName("lib_authors")
            .batchSize(50)
            .injectParentFk(false)
            .build();

    try (DatabaseDestination dest = new DatabaseDestination(config, rawFieldTypes)) {
      dest.open();

      GenerationEngine engine =
          GenerationEngine.builder()
              .recordGenerator(
                  random -> {
                    try (var ctx = GeneratorContext.enter(factory, null, count)) {
                      @SuppressWarnings("unchecked")
                      Map<String, Object> data =
                          (Map<String, Object>) generator.generate(random, objectType);
                      return data;
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

  @SuppressWarnings({"SqlSourceToSinkFlow", "java:S2077"})
  @SuppressFBWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
  private int countRows(String table) throws SQLException {
    try (Statement st = verify.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) { // nosemgrep
      rs.next();
      return rs.getInt(1);
    }
  }
}
