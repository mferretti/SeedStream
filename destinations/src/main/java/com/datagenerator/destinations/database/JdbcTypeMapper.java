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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Maps generated Java values to JDBC {@link PreparedStatement} bindings.
 *
 * <p><b>Strategy (Option A):</b> Uses {@code instanceof} checks on the generated value's runtime
 * type to determine the appropriate {@code setXxx()} call. This works because each {@code DataType}
 * always produces the same Java type:
 *
 * <ul>
 *   <li>{@code int[]} → {@link Integer} → {@code setInt()}
 *   <li>{@code decimal[]} → {@link BigDecimal} → {@code setBigDecimal()}
 *   <li>{@code boolean} → {@link Boolean} → {@code setBoolean()}
 *   <li>{@code char[]} → {@link String} → {@code setString()}
 *   <li>{@code date[]} → {@link LocalDate} → {@code setDate()}
 *   <li>{@code timestamp[]} → {@link Instant} → {@code setTimestamp()}
 *   <li>{@code enum[]}, Datafaker types → {@link String} → {@code setString()}
 *   <li>{@code null} → {@code setNull(Types.NULL)}
 * </ul>
 *
 * <p><b>Limitations:</b> Datafaker types that conceptually represent numbers (e.g. {@code age})
 * produce {@link String} values, which may fail if the target DB column is {@code INT}. This is a
 * known limitation of Option A — see TASK-042 for the Option B decision spike.
 *
 * <p><b>Thread Safety:</b> Stateless — all methods are static. Safe for concurrent use.
 */
public class JdbcTypeMapper {

  private JdbcTypeMapper() {}

  /**
   * Bind a generated value to the given parameter index on a {@link PreparedStatement}.
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
      ps.setTimestamp(index, Timestamp.from(t));
    } else {
      // String, enum values, Datafaker types, and any other Object → setString
      ps.setString(index, value.toString());
    }
  }
}
