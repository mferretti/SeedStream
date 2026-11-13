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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.MaxLengthType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.Test;

/** A {@code max_length}-capped text field still binds as a plain VARCHAR / string (#440). */
class JdbcTypeMapperMaxLengthTest {
  private final MaxLengthType capped =
      new MaxLengthType(new CustomDatafakerType("city"), 15, false);

  @Test
  void shouldBindCappedTextFieldAsString() throws SQLException {
    PreparedStatement ps = mock(PreparedStatement.class);

    JdbcTypeMapper.bind(ps, 1, "Roma", capped);

    verify(ps).setString(1, "Roma");
  }

  @Test
  void shouldBindNullCappedTextFieldAsVarchar() throws SQLException {
    PreparedStatement ps = mock(PreparedStatement.class);

    JdbcTypeMapper.bind(ps, 1, null, capped);

    verify(ps).setNull(1, Types.VARCHAR);
  }
}
