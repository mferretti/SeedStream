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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcTypeMapperTest {

  @Mock private PreparedStatement ps;

  @BeforeEach
  void setUp() throws SQLException {
    // no-op setup — mocks are injected by Mockito
  }

  // -------------------------------------------------------------------------
  // Option A — runtime instanceof inference
  // -------------------------------------------------------------------------

  @Nested
  class OptionA {

    @Test
    void shouldBindNullAsNullType() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, null);

      verify(ps).setNull(1, Types.NULL);
    }

    @Test
    void shouldBindIntegerAsInt() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, 42);

      verify(ps).setInt(1, 42);
    }

    @Test
    void shouldBindLongAsLong() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, 123456789L);

      verify(ps).setLong(1, 123456789L);
    }

    @Test
    void shouldBindBigDecimalAsBigDecimal() throws SQLException {
      BigDecimal value = new BigDecimal("99.99");
      JdbcTypeMapper.bind(ps, 1, value);

      verify(ps).setBigDecimal(1, value);
    }

    @Test
    void shouldBindBooleanAsBoolean() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, true);

      verify(ps).setBoolean(1, true);
    }

    @Test
    void shouldBindLocalDateAsDate() throws SQLException {
      LocalDate date = LocalDate.of(2024, 6, 15);
      JdbcTypeMapper.bind(ps, 1, date);

      verify(ps).setDate(1, Date.valueOf(date));
    }

    @Test
    void shouldBindInstantAsTimestamp() throws SQLException {
      Instant instant = Instant.parse("2024-06-15T10:30:00Z");
      JdbcTypeMapper.bind(ps, 1, instant);

      verify(ps).setTimestamp(1, Timestamp.from(instant));
    }

    @Test
    void shouldBindStringAsString() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, "hello");

      verify(ps).setString(1, "hello");
    }

    @Test
    void shouldBindEnumValueAsString() throws SQLException {
      JdbcTypeMapper.bind(ps, 1, "ACTIVE");

      verify(ps).setString(1, "ACTIVE");
    }

    @Test
    void shouldPropagateSQLException() throws SQLException {
      doThrow(new SQLException("DB error")).when(ps).setInt(anyInt(), anyInt());

      assertThatThrownBy(() -> JdbcTypeMapper.bind(ps, 1, 42)).isInstanceOf(SQLException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Option B — DataType-aware binding
  // -------------------------------------------------------------------------

  @Nested
  class OptionB {

    // --- Null handling with correct SQL types ---

    @Test
    void shouldBindNullIntWithCorrectSqlType() throws SQLException {
      PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");
      JdbcTypeMapper.bind(ps, 1, null, intType);

      verify(ps).setNull(1, Types.INTEGER);
    }

    @Test
    void shouldBindNullDecimalWithCorrectSqlType() throws SQLException {
      PrimitiveType decType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
      JdbcTypeMapper.bind(ps, 1, null, decType);

      verify(ps).setNull(1, Types.DECIMAL);
    }

    @Test
    void shouldBindNullBooleanWithCorrectSqlType() throws SQLException {
      PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
      JdbcTypeMapper.bind(ps, 1, null, boolType);

      verify(ps).setNull(1, Types.BOOLEAN);
    }

    @Test
    void shouldBindNullCharWithCorrectSqlType() throws SQLException {
      PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "50");
      JdbcTypeMapper.bind(ps, 1, null, charType);

      verify(ps).setNull(1, Types.VARCHAR);
    }

    @Test
    void shouldBindNullDateWithCorrectSqlType() throws SQLException {
      PrimitiveType dateType =
          new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
      JdbcTypeMapper.bind(ps, 1, null, dateType);

      verify(ps).setNull(1, Types.DATE);
    }

    @Test
    void shouldBindNullTimestampWithCorrectSqlType() throws SQLException {
      PrimitiveType tsType = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, null, null);
      JdbcTypeMapper.bind(ps, 1, null, tsType);

      verify(ps).setNull(1, Types.TIMESTAMP);
    }

    @Test
    void shouldBindNullEnumAsVarchar() throws SQLException {
      EnumType enumType = new EnumType(List.of("A", "B", "C"));
      JdbcTypeMapper.bind(ps, 1, null, enumType);

      verify(ps).setNull(1, Types.VARCHAR);
    }

    @Test
    void shouldBindNullDatafakerTypeAsVarchar() throws SQLException {
      CustomDatafakerType datafakerType = new CustomDatafakerType("first_name");
      JdbcTypeMapper.bind(ps, 1, null, datafakerType);

      verify(ps).setNull(1, Types.VARCHAR);
    }

    // --- Primitive binding with native Java types ---

    @Test
    void shouldBindIntegerValueForIntType() throws SQLException {
      PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");
      JdbcTypeMapper.bind(ps, 1, 42, intType);

      verify(ps).setInt(1, 42);
    }

    @Test
    void shouldBindBigDecimalValueForDecimalType() throws SQLException {
      PrimitiveType decType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
      BigDecimal value = new BigDecimal("9.99");
      JdbcTypeMapper.bind(ps, 1, value, decType);

      verify(ps).setBigDecimal(1, value);
    }

    @Test
    void shouldBindBooleanValueForBooleanType() throws SQLException {
      PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
      JdbcTypeMapper.bind(ps, 1, true, boolType);

      verify(ps).setBoolean(1, true);
    }

    @Test
    void shouldBindLocalDateValueForDateType() throws SQLException {
      PrimitiveType dateType =
          new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
      LocalDate date = LocalDate.of(2024, 3, 15);
      JdbcTypeMapper.bind(ps, 1, date, dateType);

      verify(ps).setDate(1, Date.valueOf(date));
    }

    @Test
    void shouldBindInstantValueForTimestampType() throws SQLException {
      PrimitiveType tsType = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, null, null);
      Instant instant = Instant.parse("2024-06-15T10:30:00Z");
      JdbcTypeMapper.bind(ps, 1, instant, tsType);

      verify(ps).setTimestamp(1, Timestamp.from(instant));
    }

    // --- String coercion (the core Option B improvement) ---

    @Test
    void shouldCoerceStringToIntForIntType() throws SQLException {
      // Simulates a Datafaker numeric type (e.g. age) that generates a String
      PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "0", "120");
      JdbcTypeMapper.bind(ps, 1, "42", intType);

      verify(ps).setInt(1, 42);
    }

    @Test
    void shouldCoerceLongToIntForIntType() throws SQLException {
      PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");
      JdbcTypeMapper.bind(ps, 1, 99L, intType);

      verify(ps).setInt(1, 99);
    }

    @Test
    void shouldCoerceStringToDecimalForDecimalType() throws SQLException {
      PrimitiveType decType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
      JdbcTypeMapper.bind(ps, 1, "19.99", decType);

      verify(ps).setBigDecimal(1, new BigDecimal("19.99"));
    }

    @Test
    void shouldCoerceStringToBooleanForBooleanType() throws SQLException {
      PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
      JdbcTypeMapper.bind(ps, 1, "true", boolType);

      verify(ps).setBoolean(1, true);
    }

    @Test
    void shouldCoerceStringToDateForDateType() throws SQLException {
      PrimitiveType dateType =
          new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
      JdbcTypeMapper.bind(ps, 1, "2024-06-15", dateType);

      verify(ps).setDate(1, Date.valueOf(LocalDate.of(2024, 6, 15)));
    }

    @Test
    void shouldCoerceStringToTimestampForTimestampType() throws SQLException {
      PrimitiveType tsType = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, null, null);
      JdbcTypeMapper.bind(ps, 1, "2024-06-15T10:30:00Z", tsType);

      verify(ps).setTimestamp(1, Timestamp.from(Instant.parse("2024-06-15T10:30:00Z")));
    }

    // --- Enum and Datafaker type binding ---

    @Test
    void shouldBindEnumValueAsString() throws SQLException {
      EnumType enumType = new EnumType(List.of("M", "F", "X"));
      JdbcTypeMapper.bind(ps, 1, "M", enumType);

      verify(ps).setString(1, "M");
    }

    @Test
    void shouldBindDatafakerValueAsString() throws SQLException {
      CustomDatafakerType datafakerType = new CustomDatafakerType("first_name");
      JdbcTypeMapper.bind(ps, 1, "Alice", datafakerType);

      verify(ps).setString(1, "Alice");
    }

    @Test
    void shouldPropagateSQLExceptionForTypedBind() throws SQLException {
      PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");
      doThrow(new SQLException("DB error")).when(ps).setInt(anyInt(), anyInt());

      assertThatThrownBy(() -> JdbcTypeMapper.bind(ps, 1, 42, intType))
          .isInstanceOf(SQLException.class);
    }
  }
}
