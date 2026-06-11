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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps a SQL column type to a SeedStream datatype string (foreign keys are resolved separately by
 * {@link DdlInspector}). Mapping is mechanical — SQL carries no value semantics, so string columns
 * lean on the shared {@link NameHints}. See {@code docs/INSPECT-V1-SPEC.md} §8 / INSPECT.md DDL
 * table.
 */
public final class DdlTypeMapper {

  /**
   * Maps a column.
   *
   * @param columnName used for name-hint inference on string types
   * @param sqlType the SQL type name (e.g. {@code VARCHAR}, {@code BIGINT})
   * @param args type arguments (e.g. {@code [255]} for {@code VARCHAR(255)})
   */
  public MappedType map(String columnName, String sqlType, List<String> args) {
    String type = sqlType == null ? "" : sqlType.toUpperCase(Locale.ROOT);
    return switch (type) {
      case "BOOLEAN", "BOOL" -> MappedType.declared("boolean");
      case "DATE" -> MappedType.declared(Defaults.DATE);
      case "TIMESTAMP", "DATETIME" -> MappedType.declared(Defaults.TIMESTAMP);
      case "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT" ->
          MappedType.defaultRange("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
      case "DECIMAL", "NUMERIC", "NUMBER", "REAL", "FLOAT", "DOUBLE" ->
          MappedType.defaultRange(
              "decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
      case "VARCHAR", "CHAR", "NVARCHAR", "NCHAR", "CHARACTER" -> {
        boolean hasLength = args != null && !args.isEmpty();
        yield mapString(columnName, length(args, Defaults.VARCHAR_DEFAULT_LENGTH), !hasLength);
      }
      case "TEXT", "CLOB", "NCLOB" -> mapString(columnName, Defaults.TEXT_MAX_LENGTH, true);
      default -> MappedType.unknownType(Defaults.STRING); // unknown — §6 Q2
    };
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
      return Integer.parseInt(args.get(0).trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
