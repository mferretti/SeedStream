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

package com.datagenerator.cli;

import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.NullableType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.core.type.SelfReferenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The value class each {@code nullable} field of a structure holds when it is not null, so binary
 * serializers (Avro, Protobuf) can build their schema from the structure instead of from the first
 * record (whose value for such a field may be null).
 */
final class NullableFieldTypes {
  private NullableFieldTypes() {}

  /** Declared class per nullable field of {@code fields}; types of unknown shape are left out. */
  static Map<String, Class<?>> of(Map<String, DataType> fields) {
    Map<String, Class<?>> out = new LinkedHashMap<>();
    fields.forEach(
        (name, type) -> {
          if (type instanceof NullableType n) {
            Class<?> valueClass = valueClass(n.getInner());
            if (valueClass != null) {
              out.put(name, valueClass);
            }
          }
        });
    return out;
  }

  private static Class<?> valueClass(DataType type) {
    return switch (type) {
      case PrimitiveType p ->
          switch (p.getKind()) {
            case INT -> Long.class;
            case DECIMAL -> BigDecimal.class;
            case BOOLEAN -> Boolean.class;
            case CHAR -> String.class;
            case DATE -> LocalDate.class;
            case TIMESTAMP -> Instant.class;
          };
      case EnumType e -> String.class;
      case ReferenceType r -> Long.class;
      case SelfReferenceType s -> Long.class;
      case ArrayType a -> List.class;
      case ObjectType o -> Map.class;
      case null, default -> null;
    };
  }
}
