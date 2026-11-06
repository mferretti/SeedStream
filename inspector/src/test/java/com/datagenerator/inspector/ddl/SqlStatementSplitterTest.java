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
    assertThat(result).hasSize(2).doesNotContain("").doesNotContain(" ");
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

  @Test
  void shouldNotSplitInsideMultilineSingleQuote() {
    List<String> result = splitter.split("SELECT 'a;\nb'; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("'a;\nb'");
  }

  @Test
  void shouldNotSplitInsideMultilineDoubleQuotedIdentifier() {
    List<String> result = splitter.split("SELECT \"a;\nb\"; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("\"a;\nb\"");
  }

  @Test
  void shouldNotSplitInsideMultilineBacktickIdentifier() {
    List<String> result = splitter.split("SELECT `a;\nb`; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("`a;\nb`");
  }

  @Test
  void shouldNotSplitInsideMultilineBracketIdentifier() {
    List<String> result = splitter.split("SELECT [a;\nb]; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("[a;\nb]");
  }

  @Test
  void shouldHonorEscapedBracketIdentifier() {
    // ]] is an escaped bracket, so the identifier does not close at the first ]
    List<String> result = splitter.split("SELECT [a]]b;c]; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("[a]]b;c]");
  }

  @Test
  void shouldNotSplitInsideMultilineDollarQuote() {
    List<String> result = splitter.split("SELECT $$a;\nb$$; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("$$a;\nb$$");
  }

  @Test
  void shouldNotSplitInsideMultilineBlockComment() {
    List<String> result = splitter.split("SELECT 1 /* a;\nb */; SELECT 2");
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldTreatLoneDollarAsOrdinaryCharacter() {
    // "a$b" is not a dollar-quote (no closing tag); the $ is an ordinary char
    List<String> result = splitter.split("SELECT a$b; SELECT 2");
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).contains("a$b");
  }

  @Test
  void shouldStripTrailingGoTerminatorAtEndOfInput() {
    List<String> result = splitter.split("SELECT 1\nGO");
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("SELECT 1");
  }

  @Test
  void shouldStripTrailingSlashTerminatorAtEndOfInput() {
    List<String> result = splitter.split("SELECT 1\n/");
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("SELECT 1");
  }

  // ---- #436: runs of empty lines ----

  @Test
  void shouldKeepSingleEmptyLineInsideStatement() {
    String sql = "CREATE TABLE t (\n  a INT,\n\n  b INT\n)";
    assertThat(splitter.split(sql)).containsExactly(sql);
  }

  @Test
  void shouldStripSingleEmptyLineBetweenStatements() {
    assertThat(splitter.split("SELECT 1;\n\nSELECT 2;")).containsExactly("SELECT 1", "SELECT 2");
  }

  @Test
  void shouldStripLeadingEmptyLinesBetweenStatements() {
    assertThat(splitter.split("SELECT 1;\n\n\n\nSELECT 2;"))
        .containsExactly("SELECT 1", "SELECT 2");
  }

  @Test
  void shouldCollapseEmptyLineRunInsideColumnList() {
    String sql = "CREATE TABLE t (\n  a INT,\n\n\n  b INT\n)";
    assertThat(splitter.split(sql)).containsExactly("CREATE TABLE t (\n  a INT,\n  b INT\n)");
  }

  @Test
  void shouldCollapseEmptyLineRunInsideColumnListWithCrlf() {
    String sql = "CREATE TABLE t (\r\n  a INT,\r\n\r\n\r\n  b INT\r\n)";
    assertThat(splitter.split(sql)).containsExactly("CREATE TABLE t (\n  a INT,\n  b INT\n)");
  }

  @Test
  void shouldCollapseEmptyLineRunAfterLineComment() {
    String sql = "-- note\n\n\nCREATE TABLE t (a INT);";
    assertThat(splitter.split(sql)).containsExactly("-- note\nCREATE TABLE t (a INT)");
  }

  @Test
  void shouldStripEmptyLineRunAtStartOfInput() {
    assertThat(splitter.split("\n\n\nCREATE TABLE t (a INT);"))
        .containsExactly("CREATE TABLE t (a INT)");
  }

  @Test
  void shouldPreserveEmptyLineRunInsideStringLiteral() {
    String sql = "CREATE TABLE t (a VARCHAR(10) DEFAULT 'a\n\n\nb');";
    assertThat(splitter.split(sql))
        .containsExactly("CREATE TABLE t (a VARCHAR(10) DEFAULT 'a\n\n\nb')");
  }

  @Test
  void shouldPreserveEmptyLineRunInsideDollarQuotedBody() {
    String sql =
        "CREATE FUNCTION f() RETURNS int AS $$\nSELECT 1;\n\n\nSELECT 2;\n$$ LANGUAGE sql;";
    assertThat(splitter.split(sql))
        .containsExactly(
            "CREATE FUNCTION f() RETURNS int AS $$\nSELECT 1;\n\n\nSELECT 2;\n$$ LANGUAGE sql");
  }

  @Test
  void shouldSplitOnGoSurroundedByEmptyLineRuns() {
    assertThat(splitter.split("SELECT 1\n\n\nGO\n\n\nSELECT 2\n\n\nGO\n\n\nSELECT 3"))
        .containsExactly("SELECT 1", "SELECT 2", "SELECT 3");
  }

  @Test
  void shouldSplitOnSlashSurroundedByEmptyLineRuns() {
    assertThat(splitter.split("SELECT 1\n\n\n/\n\n\nSELECT 2\n\n\n/\n\n\nSELECT 3"))
        .containsExactly("SELECT 1", "SELECT 2", "SELECT 3");
  }

  // ---- #443: psql meta-commands ----

  @Test
  void shouldDropPsqlMetaCommandLinesBeforeStatement() {
    String sql =
        "\\restrict abc123\n\\connect northwind\nCREATE TABLE t (a INT);\n\\unrestrict abc123\n";
    assertThat(splitter.split(sql)).containsExactly("CREATE TABLE t (a INT)");
  }

  @Test
  void shouldDropIndentedPsqlMetaCommandLine() {
    assertThat(splitter.split("  \t\\connect db\nCREATE TABLE t (a INT);"))
        .containsExactly("CREATE TABLE t (a INT)");
  }

  @Test
  void shouldDropPsqlMetaCommandLineWithCrlf() {
    assertThat(splitter.split("\\connect db\r\nCREATE TABLE t (a INT);"))
        .containsExactly("CREATE TABLE t (a INT)");
  }

  @Test
  void shouldKeepBackslashLineInsideStringLiteral() {
    String sql = "SELECT 'line1\n\\connect db\nline3'";
    assertThat(splitter.split(sql)).containsExactly(sql);
  }

  @Test
  void shouldKeepBackslashLineInsideDollarQuotedBody() {
    String sql = "CREATE FUNCTION f() RETURNS int AS $$\n\\connect db\n$$ LANGUAGE sql";
    assertThat(splitter.split(sql)).containsExactly(sql);
  }
}
