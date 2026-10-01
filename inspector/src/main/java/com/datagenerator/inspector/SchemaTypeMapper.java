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

package com.datagenerator.inspector;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Maps a single JSON Schema property node to a SeedStream datatype string. Keys only on plain JSON
 * Schema vocabulary ({@code $ref}, {@code type}, {@code format}, {@code enum}, {@code
 * minimum}/{@code maximum}, {@code maxLength}, {@code minItems}/{@code maxItems}, {@code items}),
 * so it is shared by both the OpenAPI and standalone JSON Schema inspectors. Resolution order
 * matches {@code docs/INSPECT-V1-SPEC.md} §3: {@code $ref} → format → enum → bounded numeric → name
 * hint → type default.
 */
public final class SchemaTypeMapper {

  private static final String MINIMUM = "minimum";
  private static final String MAXIMUM = "maximum";
  private static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
  private static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";
  private static final String MIN_ITEMS = "minItems";
  private static final String MAX_ITEMS = "maxItems";
  private static final String ENUM = "enum";

  public MappedType map(String fieldName, JsonNode schema) {
    if (schema.hasNonNull("$ref")) {
      return MappedType.declared("object[" + refName(schema.get("$ref").asText()) + "]");
    }

    String type = typeOf(schema);
    return switch (type) {
      case "string" -> mapString(fieldName, schema);
      case "integer" -> mapInteger(schema);
      case "number" -> mapNumber(schema);
      case "boolean" -> MappedType.declared("boolean");
      case "array" -> mapArray(fieldName, schema);
      default -> MappedType.unknownType(Defaults.STRING); // unknown / missing type — see §6 Q2
    };
  }

  /**
   * The schema's type. OpenAPI 3.1 / JSON Schema express nullability as {@code type: [T, "null"]};
   * nullability needs no mapping (§8), so the single non-null member is the type. Anything else
   * that is not a plain string (several non-null types, missing) is unknown.
   */
  private static String typeOf(JsonNode schema) {
    JsonNode type = schema.path("type");
    if (type.isArray()) {
      List<String> nonNull = new ArrayList<>();
      type.forEach(
          t -> {
            if (!"null".equals(t.asText())) {
              nonNull.add(t.asText());
            }
          });
      return nonNull.size() == 1 ? nonNull.get(0) : "";
    }
    return type.asText("");
  }

  private MappedType mapString(String fieldName, JsonNode schema) {
    String format = schema.path("format").asText("");
    switch (format) {
      case "email":
        return fakerOr("email", () -> MappedType.declared("char[1..50]"));
      case "uuid":
        return fakerOr("uuid", () -> MappedType.declared("char[36..36]"));
      case "date":
        return MappedType.declared(Defaults.DATE);
      case "date-time":
        return MappedType.declared(Defaults.TIMESTAMP);
      default:
        // fall through to enum / length / name-hint handling
    }

    if (schema.has(ENUM)) {
      return MappedType.declared(enumType(schema.get(ENUM)));
    }
    if (schema.has("maxLength")) {
      return MappedType.declared("char[1.." + schema.get("maxLength").asInt() + "]");
    }
    return NameHints.forFieldName(fieldName)
        .flatMap(FakerTypes::canonical)
        .or(() -> FakerTypes.canonical(Names.toSnakeCase(fieldName)))
        .map(MappedType::nameHint)
        .orElseGet(() -> MappedType.defaultRange(Defaults.STRING));
  }

  /** Emits the datafaker key (a name guess) if registered, otherwise the declared fallback. */
  private MappedType fakerOr(String key, Supplier<MappedType> fallback) {
    return FakerTypes.canonical(key).map(MappedType::declared).orElseGet(fallback);
  }

  private MappedType mapInteger(JsonNode schema) {
    if (schema.has(ENUM)) {
      return MappedType.declared(enumType(schema.get(ENUM)));
    }
    BigDecimal[] range =
        range(
            schema,
            BigDecimal.valueOf(Defaults.INT_MIN),
            BigDecimal.valueOf(Defaults.INT_MAX),
            BigDecimal.ONE);
    String datatype = "int[" + range[0].toBigInteger() + ".." + range[1].toBigInteger() + "]";
    return isBounded(schema) ? MappedType.declared(datatype) : MappedType.defaultRange(datatype);
  }

  private MappedType mapNumber(JsonNode schema) {
    if (schema.has(ENUM)) {
      return MappedType.declared(enumType(schema.get(ENUM)));
    }
    if (!isBounded(schema)) {
      return MappedType.defaultRange(
          "decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
    }
    BigDecimal[] range =
        range(
            schema,
            new BigDecimal(Defaults.DECIMAL_MIN),
            new BigDecimal(Defaults.DECIMAL_MAX),
            null);
    // A side left at its default keeps the default's literal (e.g. "0.0"), as before.
    boolean minDefaulted = bound(schema, MINIMUM, EXCLUSIVE_MINIMUM, null, true) == null;
    boolean maxDefaulted = bound(schema, MAXIMUM, EXCLUSIVE_MAXIMUM, null, false) == null;
    String min =
        minDefaulted && range[0].compareTo(new BigDecimal(Defaults.DECIMAL_MIN)) == 0
            ? Defaults.DECIMAL_MIN
            : plain(range[0]);
    String max =
        maxDefaulted && range[1].compareTo(new BigDecimal(Defaults.DECIMAL_MAX)) == 0
            ? Defaults.DECIMAL_MAX
            : plain(range[1]);
    return MappedType.declared("decimal[" + min + ".." + max + "]");
  }

  private static boolean isBounded(JsonNode schema) {
    return schema.has(MINIMUM)
        || schema.has(MAXIMUM)
        || schema.path(EXCLUSIVE_MINIMUM).isNumber()
        || schema.path(EXCLUSIVE_MAXIMUM).isNumber();
  }

  /**
   * Resolves [min, max] from {@code minimum}/{@code maximum} and their exclusive forms (numeric, as
   * in OpenAPI 3.1 / JSON Schema, or boolean modifiers as in OpenAPI 3.0). An exclusive bound moves
   * inward by {@code step} (1 for integers; for decimals one unit at the bound's scale, at least
   * 0.01, since generated decimals carry the bounds' scale). A missing side takes the default, but
   * never so as to invert the range: if the declared side lies beyond the opposite default, the
   * missing side keeps the default span from it instead.
   */
  private static BigDecimal[] range(
      JsonNode schema, BigDecimal defaultMin, BigDecimal defaultMax, BigDecimal integerStep) {
    BigDecimal min = bound(schema, MINIMUM, EXCLUSIVE_MINIMUM, integerStep, true);
    BigDecimal max = bound(schema, MAXIMUM, EXCLUSIVE_MAXIMUM, integerStep, false);
    BigDecimal span = defaultMax.subtract(defaultMin);
    if (min == null && max == null) {
      return new BigDecimal[] {defaultMin, defaultMax};
    }
    if (min == null) {
      min = max.compareTo(defaultMin) >= 0 ? defaultMin : max.subtract(span);
    }
    if (max == null) {
      max = min.compareTo(defaultMax) <= 0 ? defaultMax : min.add(span);
    }
    return new BigDecimal[] {min, max};
  }

  private static BigDecimal bound(
      JsonNode schema, String inclusive, String exclusive, BigDecimal integerStep, boolean lower) {
    JsonNode exclusiveNode = schema.path(exclusive);
    BigDecimal value;
    boolean isExclusive;
    if (exclusiveNode.isNumber()) {
      value = exclusiveNode.decimalValue();
      isExclusive = true;
    } else if (schema.path(inclusive).isNumber()) {
      value = schema.get(inclusive).decimalValue();
      isExclusive = exclusiveNode.asBoolean(false); // OpenAPI 3.0 boolean modifier
    } else {
      return null;
    }
    if (!isExclusive) {
      return value;
    }
    BigDecimal step =
        integerStep != null
            ? integerStep
            : BigDecimal.ONE.movePointLeft(Math.max(2, value.stripTrailingZeros().scale()));
    return lower ? value.add(step) : value.subtract(step);
  }

  /** Plain decimal notation: the decimal[...] syntax has no exponent form (1.0E-7). */
  private static String plain(BigDecimal value) {
    String text = value.stripTrailingZeros().toPlainString();
    return "-0".equals(text) ? "0" : text;
  }

  private MappedType mapArray(String fieldName, JsonNode schema) {
    JsonNode items = schema.path("items");
    MappedType inner =
        items.isMissingNode() ? MappedType.defaultRange(Defaults.STRING) : map(fieldName, items);
    boolean bounded = schema.has(MIN_ITEMS) || schema.has(MAX_ITEMS);
    int span = Defaults.ARRAY_MAX - Defaults.ARRAY_MIN;
    Integer declaredMin = schema.has(MIN_ITEMS) ? schema.get(MIN_ITEMS).asInt() : null;
    Integer declaredMax = schema.has(MAX_ITEMS) ? schema.get(MAX_ITEMS).asInt() : null;
    // A missing side takes its default unless that would invert the range.
    int min;
    if (declaredMin != null) {
      min = declaredMin;
    } else if (declaredMax != null && declaredMax < Defaults.ARRAY_MIN) {
      min = Math.max(0, declaredMax);
    } else {
      min = Defaults.ARRAY_MIN;
    }
    int max;
    if (declaredMax != null) {
      max = declaredMax;
    } else {
      max = min <= Defaults.ARRAY_MAX ? Defaults.ARRAY_MAX : min + span;
    }
    String datatype = "array[" + inner.datatype() + ", " + min + ".." + max + "]";
    return new MappedType(datatype, !bounded ? MappedType.Reason.DEFAULT_RANGE : inner.reason());
  }

  private String enumType(JsonNode enumNode) {
    StringJoiner values = new StringJoiner(",", "enum[", "]");
    enumNode.forEach(v -> values.add(v.asText()));
    return values.toString();
  }

  /**
   * Extracts and snake_cases the schema name from a {@code $ref}. A pointer ({@code
   * #/components/schemas/Foo}, {@code #/$defs/Foo}, {@code common.yaml#/.../Foo}) uses its last
   * segment; a bare file reference ({@code Address.yaml}, {@code ./schemas/address.json}) uses the
   * file's base name without extension, so the result is always a legal structure name.
   */
  private String refName(String ref) {
    int hash = ref.indexOf('#');
    String target = hash >= 0 && hash < ref.length() - 1 ? ref.substring(hash + 1) : ref;
    String last = target.substring(target.lastIndexOf('/') + 1);
    if (hash < 0) {
      int dot = last.lastIndexOf('.');
      if (dot > 0) {
        last = last.substring(0, dot);
      }
    }
    return Names.toSnakeCase(last);
  }
}
