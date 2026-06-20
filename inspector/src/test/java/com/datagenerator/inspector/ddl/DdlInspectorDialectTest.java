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

package com.datagenerator.inspector.ddl;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dialect-level integration tests: each test feeds a realistic multi-table dump to {@link
 * DdlInspector} and asserts that all expected tables are produced without exception.
 */
class DdlInspectorDialectTest {

  private final DdlInspector inspector = new DdlInspector();

  @Test
  void shouldParseMysqlDump(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE `customers` (
          `id` INT NOT NULL AUTO_INCREMENT,
          `email` VARCHAR(255),
          `full_name` VARCHAR(120),
          `is_active` TINYINT(1) DEFAULT 1,
          PRIMARY KEY (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer records';

        CREATE TABLE `orders` (
          `id` INT NOT NULL AUTO_INCREMENT,
          `customer_id` INT,
          `total` DECIMAL(10,2),
          `created_at` DATETIME,
          PRIMARY KEY (`id`),
          KEY `idx_customer` (`customer_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """;

    Inspection inspection = inspect(dir, ddl);

    assertThat(tableNames(inspection)).containsExactlyInAnyOrder("customers", "orders");
  }

  @Test
  void shouldParseOracleDump(@TempDir Path dir) throws IOException {
    String ddl =
        """
        SET DEFINE OFF;
        PROMPT Creating table CUSTOMERS

        CREATE TABLE customers (
          id        NUMBER(10) NOT NULL,
          email     VARCHAR2(255),
          full_name VARCHAR2(120),
          balance   NUMBER(12,2)
        )
        SEGMENT CREATION IMMEDIATE
        PCTFREE 10 TABLESPACE users STORAGE (INITIAL 65536 NEXT 1048576)
        /

        PROMPT Creating table products

        CREATE TABLE products (
          id          NUMBER(10) NOT NULL,
          sku         VARCHAR2(50),
          description VARCHAR2(500),
          price       NUMBER(10,2)
        )
        SEGMENT CREATION DEFERRED
        PCTFREE 10 TABLESPACE users
        /
        """;

    Inspection inspection = inspect(dir, ddl);

    assertThat(tableNames(inspection)).containsExactlyInAnyOrder("customers", "products");
  }

  @Test
  void shouldParseSqlServerDump(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE [dbo].[customers] (
          [id]        INT IDENTITY(1,1) NOT NULL,
          [email]     NVARCHAR(255) NULL,
          [full_name] NVARCHAR(120) NULL,
          CONSTRAINT [pk_customers] PRIMARY KEY CLUSTERED ([id] ASC)
        ) WITH (DATA_COMPRESSION=PAGE) ON [PRIMARY]
        GO

        CREATE TABLE [dbo].[orders] (
          [id]          INT IDENTITY(1,1) NOT NULL,
          [customer_id] INT NULL,
          [total]       DECIMAL(10,2) NULL,
          CONSTRAINT [pk_orders] PRIMARY KEY CLUSTERED ([id] ASC),
          CONSTRAINT [fk_orders_customers] FOREIGN KEY ([customer_id])
              REFERENCES [dbo].[customers] ([id])
        ) WITH (DATA_COMPRESSION=PAGE) ON [PRIMARY]
        GO
        """;

    Inspection inspection = inspect(dir, ddl);

    assertThat(tableNames(inspection)).containsExactlyInAnyOrder("customers", "orders");
  }

  @Test
  void shouldParsePostgresDump(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE users (
          id         SERIAL PRIMARY KEY,
          email      TEXT,
          tags       TEXT[],
          metadata   JSONB,
          created_at TIMESTAMPTZ
        );

        CREATE TABLE posts (
          id         SERIAL PRIMARY KEY,
          user_id    INT REFERENCES users(id),
          title      TEXT,
          body       TEXT
        );
        """;

    Inspection inspection = inspect(dir, ddl);

    assertThat(tableNames(inspection)).containsExactlyInAnyOrder("users", "posts");
  }

  private static final String GOOD_AND_BAD_DDL =
      """
      CREATE TABLE good_one (
        id   INT PRIMARY KEY,
        name VARCHAR(100)
      );

      CREATE TABLE bad (id INT, , );

      CREATE TABLE good_two (
        id    INT PRIMARY KEY,
        value DECIMAL(10,2)
      );
      """;

  @Test
  void shouldFailStrictlyOnMalformedTable(@TempDir Path dir) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, GOOD_AND_BAD_DDL);

    // Default (strict): an unparseable CREATE TABLE aborts the whole inspection — no partial
    // output.
    assertThatThrownBy(() -> inspector.inspect(sql))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("best-effort")
        .hasMessageContaining("CREATE TABLE statement(s)");
  }

  @Test
  void shouldKeepGoodTablesInBestEffortMode(@TempDir Path dir) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, GOOD_AND_BAD_DDL);

    Inspection inspection = inspector.inspect(sql, NestingOptions.none(), true);

    assertThat(tableNames(inspection)).containsExactlyInAnyOrder("good_one", "good_two");
    assertThat(inspection.warnings())
        .anyMatch(
            w ->
                w.toLowerCase(Locale.ROOT).contains("skipped")
                    && w.toLowerCase(Locale.ROOT).contains("bad"));
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return inspector.inspect(sql);
  }

  private Set<String> tableNames(Inspection inspection) {
    return inspection.structures().stream().map(s -> s.getName()).collect(Collectors.toSet());
  }
}
