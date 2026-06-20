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

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlStatementSplitterTest {

  private final SqlStatementSplitter splitter = new SqlStatementSplitter();

  @Test
  void shouldSplitTwoStatementsOnSemicolon() {
    List<String> result = splitter.split("SELECT 1; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isEqualTo("SELECT 1");
    assertThat(result.get(1)).isEqualTo("SELECT 2");
  }

  @Test
  void shouldIncludeFinalStatementWithoutTrailingSemicolon() {
    List<String> result = splitter.split("SELECT 1;\nSELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(1)).isEqualTo("SELECT 2");
  }

  @Test
  void shouldNotSplitOnSemicolonInsideSingleQuotedString() {
    List<String> result = splitter.split("SELECT 'a;b'");
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).contains("'a;b'");
  }

  @Test
  void shouldNotSplitOnSemicolonInsideEscapedSingleQuote() {
    // 'O''Brien;x' is one string with embedded semicolon
    List<String> result = splitter.split("INSERT INTO t VALUES ('O''Brien;x')");
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).contains("'O''Brien;x'");
  }

  @Test
  void shouldNotSplitOnSemicolonInsideLineComment() {
    List<String> result = splitter.split("SELECT 1 -- c;c\n; SELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldNotSplitOnSemicolonInsideBlockComment() {
    List<String> result = splitter.split("SELECT /* a;b */ 1; SELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldNotSplitOnSemicolonInsideDoubleQuotedIdentifier() {
    List<String> result = splitter.split("SELECT \"a;b\"; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("\"a;b\"");
  }

  @Test
  void shouldNotSplitOnSemicolonInsideBacktickIdentifier() {
    List<String> result = splitter.split("SELECT `a;b`; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("`a;b`");
  }

  @Test
  void shouldNotSplitOnSemicolonInsideBracketIdentifier() {
    List<String> result = splitter.split("SELECT [a;b]; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("[a;b]");
  }

  @Test
  void shouldSplitOnStandaloneGoUpperCase() {
    List<String> result = splitter.split("SELECT 1\nGO\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldSplitOnStandaloneGoLowerCase() {
    List<String> result = splitter.split("SELECT 1\ngo\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldSplitOnStandaloneGoMixedCase() {
    List<String> result = splitter.split("SELECT 1\nGo\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldNotSplitOnGoAsSubstring() {
    // CREATE TABLE GOODS — GO is embedded in the identifier, not a standalone line
    String sql =
        """
        CREATE TABLE GOODS (
          id INT,
          name VARCHAR(100)
        );
        """;
    List<String> result = splitter.split(sql);
    // Should be exactly one statement: the CREATE TABLE
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsIgnoringCase("GOODS");
  }

  @Test
  void shouldSplitOnLoneSlash() {
    List<String> result = splitter.split("SELECT 1\n/\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldNotSplitOnDivisionOperator() {
    // a/b inside an expression is not at line level
    List<String> result = splitter.split("SELECT a/b FROM t");
    assertThat(result).hasSize(1);
  }

  @Test
  void shouldHandleCrlfLineEndings() {
    List<String> result = splitter.split("SELECT 1\r\nGO\r\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldReturnEmptyListForNullInput() {
    assertThat(splitter.split(null)).isEmpty();
  }

  @Test
  void shouldReturnEmptyListForBlankInput() {
    assertThat(splitter.split("   \n  \t  ")).isEmpty();
  }

  @Test
  void shouldReturnEmptyListForCommentOnlyInput() {
    assertThat(splitter.split("-- just a comment\n/* and a block comment */")).isEmpty();
  }

  @Test
  void shouldNotProduceEmptyStatementsFromConsecutiveSemicolons() {
    List<String> result = splitter.split("SELECT 1;; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result).doesNotContain("").doesNotContain(" ");
  }

  @Test
  void shouldNotProduceEmptyStatementsFromConsecutiveGoSeparators() {
    List<String> result = splitter.split("SELECT 1\nGO\nGO\nSELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldNotSplitInsideDollarQuote() {
    List<String> result = splitter.split("SELECT $$ x; y $$; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("$$ x; y $$");
  }

  @Test
  void shouldNotSplitInsideTaggedDollarQuote() {
    List<String> result = splitter.split("SELECT $tag$ ;a; $tag$; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("$tag$ ;a; $tag$");
  }
}
