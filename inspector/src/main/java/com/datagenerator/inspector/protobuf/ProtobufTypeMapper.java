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

package com.datagenerator.inspector.protobuf;

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.FakerTypes;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.NameHints;
import com.datagenerator.inspector.Names;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Maps a single protobuf {@link FieldDescriptor} to a SeedStream datatype string. Mirrors the
 * resolution logic of {@code SchemaTypeMapper} adapted for the protobuf type system.
 */
public final class ProtobufTypeMapper {

  private static final Set<String> UNKNOWN_MESSAGE_TYPES =
      Set.of(
          "google.protobuf.Any",
          "google.protobuf.Struct",
          "google.protobuf.Value",
          "google.protobuf.ListValue",
          "google.protobuf.Duration");

  /**
   * Maps a field descriptor (handling repeated/map/singular) to a SeedStream {@link MappedType}.
   */
  public MappedType map(FieldDescriptor f) {
    if (f.isMapField()) {
      return MappedType.unknownType(Defaults.STRING);
    }
    if (f.isRepeated()) {
      MappedType inner = mapSingular(f);
      return new MappedType(
          "array[" + inner.datatype() + ", " + Defaults.ARRAY_MIN + ".." + Defaults.ARRAY_MAX + "]",
          MappedType.Reason.DEFAULT_RANGE);
    }
    return mapSingular(f);
  }

  /** Maps a non-repeated field by its Java type. */
  MappedType mapSingular(FieldDescriptor f) {
    return switch (f.getJavaType()) {
      case INT, LONG ->
          MappedType.defaultRange("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
      case FLOAT, DOUBLE ->
          MappedType.defaultRange(
              "decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
      case BOOLEAN -> MappedType.declared("boolean");
      case STRING -> mapString(f);
      case ENUM -> mapEnum(f);
      case BYTE_STRING -> MappedType.unknownType(Defaults.STRING);
      case MESSAGE -> mapMessage(f);
    };
  }

  private MappedType mapString(FieldDescriptor f) {
    return NameHints.forFieldName(f.getName())
        .flatMap(FakerTypes::canonical)
        .or(() -> FakerTypes.canonical(Names.toSnakeCase(f.getName())))
        .map(MappedType::nameHint)
        .orElseGet(() -> MappedType.defaultRange(Defaults.STRING));
  }

  private MappedType mapEnum(FieldDescriptor f) {
    var values = f.getEnumType().getValues();
    if (values.isEmpty()) {
      return MappedType.unknownType(Defaults.STRING);
    }
    StringJoiner joiner = new StringJoiner(",", "enum[", "]");
    values.forEach(v -> joiner.add(v.getName()));
    return MappedType.declared(joiner.toString());
  }

  private MappedType mapMessage(FieldDescriptor f) {
    String fn = f.getMessageType().getFullName();
    if ("google.protobuf.Timestamp".equals(fn)) {
      return MappedType.declared(Defaults.TIMESTAMP);
    }
    if (UNKNOWN_MESSAGE_TYPES.contains(fn)) {
      return MappedType.unknownType(Defaults.STRING);
    }
    return MappedType.declared("object[" + Names.toSnakeCase(f.getMessageType().getName()) + "]");
  }
}
