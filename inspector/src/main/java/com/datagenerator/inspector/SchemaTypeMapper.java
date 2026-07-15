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
  private static final String MIN_ITEMS = "minItems";
  private static final String MAX_ITEMS = "maxItems";

  public MappedType map(String fieldName, JsonNode schema) {
    if (schema.hasNonNull("$ref")) {
      return MappedType.declared("object[" + refName(schema.get("$ref").asText()) + "]");
    }

    String type = schema.path("type").asText("");
    return switch (type) {
      case "string" -> mapString(fieldName, schema);
      case "integer" -> mapInteger(schema);
      case "number" -> mapNumber(schema);
      case "boolean" -> MappedType.declared("boolean");
      case "array" -> mapArray(fieldName, schema);
      default -> MappedType.unknownType(Defaults.STRING); // unknown / missing type — see §6 Q2
    };
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

    if (schema.has("enum")) {
      return MappedType.declared(enumType(schema.get("enum")));
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
    boolean bounded = schema.has(MINIMUM) || schema.has(MAXIMUM);
    long min = schema.has(MINIMUM) ? schema.get(MINIMUM).asLong() : Defaults.INT_MIN;
    long max = schema.has(MAXIMUM) ? schema.get(MAXIMUM).asLong() : Defaults.INT_MAX;
    String datatype = "int[" + min + ".." + max + "]";
    return bounded ? MappedType.declared(datatype) : MappedType.defaultRange(datatype);
  }

  private MappedType mapNumber(JsonNode schema) {
    boolean bounded = schema.has(MINIMUM) || schema.has(MAXIMUM);
    String min = schema.has(MINIMUM) ? schema.get(MINIMUM).asText() : Defaults.DECIMAL_MIN;
    String max = schema.has(MAXIMUM) ? schema.get(MAXIMUM).asText() : Defaults.DECIMAL_MAX;
    String datatype = "decimal[" + min + ".." + max + "]";
    return bounded ? MappedType.declared(datatype) : MappedType.defaultRange(datatype);
  }

  private MappedType mapArray(String fieldName, JsonNode schema) {
    JsonNode items = schema.path("items");
    MappedType inner =
        items.isMissingNode() ? MappedType.defaultRange(Defaults.STRING) : map(fieldName, items);
    boolean bounded = schema.has(MIN_ITEMS) || schema.has(MAX_ITEMS);
    int min = schema.has(MIN_ITEMS) ? schema.get(MIN_ITEMS).asInt() : Defaults.ARRAY_MIN;
    int max = schema.has(MAX_ITEMS) ? schema.get(MAX_ITEMS).asInt() : Defaults.ARRAY_MAX;
    String datatype = "array[" + inner.datatype() + ", " + min + ".." + max + "]";
    return new MappedType(datatype, !bounded ? MappedType.Reason.DEFAULT_RANGE : inner.reason());
  }

  private String enumType(JsonNode enumNode) {
    StringJoiner values = new StringJoiner(",", "enum[", "]");
    enumNode.forEach(v -> values.add(v.asText()));
    return values.toString();
  }

  /**
   * Extracts and snake_cases the schema name from a {@code $ref} pointer. Takes the last path
   * segment, so it resolves OpenAPI {@code #/components/schemas/Foo} and JSON Schema {@code
   * #/$defs/Foo} / {@code #/definitions/Foo} pointers alike.
   */
  private String refName(String ref) {
    String last = ref.substring(ref.lastIndexOf('/') + 1);
    return Names.toSnakeCase(last);
  }
}
