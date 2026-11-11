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

import static com.datagenerator.inspector.ddl.TableModel.unquote;

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.inspector.ddl.CheckConditions.ColumnChecks;
import com.datagenerator.inspector.ddl.CheckConstraintMapper.ColumnType;
import com.datagenerator.inspector.ddl.NestingPlanner.ForeignKeyRef;
import com.datagenerator.inspector.ddl.NestingPlanner.TableInfo;
import com.datagenerator.inspector.ddl.TableModel.CheckCondition;
import com.datagenerator.inspector.ddl.TableModel.Column;
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
import java.util.stream.Collectors;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;

/**
 * Reads a SQL DDL script and maps every {@code CREATE TABLE} to a SeedStream {@link DataStructure}.
 * Foreign keys (table-level and inline {@code REFERENCES}) become {@code ref[table.column]}. See
 * {@code docs/INSPECT-V1-SPEC.md}.
 */
public class DdlInspector {

  // A comma outside single quotes (an even number of quotes follows), so ENUM('a,b','c') is 2 args.
  private static final Pattern TOP_LEVEL_COMMA = Pattern.compile(",(?=(?:[^']*'[^']*')*[^']*$)");
  // Inline "REFERENCES table(col)": group 1 = table, group 2 = referenced column (optional).
  private static final Pattern INLINE_REFERENCE =
      Pattern.compile("(?i)REFERENCES\\s+([\\w.\"`]+)(?:\\s*\\(\\s*([\\w\"`]+)\\s*\\))?");
  private static final Pattern IDENT_QUOTES = Pattern.compile("[\"`\\[\\]]");
  // Loose "mentions CREATE ... TABLE" check, run on comment/literal-stripped text. Anything that
  // matches and is not a known non-table CREATE goes to the parser, so a real table is modelled or
  // fails loudly — never silently dropped.
  private static final Pattern CREATE_TABLE_QUICK =
      Pattern.compile("(?is)\\bCREATE\\b.{0,50}\\bTABLE\\b");
  static final String DIRECTIVES = "(?:(?:SET|PROMPT|USE|EXEC)\\b[^\\n]*\\n\\s*)*";
  private static final String ROUTINE_MODIFIERS =
      "(?:(?:DEFINER\\s*=\\s*\\S+|EDITIONABLE|NONEDITIONABLE)\\s+)*";

  /**
   * Statements skipped silently although they mention {@code CREATE ... TABLE}: routine, type,
   * package and event bodies ({@code CREATE [OR REPLACE|OR ALTER] PROCEDURE|PROC|FUNCTION|TRIGGER|
   * TYPE|...}), {@code ALTER PROCEDURE|PROC|FUNCTION|TRIGGER} bodies, publications ({@code FOR
   * TABLE}) and subscriptions, and privilege statements ({@code GRANT CREATE TABLE ...}). Body-less
   * kinds (INDEX, VIEW, ...) are deliberately not listed: they never contain {@code CREATE ...
   * TABLE}, and an unterminated one must not swallow a table. {@code ALTER TABLE} is not matched
   * here; it is handled before this check.
   */
  private static final Pattern NON_TABLE_CREATE =
      Pattern.compile(
          "(?is)\\A\\s*"
              + DIRECTIVES
              + "(?:(?:GRANT|REVOKE|DENY)\\b"
              + "|CREATE\\s+(?:OR\\s+(?:REPLACE|ALTER)\\s+)?"
              + ROUTINE_MODIFIERS
              + "(?:FUNCTION|PROCEDURE|PROC|TRIGGER|PACKAGE|EVENT|PUBLICATION|SUBSCRIPTION"
              + "|TYPE\\s+\\S+\\s+AS\\s+TABLE)\\b"
              + "|ALTER\\s+"
              + ROUTINE_MODIFIERS
              + "(?:PROCEDURE|PROC|FUNCTION|TRIGGER)\\b)");

  // CREATE TABLE <name> followed by AS / PARTITION OF / LIKE / (LIKE ...): no column list to model.
  private static final String IDENT_PART =
      "(?:\"[^\"]*\"|`[^`]*`|\\[[^\\]]*\\]|[\\p{L}\\p{N}_$#@]+)";
  private static final Pattern NO_COLUMN_LIST =
      Pattern.compile(
          "(?is)\\A\\s*"
              + DIRECTIVES
              + "CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:GLOBAL|LOCAL)\\s+)?"
              + "(?:(?:TEMP|TEMPORARY|UNLOGGED)\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
              + "(?<name>"
              + IDENT_PART
              + "(?:\\."
              + IDENT_PART
              + ")*)(?:\\s+(?:AS|PARTITION\\s+OF|LIKE)\\b|\\s*\\(\\s*LIKE\\b)");

  // PostgreSQL "INHERITS (a, b)" right after the column list, run on comment/literal-free text.
  static final String QUALIFIED_NAME = IDENT_PART + "(?:\\." + IDENT_PART + ")*";
  private static final Pattern INHERITS_CLAUSE =
      Pattern.compile(
          "(?is)\\A\\s*INHERITS\\s*\\(\\s*(?<parents>"
              + QUALIFIED_NAME
              + "(?:\\s*,\\s*"
              + QUALIFIED_NAME
              + ")*)\\s*\\)");
  private static final Pattern QUALIFIED_NAME_PATTERN = Pattern.compile(QUALIFIED_NAME);
  // Stands in for "()" so the parser accepts a child without own columns; never reaches the model.
  private static final Pattern EMPTY_COLUMN_LIST = Pattern.compile("\\(\\s*\\)");
  private static final String NO_OWN_COLUMNS = "__seedstream_inherits__";

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
      Set<String> textColumns,
      Map<String, Long> ceilings,
      Set<String> dbAssignedColumns,
      Set<String> identityColumns,
      Map<String, ColumnChecks> checks) {}

  private final DdlTypeMapper mapper = new DdlTypeMapper();
  private final SqlStatementSplitter splitter = new SqlStatementSplitter();
  private final DdlPreprocessor preprocessor = new DdlPreprocessor();
  private final EmbeddedStatementExpander expander = new EmbeddedStatementExpander();

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
    List<String> rawStatements =
        splitter.split(readFile(sqlFile)).stream()
            .flatMap(chunk -> expander.expand(chunk).stream())
            .toList();

    // Phase 1: every CREATE TABLE becomes a model (no datatypes yet); ALTER TABLEs wait their turn.
    List<TableModel> models = new ArrayList<>();
    List<String> alters = new ArrayList<>();
    List<String> uniqueIndexes = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (String raw : rawStatements) {
      if (UniqueIndexApplier.isUniqueIndex(SqlScan.stripCommentsAndLiterals(raw))) {
        uniqueIndexes.add(raw); // applied with the ALTERs; never a CREATE TABLE failure
      } else {
        processStatement(raw, models, alters, warnings, failures, bestEffort);
      }
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

    if (models.isEmpty()) {
      throw new InspectorException("No CREATE TABLE statements found in " + sqlFile);
    }

    // Phase 2: ALTER TABLEs complete the models, references without columns resolve to the target
    // PK, and only then are datatypes rendered, keys applied and tables nested.
    Map<String, TableModel> byName = new LinkedHashMap<>();
    models.forEach(m -> byName.put(m.name, m));
    expandInheritance(models, byName, warnings);
    new AlterTableApplier().apply(alters, byName, warnings);
    new UniqueIndexApplier().apply(uniqueIndexes, byName, warnings);
    resolveForeignKeys(models, byName, warnings);
    Map<String, TableKeys> keys = new LinkedHashMap<>();
    List<TableInfo> tables =
        models.stream().map(m -> toTableInfo(m, keys, byName, warnings)).toList();

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
    warnSnakeCaseCollisions(tables, warnings);
    // cycle[...] would be a startup error under nesting (UniqueFieldValidator), so nesting keeps
    // enum.
    boolean cycleKeys = !nesting.enabled();
    Set<String> roots = new LinkedHashSet<>();
    Consumer<TableInfo> keyMapper =
        table -> {
          roots.add(table.name());
          applyKeys(table, keys.get(table.name()), warnings, referenced, cycleKeys);
        };
    List<String> all = new ArrayList<>();
    if (nesting.enabled()) {
      all.addAll(new NestingPlanner().plan(tables, nesting, keyMapper).warnings());
    } else {
      tables.forEach(keyMapper);
    }
    all.addAll(0, warnings);
    List<TableInfo> topLevel = tables.stream().filter(t -> roots.contains(t.name())).toList();
    boolean bare = bareRefs(tables, topLevel);
    Inspection flat = toInspection(tables, all);
    return new Inspection(
        flat.structures(),
        flat.comments(),
        flat.warnings(),
        Map.of(),
        bare ? topLevel.stream().map(TableInfo::name).toList() : List.of());
  }

  /**
   * #442: rewrites {@code ref[p.c, 1..count...]} to {@code ref[p.c...]} where the engine can
   * resolve the pool from the parent's key: single-column FK to another top-level table whose
   * referenced column ended up as serial / a plain unique range / a unique FK key. Returns whether
   * any ref was rewritten.
   */
  private boolean bareRefs(List<TableInfo> tables, List<TableInfo> topLevel) {
    Map<String, TableInfo> byName = new LinkedHashMap<>();
    topLevel.forEach(t -> byName.put(lower(t.name()), t));
    boolean rewritten = false;
    for (TableInfo table : tables) {
      for (ForeignKeyRef fk : table.foreignKeys()) {
        TableInfo parent = byName.get(lower(fk.refTable()));
        if (parent == null
            || parent == table
            || fk.localColumns().size() != 1
            || fk.refColumns().size() != 1) {
          continue;
        }
        String column = actualKey(table, fk.localColumns().get(0));
        FieldDefinition field = table.data().get(column);
        List<String> parentColumns =
            parent.data().keySet().stream()
                .filter(k -> Names.toSnakeCase(k).equals(Names.toSnakeCase(fk.refColumns().get(0))))
                .toList();
        String suffix = ", " + Defaults.REF_POOL;
        if (isRef(field)
            && field.getDatatype().contains(suffix)
            && parentColumns.size() == 1
            && resolvableKey(parent.data().get(parentColumns.get(0)).getDatatype())) {
          String bare = field.getDatatype().replaceFirst(Pattern.quote(suffix), "");
          if (bare.startsWith("ref[parent.") && bare.indexOf(',') < 0) {
            continue; // ref[parent.c] is the nested parent-record reference, not table "parent"
          }
          table.data().put(column, new FieldDefinition(bare, null));
          rewritten = true;
        }
      }
    }
    return rewritten;
  }

  private static boolean resolvableKey(String datatype) {
    return "serial".equals(datatype)
        || datatype.startsWith("serial[")
        || (datatype.startsWith("unique[") && datatype.indexOf(',') < 0)
        || (datatype.startsWith("ref[") && datatype.endsWith(", unique]"));
  }

  /**
   * The emitted {@code ref[table.column]} target is the snake_cased column, so two columns of the
   * referenced table that snake-case alike (documentation-only target) are worth a warning (#445).
   */
  private void warnSnakeCaseCollisions(List<TableInfo> tables, List<String> warnings) {
    Map<String, TableInfo> byName = new LinkedHashMap<>();
    tables.forEach(t -> byName.put(lower(t.name()), t));
    Set<String> seen = new LinkedHashSet<>();
    for (TableInfo t : tables) {
      for (ForeignKeyRef fk : t.foreignKeys()) {
        TableInfo target = byName.get(lower(fk.refTable()));
        for (String column : fk.refColumns()) {
          List<String> clashing =
              target == null
                  ? List.of()
                  : target.data().keySet().stream()
                      .filter(k -> Names.toSnakeCase(k).equals(Names.toSnakeCase(column)))
                      .toList();
          if (clashing.size() > 1 && seen.add(target.name() + "." + Names.toSnakeCase(column))) {
            warnings.add(
                target.name()
                    + ": columns "
                    + clashing
                    + " all snake-case to '"
                    + Names.toSnakeCase(column)
                    + "' — ref[...] targets to it are ambiguous (documentation only)");
          }
        }
      }
    }
  }

  /**
   * Parses a single raw statement and, when it is a modellable {@code CREATE TABLE}, appends its
   * {@link TableModel} to {@code models}; an {@code ALTER TABLE} is set aside in {@code alters}.
   * Other statements are skipped silently; unparseable or unmodellable tables are recorded as
   * failures.
   */
  private void processStatement(
      String raw,
      List<TableModel> models,
      List<String> alters,
      List<String> warnings,
      List<String> failures,
      boolean bestEffort) {
    String code = SqlScan.stripCommentsAndLiterals(raw);
    if (AlterTableApplier.isAlterTable(code)) {
      alters.add(raw); // before the CREATE TABLE check: ALTER TABLE "create table" is no CREATE
      return;
    }
    if (!CREATE_TABLE_QUICK.matcher(code).find() || NON_TABLE_CREATE.matcher(code).find()) {
      return; // not a CREATE TABLE (index, view, function, directive, ...) — skip silently
    }
    Matcher noColumns = NO_COLUMN_LIST.matcher(code);
    if (noColumns.find()) {
      String name = noColumns.group("name");
      warnings.add(
          "table '"
              + Names.toSnakeCase(unquote(name.substring(name.lastIndexOf('.') + 1)))
              + "' is defined without a column list (AS SELECT / PARTITION OF / LIKE) — skipped");
      return;
    }
    String cleaned = preprocessor.sanitize(raw);
    List<String> parents = inheritedParents(code);
    if (!parents.isEmpty()) {
      cleaned = withPlaceholderColumn(cleaned);
    }
    Statement statement;
    try {
      statement = CCJSqlParserUtil.parse(cleaned);
    } catch (JSQLParserException e) {
      recordFailure(failures, warnings, raw, bestEffort);
      return;
    }
    if (statement instanceof CreateTable createTable) {
      TableModel table = toTableModel(createTable, models.size(), warnings, parents);
      if (table != null) {
        table.addChecks(raw);
      }
      if (table != null) {
        models.add(table);
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

  /**
   * Snake-cased parents of a PostgreSQL {@code INHERITS (...)} clause, found on
   * comment/literal-free {@code code} right after the balanced column list; empty when there is
   * none.
   */
  private static List<String> inheritedParents(String code) {
    int depth = 0;
    for (int i = 0; i < code.length(); i++) {
      char c = code.charAt(i);
      if (c == '"' || c == '`') {
        i = SqlScan.skipDelimited(code, i, c) - 1;
      } else if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        Matcher clause = INHERITS_CLAUSE.matcher(code.substring(i + 1));
        List<String> parents = new ArrayList<>();
        if (clause.find()) {
          Matcher name = QUALIFIED_NAME_PATTERN.matcher(clause.group("parents"));
          while (name.find()) {
            String qualified = name.group();
            parents.add(
                Names.toSnakeCase(unquote(qualified.substring(qualified.lastIndexOf('.') + 1))));
          }
        }
        return parents;
      }
    }
    return List.of();
  }

  /** {@code ()} is not valid for the parser: gives an empty column list a throw-away column. */
  private static String withPlaceholderColumn(String sql) {
    int open = sql.indexOf('(');
    Matcher empty = EMPTY_COLUMN_LIST.matcher(sql).region(Math.max(open, 0), sql.length());
    return open >= 0 && empty.lookingAt()
        ? sql.substring(0, open) + "(" + NO_OWN_COLUMNS + " int)" + sql.substring(empty.end())
        : sql;
  }

  /**
   * Gives every child the columns of its {@code INHERITS} parents (recursively, parents in clause
   * order, same-named columns once at their first position, a redeclaring child's type winning),
   * then its own. Keys, identity and defaults are not inherited. A table on an inheritance cycle
   * keeps its own columns; a child left without any column is dropped.
   */
  private void expandInheritance(
      List<TableModel> models, Map<String, TableModel> byName, List<String> warnings) {
    Map<String, List<Column>> effective = new LinkedHashMap<>();
    Set<String> inheritanceWarnings = new LinkedHashSet<>();
    for (TableModel table : models) {
      effective.put(table.name, inheritedColumns(table, byName, inheritanceWarnings));
    }
    warnings.addAll(inheritanceWarnings);
    for (TableModel table : List.copyOf(models)) {
      table.columns.clear();
      table.columns.addAll(effective.get(table.name));
      if (table.columns.isEmpty()) {
        warnings.add("table '" + table.name + "' has no columns — skipped");
        models.remove(table);
        byName.remove(table.name);
      }
    }
  }

  private List<Column> inheritedColumns(
      TableModel table, Map<String, TableModel> byName, Set<String> warnings) {
    if (table.parents.isEmpty()) {
      return List.copyOf(table.columns);
    }
    if (inheritsFromItself(table, byName)) {
      warnings.add(
          "table '"
              + table.name
              + "' is part of an INHERITS cycle ("
              + String.join(", ", table.parents)
              + ") — inherited columns not applied");
      return List.copyOf(table.columns);
    }
    Map<String, Column> merged = new LinkedHashMap<>();
    for (String parent : table.parents) {
      TableModel source = byName.get(parent);
      if (source == null) {
        warnings.add(
            "table '" + table.name + "' INHERITS '" + parent + "', which is not in the script");
        continue;
      }
      inheritedColumns(source, byName, warnings)
          .forEach(c -> merged.putIfAbsent(lower(c.name()), c));
    }
    table.columns.forEach(c -> merged.compute(lower(c.name()), (k, inherited) -> c));
    return List.copyOf(merged.values());
  }

  private static boolean inheritsFromItself(TableModel table, Map<String, TableModel> byName) {
    Set<String> seen = new LinkedHashSet<>();
    List<String> pending = new ArrayList<>(table.parents);
    while (!pending.isEmpty()) {
      String next = pending.remove(pending.size() - 1);
      if (next.equals(table.name)) {
        return true;
      }
      TableModel parent = byName.get(next);
      if (parent != null && seen.add(next)) {
        pending.addAll(parent.parents);
      }
    }
    return false;
  }

  /** Reads columns, keys, foreign keys and identity flags of a {@code CREATE TABLE}. */
  private TableModel toTableModel(
      CreateTable createTable, int order, List<String> warnings, List<String> parents) {
    String name = Names.toSnakeCase(unquote(createTable.getTable().getName()));
    List<ColumnDefinition> columns =
        createTable.getColumnDefinitions() == null
            ? List.of()
            : createTable.getColumnDefinitions().stream()
                .filter(c -> !NO_OWN_COLUMNS.equals(c.getColumnName()))
                .toList();
    if (columns.isEmpty() && parents.isEmpty()) {
      warnings.add("table '" + name + "' has no columns — skipped");
      return null;
    }

    TableModel table = new TableModel(name, order);
    table.parents.addAll(parents);
    for (ColumnDefinition column : columns) {
      String columnName = unquote(column.getColumnName());
      table.columns.add(new Column(columnName, column.getColDataType()));
      List<String> specs = column.getColumnSpecs() == null ? List.of() : column.getColumnSpecs();
      String joined = String.join(" ", specs).toUpperCase(Locale.ROOT);
      if (joined.contains("ALWAYS") && joined.contains("IDENTITY")) {
        table.dbAssigned.add(lower(columnName));
      }
      if (joined.contains(DdlPreprocessor.IDENTITY_MARKER)) {
        table.identity.add(lower(columnName));
      }
      if (specs.stream().anyMatch("PRIMARY"::equalsIgnoreCase)) {
        table.addPrimaryKey(List.of(columnName));
      }
      if (specs.stream().anyMatch("UNIQUE"::equalsIgnoreCase)) {
        table.addUnique(List.of(columnName));
      }
    }
    if (createTable.getIndexes() != null) {
      createTable.getIndexes().forEach(table::addIndex);
    }
    for (ColumnDefinition column : columns) {
      inlineForeignKey(unquote(column.getColumnName()), column.getColumnSpecs())
          .ifPresent(table.foreignKeys::add);
    }
    return table;
  }

  /**
   * Resolves each foreign key that omits its referenced columns ({@code REFERENCES customers}) to
   * the primary key of the referenced table, positionally, and drops duplicates (a key declared
   * both inline and by {@code ALTER}). A key that cannot be resolved is dropped with a warning: its
   * column keeps the plain type rather than pointing at a guessed column.
   */
  private void resolveForeignKeys(
      List<TableModel> models, Map<String, TableModel> byName, List<String> warnings) {
    for (TableModel table : models) {
      Set<String> seen = new LinkedHashSet<>();
      List<ForeignKeyRef> resolved = new ArrayList<>();
      for (ForeignKeyRef fk : table.foreignKeys) {
        Optional<ForeignKeyRef> usable =
            fk.refColumns().isEmpty()
                ? resolveToPrimaryKey(table, fk, byName, warnings)
                : Optional.of(fk);
        usable
            .filter(ref -> seen.add(lower(ref.toString())))
            .ifPresent(resolved::add); // value-based key
      }
      table.foreignKeys.clear();
      table.foreignKeys.addAll(resolved);
    }
  }

  private Optional<ForeignKeyRef> resolveToPrimaryKey(
      TableModel table, ForeignKeyRef fk, Map<String, TableModel> byName, List<String> warnings) {
    TableModel target = byName.get(fk.refTable());
    String label =
        table.name
            + ": FOREIGN KEY ("
            + String.join(", ", fk.localColumns())
            + ") REFERENCES "
            + fk.refTable();
    if (target == null || target.primary.isEmpty()) {
      warnings.add(
          label
              + (target == null ? " — table is not in the script" : " — it has no PRIMARY KEY")
              + ", referenced column unknown; kept as plain column(s)");
      return Optional.empty();
    }
    List<String> primaryKey = target.primary.get(0);
    if (primaryKey.size() != fk.localColumns().size()) {
      warnings.add(
          label
              + " — PRIMARY KEY("
              + String.join(", ", primaryKey)
              + ") has a different number of columns; kept as plain column(s)");
      return Optional.empty();
    }
    return Optional.of(new ForeignKeyRef(fk.localColumns(), fk.refTable(), primaryKey));
  }

  /** Renders the datatypes of a finished model; its key facts go to {@code keys}. */
  private TableInfo toTableInfo(
      TableModel table,
      Map<String, TableKeys> keys,
      Map<String, TableModel> byName,
      List<String> warnings) {
    Map<String, String> foreignKeyTypes = foreignKeyDatatypes(table.foreignKeys);
    Map<String, ColumnChecks> checks =
        CheckConditions.analyse(
            table.name,
            effectiveChecks(table, byName, new LinkedHashSet<>()),
            table.columns.stream().map(c -> lower(c.name())).collect(Collectors.toSet()),
            warnings);
    LinkedHashMap<String, FieldDefinition> data = new LinkedHashMap<>();
    LinkedHashMap<String, String> fieldComments = new LinkedHashMap<>();
    Set<String> serialColumns = new LinkedHashSet<>();
    Set<String> uuidColumns = new LinkedHashSet<>();
    Set<String> textColumns = new LinkedHashSet<>();
    Map<String, Long> ceilings = new LinkedHashMap<>();

    for (Column column : table.columns) {
      String datatype = foreignKeyTypes.get(lower(column.name()));
      if (datatype == null) {
        ColDataType colType = column.type();
        String sqlType = baseTypeName(colType).toUpperCase(Locale.ROOT);
        if (SERIAL_TYPES.contains(sqlType)) {
          serialColumns.add(lower(column.name()));
        } else if (UUID_TYPES.contains(sqlType)) {
          uuidColumns.add(lower(column.name()));
        }
        if (DdlTypeMapper.isText(sqlType)) {
          textColumns.add(lower(column.name()));
        }
        DdlTypeMapper.keyCeiling(sqlType, typeArguments(colType))
            .ifPresent(max -> ceilings.put(lower(column.name()), max));
        MappedType mapped =
            mapper.map(column.name(), baseTypeName(colType), typeArguments(colType));
        if (mapped.flagged()) {
          fieldComments.put(column.name(), mapped.comment());
        }
        if (colType.getArrayData() != null && !colType.getArrayData().isEmpty()) {
          fieldComments.put(column.name(), "array column generated as a scalar — verify");
        }
        datatype = mapped.datatype();
        ColumnChecks columnChecks = checks.get(lower(column.name()));
        if (columnChecks != null) {
          CheckConstraintMapper.Result applied =
              CheckConstraintMapper.apply(
                  columnChecks,
                  ColumnType.of(mapped, baseTypeName(colType), typeArguments(colType)));
          if (applied.datatype() != null) {
            datatype = applied.datatype();
            if (mapped.reason() == MappedType.Reason.NAME_HINT) {
              fieldComments.remove(column.name()); // the CHECK settles what the name only guessed
            }
          }
          applied
              .notes()
              .forEach(note -> fieldComments.merge(column.name(), note, DdlInspector::join));
        }
      }
      data.put(column.name(), new FieldDefinition(datatype, null));
    }

    keys.put(
        table.name,
        new TableKeys(
            table.primary,
            table.uniques,
            serialColumns,
            uuidColumns,
            textColumns,
            ceilings,
            table.dbAssigned,
            table.identity,
            checks));
    return new TableInfo(
        table.name,
        data,
        fieldComments,
        flatten(table.primary),
        flatten(table.uniques),
        table.foreignKeys,
        table.order);
  }

  /**
   * The table's own CHECKs plus those of its {@code INHERITS} parents (recursively), except the
   * ones declared {@code NO INHERIT}. Resolved here, after the {@code ALTER}s, so a parent CHECK
   * added by {@code ALTER} reaches children declared before it.
   */
  private List<CheckCondition> effectiveChecks(
      TableModel table, Map<String, TableModel> byName, Set<String> path) {
    List<CheckCondition> all = new ArrayList<>();
    if (path.add(table.name)) {
      for (String parent : table.parents) {
        TableModel source = byName.get(parent);
        if (source != null) {
          effectiveChecks(source, byName, path).stream()
              .filter(c -> !c.noInherit())
              .forEach(all::add);
        }
      }
      path.remove(table.name);
    }
    all.addAll(table.checks);
    return all;
  }

  private static String join(String old, String added) {
    return old + "; " + added;
  }

  private Set<String> flatten(List<List<String>> constraints) {
    Set<String> result = new LinkedHashSet<>();
    constraints.forEach(result::addAll);
    return result;
  }

  /**
   * Rewrites key columns of a table so generated data honours PRIMARY KEY / UNIQUE constraints:
   * integer keys become {@code serial} / {@code unique[1..count]} / {@code ref[..., unique]},
   * anything else is left as-is with a "not enforced" comment. A single-column key over an enum is
   * {@code cycle[...]} unless {@code cycleKeys} is off (nesting). Each column gets at most one key
   * role (PK first, then UNIQUE in declaration order). See {@code docs/INSPECT-V1-SPEC.md}.
   */
  private void applyKeys(
      TableInfo table,
      TableKeys keys,
      List<String> warnings,
      Map<String, Set<String>> referenced,
      boolean cycleKeys) {
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
                    table,
                    keys,
                    new KeySpec("pk", true, c),
                    claimed,
                    foreignColumns,
                    cycleKeys,
                    warnings));
    for (int i = 0; i < keys.uniques().size(); i++) {
      KeySpec spec = new KeySpec("uq" + (i + 1), false, keys.uniques().get(i));
      applyKey(table, keys, spec, claimed, foreignColumns, cycleKeys, warnings);
    }
    for (String column : List.copyOf(table.data().keySet())) {
      String key = lower(column);
      if (keys.serialColumns().contains(key)
          && !claimed.contains(key)
          && !foreignColumns.contains(key)) {
        setKeyType(table, column, serialType(keys, key));
      }
    }
    applyKeyChecks(table, keys);
    handleDbAssigned(table, keys, warnings, referenced);
  }

  /**
   * Generated keys and references are not narrowed by a CHECK; the column says so unless the CHECK
   * holds for every value the key takes (it starts at 1).
   */
  private void applyKeyChecks(TableInfo table, TableKeys keys) {
    keys.checks()
        .forEach(
            (column, checks) -> {
              FieldDefinition field = table.data().get(actualKey(table, column));
              String datatype = field == null ? "" : field.getDatatype();
              boolean reference = datatype.startsWith("ref[");
              if (reference || datatype.startsWith("serial") || datatype.startsWith("unique[")) {
                CheckConstraintMapper.keyNotes(checks, reference)
                    .forEach(note -> addComment(table, column, note));
              }
            });
  }

  /**
   * DB-assigned columns: omit unless some FK references them. GENERATED ALWAYS identity is handled
   * for single-column PKs only; SQL Server IDENTITY (#444) also for non-PK columns, as SQL Server
   * rejects explicit values there too.
   */
  private void handleDbAssigned(
      TableInfo table, TableKeys keys, List<String> warnings, Map<String, Set<String>> referenced) {
    Set<String> singlePks = new LinkedHashSet<>();
    for (List<String> pk : keys.primary()) {
      if (pk.size() == 1) {
        singlePks.add(lower(pk.get(0)));
      }
    }
    Set<String> compositePk = new LinkedHashSet<>();
    keys.primary().stream()
        .filter(pk -> pk.size() > 1)
        .forEach(pk -> pk.forEach(c -> compositePk.add(lower(c))));
    for (String column : List.copyOf(table.data().keySet())) {
      String key = lower(column);
      boolean generatedAlways = keys.dbAssignedColumns().contains(key);
      boolean identity = keys.identityColumns().contains(key);
      boolean singlePk = singlePks.contains(key);
      if ((generatedAlways && singlePk) || identity) {
        String kind = generatedAlways ? "GENERATED ALWAYS AS IDENTITY" : "IDENTITY";
        Set<String> refs = referenced.get(lower(table.name()) + "." + key);
        boolean omit = refs == null && !compositePk.contains(key);
        boolean serial = table.data().get(column).getDatatype().startsWith("serial");
        warnings.add(dbAssignedWarning(table.name() + "." + column, kind, refs, omit, serial));
        if (omit) {
          table.data().remove(column);
          table.comments().remove(column);
        }
      }
    }
  }

  private String dbAssignedWarning(
      String label, String kind, Set<String> refs, boolean omit, boolean serial) {
    if (omit) {
      return label
          + ": "
          + kind
          + " — omitted (DB assigns it); any FK referencing it"
          + " needs DB-assigned ordered generation (#400)";
    }
    return label
        + ": "
        + kind
        + (refs == null ? " in a composite key" : " but referenced by " + refs)
        + " — kept"
        + (serial ? " as serial" : "")
        + "; needs DB-assigned ordered generation (#400)";
  }

  /** One PRIMARY KEY / UNIQUE constraint: its group name, kind and declared columns. */
  private record KeySpec(String group, boolean primary, List<String> declared) {}

  private void applyKey(
      TableInfo table,
      TableKeys keys,
      KeySpec spec,
      Set<String> claimed,
      Set<String> foreignColumns,
      boolean cycleKeys,
      List<String> warnings) {
    String label =
        (spec.primary() ? "PRIMARY KEY(" : "UNIQUE(") + String.join(", ", spec.declared()) + ")";
    List<String> columns = claimColumns(table, spec, label, claimed, warnings);
    if (columns.isEmpty()) {
      return;
    }
    if (cycleKeys && spec.declared().size() == 1 && isEnum(table, columns.get(0))) {
      applyCycleKey(table, label, columns.get(0), warnings);
      return;
    }
    boolean enforceable =
        columns.stream()
            .allMatch(
                c ->
                    foreignColumns.contains(lower(c))
                        || isIntegerType(table.data().get(actualKey(table, c)))
                        || isTextKey(table, keys, c, spec.primary()));
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
      boolean foreign = foreignColumns.contains(lower(column));
      String datatype = keyDatatype(table, keys, spec, column, single, foreign);
      setKeyType(table, column, datatype);
      Long ceiling = keys.ceilings().get(lower(column));
      String max = ceiling == null ? "" : " — max " + ceiling + " rows";
      if (foreign) {
        continue;
      }
      if (keys.textColumns().contains(lower(column))) {
        addComment(table, column, "text key generated as numeric strings" + max);
      } else if (datatype.startsWith("unique[") && ceiling != null && ceiling < Integer.MAX_VALUE) {
        addComment(table, column, "max " + ceiling + " rows");
      }
    }
  }

  /**
   * A text column a key can generate as numeric strings. Outside a PRIMARY KEY, a name-hinted one
   * (email, username) keeps its semantic type: collision-free semantic strings are #455.
   */
  private boolean isTextKey(TableInfo table, TableKeys keys, String column, boolean primary) {
    String datatype = table.data().get(actualKey(table, column)).getDatatype();
    return keys.textColumns().contains(lower(column))
        && !isEnum(table, column)
        && (primary || datatype.startsWith("char["));
  }

  /** {@code serial[1..ceiling]} when the column type has a ceiling, else plain {@code serial}. */
  private String serialType(TableKeys keys, String column) {
    Long ceiling = keys.ceilings().get(column);
    return ceiling == null ? "serial" : "serial[1.." + ceiling + "]";
  }

  /**
   * A single-column key over a value list: {@code enum[a,b]} becomes {@code cycle[a,b]}, which
   * hands out each value once, so the key is unique only up to {@code --count} (the number of
   * values).
   */
  private void applyCycleKey(TableInfo table, String label, String column, List<String> warnings) {
    String datatype = table.data().get(actualKey(table, column)).getDatatype();
    String values = datatype.substring("enum[".length(), datatype.length() - 1);
    int count = values.split(",").length;
    setKeyType(table, column, "cycle[" + values + "]");
    addComment(
        table,
        column,
        "each value once up to --count " + count + ", then values repeat and the key collides");
    warnings.add(
        table.name()
            + ": "
            + label
            + " is cycle["
            + values
            + "] — unique only while --count ≤ "
            + count);
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
      TableInfo table,
      TableKeys keys,
      KeySpec spec,
      String column,
      boolean single,
      boolean foreign) {
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
      return spec.primary() ? serialType(keys, lower(column)) : "unique[" + Defaults.REF_POOL + "]";
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
    table.comments().merge(key, comment, DdlInspector::join);
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

  private boolean isEnum(TableInfo table, String column) {
    FieldDefinition field = table.data().get(actualKey(table, column));
    return field != null && field.getDatatype().startsWith("enum[");
  }

  private boolean isRef(FieldDefinition field) {
    return field != null && field.getDatatype().startsWith("ref[");
  }

  /** {@code ref[customers.id, 1..count]} → {@code customers.id}. */
  private String refTarget(String refDatatype) {
    int comma = refDatatype.indexOf(',');
    return refDatatype
        .substring("ref[".length(), comma < 0 ? refDatatype.length() - 1 : comma)
        .trim();
  }

  private String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  /** Maps each local column (lower-cased) to its {@code ref[table.column, pool]} datatype. */
  private Map<String, String> foreignKeyDatatypes(List<ForeignKeyRef> foreignKeys) {
    Map<String, String> result = new LinkedHashMap<>();
    for (ForeignKeyRef fk : foreignKeys) {
      for (int i = 0; i < fk.localColumns().size(); i++) {
        String referenced = fk.refColumns().get(i < fk.refColumns().size() ? i : 0);
        result.putIfAbsent(
            lower(fk.localColumns().get(i)),
            "ref["
                + fk.refTable()
                + "."
                + Names.toSnakeCase(referenced)
                + ", "
                + Defaults.REF_POOL
                + "]");
      }
    }
    return result;
  }

  /**
   * Extracts the foreign key of an inline {@code ... REFERENCES table(col)} column spec; the
   * referenced column is empty when the DDL omits it. Tolerant of JSQLParser tokenization: 5.3
   * split {@code REFERENCES}, {@code table} and {@code (col)} into separate spec tokens, while 5.4
   * emits the whole clause as one token. Joining the specs and matching against the clause handles
   * both. The table is schema-less.
   */
  private Optional<ForeignKeyRef> inlineForeignKey(String columnName, List<String> specs) {
    if (specs == null || specs.isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = INLINE_REFERENCE.matcher(String.join(" ", specs));
    if (!matcher.find()) {
      return Optional.empty();
    }
    String table = unquote(matcher.group(1));
    return Optional.of(
        new ForeignKeyRef(
            List.of(columnName),
            Names.toSnakeCase(table.substring(table.lastIndexOf('.') + 1)),
            matcher.group(2) == null ? List.of() : List.of(unquote(matcher.group(2)))));
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
    return IDENT_QUOTES.matcher(paren >= 0 ? raw.substring(0, paren) : raw).replaceAll("").trim();
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
    return Arrays.stream(TOP_LEVEL_COMMA.split(raw.substring(open + 1, close)))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
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

  static String shortSnippet(String statement) {
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
