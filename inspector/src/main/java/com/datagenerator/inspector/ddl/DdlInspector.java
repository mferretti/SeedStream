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

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
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

  private final DdlTypeMapper mapper = new DdlTypeMapper();

  /** Inspects a SQL DDL file and returns one structure per {@code CREATE TABLE}. */
  public Inspection inspect(Path sqlFile) {
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(readFile(sqlFile));
    } catch (JSQLParserException e) {
      throw new InspectorException("Failed to parse SQL DDL: " + sqlFile, e);
    }

    List<DataStructure> structures = new ArrayList<>();
    List<String> inferred = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (Statement statement : statements) {
      if (statement instanceof CreateTable createTable) {
        DataStructure structure = toStructure(createTable, inferred, warnings);
        if (structure != null) {
          structures.add(structure);
        }
      }
    }

    if (structures.isEmpty()) {
      throw new InspectorException("No CREATE TABLE statements found in " + sqlFile);
    }
    return new Inspection(structures, inferred, warnings);
  }

  private DataStructure toStructure(
      CreateTable createTable, List<String> inferred, List<String> warnings) {
    String name = Names.toSnakeCase(unquote(createTable.getTable().getName()));
    List<ColumnDefinition> columns = createTable.getColumnDefinitions();
    if (columns == null || columns.isEmpty()) {
      warnings.add("table '" + name + "' has no columns — skipped");
      return null;
    }

    Map<String, String> foreignKeys = tableForeignKeys(createTable);
    Map<String, FieldDefinition> data = new LinkedHashMap<>();

    for (ColumnDefinition column : columns) {
      String columnName = unquote(column.getColumnName());
      String datatype = resolveForeignKey(columnName, column, foreignKeys).orElse(null);
      if (datatype == null) {
        ColDataType colType = column.getColDataType();
        MappedType mapped = mapper.map(columnName, baseTypeName(colType), typeArguments(colType));
        if (mapped.inferred()) {
          inferred.add(name + "." + columnName);
        }
        datatype = mapped.datatype();
      }
      data.put(columnName, new FieldDefinition(datatype, null));
    }

    return new DataStructure(name, null, data);
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
              "ref[" + refTable + "." + unquote(referenced) + "]");
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
        referenced = specs.get(i + 2).replaceAll("[()]", "");
      }
      return Optional.of(
          "ref[" + Names.toSnakeCase(unquote(table)) + "." + unquote(referenced) + "]");
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
    return Arrays.stream(raw.substring(open + 1, close).split(","))
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
    return identifier.replaceAll("[\"`\\[\\]]", "").trim();
  }

  private String readFile(Path sqlFile) {
    try {
      return Files.readString(sqlFile);
    } catch (IOException e) {
      throw new InspectorException("Failed to read SQL DDL: " + sqlFile, e);
    }
  }
}
