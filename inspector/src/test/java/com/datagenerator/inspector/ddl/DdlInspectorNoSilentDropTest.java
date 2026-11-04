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
import com.datagenerator.schema.model.DataStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Strict mode must never silently lose a real table: every {@code CREATE TABLE} in the script is
 * either modelled as a structure, mentioned in a warning, or makes the inspection fail loudly.
 */
class DdlInspectorNoSilentDropTest {

  private static final String BASE = "CREATE TABLE base (id INTEGER);\n";
  private static final String BOM = "﻿";

  // ---- must be modelled ----

  @Test
  void shouldModelUnquotedNonAsciiTableName(@TempDir Path dir) throws IOException {
    assertModelled(dir, "CREATE TABLE été (id INT);\n");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE TABLE \"a--b\" (id INT);",
        "CREATE TABLE \"it's\" (id INT);",
        "CREATE TABLE `a--b` (id INT);",
        "CREATE TABLE `it's` (id INT);"
      })
  void shouldModelQuotedIdentifierContainingQuoteOrCommentChars(String create, @TempDir Path dir)
      throws IOException {
    assertModelled(dir, create + "\n");
  }

  /**
   * Baseline (26383e8) already fails loudly on these; the fix must keep them loud or model them.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {"CREATE TABLE [brk--tbl] (id INT);", "CREATE TABLE [brk's_tbl] (id INT);"})
  void shouldNotSilentlyDropBracketIdentifierContainingQuoteOrCommentChars(
      String create, @TempDir Path dir) throws IOException {
    assertNotSilent(dir, BASE + create + "\n", "brk");
  }

  @Test
  void shouldModelTableAfterUnknownJunkStatement(@TempDir Path dir) throws IOException {
    assertModelled(dir, "FOO BAR;\nCREATE TABLE junk_tbl (id INT);\n");
  }

  @Test
  void shouldModelSingleTableFileStartingWithBom(@TempDir Path dir) throws IOException {
    Inspection inspection = strict(dir, BOM + "CREATE TABLE only_one (id INT);\n");

    assertThat(names(inspection)).containsExactly("only_one");
  }

  @Test
  void shouldModelEveryTableOfMultiTableFileStartingWithBom(@TempDir Path dir) throws IOException {
    Inspection inspection =
        strict(dir, BOM + "CREATE TABLE first_t (id INT);\nCREATE TABLE second_t (id INT);\n");

    assertThat(names(inspection)).containsExactly("first_t", "second_t");
  }

  // ---- modelled or loud, never silent ----

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE EXTERNAL TABLE ext_tbl (id INT) LOCATION '/tmp/x';",
        "CREATE PRIVATE TEMPORARY TABLE ora$ptt_tbl (id INT);",
        "CREATE CACHED TABLE cached_tbl (id INT);",
        "CREATE MEMORY TABLE memory_tbl (id INT);"
      })
  void shouldNotSilentlyDropVendorTableKinds(String create, @TempDir Path dir) throws IOException {
    String key =
        create.contains("ext_tbl")
            ? "ext_tbl"
            : create.contains("ptt_tbl")
                ? "ptt_tbl"
                : create.contains("cached_tbl") ? "cached_tbl" : "memory_tbl";
    assertNotSilent(dir, BASE + create + "\n", key);
  }

  @Test
  void shouldNotSilentlyDropVirtualTable(@TempDir Path dir) throws IOException {
    assertNotSilent(dir, BASE + "CREATE VIRTUAL TABLE fts_tbl USING fts5(a);\n", "fts_tbl");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SPOOL out.log", "WHENEVER SQLERROR CONTINUE", "REM some remark text"})
  void shouldNotSilentlyDropTableAfterSqlPlusLine(String sqlPlusLine, @TempDir Path dir)
      throws IOException {
    assertNotSilent(dir, BASE + sqlPlusLine + "\nCREATE TABLE plus_tbl (id INT);\n", "plus_tbl");
  }

  @Test
  void shouldNotSilentlyDropTableFollowingUnterminatedDrop(@TempDir Path dir) throws IOException {
    assertNotSilent(
        dir, BASE + "DROP TABLE drop_tbl\nCREATE TABLE drop_tbl (id INT)\nGO\n", "drop_tbl");
  }

  @Test
  void shouldNotSilentlyDropTableFollowingCopyDataBlock(@TempDir Path dir) throws IOException {
    assertNotSilent(
        dir,
        "CREATE TABLE copy_a (id INT);\nCOPY copy_a(id) FROM stdin;\n1\n\\.\n\n"
            + "CREATE TABLE copy_b (id INT);\n",
        "copy_b");
  }

  // ---- helpers ----

  /**
   * Strict inspection of {@code BASE + ddl} must yield exactly {@code base} plus one more table.
   */
  private static void assertModelled(Path dir, String ddl) throws IOException {
    Inspection inspection = strict(dir, BASE + ddl);

    assertThat(names(inspection)).as("tables of: %s", ddl).hasSize(2).contains("base");
  }

  /** Table {@code key} is a structure, named in a warning, or named in a strict-mode failure. */
  private static void assertNotSilent(Path dir, String ddl, String key) throws IOException {
    List<String> seen = new ArrayList<>();
    try {
      Inspection inspection = strict(dir, ddl);
      seen.addAll(names(inspection));
      seen.addAll(inspection.warnings());
    } catch (InspectorException e) {
      seen.add(String.valueOf(e.getMessage()));
    }

    assertThat(seen)
        .as("table '%s' silently dropped from: %s", key, ddl)
        .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(key));
  }

  private static Inspection strict(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return new DdlInspector().inspect(sql, NestingOptions.none(), false);
  }

  private static List<String> names(Inspection inspection) {
    return inspection.structures().stream().map(DataStructure::getName).toList();
  }
}
