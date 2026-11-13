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

import static com.datagenerator.inspector.ddl.TableModel.foreignKeyRef;
import static com.datagenerator.inspector.ddl.TableModel.unquote;

import com.datagenerator.inspector.Names;
import com.datagenerator.inspector.ddl.NestingPlanner.ForeignKeyRef;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterExpression.ColumnDataType;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

/**
 * Applies {@code ALTER TABLE} statements to the {@link TableModel}s built from the {@code CREATE
 * TABLE}s. Every major exporter declares keys that way (pg_dump, SSMS, mysqldump, Oracle's
 * DBMS_METADATA), so a key declared by {@code ALTER} must come out exactly as the same key declared
 * inline.
 *
 * <p>Read: {@code ADD [CONSTRAINT] PRIMARY KEY | UNIQUE | FOREIGN KEY | CHECK} and {@code ALTER
 * COLUMN ... ADD GENERATED ALWAYS AS IDENTITY}. Ignored silently: everything else that leaves the
 * columns as they are (owner, triggers, {@code NOT NULL}, {@code AUTO_INCREMENT}, defaults, ...).
 * Warned and not applied: statements that change the structure (add / drop / rename / retype a
 * column). An {@code ALTER} never aborts the inspection: one that cannot be parsed, or that names
 * an unknown table, becomes a warning.
 */
final class AlterTableApplier {

  private static final Pattern ALTER_TABLE = Pattern.compile("(?is)\\A\\s*ALTER\\s+TABLE\\b");
  // SQL Server "ALTER TABLE t [NO]CHECK CONSTRAINT x|ALL" only toggles enforcement.
  private static final Pattern TOGGLE_CONSTRAINT =
      Pattern.compile("(?i)\\b(?:NO)?CHECK\\s+CONSTRAINT\\b");
  private static final Pattern GENERATED_ALWAYS =
      Pattern.compile("(?i)\\bADD\\s+GENERATED\\s+ALWAYS\\s+AS\\s+IDENTITY\\b");

  private final DdlPreprocessor preprocessor = new DdlPreprocessor();

  /** Whether {@code code} (comment- and literal-free statement text) is an {@code ALTER TABLE}. */
  static boolean isAlterTable(String code) {
    return ALTER_TABLE.matcher(DdlPreprocessor.stripLeadingDirectives(code)).find();
  }

  /** Applies every statement to the table of that name in {@code tables} (keyed by snake_case). */
  void apply(List<String> statements, Map<String, TableModel> tables, List<String> warnings) {
    for (String raw : statements) {
      String sql = preprocessor.sanitizeAlter(raw);
      if (!TOGGLE_CONSTRAINT.matcher(sql).find()) {
        applyStatement(sql, raw, tables, warnings);
      }
    }
  }

  private static final Pattern STORAGE_SETTING =
      Pattern.compile("(?i)\\bSET\\s+(?:STATISTICS|STORAGE|COMPRESSION)\\b");

  private void applyStatement(
      String sql, String raw, Map<String, TableModel> tables, List<String> warnings) {
    Statement parsed;
    try {
      parsed = CCJSqlParserUtil.parse(sql);
    } catch (JSQLParserException e) {
      warnings.add(
          "could not parse ALTER TABLE statement, ignored: " + DdlInspector.shortSnippet(raw));
      return;
    }
    if (!(parsed instanceof Alter alter) || alter.getAlterExpressions() == null) {
      return;
    }
    String name = unquote(alter.getTable().getName());
    TableModel table = tables.get(Names.toSnakeCase(name));
    if (table == null) {
      warnings.add("ALTER TABLE " + name + ": table is not in the script — ignored");
      return;
    }
    table.addChecks(raw);
    for (AlterExpression expression : alter.getAlterExpressions()) {
      apply(table, expression, tables, warnings);
    }
  }

  private void apply(
      TableModel table,
      AlterExpression expression,
      Map<String, TableModel> tables,
      List<String> warnings) {
    switch (expression.getOperation()) {
      case ADD -> add(table, expression, tables, warnings);
      case ALTER -> alterColumn(table, expression, warnings);
      case MODIFY -> modifyColumn(table, expression);
      case DROP, CHANGE, RENAME, RENAME_TABLE -> warnNotApplied(table, expression, warnings);
      default -> {
        // MODIFY (NOT NULL, AUTO_INCREMENT), OWNER TO, triggers, partitions, ...: no key involved
      }
    }
  }

  private void add(
      TableModel table,
      AlterExpression expression,
      Map<String, TableModel> tables,
      List<String> warnings) {
    Index index = expression.getIndex();
    if (index instanceof ForeignKeyIndex fk) {
      addForeignKey(table, foreignKeyRef(fk), tables, warnings);
    } else if (index != null) {
      table.addIndex(index);
    } else if (expression.getColDataTypeList() != null) {
      warnNotApplied(table, expression, warnings); // ADD [COLUMN] ...
    }
  }

  /** A foreign key to a table the script never creates would leave a dangling {@code ref[...]}. */
  private void addForeignKey(
      TableModel table, ForeignKeyRef fk, Map<String, TableModel> tables, List<String> warnings) {
    if (tables.containsKey(fk.refTable())) {
      table.foreignKeys.add(fk);
    } else {
      warnings.add(
          table.name
              + ": FOREIGN KEY ("
              + String.join(", ", fk.localColumns())
              + ") REFERENCES "
              + fk.refTable()
              + " — table is not in the script, kept as plain column(s)");
    }
  }

  private static final Pattern SET_NOT_NULL =
      Pattern.compile("(?i)\\bALTER\\s+(?:COLUMN\\s+)?(\\S+)\\s+SET\\s+NOT\\s+NULL\\b");

  private static final Pattern MODIFY_ELEMENT =
      Pattern.compile("(?is)\\s*(?:COLUMN\\s+)?(\\S+)\\s.*\\bNOT\\s+NULL\\b.*");

  /** {@code MODIFY (a NOT NULL, b NUMBER(10,2) NOT NULL ENABLE)}: each such column is NOT NULL. */
  private void modifyColumn(TableModel table, AlterExpression expression) {
    String text = expression.toString().trim();
    int start = text.toUpperCase(Locale.ROOT).indexOf("MODIFY");
    String list = start < 0 ? "" : text.substring(start + "MODIFY".length()).trim();
    if (list.startsWith("(") && list.endsWith(")")) {
      list = list.substring(1, list.length() - 1);
    }
    int depth = 0;
    int from = 0;
    for (int i = 0; i <= list.length(); i++) {
      char c = i < list.length() ? list.charAt(i) : ',';
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        Matcher element = MODIFY_ELEMENT.matcher(list.substring(from, i));
        if (element.matches()) {
          table.notNull.add(unquote(element.group(1)).toLowerCase(Locale.ROOT));
        }
        from = i + 1;
      }
    }
  }

  private void alterColumn(TableModel table, AlterExpression expression, List<String> warnings) {
    Matcher notNull = SET_NOT_NULL.matcher(expression.toString());
    if (notNull.find()) {
      table.notNull.add(unquote(notNull.group(1)).toLowerCase(Locale.ROOT));
    }
    List<ColumnDataType> columns = expression.getColDataTypeList();
    if (columns == null || columns.isEmpty()) {
      return; // SET / DROP NOT NULL, SET DEFAULT, ...
    }
    ColumnDataType column = columns.get(0);
    if (STORAGE_SETTING.matcher(expression.toString()).find()) {
      return; // pg_dump's SET STATISTICS / SET STORAGE / SET COMPRESSION do not change the type
    }
    if (column.getColDataType() != null) {
      warnNotApplied(table, expression, warnings); // ALTER COLUMN c TYPE ...
    } else if (GENERATED_ALWAYS.matcher(expression.toString()).find()) {
      table.dbAssigned.add(unquote(column.getColumnName()).toLowerCase(Locale.ROOT));
    }
    // GENERATED BY DEFAULT accepts a client value, which is what an inline one does too
  }

  private void warnNotApplied(TableModel table, AlterExpression expression, List<String> warnings) {
    warnings.add(
        table.name
            + ": ALTER TABLE "
            + expression
            + " not applied — only key constraints are read from ALTER TABLE");
  }
}
