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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Defaults;
import com.datagenerator.inspector.MappedType;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProtobufTypeMapperTest {

  private static Descriptor descriptor;
  private static final ProtobufTypeMapper mapper = new ProtobufTypeMapper();

  @BeforeAll
  static void buildDescriptor() throws DescriptorValidationException {
    EnumDescriptorProto status =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("CLOSED").setNumber(1))
            .build();

    DescriptorProto message =
        DescriptorProto.newBuilder()
            .setName("TestMsg")
            .addField(fieldOf("int_field", 1, Type.TYPE_INT32, Label.LABEL_OPTIONAL))
            .addField(fieldOf("long_field", 2, Type.TYPE_INT64, Label.LABEL_OPTIONAL))
            .addField(fieldOf("float_field", 3, Type.TYPE_FLOAT, Label.LABEL_OPTIONAL))
            .addField(fieldOf("double_field", 4, Type.TYPE_DOUBLE, Label.LABEL_OPTIONAL))
            .addField(fieldOf("bool_field", 5, Type.TYPE_BOOL, Label.LABEL_OPTIONAL))
            .addField(fieldOf("string_field", 6, Type.TYPE_STRING, Label.LABEL_OPTIONAL))
            .addField(fieldOf("bytes_field", 7, Type.TYPE_BYTES, Label.LABEL_OPTIONAL))
            .addField(fieldOf("email", 8, Type.TYPE_STRING, Label.LABEL_OPTIONAL))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("status")
                    .setNumber(9)
                    .setType(Type.TYPE_ENUM)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setTypeName(".pkg.Status")
                    .build())
            .addField(fieldOf("repeated_field", 10, Type.TYPE_INT32, Label.LABEL_REPEATED))
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("pkg")
            .setSyntax("proto3")
            .addEnumType(status)
            .addMessageType(message)
            .build();

    FileDescriptor fd = FileDescriptor.buildFrom(fdp, new FileDescriptor[0]);
    descriptor = fd.getMessageTypes().get(0);
  }

  private static FieldDescriptorProto fieldOf(String name, int num, Type type, Label label) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(num)
        .setType(type)
        .setLabel(label)
        .build();
  }

  @Test
  void shouldMapInt32ToIntRange() {
    FieldDescriptor f = descriptor.findFieldByName("int_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .isEqualTo("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapInt64ToIntRange() {
    FieldDescriptor f = descriptor.findFieldByName("long_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .isEqualTo("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapFloatToDecimalRange() {
    FieldDescriptor f = descriptor.findFieldByName("float_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .isEqualTo("decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapDoubleToDecimalRange() {
    FieldDescriptor f = descriptor.findFieldByName("double_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .isEqualTo("decimal[" + Defaults.DECIMAL_MIN + ".." + Defaults.DECIMAL_MAX + "]");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapBooleanToDeclared() {
    FieldDescriptor f = descriptor.findFieldByName("bool_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype()).isEqualTo("boolean");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DECLARED);
    assertThat(result.flagged()).isFalse();
  }

  @Test
  void shouldMapGenericStringToDefaultRange() {
    FieldDescriptor f = descriptor.findFieldByName("string_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype()).isEqualTo(Defaults.STRING);
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapEmailStringToNameHint() {
    FieldDescriptor f = descriptor.findFieldByName("email");
    MappedType result = mapper.map(f);
    assertThat(result.reason()).isEqualTo(MappedType.Reason.NAME_HINT);
    assertThat(result.flagged()).isTrue();
    assertThat(result.comment()).contains("guessed from column name");
  }

  @Test
  void shouldMapBytesToUnknownType() {
    FieldDescriptor f = descriptor.findFieldByName("bytes_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype()).isEqualTo(Defaults.STRING);
    assertThat(result.reason()).isEqualTo(MappedType.Reason.UNKNOWN_TYPE);
    assertThat(result.flagged()).isTrue();
  }

  @Test
  void shouldMapEnumToDeclaredEnum() {
    FieldDescriptor f = descriptor.findFieldByName("status");
    MappedType result = mapper.map(f);
    assertThat(result.datatype()).isEqualTo("enum[ACTIVE,CLOSED]");
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DECLARED);
    assertThat(result.flagged()).isFalse();
  }

  @Test
  void shouldMapRepeatedFieldToArray() {
    FieldDescriptor f = descriptor.findFieldByName("repeated_field");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .startsWith("array[")
        .contains(String.valueOf(Defaults.ARRAY_MIN) + ".." + Defaults.ARRAY_MAX);
    assertThat(result.reason()).isEqualTo(MappedType.Reason.DEFAULT_RANGE);
  }

  @ParameterizedTest
  @EnumSource(
      value = Type.class,
      names = {
        "TYPE_INT32",
        "TYPE_INT64",
        "TYPE_SINT32",
        "TYPE_SINT64",
        "TYPE_FIXED32",
        "TYPE_FIXED64",
        "TYPE_SFIXED32",
        "TYPE_SFIXED64",
        "TYPE_UINT32",
        "TYPE_UINT64"
      })
  void shouldMapAllIntegerTypesToIntRange(Type protoType) throws DescriptorValidationException {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("M")
            .addField(fieldOf("f", 1, protoType, Label.LABEL_OPTIONAL))
            .build();
    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("x.proto")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();
    FileDescriptor fd = FileDescriptor.buildFrom(fdp, new FileDescriptor[0]);
    FieldDescriptor f = fd.getMessageTypes().get(0).findFieldByName("f");
    MappedType result = mapper.map(f);
    assertThat(result.datatype())
        .isEqualTo("int[" + Defaults.INT_MIN + ".." + Defaults.INT_MAX + "]");
  }
}
