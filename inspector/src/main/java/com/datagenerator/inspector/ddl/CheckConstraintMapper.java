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
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.MappedType.Reason;
import com.datagenerator.inspector.ddl.CheckConditions.Atom;
import com.datagenerator.inspector.ddl.CheckConditions.ColumnChecks;
import com.datagenerator.inspector.ddl.CheckConditions.Day;
import com.datagenerator.inspector.ddl.CheckConditions.Lit;
import com.datagenerator.inspector.ddl.CheckConditions.Now;
import com.datagenerator.inspector.ddl.CheckConditions.Num;
import com.datagenerator.inspector.ddl.CheckConditions.Op;
import com.datagenerator.inspector.ddl.CheckConditions.Today;
import com.datagenerator.inspector.ddl.CheckConditions.Txt;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns the {@code CHECK} conditions of one column and its mapped type into a narrower datatype, or
 * leaves the type alone and says why in a review note. The rule: narrow only when every generated
 * value is certain to satisfy the constraint. Bounds are exact ({@link BigDecimal}) and rounded
 * inward to the column's scale, so the value the database stores still meets the check. Dates that
 * depend on the clock become fixed ranges, never {@code now}, so output stays reproducible.
 */
final class CheckConstraintMapper {

  /** The column type a CHECK is applied to. */
  record ColumnType(
      String datatype, boolean text, int maxLength, int scale, boolean singlePrecision) {

    /**
     * Type of a mapped column; {@code sqlType} is the SQL type name and {@code args} its arguments,
     * e.g. {@code (10,2)}.
     */
    static ColumnType of(MappedType mapped, String sqlType, List<String> args) {
      boolean text = mapped.reason() == Reason.NAME_HINT || mapped.datatype().startsWith("char[");
      int precision = argument(args, 0, -1);
      return new ColumnType(
          mapped.datatype(),
          text,
          precision,
          argument(args, 1, 2),
          isSinglePrecision(sqlType.toUpperCase(Locale.ROOT), precision));
    }

    /** 4-byte floats; a bare FLOAT may be one too (MySQL), so it is treated as one. */
    private static boolean isSinglePrecision(String sqlType, int precision) {
      return switch (sqlType) {
        case "REAL", "FLOAT4", "BINARY_FLOAT" -> true;
        case "FLOAT" -> precision < 0 || precision <= FLOAT4_PRECISION;
        default -> false;
      };
    }

    private static int argument(List<String> args, int index, int fallback) {
      if (args == null || args.size() <= index) {
        return fallback;
      }
      try {
        return Integer.parseInt(args.get(index).strip().split("\\s+")[0]);
      } catch (NumberFormatException e) {
        return fallback;
      }
    }
  }

  /** The new datatype (null = unchanged) and the review notes to attach to the column. */
  record Result(String datatype, Set<String> notes) {}

  private static final Pattern RANGE =
      Pattern.compile("^(?:int|decimal)\\[(-?\\d+(?:\\.\\d+)?)\\.\\.(-?\\d+(?:\\.\\d+)?)]$");
  private static final Pattern DATE_RANGE =
      Pattern.compile("^date\\[(\\d{4}-\\d\\d-\\d\\d)\\.\\.(\\d{4}-\\d\\d-\\d\\d)]$");
  private static final LocalDate PAST_START = LocalDate.of(1970, 1, 1);
  private static final LocalDate PAST_END = LocalDate.of(2019, 12, 31);
  private static final LocalDate FUTURE_START = LocalDate.of(2100, 1, 1);
  private static final LocalDate FUTURE_END = LocalDate.of(2100, 12, 31);
  private static final int OPEN_BOUND_YEARS = 10;
  private static final int FLOAT4_PRECISION = 24;
  private static final long KEY_MAX = Defaults.INT_MAX;

  private CheckConstraintMapper() {}

  /** Applies the column's conditions to its type. */
  static Result apply(ColumnChecks checks, ColumnType type) {
    Set<String> notes = new LinkedHashSet<>(checks.notes);
    if (checks.atoms.isEmpty()) {
      return new Result(null, notes);
    }
    String datatype = type.datatype();
    String narrowed;
    if (datatype.startsWith("int[")) {
      narrowed = number(checks, type, 0, notes);
    } else if (datatype.startsWith("decimal[")) {
      narrowed = number(checks, type, type.scale(), notes);
    } else if (datatype.startsWith("date[")) {
      narrowed = date(checks, datatype, notes);
    } else if (datatype.startsWith("timestamp[")) {
      timestamp(checks, notes);
      narrowed = null;
    } else if (type.text()) {
      narrowed = text(checks, type, notes);
    } else {
      checks.atoms.forEach(atom -> notes.add(unsupported(atom)));
      narrowed = null;
    }
    return new Result(narrowed, notes);
  }

  /**
   * Notes for a column that became a generated key or a reference: values start at 1 and rise, or
   * come from another table, so only a constraint that holds from 1 upwards is satisfied.
   */
  static Set<String> keyNotes(ColumnChecks checks, boolean reference) {
    Set<String> notes = new LinkedHashSet<>(checks.notes);
    if (reference) {
      checks.atoms.forEach(atom -> notes.add(atom.source() + " cannot be applied to a reference"));
    } else {
      ColumnType ints = new ColumnType("int[1.." + KEY_MAX + "]", false, -1, 0, false);
      Result result = apply(checks, ints);
      notes.addAll(result.notes());
      if (result.datatype() != null && boundsKeyValues(result.datatype())) {
        checks.atoms.forEach(
            atom -> notes.add(atom.source() + " may be violated: key values start at 1, verify"));
      }
    }
    return notes;
  }

  /** Whether a range cuts off the values 1.. that a generated key takes. */
  private static boolean boundsKeyValues(String datatype) {
    Matcher range = RANGE.matcher(datatype);
    return range.matches()
        && (new BigDecimal(range.group(1)).compareTo(BigDecimal.ONE) > 0
            || new BigDecimal(range.group(2)).compareTo(new BigDecimal(KEY_MAX)) < 0);
  }

  private static String unsupported(Atom atom) {
    return atom.source() + " not applied — generated values may violate it, verify";
  }

  /** Names every constraint that took part, so the whole condition shows. */
  private static String unsatisfiable(ColumnChecks checks) {
    return sources(checks) + " leaves no value the column type can hold — not applied, verify";
  }

  /** A value enum[...] cannot express (comma, bracket, empty, outer space); not the column type. */
  private static String inexpressible(ColumnChecks checks) {
    return sources(checks) + " has a value enum[...] can't express — not applied, verify";
  }

  private static String sources(ColumnChecks checks) {
    return checks.atoms.stream().map(Atom::source).distinct().collect(Collectors.joining(" and "));
  }

  // ------------------------------------------------------------------ integers and decimals

  private static String number(ColumnChecks checks, ColumnType type, int scale, Set<String> notes) {
    Matcher range = RANGE.matcher(type.datatype());
    if (!range.matches()) {
      checks.atoms.forEach(atom -> notes.add(unsupported(atom)));
      return null;
    }
    boolean integer = scale == 0;
    BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
    BigDecimal currentMin = new BigDecimal(range.group(1));
    BigDecimal currentMax = new BigDecimal(range.group(2));
    BigDecimal low = null;
    BigDecimal high = null;
    for (Atom atom : checks.atoms) {
      Optional<BigDecimal> value = singleNumber(atom);
      Optional<List<BigDecimal>> list = integer ? integers(atom) : Optional.empty();
      if (atom.op() == Op.IN && list.isPresent() && contiguous(list.get())) {
        low = max(low, min(list.get()));
        high = min(high, max(list.get()));
      } else if (value.isEmpty() || atom.op() == Op.IN) {
        notes.add(unsupported(atom));
      } else if (type.singlePrecision() && !exactFloat(value.get())) {
        notes.add(unsupported(atom)); // the stored float4 would differ from the bound
      } else {
        BigDecimal v = value.get();
        BigDecimal up = v.setScale(scale, RoundingMode.CEILING);
        BigDecimal down = v.setScale(scale, RoundingMode.FLOOR);
        switch (atom.op()) {
          case GE -> low = max(low, up);
          case GT -> low = max(low, down.add(unit));
          case LE -> high = min(high, down);
          case LT -> high = min(high, up.subtract(unit));
          default -> { // EQ: a value the column scale can hold, or nothing
            low = max(low, up);
            high = min(high, down);
          }
        }
      }
    }
    BigDecimal floor = integer ? currentMax.add(BigDecimal.ONE).negate() : currentMax.negate();
    BigDecimal newLow =
        low != null
            ? low.max(floor)
            : high != null && high.compareTo(currentMin) <= 0 ? floor : currentMin;
    BigDecimal newHigh = high != null ? high.min(currentMax) : currentMax;
    if (newLow.compareTo(newHigh) > 0) {
      notes.add(unsatisfiable(checks));
      return null;
    }
    if (newLow.compareTo(currentMin) == 0 && newHigh.compareTo(currentMax) == 0) {
      return null;
    }
    String prefix = integer ? "int[" : "decimal[";
    return prefix + render(newLow, integer) + ".." + render(newHigh, integer) + "]";
  }

  private static boolean exactFloat(BigDecimal value) {
    return new BigDecimal((double) value.floatValue()).compareTo(value) == 0;
  }

  private static Optional<BigDecimal> singleNumber(Atom atom) {
    return atom.values().size() == 1 && atom.values().get(0) instanceof Num n
        ? Optional.of(n.value())
        : Optional.empty();
  }

  /** The listed values when all are whole numbers. */
  private static Optional<List<BigDecimal>> integers(Atom atom) {
    List<BigDecimal> values = new ArrayList<>();
    for (Lit lit : atom.values()) {
      if (!(lit instanceof Num n) || n.value().stripTrailingZeros().scale() > 0) {
        return Optional.empty();
      }
      values.add(n.value());
    }
    return values.isEmpty() ? Optional.empty() : Optional.of(values);
  }

  private static boolean contiguous(List<BigDecimal> values) {
    long distinct = values.stream().map(BigDecimal::stripTrailingZeros).distinct().count();
    return min(values).add(BigDecimal.valueOf(distinct - 1)).compareTo(max(values)) == 0;
  }

  private static BigDecimal min(List<BigDecimal> values) {
    return values.stream().min(BigDecimal::compareTo).orElseThrow();
  }

  private static BigDecimal max(List<BigDecimal> values) {
    return values.stream().max(BigDecimal::compareTo).orElseThrow();
  }

  private static BigDecimal max(BigDecimal a, BigDecimal b) {
    return a == null || b.compareTo(a) > 0 ? b : a;
  }

  private static BigDecimal min(BigDecimal a, BigDecimal b) {
    return a == null || b.compareTo(a) < 0 ? b : a;
  }

  /** Integers plain; decimals with trailing zeros dropped but one decimal kept ({@code 1.0}). */
  private static String render(BigDecimal value, boolean integer) {
    if (integer) {
      return value.toBigInteger().toString();
    }
    String plain = value.stripTrailingZeros().toPlainString();
    return plain.contains(".") ? plain : plain + ".0";
  }

  // ------------------------------------------------------------------ dates

  private static String date(ColumnChecks checks, String datatype, Set<String> notes) {
    Matcher range = DATE_RANGE.matcher(datatype);
    if (!range.matches()) {
      checks.atoms.forEach(atom -> notes.add(unsupported(atom)));
      return null;
    }
    LocalDate low = null;
    LocalDate high = null;
    boolean past = false;
    boolean future = false;
    for (Atom atom : checks.atoms) {
      Optional<LocalDate> day = singleDay(atom);
      boolean now =
          atom.values().size() == 1
              && (atom.values().get(0) instanceof Now || atom.values().get(0) instanceof Today);
      if (now && (atom.op() == Op.LT || atom.op() == Op.LE)) {
        past = true;
      } else if (now && (atom.op() == Op.GT || atom.op() == Op.GE)) {
        future = true;
      } else if (day.isEmpty() || atom.op() == Op.IN) {
        notes.add(unsupported(atom));
      } else {
        LocalDate d = day.get();
        switch (atom.op()) {
          case GE -> low = later(low, d);
          case GT -> low = later(low, d.plusDays(1));
          case LE -> high = earlier(high, d);
          case LT -> high = earlier(high, d.minusDays(1));
          default -> {
            low = later(low, d);
            high = earlier(high, d);
          }
        }
      }
    }
    boolean clock = past || future;
    LocalDate baseStart = LocalDate.parse(range.group(1));
    LocalDate baseEnd = LocalDate.parse(range.group(2));
    if (past && future) {
      notes.add(unsatisfiable(checks));
      return null;
    }
    if (clock) {
      baseStart = past ? PAST_START : FUTURE_START;
      baseEnd = past ? PAST_END : FUTURE_END;
    }
    Optional<LocalDate[]> bounds = dateBounds(low, high, baseStart, baseEnd, clock);
    if (bounds.isEmpty()) {
      notes.add(unsatisfiable(checks));
      return null;
    }
    if (clock) {
      notes.add(
          checks.atoms.get(0).source()
              + " depends on the current date — generated as the fixed range "
              + bounds.get()[0]
              + ".."
              + bounds.get()[1]
              + ", verify");
    }
    boolean unchanged = bounds.get()[0].equals(baseStart) && bounds.get()[1].equals(baseEnd);
    return unchanged && !clock ? null : "date[" + bounds.get()[0] + ".." + bounds.get()[1] + "]";
  }

  /**
   * The default range cut by the explicit bounds. A bound entirely outside the default keeps both
   * sides consistent (ten years on the open side), unless the clock fixes the range.
   */
  private static Optional<LocalDate[]> dateBounds(
      LocalDate low, LocalDate high, LocalDate start, LocalDate end, boolean clock) {
    LocalDate newLow = low == null ? start : later(low, start);
    LocalDate newHigh = high == null ? end : earlier(high, end);
    if (!newLow.isAfter(newHigh)) {
      return Optional.of(new LocalDate[] {newLow, newHigh});
    }
    if (clock || (low != null && high != null && low.isAfter(high))) {
      return Optional.empty();
    }
    if (low != null && high != null) {
      return Optional.of(new LocalDate[] {low, high});
    }
    return low == null
        ? Optional.of(new LocalDate[] {high.minusYears(OPEN_BOUND_YEARS), high})
        : Optional.of(new LocalDate[] {low, low.plusYears(OPEN_BOUND_YEARS)});
  }

  private static Optional<LocalDate> singleDay(Atom atom) {
    if (atom.values().size() != 1) {
      return Optional.empty();
    }
    return switch (atom.values().get(0)) {
      case Day d -> Optional.of(d.value());
      case Txt t -> parseDay(t.value());
      default -> Optional.empty();
    };
  }

  private static Optional<LocalDate> parseDay(String text) {
    try {
      return Optional.of(LocalDate.parse(text.strip()));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static LocalDate later(LocalDate a, LocalDate b) {
    return a == null || b.isAfter(a) ? b : a;
  }

  private static LocalDate earlier(LocalDate a, LocalDate b) {
    return a == null || b.isBefore(a) ? b : a;
  }

  // ------------------------------------------------------------------ timestamps and text

  /** {@code timestamp[now-365d..now]} already meets "before now"; anything else is a note. */
  private static void timestamp(ColumnChecks checks, Set<String> notes) {
    for (Atom atom : checks.atoms) {
      boolean beforeNow =
          (atom.op() == Op.LT || atom.op() == Op.LE)
              && atom.values().size() == 1
              && atom.values().get(0) instanceof Now;
      if (!beforeNow) {
        notes.add(unsupported(atom));
      }
    }
  }

  /**
   * The values every IN / equality allows, as {@code enum[...]} when it can express and hold them.
   */
  private static String text(ColumnChecks checks, ColumnType type, Set<String> notes) {
    List<String> allowed = null;
    boolean honoured = checks.notes.isEmpty();
    for (Atom atom : checks.atoms) {
      Optional<List<String>> values = texts(atom);
      if (values.isEmpty()) {
        notes.add(unsupported(atom));
        honoured = false;
      } else if (allowed == null) {
        allowed = values.get().stream().distinct().toList();
      } else {
        allowed = allowed.stream().filter(values.get()::contains).toList();
      }
    }
    if (allowed == null || !honoured) { // a part left on the column could reject the values
      return null;
    }
    if (allowed.isEmpty()) {
      notes.add(unsatisfiable(checks));
      return null;
    }
    if (!allowed.stream().allMatch(DdlTypeMapper::isEnumMember)) {
      notes.add(inexpressible(checks));
      return null;
    }
    if (!allowed.stream().allMatch(v -> fits(v, type.maxLength()))) {
      notes.add(unsatisfiable(checks));
      return null;
    }
    return "enum[" + String.join(",", allowed) + "]";
  }

  private static boolean fits(String value, int maxLength) {
    return maxLength < 0 || value.length() <= maxLength;
  }

  private static Optional<List<String>> texts(Atom atom) {
    if (atom.op() != Op.EQ && atom.op() != Op.IN) {
      return Optional.empty();
    }
    List<String> values = new ArrayList<>();
    for (Lit lit : atom.values()) {
      if (!(lit instanceof Txt t)) {
        return Optional.empty();
      }
      values.add(t.value());
    }
    return Optional.of(values);
  }
}
