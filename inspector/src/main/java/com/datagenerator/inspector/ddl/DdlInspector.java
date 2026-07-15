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

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.inspector.ddl.NestingPlanner.ForeignKeyRef;
import com.datagenerator.inspector.ddl.NestingPlanner.TableInfo;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

/**
 * Reads a SQL DDL script and maps every {@code CREATE TABLE} to a SeedStream {@link DataStructure}.
 * Foreign keys (table-level and inline {@code REFERENCES}) become {@code ref[table.column]}. See
 * {@code docs/INSPECT-V1-SPEC.md}.
 */
public class DdlInspector {

  private static final Pattern PARENS = Pattern.compile("[()]");
  private static final Pattern COMMA = Pattern.compile(",");
  private static final Pattern IDENT_QUOTES = Pattern.compile("[\"`\\[\\]]");
  private static final Pattern CREATE_TABLE_QUICK =
      Pattern.compile("(?is)\\bCREATE\\b.{0,50}\\bTABLE\\b");

  private final DdlTypeMapper mapper = new DdlTypeMapper();
  private final SqlStatementSplitter splitter = new SqlStatementSplitter();
  private final DdlPreprocessor preprocessor = new DdlPreprocessor();

  /** Inspects a SQL DDL file and returns one structure per {@code CREATE TABLE} (no nesting). */
  public Inspection inspect(Path sqlFile) {
    return inspect(sqlFile, NestingOptions.none());
  }

  /**
   * Inspects a SQL DDL file. With {@link NestingOptions#enabled()} the planner inverts {@code 1:n}
   * / {@code 1:1} foreign keys into nested {@code array[object[child]]} / {@code object[child]}
   * fields; otherwise every FK stays a flat {@code ref[parent.col]}. Strict by default — see {@link
   * #inspect(Path, NestingOptions, boolean)}.
   */
  public Inspection inspect(Path sqlFile, NestingOptions nesting) {
    return inspect(sqlFile, nesting, false);
  }

  /**
   * Inspects a SQL DDL file.
   *
   * <p>By default ({@code bestEffort = false}) any {@code CREATE TABLE} that cannot be parsed or
   * modelled aborts the whole inspection with an {@link InspectorException} and writes nothing: a
   * silently dropped table would leave {@code ref[...]} / {@code object[...]} references dangling
   * and break generation. With {@code bestEffort = true} such tables are skipped with a warning and
   * the parseable subset is returned. Statements that are not {@code CREATE TABLE} (indexes, views,
   * directives, …) are always skipped silently in both modes.
   */
  public Inspection inspect(Path sqlFile, NestingOptions nesting, boolean bestEffort) {
    List<String> rawStatements = splitter.split(readFile(sqlFile));

    List<TableInfo> tables = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (String raw : rawStatements) {
      processStatement(raw, tables, warnings, failures, bestEffort);
    }

    if (!bestEffort && !failures.isEmpty()) {
      throw new InspectorException(
          "Failed to parse "
              + failures.size()
              + " CREATE TABLE statement(s); no output written (a missing table would break "
              + "foreign-key references at generation time). Re-run with --best-effort to emit the "
              + "parseable subset. Offending: "
              + String.join("; ", failures));
    }

    if (tables.isEmpty()) {
      throw new InspectorException("No CREATE TABLE statements found in " + sqlFile);
    }

    if (nesting.enabled()) {
      Inspection nested = new NestingPlanner().plan(tables, nesting);
      List<String> all = new ArrayList<>(warnings);
      all.addAll(nested.warnings());
      return Inspection.of(nested.structures(), nested.comments(), all);
    }
    return toInspection(tables, warnings);
  }

  /**
   * Parses a single raw statement and, when it is a modellable {@code CREATE TABLE}, appends its
   * {@link TableInfo} to {@code tables}. Non-{@code CREATE TABLE} statements are skipped silently;
   * unparseable or unmodellable tables are recorded as failures.
   */
  private void processStatement(
      String raw,
      List<TableInfo> tables,
      List<String> warnings,
      List<String> failures,
      boolean bestEffort) {
    if (!CREATE_TABLE_QUICK.matcher(raw).find()) {
      return;
    }
    String cleaned = preprocessor.sanitize(raw);
    Statement statement;
    try {
      statement = CCJSqlParserUtil.parse(cleaned);
    } catch (JSQLParserException e) {
      recordFailure(failures, warnings, raw, bestEffort);
      return;
    }
    if (statement instanceof CreateTable createTable) {
      TableInfo table = toTableInfo(createTable, tables.size(), warnings);
      if (table != null) {
        tables.add(table);
      } else {
        // Parsed as a table but carries no modellable columns (e.g. CREATE TABLE ... AS SELECT).
        recordFailure(failures, warnings, raw, bestEffort);
      }
    }
    // Parsed to a non-CreateTable statement (index/view/etc.) — skip silently.
  }

  /** Flat (non-nested) projection: one structure per table, FK columns as {@code ref[]}. */
  private Inspection toInspection(List<TableInfo> tables, List<String> warnings) {
    List<DataStructure> structures = new ArrayList<>();
    Map<String, Map<String, String>> comments = new LinkedHashMap<>();
    for (TableInfo table : tables) {
      structures.add(new DataStructure(table.name(), null, table.data()));
      if (!table.comments().isEmpty()) {
        comments.put(table.name(), table.comments());
      }
    }
    return Inspection.of(structures, comments, warnings);
  }

  private TableInfo toTableInfo(CreateTable createTable, int order, List<String> warnings) {
    String name = Names.toSnakeCase(unquote(createTable.getTable().getName()));
    List<ColumnDefinition> columns = createTable.getColumnDefinitions();
    if (columns == null || columns.isEmpty()) {
      warnings.add("table '" + name + "' has no columns — skipped");
      return null;
    }

    Map<String, String> foreignKeys = tableForeignKeys(createTable);
    LinkedHashMap<String, FieldDefinition> data = new LinkedHashMap<>();
    LinkedHashMap<String, String> fieldComments = new LinkedHashMap<>();

    for (ColumnDefinition column : columns) {
      String columnName = unquote(column.getColumnName());
      String datatype = resolveForeignKey(columnName, column, foreignKeys).orElse(null);
      if (datatype == null) {
        ColDataType colType = column.getColDataType();
        MappedType mapped = mapper.map(columnName, baseTypeName(colType), typeArguments(colType));
        if (mapped.flagged()) {
          fieldComments.put(columnName, mapped.comment());
        }
        datatype = mapped.datatype();
      }
      data.put(columnName, new FieldDefinition(datatype, null));
    }

    return new TableInfo(
        name,
        data,
        fieldComments,
        keyConstraintColumns(createTable, columns, "PRIMARY"),
        keyConstraintColumns(createTable, columns, "UNIQUE"),
        foreignKeyRefs(createTable, columns),
        order);
  }

  /**
   * Collects the column names carrying a key constraint of the given kind ({@code PRIMARY} or
   * {@code UNIQUE}), from both inline column specs and table-level index constraints.
   */
  private Set<String> keyConstraintColumns(
      CreateTable createTable, List<ColumnDefinition> columns, String kind) {
    Set<String> result = new LinkedHashSet<>();
    for (ColumnDefinition column : columns) {
      List<String> specs = column.getColumnSpecs();
      if (specs != null && specs.stream().anyMatch(kind::equalsIgnoreCase)) {
        result.add(unquote(column.getColumnName()));
      }
    }
    List<Index> indexes = createTable.getIndexes();
    if (indexes != null) {
      for (Index index : indexes) {
        if (index instanceof ForeignKeyIndex) {
          continue;
        }
        String type = index.getType();
        if (type != null && type.toUpperCase(Locale.ROOT).startsWith(kind)) {
          index.getColumnsNames().forEach(c -> result.add(unquote(c)));
        }
      }
    }
    return result;
  }

  /** Collects FK constraints (table-level and inline {@code REFERENCES}) as structured edges. */
  private List<ForeignKeyRef> foreignKeyRefs(
      CreateTable createTable, List<ColumnDefinition> columns) {
    List<ForeignKeyRef> result = new ArrayList<>();
    List<Index> indexes = createTable.getIndexes();
    if (indexes != null) {
      for (Index index : indexes) {
        if (index instanceof ForeignKeyIndex fk) {
          result.add(
              new ForeignKeyRef(
                  fk.getColumnsNames().stream().map(this::unquote).toList(),
                  Names.toSnakeCase(unquote(fk.getTable().getName())),
                  fk.getReferencedColumnNames().stream().map(this::unquote).toList()));
        }
      }
    }
    for (ColumnDefinition column : columns) {
      inlineForeignKeyRef(unquote(column.getColumnName()), column.getColumnSpecs())
          .ifPresent(result::add);
    }
    return result;
  }

  /** Best-effort structured parse of an inline {@code ... REFERENCES table(column)} column spec. */
  private Optional<ForeignKeyRef> inlineForeignKeyRef(String columnName, List<String> specs) {
    if (specs == null) {
      return Optional.empty();
    }
    for (int i = 0; i < specs.size(); i++) {
      if (!"REFERENCES".equalsIgnoreCase(specs.get(i)) || i + 1 >= specs.size()) {
        continue;
      }
      String token = specs.get(i + 1);
      String table = token;
      String referenced = "id";
      int paren = token.indexOf('(');
      if (paren >= 0) {
        table = token.substring(0, paren);
        referenced = token.substring(paren + 1).replace(")", "");
      } else if (i + 2 < specs.size() && specs.get(i + 2).startsWith("(")) {
        referenced = PARENS.matcher(specs.get(i + 2)).replaceAll("");
      }
      return Optional.of(
          new ForeignKeyRef(
              List.of(columnName),
              Names.toSnakeCase(unquote(table)),
              List.of(unquote(referenced))));
    }
    return Optional.empty();
  }

  /** Resolves a foreign-key reference for a column from table-level then inline constraints. */
  private Optional<String> resolveForeignKey(
      String columnName, ColumnDefinition column, Map<String, String> tableForeignKeys) {
    String key = columnName.toLowerCase(Locale.ROOT);
    if (tableForeignKeys.containsKey(key)) {
      return Optional.of(tableForeignKeys.get(key));
    }
    return inlineForeignKey(column.getColumnSpecs());
  }

  /** Maps local column name (lowercased) to {@code ref[table.column]} for table-level FKs. */
  private Map<String, String> tableForeignKeys(CreateTable createTable) {
    Map<String, String> result = new LinkedHashMap<>();
    List<Index> indexes = createTable.getIndexes();
    if (indexes == null) {
      return result;
    }
    for (Index index : indexes) {
      if (index instanceof ForeignKeyIndex fk) {
        String refTable = Names.toSnakeCase(unquote(fk.getTable().getName()));
        List<String> localColumns = fk.getColumnsNames();
        List<String> referencedColumns = fk.getReferencedColumnNames();
        for (int i = 0; i < localColumns.size(); i++) {
          String referenced = columnAt(referencedColumns, i);
          result.put(
              unquote(localColumns.get(i)).toLowerCase(Locale.ROOT),
              "ref[" + refTable + "." + unquote(referenced) + ", " + Defaults.REF_POOL + "]");
        }
      }
    }
    return result;
  }

  /** Best-effort parse of an inline {@code ... REFERENCES table(column)} column spec. */
  private Optional<String> inlineForeignKey(List<String> specs) {
    if (specs == null) {
      return Optional.empty();
    }
    for (int i = 0; i < specs.size(); i++) {
      if (!"REFERENCES".equalsIgnoreCase(specs.get(i)) || i + 1 >= specs.size()) {
        continue;
      }
      String token = specs.get(i + 1);
      String table = token;
      String referenced = "id";
      int paren = token.indexOf('(');
      if (paren >= 0) {
        table = token.substring(0, paren);
        referenced = token.substring(paren + 1).replace(")", "");
      } else if (i + 2 < specs.size() && specs.get(i + 2).startsWith("(")) {
        referenced = PARENS.matcher(specs.get(i + 2)).replaceAll("");
      }
      return Optional.of(
          "ref["
              + Names.toSnakeCase(unquote(table))
              + "."
              + unquote(referenced)
              + ", "
              + Defaults.REF_POOL
              + "]");
    }
    return Optional.empty();
  }

  /**
   * The base type name without arguments. JSQLParser 5.x returns parameterized types inline (e.g.
   * {@code "VARCHAR (255)"}), so the name is the text before the first parenthesis.
   */
  private String baseTypeName(ColDataType colType) {
    String raw = colType.getDataType();
    if (raw == null) {
      return "";
    }
    int paren = raw.indexOf('(');
    return (paren >= 0 ? raw.substring(0, paren) : raw).trim();
  }

  /** Type arguments, from the dedicated list when present, else parsed from the inline form. */
  private List<String> typeArguments(ColDataType colType) {
    List<String> declared = colType.getArgumentsStringList();
    if (declared != null && !declared.isEmpty()) {
      return declared;
    }
    String raw = colType.getDataType();
    if (raw == null) {
      return List.of();
    }
    int open = raw.indexOf('(');
    int close = raw.lastIndexOf(')');
    if (open < 0 || close <= open) {
      return List.of();
    }
    return Arrays.stream(COMMA.split(raw.substring(open + 1, close)))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  private String columnAt(List<String> columns, int index) {
    if (columns == null || columns.isEmpty()) {
      return "id";
    }
    return index < columns.size() ? columns.get(index) : columns.get(0);
  }

  private String unquote(String identifier) {
    if (identifier == null) {
      return "";
    }
    return IDENT_QUOTES.matcher(identifier).replaceAll("").trim();
  }

  /**
   * Records an unparseable / unmodellable {@code CREATE TABLE}. Always tracked so strict mode can
   * abort; in best-effort mode it also surfaces as a warning so the skipped table stays visible.
   */
  private void recordFailure(
      List<String> failures, List<String> warnings, String raw, boolean bestEffort) {
    String snippet = shortSnippet(raw);
    failures.add(snippet);
    if (bestEffort) {
      warnings.add("skipped unparseable CREATE TABLE: " + snippet);
    }
  }

  private String shortSnippet(String statement) {
    String oneLine = statement.replace('\n', ' ').replace('\r', ' ');
    return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 60) + "...";
  }

  private String readFile(Path sqlFile) {
    try {
      return Files.readString(sqlFile);
    } catch (IOException e) {
      throw new InspectorException("Failed to read SQL DDL: " + sqlFile, e);
    }
  }
}
