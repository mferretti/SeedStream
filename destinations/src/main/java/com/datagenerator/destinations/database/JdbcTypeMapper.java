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

package com.datagenerator.destinations.database;

import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ReferenceType;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Maps generated Java values to JDBC {@link PreparedStatement} bindings.
 *
 * <p>Provides two strategies:
 *
 * <ul>
 *   <li><b>Option A</b> ({@link #bind(PreparedStatement, int, Object)}): uses {@code instanceof}
 *       checks on the runtime type. Simple and works for all primitive types but cannot handle
 *       Datafaker types that produce {@link String} for semantically numeric fields (e.g. {@code
 *       age}).
 *   <li><b>Option B</b> ({@link #bind(PreparedStatement, int, Object, DataType)}): uses the
 *       declared {@link DataType} for accurate SQL type resolution, correct {@code setNull} SQL
 *       types, and coercion of {@link String} values to the target primitive type. Use this when
 *       the field schema is available (e.g. from the parsed YAML structure).
 * </ul>
 *
 * <p><b>Type mapping (Option B):</b>
 *
 * <ul>
 *   <li>{@link PrimitiveType.Kind#INT} → {@code setInt()} (coerces {@link String} via parseInt)
 *   <li>{@link PrimitiveType.Kind#DECIMAL} → {@code setBigDecimal()} (coerces {@link String})
 *   <li>{@link PrimitiveType.Kind#BOOLEAN} → {@code setBoolean()} (coerces {@link String})
 *   <li>{@link PrimitiveType.Kind#CHAR} → {@code setString()}
 *   <li>{@link PrimitiveType.Kind#DATE} → {@code setDate()} (coerces ISO-8601 {@link String})
 *   <li>{@link PrimitiveType.Kind#TIMESTAMP} → {@code setObject()} with a UTC {@link LocalDateTime}
 *       (coerces ISO-8601 {@link String})
 *   <li>{@link EnumType} → {@code setString()}
 *   <li>{@link CustomDatafakerType} → {@code setString()} (Datafaker always produces strings)
 *   <li>{@code null} → {@code setNull()} with correct SQL type derived from {@link DataType}
 * </ul>
 *
 * <p><b>Timestamps are written as a UTC wall clock.</b> {@code setTimestamp(i, ts)} lets the driver
 * render the instant in the JVM's default time zone, so the same seed produced different stored
 * values on machines in different zones — an {@code Instant} of {@code 12:00:00Z} landed as {@code
 * 14:00:00} in a {@code TIMESTAMP} column on a CEST box and as {@code 12:00:00} on a UTC box.
 * Binding a {@link LocalDateTime} taken at {@link ZoneOffset#UTC} removes the JVM zone from the
 * equation entirely: the value carries no offset, so the driver has nothing to convert and the
 * stored wall clock depends only on the seed.
 *
 * <p>This governs the value leaving the client. Zone-aware column types still apply their own
 * conversion on top: PostgreSQL {@code timestamptz} and Oracle {@code TIMESTAMP WITH TIME ZONE}
 * preserve the instant, but MySQL {@code TIMESTAMP} converts using the connection's session zone
 * and can still drift — see issue #218. Zone-naive columns ({@code TIMESTAMP}, {@code DATETIME},
 * {@code DATETIME2}) are exact.
 *
 * <p><b>Thread Safety:</b> Stateless — all methods are static and every value bound is immutable.
 * Safe for concurrent use.
 */
// java.sql.Date is required by PreparedStatement.setDate(); java.time cannot be used at the JDBC
// binding boundary. Sonar S2143 on this is a false positive (marked as such on the server); the
// rule fires file-level with no anchor line, so @SuppressWarnings/NOSONAR cannot silence it.
public class JdbcTypeMapper {

  private JdbcTypeMapper() {}

  /**
   * Bind a generated value using runtime type inference (Option A).
   *
   * <p>Use when no schema information is available. Falls back to {@code setString} for any type
   * not explicitly handled.
   *
   * @param ps the prepared statement to bind to
   * @param index 1-based parameter index
   * @param value the generated value (may be null)
   * @throws SQLException if the JDBC binding fails
   */
  public static void bind(PreparedStatement ps, int index, Object value) throws SQLException {
    if (value == null) {
      ps.setNull(index, Types.NULL);
    } else if (value instanceof Integer i) {
      ps.setInt(index, i);
    } else if (value instanceof Long l) {
      ps.setLong(index, l);
    } else if (value instanceof BigDecimal d) {
      ps.setBigDecimal(index, d);
    } else if (value instanceof Boolean b) {
      ps.setBoolean(index, b);
    } else if (value instanceof LocalDate d) {
      ps.setDate(index, Date.valueOf(d));
    } else if (value instanceof Instant t) {
      ps.setObject(index, toUtcWallClock(t));
    } else {
      // String, enum values, Datafaker types, and any other Object → setString
      ps.setString(index, value.toString());
    }
  }

  /**
   * Bind a generated value using declared {@link DataType} metadata (Option B).
   *
   * <p>Preferred over {@link #bind(PreparedStatement, int, Object)} when the field schema is known.
   * Provides:
   *
   * <ul>
   *   <li>Correct SQL type for {@code setNull} (not {@code Types.NULL})
   *   <li>String-to-primitive coercion (e.g. Datafaker {@code "42"} → {@code setInt(42)})
   *   <li>Accurate binding for all declared primitive types
   * </ul>
   *
   * @param ps the prepared statement to bind to
   * @param index 1-based parameter index
   * @param value the generated value (may be null)
   * @param type the declared DataType for this field (must not be null)
   * @throws SQLException if the JDBC binding fails
   * @throws NumberFormatException if a String value cannot be coerced to the declared numeric type
   */
  public static void bind(PreparedStatement ps, int index, Object value, DataType type)
      throws SQLException {
    if (value == null) {
      ps.setNull(index, sqlTypeFor(type));
      return;
    }

    if (type instanceof PrimitiveType p) {
      switch (p.getKind()) {
        case INT -> ps.setInt(index, toInt(value));
        case DECIMAL -> ps.setBigDecimal(index, toDecimal(value));
        case BOOLEAN -> ps.setBoolean(index, toBool(value));
        case CHAR -> ps.setString(index, value.toString());
        case DATE -> ps.setDate(index, toDate(value));
        case TIMESTAMP -> ps.setObject(index, toUtcWallClock(value));
        default -> throw new IllegalStateException("Unexpected PrimitiveType.Kind: " + p.getKind());
      }
      return;
    }

    if (type instanceof ReferenceType) {
      // ref[] generates Long; bind as BIGINT (fits INT columns when value is in range)
      ps.setLong(
          index,
          switch (value) {
            case Long l -> l;
            case Integer i -> i.longValue();
            default -> Long.parseLong(value.toString());
          });
      return;
    }

    // EnumType, CustomDatafakerType, and anything else → setString
    ps.setString(index, value.toString());
  }

  // --- Private helpers ---

  /**
   * Derive the SQL type constant for use in {@code setNull()}.
   *
   * @param type the declared DataType
   * @return a {@link Types} constant
   */
  private static int sqlTypeFor(DataType type) {
    if (type instanceof PrimitiveType p) {
      return switch (p.getKind()) {
        case INT -> Types.INTEGER;
        case DECIMAL -> Types.DECIMAL;
        case BOOLEAN -> Types.BOOLEAN;
        case CHAR -> Types.VARCHAR;
        case DATE -> Types.DATE;
        case TIMESTAMP -> Types.TIMESTAMP;
      };
    }
    if (type instanceof ReferenceType) return Types.BIGINT;
    return Types.VARCHAR; // EnumType, CustomDatafakerType
  }

  private static int toInt(Object value) {
    if (value instanceof Integer i) return i;
    if (value instanceof Long l) return l.intValue();
    return Integer.parseInt(value.toString());
  }

  private static BigDecimal toDecimal(Object value) {
    if (value instanceof BigDecimal d) return d;
    return new BigDecimal(value.toString());
  }

  private static boolean toBool(Object value) {
    if (value instanceof Boolean b) return b;
    return Boolean.parseBoolean(value.toString());
  }

  private static Date toDate(Object value) {
    if (value instanceof LocalDate d) return Date.valueOf(d);
    return Date.valueOf(LocalDate.parse(value.toString()));
  }

  /**
   * Converts a generated value to the UTC wall clock to bind. Returning {@link LocalDateTime} — a
   * value with no offset — is what keeps the write independent of the JVM's time zone.
   */
  private static LocalDateTime toUtcWallClock(Object value) {
    Instant instant = value instanceof Instant t ? t : Instant.parse(value.toString());
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
