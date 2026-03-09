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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
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
  void shouldBindUnknownObjectAsStringViaToString() throws SQLException {
    // Enum values and Datafaker types fall through to setString
    JdbcTypeMapper.bind(ps, 1, "ACTIVE");

    verify(ps).setString(1, "ACTIVE");
  }

  @Test
  void shouldPropagateSQLException() throws SQLException {
    doThrow(new SQLException("DB error")).when(ps).setInt(anyInt(), anyInt());

    assertThatThrownBy(() -> JdbcTypeMapper.bind(ps, 1, 42)).isInstanceOf(SQLException.class);
  }
}
