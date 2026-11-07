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

import com.datagenerator.inspector.Names;
import com.datagenerator.inspector.ddl.NestingPlanner.ForeignKeyRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

/**
 * Mutable model of one table, assembled from its {@code CREATE TABLE} and then from the {@code
 * ALTER TABLE} statements that add constraints to it. Datatypes are rendered only afterwards, so a
 * constraint declared by {@code ALTER} (or a foreign key without a referenced column list) yields
 * exactly what the same constraint declared inline would. Names are kept as written (unquoted), the
 * same way inline constraints always were.
 */
final class TableModel {

  private static final Pattern SORT_DIRECTION = Pattern.compile("(?i)\\s+(ASC|DESC)$");
  private static final Pattern IDENT_QUOTES = Pattern.compile("[\"`\\[\\]]");

  /** A column with its SQL type, rendered to a SeedStream datatype later. */
  record Column(String name, ColDataType type) {}

  /**
   * A {@code CHECK} condition as written (name is null when unnamed). {@code noInherit}: PostgreSQL
   * {@code NO INHERIT}, which keeps it off the {@code INHERITS} children.
   */
  record CheckCondition(String name, String text, boolean noInherit) {}

  /** The snake_case structure name; also the key ALTER TABLE statements are matched on. */
  final String name;

  /** Zero-based declaration order. */
  final int order;

  final List<Column> columns = new ArrayList<>();

  /** The table's own {@code CHECK} conditions, from {@code CREATE TABLE} and {@code ALTER}. */
  final List<CheckCondition> checks = new ArrayList<>();

  /** Snake-cased names of the {@code INHERITS} parents, in clause order. */
  final List<String> parents = new ArrayList<>();

  /** One column list per PRIMARY KEY / UNIQUE constraint (composite ones have several columns). */
  final List<List<String>> primary = new ArrayList<>();

  final List<List<String>> uniques = new ArrayList<>();

  /** Foreign keys; an empty {@code refColumns()} means "the referenced table's primary key". */
  final List<ForeignKeyRef> foreignKeys = new ArrayList<>();

  /** Lower-cased names of {@code GENERATED ALWAYS AS IDENTITY} columns. */
  final Set<String> dbAssigned = new LinkedHashSet<>();

  /** Lower-cased names of SQL Server {@code IDENTITY} columns. */
  final Set<String> identity = new LinkedHashSet<>();

  TableModel(String tableName, int declarationOrder) {
    this.name = tableName;
    this.order = declarationOrder;
  }

  void addChecks(String sql) {
    CheckClauses.find(sql)
        .forEach(c -> checks.add(new CheckCondition(c.name(), c.condition(), c.noInherit())));
  }

  void addPrimaryKey(List<String> columnNames) {
    addKey(primary, columnNames);
  }

  void addUnique(List<String> columnNames) {
    addKey(uniques, columnNames);
  }

  /**
   * Adds the constraint an index definition declares: a table-level one in {@code CREATE TABLE} or
   * the one of an {@code ALTER TABLE ... ADD}. Plain indexes, {@code CHECK} constraints and the
   * like declare no key and are skipped.
   */
  void addIndex(Index index) {
    if (index instanceof ForeignKeyIndex fk) {
      foreignKeys.add(foreignKeyRef(fk));
      return;
    }
    String type = index.getType() == null ? "" : index.getType().toUpperCase(Locale.ROOT);
    if (type.startsWith("PRIMARY")) {
      addPrimaryKey(keyColumns(index));
    } else if (type.startsWith("UNIQUE")) {
      addUnique(keyColumns(index));
    }
  }

  /**
   * A key is declared once, however many times (inline and by {@code ALTER}) the DDL repeats it.
   */
  private static void addKey(List<List<String>> keys, List<String> columnNames) {
    if (keys.stream().noneMatch(known -> sameColumns(known, columnNames))) {
      keys.add(columnNames);
    }
  }

  private static boolean sameColumns(List<String> a, List<String> b) {
    return a.size() == b.size()
        && IntStream.range(0, a.size()).allMatch(i -> a.get(i).equalsIgnoreCase(b.get(i)));
  }

  private static List<String> keyColumns(Index index) {
    return index.getColumnsNames().stream().map(TableModel::keyColumn).toList();
  }

  static ForeignKeyRef foreignKeyRef(ForeignKeyIndex fk) {
    List<String> referenced = fk.getReferencedColumnNames();
    return new ForeignKeyRef(
        fk.getColumnsNames().stream().map(TableModel::unquote).toList(),
        Names.toSnakeCase(unquote(fk.getTable().getName())),
        referenced == null ? List.of() : referenced.stream().map(TableModel::unquote).toList());
  }

  /**
   * A key constraint's column, without its sort direction: {@code PRIMARY KEY ([id] ASC)} (the SSMS
   * default) reports the column as {@code "id ASC"}, which matched no field (#377).
   */
  static String keyColumn(String indexColumn) {
    return unquote(SORT_DIRECTION.matcher(indexColumn.trim()).replaceAll(""));
  }

  static String unquote(String identifier) {
    if (identifier == null) {
      return "";
    }
    return IDENT_QUOTES.matcher(identifier).replaceAll("").trim();
  }
}
