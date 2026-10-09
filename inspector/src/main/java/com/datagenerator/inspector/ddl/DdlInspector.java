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
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
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

  private static final Pattern COMMA = Pattern.compile(",");
  // Inline "REFERENCES table(col)": group 1 = table, group 2 = referenced column (optional).
  private static final Pattern INLINE_REFERENCE =
      Pattern.compile("(?i)REFERENCES\\s+([\\w.\"`]+)(?:\\s*\\(\\s*([\\w\"`]+)\\s*\\))?");
  private static final Pattern SORT_DIRECTION = Pattern.compile("(?i)\\s+(ASC|DESC)$");
  private static final Pattern IDENT_QUOTES = Pattern.compile("[\"`\\[\\]]");
  private static final Pattern CREATE_TABLE_QUICK =
      Pattern.compile("(?is)\\bCREATE\\b.{0,50}\\bTABLE\\b");

  private static final Set<String> SERIAL_TYPES =
      Set.of("SERIAL", "BIGSERIAL", "SMALLSERIAL", "SERIAL4", "SERIAL8");
  private static final Set<String> UUID_TYPES = Set.of("UUID", "UNIQUEIDENTIFIER");
  private static final String NOT_ENFORCED = "UNIQUE/PRIMARY KEY not enforced — values may collide";

  /** Key constraints and type facts of one table, kept aside until nesting is decided. */
  private record TableKeys(
      List<List<String>> primary,
      List<List<String>> uniques,
      Set<String> serialColumns,
      Set<String> uuidColumns,
      Set<String> dbAssignedColumns) {}

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
    Map<String, TableKeys> keys = new LinkedHashMap<>();
    for (String raw : rawStatements) {
      processStatement(raw, tables, keys, warnings, failures, bestEffort);
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

    Map<String, Set<String>> referenced = new LinkedHashMap<>();
    for (TableInfo t : tables) {
      for (var fk : t.foreignKeys()) {
        for (String c : fk.refColumns()) {
          referenced
              .computeIfAbsent(lower(fk.refTable()) + "." + lower(c), k -> new TreeSet<>())
              .add(t.name());
        }
      }
    }
    Consumer<TableInfo> keyMapper =
        table -> applyKeys(table, keys.get(table.name()), warnings, referenced);
    if (nesting.enabled()) {
      Inspection nested = new NestingPlanner().plan(tables, nesting, keyMapper);
      List<String> all = new ArrayList<>(warnings);
      all.addAll(nested.warnings());
      return Inspection.of(nested.structures(), nested.comments(), all);
    }
    tables.forEach(keyMapper);
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
      Map<String, TableKeys> keys,
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
      TableInfo table = toTableInfo(createTable, tables.size(), warnings, keys);
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

  private TableInfo toTableInfo(
      CreateTable createTable, int order, List<String> warnings, Map<String, TableKeys> keys) {
    String name = Names.toSnakeCase(unquote(createTable.getTable().getName()));
    List<ColumnDefinition> columns = createTable.getColumnDefinitions();
    if (columns == null || columns.isEmpty()) {
      warnings.add("table '" + name + "' has no columns — skipped");
      return null;
    }

    Map<String, String> foreignKeys = tableForeignKeys(createTable);
    LinkedHashMap<String, FieldDefinition> data = new LinkedHashMap<>();
    LinkedHashMap<String, String> fieldComments = new LinkedHashMap<>();
    Set<String> serialColumns = new LinkedHashSet<>();
    Set<String> uuidColumns = new LinkedHashSet<>();
    Set<String> dbAssigned = new LinkedHashSet<>();

    for (ColumnDefinition column : columns) {
      String columnName = unquote(column.getColumnName());
      if (column.getColumnSpecs() != null) {
        String specs = String.join(" ", column.getColumnSpecs()).toUpperCase(Locale.ROOT);
        if (specs.contains("ALWAYS") && specs.contains("IDENTITY")) {
          dbAssigned.add(columnName.toLowerCase(Locale.ROOT));
        }
      }
      String datatype = resolveForeignKey(columnName, column, foreignKeys).orElse(null);
      if (datatype == null) {
        ColDataType colType = column.getColDataType();
        String sqlType = baseTypeName(colType).toUpperCase(Locale.ROOT);
        if (SERIAL_TYPES.contains(sqlType)) {
          serialColumns.add(columnName.toLowerCase(Locale.ROOT));
        } else if (UUID_TYPES.contains(sqlType)) {
          uuidColumns.add(columnName.toLowerCase(Locale.ROOT));
        }
        MappedType mapped = mapper.map(columnName, baseTypeName(colType), typeArguments(colType));
        if (mapped.flagged()) {
          fieldComments.put(columnName, mapped.comment());
        }
        datatype = mapped.datatype();
      }
      data.put(columnName, new FieldDefinition(datatype, null));
    }

    List<List<String>> primary = keyConstraints(createTable, columns, "PRIMARY");
    List<List<String>> uniques = keyConstraints(createTable, columns, "UNIQUE");
    keys.put(name, new TableKeys(primary, uniques, serialColumns, uuidColumns, dbAssigned));
    return new TableInfo(
        name,
        data,
        fieldComments,
        flatten(primary),
        flatten(uniques),
        foreignKeyRefs(createTable, columns),
        order);
  }

  private Set<String> flatten(List<List<String>> constraints) {
    Set<String> result = new LinkedHashSet<>();
    constraints.forEach(result::addAll);
    return result;
  }

  /**
   * Collects one column list per key constraint of the given kind ({@code PRIMARY} or {@code
   * UNIQUE}): each inline column spec is a single-column constraint, each table-level index one
   * (possibly composite) constraint.
   */
  private List<List<String>> keyConstraints(
      CreateTable createTable, List<ColumnDefinition> columns, String kind) {
    List<List<String>> result = new ArrayList<>();
    for (ColumnDefinition column : columns) {
      List<String> specs = column.getColumnSpecs();
      if (specs != null && specs.stream().anyMatch(kind::equalsIgnoreCase)) {
        result.add(List.of(unquote(column.getColumnName())));
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
          result.add(index.getColumnsNames().stream().map(this::keyColumn).toList());
        }
      }
    }
    return result;
  }

  /**
   * Rewrites key columns of a table so generated data honours PRIMARY KEY / UNIQUE constraints:
   * integer keys become {@code serial} / {@code unique[1..count]} / {@code ref[..., unique]},
   * anything else is left as-is with a "not enforced" comment. Each column gets at most one key
   * role (PK first, then UNIQUE in declaration order). See {@code docs/INSPECT-V1-SPEC.md}.
   */
  private void applyKeys(
      TableInfo table, TableKeys keys, List<String> warnings, Map<String, Set<String>> referenced) {
    if (keys == null) {
      return;
    }
    Set<String> foreignColumns = new LinkedHashSet<>();
    // Only columns still emitted as ref[...]; nesting may have rewritten or dropped an FK column.
    table.foreignKeys().stream()
        .flatMap(fk -> fk.localColumns().stream())
        .filter(c -> isRef(table.data().get(actualKey(table, c))))
        .forEach(c -> foreignColumns.add(lower(c)));
    Set<String> claimed = new LinkedHashSet<>();
    keys.primary()
        .forEach(
            c ->
                applyKey(
                    table, keys, new KeySpec("pk", true, c), claimed, foreignColumns, warnings));
    for (int i = 0; i < keys.uniques().size(); i++) {
      KeySpec spec = new KeySpec("uq" + (i + 1), false, keys.uniques().get(i));
      applyKey(table, keys, spec, claimed, foreignColumns, warnings);
    }
    for (String column : List.copyOf(table.data().keySet())) {
      String key = lower(column);
      if (keys.serialColumns().contains(key)
          && !claimed.contains(key)
          && !foreignColumns.contains(key)) {
        setKeyType(table, column, "serial");
      }
    }
    handleDbAssigned(table, keys, warnings, referenced);
  }

  /** GENERATED ALWAYS identity single-column PKs: omit unless some FK references them. */
  private void handleDbAssigned(
      TableInfo table, TableKeys keys, List<String> warnings, Map<String, Set<String>> referenced) {
    for (List<String> pk : keys.primary()) {
      if (pk.size() != 1 || !keys.dbAssignedColumns().contains(lower(pk.get(0)))) {
        continue;
      }
      String column = pk.get(0);
      String label = table.name() + "." + column;
      Set<String> refs = referenced.get(lower(table.name()) + "." + lower(column));
      if (refs == null) {
        String actual = actualKey(table, column);
        table.data().remove(actual);
        table.comments().remove(actual);
        warnings.add(
            label
                + ": GENERATED ALWAYS AS IDENTITY — omitted (DB assigns it); any FK referencing it"
                + " needs DB-assigned ordered generation (#400)");
      } else {
        warnings.add(
            label
                + ": GENERATED ALWAYS AS IDENTITY but referenced by "
                + refs
                + " — kept as serial; needs DB-assigned ordered generation (#400)");
      }
    }
  }

  /** One PRIMARY KEY / UNIQUE constraint: its group name, kind and declared columns. */
  private record KeySpec(String group, boolean primary, List<String> declared) {}

  private void applyKey(
      TableInfo table,
      TableKeys keys,
      KeySpec spec,
      Set<String> claimed,
      Set<String> foreignColumns,
      List<String> warnings) {
    String label =
        (spec.primary() ? "PRIMARY KEY(" : "UNIQUE(") + String.join(", ", spec.declared()) + ")";
    List<String> columns = claimColumns(table, spec, label, claimed, warnings);
    if (columns.isEmpty()) {
      return;
    }
    boolean enforceable =
        columns.stream()
            .allMatch(
                c ->
                    foreignColumns.contains(lower(c))
                        || isIntegerType(table.data().get(actualKey(table, c))));
    if (!enforceable) {
      boolean singleUuid =
          columns.size() == 1 && keys.uuidColumns().contains(lower(columns.get(0)));
      if (!singleUuid) {
        columns.forEach(c -> addComment(table, c, NOT_ENFORCED));
        warnings.add(
            table.name()
                + ": "
                + label
                + " not enforced — non-integer column(s), values may collide");
      }
      return;
    }
    boolean single = columns.size() == 1;
    for (String column : columns) {
      setKeyType(
          table,
          column,
          keyDatatype(table, spec, column, single, foreignColumns.contains(lower(column))));
    }
  }

  /** Columns of the key not already claimed by an earlier key; warns about the ones that were. */
  private List<String> claimColumns(
      TableInfo table, KeySpec spec, String label, Set<String> claimed, List<String> warnings) {
    List<String> columns = new ArrayList<>();
    for (String column : spec.declared()) {
      if (claimed.add(lower(column))) {
        columns.add(column);
      } else {
        warnings.add(
            table.name()
                + "."
                + column
                + ": also in "
                + label
                + " — only the first key is enforced");
      }
    }
    return columns;
  }

  private String keyDatatype(
      TableInfo table, KeySpec spec, String column, boolean single, boolean foreign) {
    if (foreign) {
      String target = refTarget(table.data().get(actualKey(table, column)).getDatatype());
      return "ref["
          + target
          + ", "
          + Defaults.REF_POOL
          + ", unique"
          + (single ? "" : "=" + spec.group())
          + "]";
    }
    if (single) {
      return spec.primary() ? "serial" : "unique[" + Defaults.REF_POOL + "]";
    }
    return "unique[" + spec.group() + ", " + Defaults.REF_POOL + "]";
  }

  private void setKeyType(TableInfo table, String column, String datatype) {
    String key = actualKey(table, column);
    table.data().put(key, new FieldDefinition(datatype, null));
    table.comments().remove(key); // default-range / name-hint notes no longer apply
  }

  private void addComment(TableInfo table, String column, String comment) {
    String key = actualKey(table, column);
    table.comments().merge(key, comment, (old, added) -> old + "; " + added);
  }

  private String actualKey(TableInfo table, String column) {
    return table.data().keySet().stream()
        .filter(k -> k.equalsIgnoreCase(column))
        .findFirst()
        .orElse(column);
  }

  private boolean isIntegerType(FieldDefinition field) {
    return field != null && field.getDatatype().startsWith("int[");
  }

  private boolean isRef(FieldDefinition field) {
    return field != null && field.getDatatype().startsWith("ref[");
  }

  /** {@code ref[customers.id, 1..count]} → {@code customers.id}. */
  private String refTarget(String refDatatype) {
    return refDatatype.substring("ref[".length(), refDatatype.indexOf(',')).trim();
  }

  private String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
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
    return parseInlineReference(specs)
        .map(
            ref ->
                new ForeignKeyRef(List.of(columnName), Names.toSnakeCase(ref[0]), List.of(ref[1])));
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
    return parseInlineReference(specs)
        .map(
            ref ->
                "ref[" + Names.toSnakeCase(ref[0]) + "." + ref[1] + ", " + Defaults.REF_POOL + "]");
  }

  /**
   * Extracts {@code [table, referencedColumn]} from an inline {@code ... REFERENCES table(col)}
   * column spec. Tolerant of JSQLParser tokenization: 5.3 split {@code REFERENCES}, {@code table}
   * and {@code (col)} into separate spec tokens, while 5.4 emits the whole clause as one token.
   * Joining the specs and matching against the clause handles both. Referenced column defaults to
   * {@code id} when the DDL omits it. Returned names are unquoted, table not yet snake-cased.
   */
  private Optional<String[]> parseInlineReference(List<String> specs) {
    if (specs == null || specs.isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = INLINE_REFERENCE.matcher(String.join(" ", specs));
    if (!matcher.find()) {
      return Optional.empty();
    }
    String referenced = matcher.group(2) != null ? matcher.group(2) : "id";
    return Optional.of(new String[] {unquote(matcher.group(1)), unquote(referenced)});
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

  /**
   * A key constraint's column, without its sort direction: {@code PRIMARY KEY ([id] ASC)} (the SSMS
   * default) reports the column as {@code "id ASC"}, which matched no field (#377).
   */
  private String keyColumn(String indexColumn) {
    return unquote(SORT_DIRECTION.matcher(indexColumn.trim()).replaceAll(""));
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
