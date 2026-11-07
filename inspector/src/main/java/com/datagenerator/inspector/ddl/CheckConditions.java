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

import com.datagenerator.inspector.ddl.TableModel.CheckCondition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.ArrayConstructor;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression.DateTime;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;

/**
 * Reads {@code CHECK} conditions into what they say about each column: comparisons with a literal,
 * {@code BETWEEN}, {@code IN} lists (and the {@code OR}-of-equalities and {@code = ANY
 * (ARRAY[...])} forms SQL Server and pg_dump print them as) become {@link Atom}s; anything else is
 * a note on every column it names. Vendor spellings are folded here: parentheses, casts, bracket
 * quoting and {@code N'...'} never reach the atoms. Only top-level {@code AND} parts are read, and
 * the {@code IS NULL} branch of an {@code OR} is dropped, so an atom is something every stored
 * value must satisfy.
 */
final class CheckConditions {

  /** Comparison of a column with literals; {@code IN} carries every listed value. */
  enum Op {
    EQ,
    LT,
    LE,
    GT,
    GE,
    IN
  }

  /** A literal as the constraint wrote it. */
  sealed interface Lit permits Num, Txt, Day, Now, Today, Other {}

  record Num(BigDecimal value) implements Lit {}

  record Txt(String value) implements Lit {}

  record Day(LocalDate value) implements Lit {}

  /**
   * The current date and time ({@code now()}, {@code CURRENT_TIMESTAMP}, {@code getdate()}, ...).
   */
  record Now() implements Lit {}

  /** Today's date ({@code CURRENT_DATE}, {@code CURDATE()}): midnight, not the current instant. */
  record Today() implements Lit {}

  /** NULL, a boolean, an expression: nothing a range or list can be built from. */
  record Other() implements Lit {}

  /** {@code column op values}; {@code source} names the constraint in review comments. */
  record Atom(String column, String source, Op op, List<Lit> values) {}

  /** Everything the CHECKs of a table say about one column. */
  static final class ColumnChecks {
    final List<Atom> atoms = new ArrayList<>();
    final Set<String> notes = new LinkedHashSet<>();
  }

  private static final Set<String> NOW_NAMES =
      Set.of(
          "NOW",
          "GETDATE",
          "SYSDATE",
          "SYSDATETIME",
          "GETUTCDATE",
          "SYSTIMESTAMP",
          "CURRENT_TIMESTAMP",
          "LOCALTIMESTAMP");
  private static final Set<String> TODAY_NAMES = Set.of("CURDATE", "CURRENT_DATE");
  private static final Pattern TEXT_TYPE =
      Pattern.compile("(?:text|varchar|character varying)(?:\\[])?");
  private static final Pattern NUMERIC_TYPE = Pattern.compile("(?:numeric|decimal)(?:\\[])?");
  private static final Pattern DOUBLE_TYPE = Pattern.compile("double precision|float8");
  private static final Pattern INTEGER_TYPE =
      Pattern.compile("int|integer|int2|int4|int8|smallint|bigint");
  private static final int SNIPPET_LENGTH = 60;

  private final String table;
  private final Set<String> columns;
  private final List<String> warnings;
  private final Map<String, ColumnChecks> result = new LinkedHashMap<>();
  private final Set<String> warned = new LinkedHashSet<>();

  private CheckConditions(String tableName, Set<String> columnNames, List<String> warningList) {
    this.table = tableName;
    this.columns = columnNames;
    this.warnings = warningList;
  }

  /**
   * What {@code checks} say about each column (keyed by lower-cased name). A condition that cannot
   * be read, or that names no column of the table, is a warning and changes nothing.
   *
   * @param columnNames lower-cased column names of the table
   */
  static Map<String, ColumnChecks> analyse(
      String tableName,
      List<CheckCondition> checks,
      Set<String> columnNames,
      List<String> warnings) {
    CheckConditions reader = new CheckConditions(tableName, columnNames, warnings);
    checks.forEach(reader::read);
    return reader.result;
  }

  private void read(CheckCondition check) {
    String name = check.name();
    Expression condition;
    try {
      condition =
          CCJSqlParserUtil.parseCondExpression(bracketsToQuotes(check.text()), false, p -> {});
    } catch (JSQLParserException e) {
      warnings.add(table + ": " + source(name, check.text()) + " could not be read — ignored");
      return;
    }
    andParts(condition).forEach(part -> readPart(part, name));
  }

  private void readPart(Expression part, String name) {
    if (part instanceof IsNullExpression test && test.isNot()) {
      return; // NOT NULL says nothing about the values
    }
    if (part instanceof OrExpression) {
      readOr(part, name);
    } else {
      simple(part, name).ifPresentOrElse(this::record, () -> complex(part, name));
    }
  }

  /** {@code x IS NULL OR rest} means {@code rest}; equalities on one column are an IN list. */
  private void readOr(Expression or, String name) {
    List<Expression> branches = new ArrayList<>();
    orBranches(or, branches);
    List<Expression> values = branches.stream().filter(b -> !isNullTest(b)).toList();
    if (values.size() == 1) {
      andParts(values.get(0)).forEach(part -> readPart(part, name));
    } else {
      inList(values, name).ifPresentOrElse(atom -> record(List.of(atom)), () -> complex(or, name));
    }
  }

  /**
   * {@code a = 1 OR a = 2} as {@code a IN (1, 2)}; empty unless every branch is such an equality.
   */
  private Optional<Atom> inList(List<Expression> branches, String name) {
    if (branches.isEmpty()) {
      return Optional.empty();
    }
    String column = null;
    List<Lit> values = new ArrayList<>();
    for (Expression branch : branches) {
      Optional<Atom> eq = simple(branch, name).filter(a -> a.size() == 1).map(a -> a.get(0));
      if (eq.isEmpty()
          || eq.get().op() != Op.EQ
          || (column != null && !column.equals(eq.get().column()))) {
        return Optional.empty();
      }
      column = eq.get().column();
      values.addAll(eq.get().values());
    }
    return Optional.of(new Atom(column, source(name, branches.get(0).toString()), Op.IN, values));
  }

  /** The atoms of a simple condition on one column, or empty if it is not one. */
  private Optional<List<Atom>> simple(Expression part, String name) {
    Expression e = unwrap(part);
    String source = source(name, e.toString());
    if (e instanceof EqualsTo eq && isAny(eq.getRightExpression())) {
      return any(eq, source);
    }
    if (e instanceof Between between && !between.isNot()) {
      return between(between, source);
    }
    if (e instanceof InExpression in && !in.isNot()) {
      return in(in, source);
    }
    if (e instanceof BinaryExpression binary) {
      Op op = opOf(binary);
      return op == null ? Optional.empty() : comparison(binary, op, source);
    }
    return Optional.empty();
  }

  private Optional<List<Atom>> comparison(BinaryExpression b, Op op, String source) {
    String left = columnName(b.getLeftExpression());
    Lit right = literal(b.getRightExpression());
    if (left != null && !(right instanceof Other)) {
      return Optional.of(List.of(new Atom(left, source, op, List.of(right))));
    }
    String column = columnName(b.getRightExpression());
    Lit value = literal(b.getLeftExpression());
    if (column != null && !(value instanceof Other)) {
      return Optional.of(List.of(new Atom(column, source, flip(op), List.of(value))));
    }
    return Optional.empty();
  }

  private Optional<List<Atom>> between(Between b, String source) {
    String column = columnName(b.getLeftExpression());
    Lit low = literal(b.getBetweenExpressionStart());
    Lit high = literal(b.getBetweenExpressionEnd());
    if (column == null || low instanceof Other || high instanceof Other) {
      return Optional.empty();
    }
    return Optional.of(
        List.of(
            new Atom(column, source, Op.GE, List.of(low)),
            new Atom(column, source, Op.LE, List.of(high))));
  }

  private Optional<List<Atom>> in(InExpression in, String source) {
    String column = columnName(in.getLeftExpression());
    if (column == null || !(in.getRightExpression() instanceof ExpressionList<?> list)) {
      return Optional.empty();
    }
    return listAtom(column, list, source);
  }

  /** {@code col = ANY (ARRAY[...])}, which is how pg_dump prints {@code IN}. */
  private Optional<List<Atom>> any(EqualsTo eq, String source) {
    String column = columnName(eq.getLeftExpression());
    Function any = (Function) unwrap(eq.getRightExpression());
    ExpressionList<?> parameters = any.getParameters();
    if (column == null
        || parameters == null
        || parameters.size() != 1
        || !(stripCasts(parameters.get(0)) instanceof ArrayConstructor array)
        || array.getExpressions() == null) {
      return Optional.empty();
    }
    return listAtom(column, array.getExpressions(), source);
  }

  private Optional<List<Atom>> listAtom(String column, ExpressionList<?> list, String source) {
    List<Lit> values = new ArrayList<>();
    for (Expression item : list) {
      Lit value = literal(item);
      if (value instanceof Other) {
        return Optional.empty();
      }
      values.add(value);
    }
    return Optional.of(List.of(new Atom(column, source, Op.IN, values)));
  }

  private void record(List<Atom> atoms) {
    for (Atom atom : atoms) {
      if (columns.contains(atom.column())) {
        result.computeIfAbsent(atom.column(), k -> new ColumnChecks()).atoms.add(atom);
      } else if (warned.add(atom.source() + atom.column())) {
        warnings.add(table + ": " + atom.source() + " names unknown column " + atom.column());
      }
    }
  }

  /** A condition no range or list can express: a note on each column it names. */
  private void complex(Expression part, String name) {
    String source = source(name, part.toString());
    Set<String> named = columnsIn(part);
    Set<String> known = new LinkedHashSet<>(named);
    known.retainAll(columns);
    if (known.isEmpty() && !named.isEmpty()) {
      if (warned.add(source)) {
        warnings.add(table + ": " + source + " names unknown column(s) " + named + " — ignored");
      }
      return;
    }
    String note = source + " not applied — generated values may violate it, verify";
    known.forEach(
        column -> result.computeIfAbsent(column, k -> new ColumnChecks()).notes.add(note));
  }

  // ------------------------------------------------------------------ expression helpers

  private Set<String> columnsIn(Expression part) {
    Set<String> names = new LinkedHashSet<>();
    part.accept(
        new ExpressionVisitorAdapter<Void>() {
          @Override
          public <S> Void visit(Column column, S context) {
            String lower = lowerName(column);
            if (columns.contains(lower) || !isClockName(lower)) {
              names.add(lower);
            }
            return null;
          }
        },
        null);
    return names;
  }

  private static String lowerName(Column column) {
    return TableModel.unquote(column.getColumnName()).toLowerCase(Locale.ROOT);
  }

  /** The column an operand is, under parentheses and casts; null if it is anything else. */
  private String columnName(Expression operand) {
    if (stripCasts(operand) instanceof Column column) {
      String lower = lowerName(column);
      return columns.contains(lower) || !isClockName(lower) ? lower : null;
    }
    return null;
  }

  private Lit literal(Expression operand) {
    Expression e = unwrap(operand);
    return switch (e) {
      case LongValue number -> new Num(new BigDecimal(number.getBigIntegerValue()));
      case DoubleValue number -> number(number.toString());
      case SignedExpression signed -> signed(signed);
      case StringValue text -> new Txt(text.getValue().replace("''", "'"));
      case DateTimeLiteralExpression literal -> dateLiteral(literal);
      case CastExpression cast -> cast(cast);
      case TimeKeyExpression key -> clock(key.getStringValue());
      case Function function -> function(function);
      case Column column ->
          !columns.contains(lowerName(column)) && isClockName(lowerName(column))
              ? clock(lowerName(column))
              : new Other();
      default -> new Other();
    };
  }

  private static Lit number(String text) {
    try {
      return new Num(new BigDecimal(text));
    } catch (NumberFormatException e) {
      return new Other();
    }
  }

  private Lit signed(SignedExpression signed) {
    Lit inner = literal(signed.getExpression());
    if (inner instanceof Num n && signed.getSign() == '-') {
      return new Num(n.value().negate());
    }
    return inner instanceof Num && signed.getSign() == '+' ? inner : new Other();
  }

  private static Lit dateLiteral(DateTimeLiteralExpression literal) {
    String value = literal.getValue() == null ? "" : literal.getValue().replace("'", "");
    return literal.getType() == DateTime.DATE ? day(value) : new Other();
  }

  /** A cast of a literal keeps it only when the value stays what the constraint wrote. */
  private Lit cast(CastExpression cast) {
    Lit inner = literal(cast.getLeftExpression());
    String type = typeOf(cast);
    boolean keeps =
        switch (inner) {
          case Txt t -> TEXT_TYPE.matcher(type).matches();
          case Num n ->
              NUMERIC_TYPE.matcher(type).matches()
                  || DOUBLE_TYPE.matcher(type).matches()
                  || (INTEGER_TYPE.matcher(type).matches()
                      && n.value().stripTrailingZeros().scale() <= 0);
          case Day d -> "date".equals(type);
          default -> false;
        };
    if (keeps) {
      return inner;
    }
    if ("date".equals(type)) {
      return switch (inner) {
        case Txt t -> day(t.value());
        case Now n -> new Today();
        case Today t -> inner;
        default -> new Other();
      };
    }
    return new Other();
  }

  private static String typeOf(CastExpression cast) {
    return cast.getColDataType() == null
        ? ""
        : cast.getColDataType().toString().toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
  }

  private Lit function(Function function) {
    String name = function.getName() == null ? "" : function.getName().toUpperCase(Locale.ROOT);
    ExpressionList<?> parameters = function.getParameters();
    if (isClockName(name) && (parameters == null || parameters.isEmpty())) {
      return clock(name);
    }
    if ("TO_DATE".equals(name)
        && parameters != null
        && parameters.size() == 2
        && literal(parameters.get(0)) instanceof Txt text
        && literal(parameters.get(1)) instanceof Txt format
        && "YYYY-MM-DD".equalsIgnoreCase(format.value())) {
      return day(text.value());
    }
    return new Other();
  }

  private static boolean isClockName(String name) {
    String upper = name.toUpperCase(Locale.ROOT);
    return NOW_NAMES.contains(upper) || TODAY_NAMES.contains(upper);
  }

  private static Lit clock(String name) {
    return name != null && TODAY_NAMES.contains(name.toUpperCase(Locale.ROOT))
        ? new Today()
        : new Now();
  }

  private static Lit day(String text) {
    try {
      return new Day(LocalDate.parse(text.strip()));
    } catch (DateTimeParseException e) {
      return new Other();
    }
  }

  private static boolean isAny(Expression e) {
    return unwrap(e) instanceof Function f && "ANY".equalsIgnoreCase(f.getName());
  }

  private static Op opOf(BinaryExpression e) {
    return switch (e) {
      case EqualsTo x -> Op.EQ;
      case GreaterThan x -> Op.GT;
      case GreaterThanEquals x -> Op.GE;
      case MinorThan x -> Op.LT;
      case MinorThanEquals x -> Op.LE;
      default -> null;
    };
  }

  private static Op flip(Op op) {
    return switch (op) {
      case LT -> Op.GT;
      case LE -> Op.GE;
      case GT -> Op.LT;
      case GE -> Op.LE;
      default -> op;
    };
  }

  private static boolean isNullTest(Expression e) {
    return e instanceof IsNullExpression n && !n.isNot();
  }

  private static List<Expression> andParts(Expression e) {
    List<Expression> parts = new ArrayList<>();
    collect(e, AndExpression.class, parts);
    return parts;
  }

  private static void orBranches(Expression e, List<Expression> out) {
    collect(e, OrExpression.class, out);
  }

  private static void collect(
      Expression e, Class<? extends BinaryExpression> joiner, List<Expression> out) {
    Expression x = unwrap(e);
    if (joiner.isInstance(x)) {
      BinaryExpression binary = joiner.cast(x);
      collect(binary.getLeftExpression(), joiner, out);
      collect(binary.getRightExpression(), joiner, out);
    } else {
      out.add(x);
    }
  }

  /** Removes redundant parentheses: {@code ((x >= 5))} is {@code x >= 5}. */
  private static Expression unwrap(Expression e) {
    Expression x = e;
    while (x instanceof ParenthesedExpressionList<?> list && list.size() == 1) {
      x = list.get(0);
    }
    return x;
  }

  /** Under parentheses and the casts that leave a column's value as it is (text, numeric). */
  private static Expression stripCasts(Expression e) {
    Expression x = unwrap(e);
    while (x instanceof CastExpression cast
        && (TEXT_TYPE.matcher(typeOf(cast)).matches()
            || NUMERIC_TYPE.matcher(typeOf(cast)).matches())) {
      x = unwrap(cast.getLeftExpression());
    }
    return x;
  }

  /**
   * {@code [Discount]} to {@code "Discount"}, as the parser only reads quoted identifiers that way;
   * a bracket right after a word ({@code ARRAY[...]}, {@code text[]}) is not an identifier.
   */
  private static String bracketsToQuotes(String text) {
    StringBuilder out = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      int close = c == '[' ? text.indexOf(']', i) : -1;
      boolean afterWord =
          !out.isEmpty()
              && (Character.isLetterOrDigit(out.charAt(out.length() - 1))
                  || out.charAt(out.length() - 1) == '_');
      if (c == '[' && !afterWord && close > i + 1) {
        out.append('"').append(text, i + 1, close).append('"');
        i = close + 1;
      } else {
        int end = c == '\'' || c == '"' ? SqlScan.skipOpaque(text, i) : i + 1;
        out.append(text, i, Math.min(end, text.length()));
        i = Math.min(end, text.length());
      }
    }
    return out.toString();
  }

  /** {@code CHECK name (condition)}, the phrase review comments and warnings use. */
  private static String source(String name, String condition) {
    String oneLine = String.join(" ", condition.strip().split("\\s+"));
    String shown =
        oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "...";
    return "CHECK " + (name == null ? "" : name + " ") + "(" + shown + ")";
  }
}
