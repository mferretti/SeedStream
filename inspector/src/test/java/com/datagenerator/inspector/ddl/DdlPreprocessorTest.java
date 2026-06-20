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

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

class DdlPreprocessorTest {

  private final DdlPreprocessor preprocessor = new DdlPreprocessor();

  @Test
  void shouldTruncateMssqlTrailingOptions() throws Exception {
    String input =
        """
        CREATE TABLE [dbo].[orders] (
          [id] INT NOT NULL,
          [name] NVARCHAR(100)
        ) WITH (DATA_COMPRESSION=PAGE) ON [PRIMARY]""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    // trailing options stripped
    assertThat(sanitized).doesNotContainIgnoringCase("WITH");
    assertThat(sanitized).doesNotContain("ON [PRIMARY]");
    // column list preserved
    assertThat(sanitized).containsIgnoringCase("NVARCHAR");
  }

  @Test
  void shouldTruncateMysqlTrailingOptions() throws Exception {
    String input =
        """
        CREATE TABLE customers (
          id INT NOT NULL AUTO_INCREMENT,
          name VARCHAR(100)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer table'""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    assertThat(sanitized).doesNotContainIgnoringCase("ENGINE");
    assertThat(sanitized).doesNotContainIgnoringCase("CHARSET");
    assertThat(sanitized).containsIgnoringCase("VARCHAR");
  }

  @Test
  void shouldTruncateOracleTrailingOptions() throws Exception {
    String input =
        """
        CREATE TABLE addresses (
          id NUMBER(10) NOT NULL,
          street VARCHAR2(200)
        ) SEGMENT CREATION IMMEDIATE PCTFREE 10 TABLESPACE users STORAGE (INITIAL 65536)""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    assertThat(sanitized).doesNotContainIgnoringCase("SEGMENT");
    assertThat(sanitized).doesNotContainIgnoringCase("TABLESPACE");
    assertThat(sanitized).containsIgnoringCase("VARCHAR2");
  }

  @Test
  void shouldRemoveInlineClusteredKeyword() throws Exception {
    String input =
        """
        CREATE TABLE [t] (
          [Id] INT NOT NULL,
          CONSTRAINT [pk_t] PRIMARY KEY CLUSTERED ([Id] ASC)
        )""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    assertThat(sanitized).doesNotContainIgnoringCase("CLUSTERED");
  }

  @Test
  void shouldRemoveNonclusteredKeyword() throws Exception {
    String input =
        """
        CREATE TABLE [t] (
          [Id] INT NOT NULL PRIMARY KEY NONCLUSTERED,
          [val] INT
        )""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    assertThat(sanitized).doesNotContainIgnoringCase("NONCLUSTERED");
  }

  @Test
  void shouldPreserveDefaultWithParensInsideColumnList() throws Exception {
    String input =
        """
        CREATE TABLE t (
          id INT NOT NULL DEFAULT (0),
          price DECIMAL(10,2)
        )""";

    String sanitized = preprocessor.sanitize(input);
    assertThatCode(() -> CCJSqlParserUtil.parse(sanitized)).doesNotThrowAnyException();
    // DEFAULT (0) and NUMBER(10,2) paren groups must be preserved
    assertThat(sanitized).contains("DEFAULT (0)");
    assertThat(sanitized).contains("DECIMAL(10,2)");
  }

  @Test
  void shouldPreserveNumberTypeArgumentsInsideColumnList() throws Exception {
    String input =
        """
        CREATE TABLE t (
          id NUMBER(10,2),
          code IDENTITY(1,1)
        )""";

    // Just check sanitize doesn't destroy the content — the actual parse may or may not
    // succeed depending on JSQLParser's NUMBER support, so we only check preservation
    String sanitized = preprocessor.sanitize(input);
    assertThat(sanitized).contains("NUMBER(10,2)");
  }

  @Test
  void shouldReturnNonCreateTableInputUnchanged() {
    String input = "ALTER TABLE t ADD COLUMN x INT";
    assertThat(preprocessor.sanitize(input)).isEqualTo(input);
  }
}
