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
import com.datagenerator.inspector.FakerTypes;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.NameHints;
import com.datagenerator.inspector.Names;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps a SQL column type to a SeedStream datatype string (foreign keys are resolved separately by
 * {@link DdlInspector}). Mapping is mechanical — SQL carries no value semantics, so string columns
 * lean on the shared {@link NameHints}. See {@code docs/INSPECT-V1-SPEC.md} §7c.
 *
 * <p>Type names are first folded to a small canonical alphabet ({@link #canonicalType}) so that
 * multi-word ANSI forms ({@code CHARACTER VARYING}, {@code DOUBLE PRECISION}, {@code TIMESTAMP WITH
 * TIME ZONE}) and vendor aliases ({@code SERIAL}, {@code INT8}, {@code UUID}, {@code JSONB}) land
 * on the same branch as their common synonyms instead of falling through to the unknown-type
 * default.
 */
public final class DdlTypeMapper {

  private static final String TYPE_TIMESTAMP = "TIMESTAMP";
  private static final String TYPE_DECIMAL = "DECIMAL";
  private static final String TYPE_VARCHAR = "VARCHAR";

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /**
   * Vendor / multi-word SQL type names folded onto a canonical key. The canonical keys are the ones
   * the {@link #map} switch branches on; anything absent here passes through unchanged and, if
   * still unrecognized, defaults to {@link Defaults#STRING} flagged {@code UNKNOWN_TYPE}.
   */
  private static final Map<String, String> SYNONYMS =
      Map.ofEntries(
          Map.entry("BOOL", "BOOLEAN"),
          Map.entry("DATETIME", TYPE_TIMESTAMP),
          Map.entry("SMALLDATETIME", TYPE_TIMESTAMP),
          Map.entry("DATETIME2", TYPE_TIMESTAMP),
          Map.entry("DATETIMEOFFSET", TYPE_TIMESTAMP),
          Map.entry("TIMESTAMPTZ", TYPE_TIMESTAMP),
          Map.entry("TIMESTAMP WITH TIME ZONE", TYPE_TIMESTAMP),
          Map.entry("TIMESTAMP WITHOUT TIME ZONE", TYPE_TIMESTAMP),
          Map.entry("UNIQUEIDENTIFIER", "UUID"),
          Map.entry("INTEGER", "INT"),
          Map.entry("BIGINT", "INT"),
          Map.entry("SMALLINT", "INT"),
          Map.entry("TINYINT", "INT"),
          Map.entry("MEDIUMINT", "INT"),
          Map.entry("INT2", "INT"),
          Map.entry("INT4", "INT"),
          Map.entry("INT8", "INT"),
          Map.entry("SERIAL", "INT"),
          Map.entry("BIGSERIAL", "INT"),
          Map.entry("SMALLSERIAL", "INT"),
          Map.entry("SERIAL4", "INT"),
          Map.entry("SERIAL8", "INT"),
          Map.entry("NUMERIC", TYPE_DECIMAL),
          Map.entry("NUMBER", TYPE_DECIMAL),
          Map.entry("DEC", TYPE_DECIMAL),
          Map.entry("REAL", TYPE_DECIMAL),
          Map.entry("FLOAT", TYPE_DECIMAL),
          Map.entry("FLOAT4", TYPE_DECIMAL),
          Map.entry("FLOAT8", TYPE_DECIMAL),
          Map.entry("DOUBLE", TYPE_DECIMAL),
          Map.entry("DOUBLE PRECISION", TYPE_DECIMAL),
          Map.entry("BINARY_FLOAT", TYPE_DECIMAL),
          Map.entry("BINARY_DOUBLE", TYPE_DECIMAL),
          Map.entry("MONEY", TYPE_DECIMAL),
          Map.entry("SMALLMONEY", TYPE_DECIMAL),
          Map.entry("CHARACTER", TYPE_VARCHAR),
          Map.entry("CHARACTER VARYING", TYPE_VARCHAR),
          Map.entry("CHAR VARYING", TYPE_VARCHAR),
          Map.entry("NATIONAL CHARACTER", TYPE_VARCHAR),
          Map.entry("NATIONAL CHARACTER VARYING", TYPE_VARCHAR),
          Map.entry("NVARCHAR", TYPE_VARCHAR),
          Map.entry("NCHAR", TYPE_VARCHAR),
          Map.entry("CHAR", TYPE_VARCHAR),
          Map.entry("VARCHAR2", TYPE_VARCHAR),
          Map.entry("NVARCHAR2", TYPE_VARCHAR),
          Map.entry("VARCHARACTER", TYPE_VARCHAR),
          Map.entry("STRING", TYPE_VARCHAR),
          Map.entry("CLOB", "TEXT"),
          Map.entry("NCLOB", "TEXT"),
          Map.entry("TINYTEXT", "TEXT"),
          Map.entry("MEDIUMTEXT", "TEXT"),
          Map.entry("LONGTEXT", "TEXT"),
          Map.entry("NTEXT", "TEXT"),
          Map.entry("LONG VARCHAR", "TEXT"));

  /**
   * Maps a column.
   *
   * @param columnName used for name-hint inference on string types
   * @param sqlType the SQL type name (e.g. {@code VARCHAR}, {@code BIGINT})
   * @param args type arguments (e.g. {@code [255]} for {@code VARCHAR(255)})
   */
  public MappedType map(String columnName, String sqlType, List<String> args) {
    String raw = normalize(sqlType);
    String type = SYNONYMS.getOrDefault(raw, raw);
    if ("BIT".equals(raw) && isAtMostOneBit(args)) {
      return MappedType.declared("boolean"); // SQL Server BIT / BIT(1): a 0/1 flag
    }
    if ("TINYINT".equals(raw)
        && args != null
        && args.size() == 1
        && "1".equals(args.get(0).trim())) {
      return MappedType.declared("boolean"); // MySQL's boolean convention
    }
    return switch (type) {
      case "BOOLEAN" -> MappedType.declared("boolean");
      case "DATE" -> MappedType.declared(Defaults.DATE);
      case TYPE_TIMESTAMP -> MappedType.declared(Defaults.TIMESTAMP);
      case "UUID" -> uuidType();
      case "INT" -> MappedType.defaultRange("int[" + Defaults.INT_MIN + ".." + intMax(raw) + "]");
      case TYPE_DECIMAL -> mapNumeric(raw, args);
      case "ENUM", "SET" -> mapEnum(raw, args);
      case TYPE_VARCHAR -> {
        boolean hasLength = args != null && !args.isEmpty();
        int length = length(args, Defaults.VARCHAR_DEFAULT_LENGTH);
        yield length == 0
            ? MappedType.declared("char[0..0]") // zero-length column: only "" fits
            : mapString(columnName, length, !hasLength);
      }
      case "TEXT" -> mapString(columnName, Defaults.TEXT_MAX_LENGTH, true);
      default -> MappedType.unknownType(Defaults.STRING); // unknown — §6 Q2
    };
  }

  /**
   * Folds a raw SQL type name to a canonical key: upper-cased, whitespace collapsed, then resolved
   * through {@link #SYNONYMS}. {@code "character varying"} and {@code "CHARACTER VARYING"} both
   * become {@code VARCHAR}; an unmapped name passes through so the switch can default it to
   * unknown.
   */
  private static String normalize(String sqlType) {
    if (sqlType == null) {
      return "";
    }
    return WHITESPACE.matcher(sqlType.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
  }

  /** {@code BIT} without a length, or {@code BIT(1)}; {@code BIT(n>1)} is a bit string. */
  private static boolean isAtMostOneBit(List<String> args) {
    return args == null || args.isEmpty() || "1".equals(args.get(0).trim());
  }

  /**
   * Upper bound of the default int range, capped to what narrow integer columns can hold so
   * generated values insert (#377): SMALLINT 32767; TINYINT 127, which fits both MySQL's signed and
   * SQL Server's unsigned TINYINT.
   */
  private static long intMax(String rawType) {
    return switch (rawType) {
      case "SMALLINT", "INT2", "SMALLSERIAL" -> Math.min(Defaults.INT_MAX, 32_767L);
      case "TINYINT" -> Math.min(Defaults.INT_MAX, 127L);
      case null, default -> Defaults.INT_MAX;
    };
  }

  private static final Set<String> BINARY_TYPES =
      Set.of(
          "BYTEA",
          "BLOB",
          "TINYBLOB",
          "MEDIUMBLOB",
          "LONGBLOB",
          "BINARY",
          "VARBINARY",
          "IMAGE",
          "RAW",
          "LONG RAW");

  /** True for binary SQL types (#439), which have no SeedStream type. */
  static boolean isBinary(String sqlType) {
    return BINARY_TYPES.contains(normalize(sqlType));
  }

  /** True for the {@code VARCHAR} / {@code TEXT} families (any length). */
  static boolean isText(String sqlType) {
    String type = SYNONYMS.getOrDefault(normalize(sqlType), normalize(sqlType));
    return TYPE_VARCHAR.equals(type) || "TEXT".equals(type);
  }

  /**
   * Largest key value a column of this SQL type can hold when keys are generated from 1 (numeric
   * strings for text); empty when unbounded or not a key type.
   */
  static OptionalLong keyCeiling(String sqlType, List<String> args) {
    String raw = normalize(sqlType);
    String type = SYNONYMS.getOrDefault(raw, raw);
    switch (raw) {
      case "TINYINT":
        return OptionalLong.of(127L);
      case "SMALLINT", "INT2", "SMALLSERIAL":
        return OptionalLong.of(32_767L);
      case "MEDIUMINT":
        return OptionalLong.of(8_388_607L);
      case "INT", "INTEGER", "INT4", "SERIAL", "SERIAL4":
        return OptionalLong.of(Integer.MAX_VALUE);
      case null, default:
        break;
    }
    if (args == null
        || args.isEmpty()
        || !(TYPE_DECIMAL.equals(type) || TYPE_VARCHAR.equals(type))) {
      return OptionalLong.empty();
    }
    boolean text = TYPE_VARCHAR.equals(type);
    if (!text
        && (!Set.of("DECIMAL", "NUMERIC", "NUMBER", "DEC").contains(raw)
            || (args.size() > 1 && !"0".equals(args.get(1).trim())))) {
      return OptionalLong.empty();
    }
    try {
      int digits = Integer.parseInt(args.get(0).trim().split("\\s+")[0]); // "5 BYTE" -> 5
      return digits >= 1 && digits <= 18
          ? OptionalLong.of((long) Math.pow(10, digits) - 1)
          : OptionalLong.empty();
    } catch (NumberFormatException e) {
      return OptionalLong.empty();
    }
  }

  /**
   * Exact and floating numerics. Fixed-point {@code DECIMAL/NUMERIC/NUMBER(p,s)} with scale 0 are
   * integers (#447), capped to the column capacity {@code 10^p - 1} and the default int max;
   * otherwise the decimal range is capped to the column capacity {@code 10^(p-s) - 10^-s} (#377,
   * e.g. {@code DECIMAL(4,2)} -> {@code 99.99}). Floating types (FLOAT, REAL, MONEY, ...) take no
   * precision/scale and keep the default; so do {@code NUMBER} and {@code NUMBER(*)}.
   */
  private static MappedType mapNumeric(String rawType, List<String> args) {
    String defaultRange = "decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]";
    String defaultInt = "int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]";
    boolean fixedPoint = Set.of(TYPE_DECIMAL, "NUMERIC", "NUMBER", "DEC").contains(rawType);
    if (!fixedPoint || args == null || args.isEmpty()) {
      return MappedType.defaultRange(defaultRange);
    }
    try {
      boolean unboundedPrecision = "*".equals(args.get(0).trim());
      int precision = unboundedPrecision ? 0 : Integer.parseInt(args.get(0).trim());
      int scale = args.size() > 1 ? Integer.parseInt(args.get(1).trim()) : 0;
      if (scale < 0) {
        return MappedType.review(
            defaultInt, "negative scale rounds values to a power of ten — verify the range");
      }
      if (unboundedPrecision && scale == 0 && args.size() > 1) {
        return MappedType.defaultRange(defaultInt); // NUMBER(*,0)
      }
      if (precision <= 0) {
        return MappedType.defaultRange(defaultRange);
      }
      if (scale == 0) {
        long capacity = precision < 6 ? (long) Math.pow(10, precision) - 1 : Defaults.INT_MAX;
        return capacity < Defaults.INT_MAX
            ? MappedType.declared("int[" + Defaults.INT_MIN + ".." + capacity + "]")
            : MappedType.defaultRange(defaultInt);
      }
      BigDecimal capacity =
          BigDecimal.TEN
              .pow(Math.max(precision - scale, 0), MathContext.UNLIMITED)
              .subtract(BigDecimal.ONE.movePointLeft(scale));
      BigDecimal defaultMax = new BigDecimal(Defaults.DECIMAL_MAX);
      if (capacity.compareTo(defaultMax) >= 0) {
        // keep the default bounds, but at the declared scale so values fit the column
        return MappedType.defaultRange(
            scale > defaultMax.scale()
                ? "decimal["
                    + BigDecimal.ZERO.setScale(scale).toPlainString()
                    + ".."
                    + defaultMax.setScale(scale).toPlainString()
                    + "]"
                : defaultRange);
      }
      String min = BigDecimal.ZERO.setScale(scale).toPlainString();
      return MappedType.defaultRange(
          "decimal[" + min + ".." + capacity.setScale(scale).toPlainString() + "]");
    } catch (NumberFormatException | ArithmeticException e) {
      return MappedType.defaultRange(defaultRange);
    }
  }

  /**
   * MySQL {@code ENUM('a','b')} / {@code SET(...)}: args are the raw SQL literals. Members that
   * {@code enum[...]} cannot express (comma, closing bracket, empty) fall back to a char sized to
   * the longest member.
   */
  private static MappedType mapEnum(String rawType, List<String> args) {
    if (args == null || args.isEmpty()) {
      return MappedType.unknownType(Defaults.STRING);
    }
    List<String> members = args.stream().map(DdlTypeMapper::unquoteLiteral).toList();
    if (!members.stream().allMatch(DdlTypeMapper::isEnumMember)) {
      int longest = members.stream().mapToInt(String::length).max().orElse(1);
      return MappedType.review(
          "char[1.." + Math.max(longest, 1) + "]",
          rawType + " members cannot be expressed as enum[...] — generated as char, verify");
    }
    String datatype = "enum[" + String.join(",", members) + "]";
    return "SET".equals(rawType)
        ? MappedType.review(
            datatype, "SET holds several members per row; one member generated — verify")
        : MappedType.declared(datatype);
  }

  /** Whether {@code enum[...]} can express the value (no comma or bracket, not blank-padded). */
  static boolean isEnumMember(String member) {
    return !member.isEmpty()
        && member.equals(member.strip())
        && !member.contains(",")
        && !member.contains("]");
  }

  /**
   * A SQL string literal's value: {@code 'it''s'} and {@code "it""s"} lose their quotes and
   * doubling.
   */
  static String unquoteLiteral(String literal) {
    String a = literal.trim();
    if (a.length() >= 2
        && (a.charAt(0) == '\'' || a.charAt(0) == '"')
        && a.endsWith(a.substring(0, 1))) {
      String quote = a.substring(0, 1);
      return a.substring(1, a.length() - 1).replace(quote + quote, quote);
    }
    return a;
  }

  /**
   * Native {@code UUID} column → the {@code uuid} datafaker key, or a fixed-width char fallback.
   */
  private MappedType uuidType() {
    return FakerTypes.canonical("uuid")
        .map(MappedType::declared)
        .orElseGet(() -> MappedType.declared("char[36..36]"));
  }

  private MappedType mapString(String columnName, int maxLength, boolean lengthInferred) {
    Optional<String> hint =
        NameHints.forFieldName(columnName)
            .flatMap(FakerTypes::canonical)
            .or(() -> FakerTypes.canonical(Names.toSnakeCase(columnName)));
    if (hint.isPresent()) {
      return MappedType.nameHint(hint.get());
    }
    String datatype = "char[1.." + maxLength + "]";
    return lengthInferred ? MappedType.defaultRange(datatype) : MappedType.declared(datatype);
  }

  private int length(List<String> args, int fallback) {
    if (args == null || args.isEmpty()) {
      return fallback;
    }
    try {
      // first token only: Oracle writes VARCHAR2(15 BYTE) / CHAR(5 CHAR)
      return Integer.parseInt(WHITESPACE.split(args.get(0).trim())[0]);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
