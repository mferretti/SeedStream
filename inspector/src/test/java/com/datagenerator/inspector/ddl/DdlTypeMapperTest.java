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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.MappedType.Reason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DdlTypeMapperTest {

  private DdlTypeMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new DdlTypeMapper();
  }

  // --- boolean ---

  @Test
  void booleanMapsToBoolean() {
    MappedType mt = mapper.map("active", "BOOLEAN", List.of());
    assertThat(mt.datatype()).isEqualTo("boolean");
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void boolShorthandMapsToBoolean() {
    MappedType mt = mapper.map("enabled", "BOOL", List.of());
    assertThat(mt.datatype()).isEqualTo("boolean");
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  // --- date / timestamp ---

  @Test
  void dateMapsToDefaultDate() {
    MappedType mt = mapper.map("birth_date", "DATE", List.of());
    assertThat(mt.datatype()).isEqualTo(Defaults.DATE);
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "TIMESTAMP",
        "DATETIME",
        "TIMESTAMP WITH TIME ZONE",
        "TIMESTAMP WITHOUT TIME ZONE",
        "TIMESTAMPTZ"
      })
  void sqlTypeMapsToTimestamp(String sqlType) {
    MappedType mt = mapper.map("created_at", sqlType, List.of());
    assertThat(mt.datatype()).isEqualTo(Defaults.TIMESTAMP);
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  // --- integer types ---

  @Test
  void intMapsToDefaultRange() {
    MappedType mt = mapper.map("quantity", "INT", List.of());
    assertThat(mt.datatype()).isEqualTo("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void integerMapsToDefaultRange() {
    MappedType mt = mapper.map("count", "INTEGER", List.of());
    assertThat(mt.datatype()).isEqualTo("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void bigintMapsToDefaultRange() {
    MappedType mt = mapper.map("id", "BIGINT", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
    assertThat(mt.datatype()).startsWith("int[");
  }

  @Test
  void smallintMapsToDefaultRange() {
    MappedType mt = mapper.map("code", "SMALLINT", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void tinyintMapsToDefaultRange() {
    MappedType mt = mapper.map("flag", "TINYINT", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  // --- decimal types ---

  @Test
  void decimalMapsToDefaultDecimalRange() {
    MappedType mt = mapper.map("amount", "DECIMAL", List.of());
    assertThat(mt.datatype())
        .isEqualTo("decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void numericMapsToDefaultDecimalRange() {
    MappedType mt = mapper.map("price", "NUMERIC", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
    assertThat(mt.datatype()).startsWith("decimal[");
  }

  @Test
  void floatMapsToDefaultDecimalRange() {
    MappedType mt = mapper.map("score", "FLOAT", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void doubleMapsToDefaultDecimalRange() {
    MappedType mt = mapper.map("ratio", "DOUBLE", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  // --- VARCHAR with explicit length ---

  @Test
  void varcharWithExplicitLengthIsDeclared() {
    MappedType mt = mapper.map("generic_col", "VARCHAR", List.of("100"));
    // no name-hint match for "generic_col" → char type, DECLARED because length was explicit
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mt.datatype()).isEqualTo("char[1..100]");
  }

  @Test
  void varcharWithNoLengthIsDefaultRange() {
    MappedType mt = mapper.map("generic_col", "VARCHAR", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
    assertThat(mt.datatype()).isEqualTo("char[1.." + Defaults.VARCHAR_DEFAULT_LENGTH + "]");
  }

  @Test
  void charWithExplicitLengthIsDeclared() {
    MappedType mt = mapper.map("code_col", "CHAR", List.of("10"));
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mt.datatype()).isEqualTo("char[1..10]");
  }

  // --- VARCHAR with name-hint for column name "email" → NAME_HINT ---

  @Test
  void varcharWithEmailColumnNameIsNameHint() {
    MappedType mt = mapper.map("email", "VARCHAR", List.of("255"));
    assertThat(mt.reason()).isEqualTo(Reason.NAME_HINT);
    assertThat(mt.flagged()).isTrue();
  }

  // --- TEXT / CLOB ---

  @ParameterizedTest
  @ValueSource(strings = {"TEXT", "CLOB", "LONGTEXT"})
  void sqlTypeMapsToTextChar(String sqlType) {
    MappedType mt = mapper.map("col", sqlType, List.of());
    assertThat(mt.datatype()).isEqualTo("char[1.." + Defaults.TEXT_MAX_LENGTH + "]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  // --- multi-word ANSI types (§7c / §8 follow-up) ---

  @Test
  void characterVaryingMapsLikeVarchar() {
    MappedType mt = mapper.map("generic_col", "CHARACTER VARYING", List.of("80"));
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mt.datatype()).isEqualTo("char[1..80]");
  }

  @Test
  void characterVaryingIsWhitespaceInsensitive() {
    MappedType mt = mapper.map("generic_col", "CHARACTER   VARYING", List.of());
    assertThat(mt.datatype()).isEqualTo("char[1.." + Defaults.VARCHAR_DEFAULT_LENGTH + "]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void doublePrecisionMapsToDecimal() {
    MappedType mt = mapper.map("ratio", "DOUBLE PRECISION", List.of());
    assertThat(mt.datatype()).startsWith("decimal[");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  // --- vendor aliases (§7c / §8 follow-up) ---

  @Test
  void serialMapsToIntDefaultRange() {
    MappedType mt = mapper.map("id", "SERIAL", List.of());
    assertThat(mt.datatype()).startsWith("int[");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void bigserialMapsToIntDefaultRange() {
    assertThat(mapper.map("id", "BIGSERIAL", List.of()).datatype()).startsWith("int[");
  }

  @Test
  void int8MapsToInt() {
    assertThat(mapper.map("n", "INT8", List.of()).datatype()).startsWith("int[");
  }

  @Test
  void moneyMapsToDecimal() {
    assertThat(mapper.map("price", "MONEY", List.of()).datatype()).startsWith("decimal[");
  }

  @Test
  void varchar2MapsLikeVarchar() {
    MappedType mt = mapper.map("code_col", "VARCHAR2", List.of("12"));
    assertThat(mt.datatype()).isEqualTo("char[1..12]");
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void nativeUuidTypeMapsToUuidKeyOrCharFallback() {
    MappedType mt = mapper.map("ext_id", "UUID", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mt.datatype()).isIn("uuid", "char[36..36]");
  }

  @Test
  void lowercaseTypeNameStillResolves() {
    assertThat(mapper.map("active", "boolean", List.of()).datatype()).isEqualTo("boolean");
  }

  // --- unknown type fallback ---

  @Test
  void unknownSqlTypeReturnsUnknownTypeMapped() {
    MappedType mt = mapper.map("whatever", "GEOMETRY", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
    assertThat(mt.datatype()).isEqualTo(Defaults.STRING);
    assertThat(mt.flagged()).isTrue();
  }

  @Test
  void nullSqlTypeFallsBackToUnknown() {
    MappedType mt = mapper.map("col", null, List.of());
    assertThat(mt.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
  }

  @Test
  void emptySqlTypeFallsBackToUnknown() {
    MappedType mt = mapper.map("col", "", List.of());
    assertThat(mt.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
  }

  // ── #377: column capacity, BIT, DATETIME2 ───────────────────────────────────

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "DECIMAL|4,2|decimal[0.00..99.99]",
        "NUMERIC|5,2|decimal[0.00..999.99]",
        "NUMBER|1|decimal[0..9]",
        "DEC|3,0|decimal[0..999]",
        "DECIMAL|2,2|decimal[0.00..0.99]",
        "decimal|4,1|decimal[0.0..999.9]",
        "DECIMAL|6,1|decimal[0.0..9999.99]",
        "DECIMAL|10,2|decimal[0.0..9999.99]",
        "NUMERIC|38,10|decimal[0.0..9999.99]",
        "DECIMAL||decimal[0.0..9999.99]",
        "FLOAT|2|decimal[0.0..9999.99]",
        "REAL||decimal[0.0..9999.99]",
        "MONEY||decimal[0.0..9999.99]"
      })
  void shouldCapDecimalRangeToFixedPointCapacity(String type, String args, String expected) {
    List<String> argList = args == null ? List.of() : List.of(args.split(","));

    assertThat(mapper.map("amount", type, argList).datatype()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "SMALLINT, int[1..32767]",
    "INT2, int[1..32767]",
    "SMALLSERIAL, int[1..32767]",
    "TINYINT, int[1..127]",
    "tinyint, int[1..127]",
    "MEDIUMINT, int[1..999999]",
    "INT, int[1..999999]",
    "BIGINT, int[1..999999]"
  })
  void shouldCapIntegerRangeToColumnWidth(String type, String expected) {
    assertThat(mapper.map("n", type, List.of()).datatype()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "1"})
  void shouldMapBitWithoutLengthOrLengthOneToBoolean(String length) {
    List<String> args = length.isEmpty() ? List.of() : List.of(length);

    MappedType mt = mapper.map("flag", "BIT", args);
    assertThat(mt.datatype()).isEqualTo("boolean");
    assertThat(mt.flagged()).isFalse();
  }

  @Test
  void shouldKeepMultiBitStringAsFlaggedUnknownType() {
    MappedType mt = mapper.map("bits", "BIT", List.of("8"));

    assertThat(mt.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DATETIME2", "datetime2", "DATETIMEOFFSET"})
  void shouldMapSqlServerHighPrecisionDateTimesToTimestamp(String type) {
    assertThat(mapper.map("at", type, List.of("7")).datatype()).isEqualTo(Defaults.TIMESTAMP);
  }
}
