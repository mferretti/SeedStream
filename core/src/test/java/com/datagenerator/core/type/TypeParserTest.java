package com.datagenerator.core.type;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.exception.TypeParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypeParserTest {
  private TypeParser parser;

  @BeforeEach
  void setUp() {
    parser = new TypeParser();
  }

  @Test
  void shouldParseCharType() {
    DataType type = parser.parse("char[3..15]");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.CHAR);
    assertThat(primitiveType.getMinValue()).isEqualTo("3");
    assertThat(primitiveType.getMaxValue()).isEqualTo("15");
    assertThat(primitiveType.describe()).isEqualTo("char[3..15]");
  }

  @Test
  void shouldParseIntType() {
    DataType type = parser.parse("int[1..999]");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.INT);
    assertThat(primitiveType.getMinValue()).isEqualTo("1");
    assertThat(primitiveType.getMaxValue()).isEqualTo("999");
  }

  @Test
  void shouldParseDecimalType() {
    DataType type = parser.parse("decimal[0.01..9999.99]");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.DECIMAL);
    assertThat(primitiveType.getMinValue()).isEqualTo("0.01");
    assertThat(primitiveType.getMaxValue()).isEqualTo("9999.99");
  }

  @Test
  void shouldParseDateType() {
    DataType type = parser.parse("date[2020-01-01..2025-12-31]");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.DATE);
    assertThat(primitiveType.getMinValue()).isEqualTo("2020-01-01");
    assertThat(primitiveType.getMaxValue()).isEqualTo("2025-12-31");
  }

  @Test
  void shouldParseTimestampType() {
    DataType type = parser.parse("timestamp[now-30d..now]");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.TIMESTAMP);
  }

  @Test
  void shouldParseBooleanType() {
    DataType type = parser.parse("boolean");

    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.BOOLEAN);
    assertThat(primitiveType.getMinValue()).isNull();
    assertThat(primitiveType.getMaxValue()).isNull();
    assertThat(primitiveType.describe()).isEqualTo("boolean");
  }

  @Test
  void shouldParseEnumType() {
    DataType type = parser.parse("enum[PENDING,APPROVED,REJECTED]");

    assertThat(type).isInstanceOf(EnumType.class);
    EnumType enumType = (EnumType) type;
    assertThat(enumType.getValues()).containsExactly("PENDING", "APPROVED", "REJECTED");
    assertThat(enumType.describe()).isEqualTo("enum[PENDING,APPROVED,REJECTED]");
  }

  @Test
  void shouldParseObjectType() {
    DataType type = parser.parse("object[company]");

    assertThat(type).isInstanceOf(ObjectType.class);
    ObjectType objectType = (ObjectType) type;
    assertThat(objectType.getStructureName()).isEqualTo("company");
    assertThat(objectType.describe()).isEqualTo("object[company]");
  }

  @Test
  void shouldParseReferenceType() {
    DataType type = parser.parse("ref[user.id]");

    assertThat(type).isInstanceOf(ReferenceType.class);
    ReferenceType refType = (ReferenceType) type;
    assertThat(refType.getTargetStructure()).isEqualTo("user");
    assertThat(refType.getTargetField()).isEqualTo("id");
    assertThat(refType.describe()).isEqualTo("ref[user.id]");
  }

  @Test
  void shouldParseSimpleArrayType() {
    DataType type = parser.parse("array[int[1..100], 5..10]");

    assertThat(type).isInstanceOf(ArrayType.class);
    ArrayType arrayType = (ArrayType) type;
    assertThat(arrayType.getMinLength()).isEqualTo(5);
    assertThat(arrayType.getMaxLength()).isEqualTo(10);
    assertThat(arrayType.getElementType()).isInstanceOf(PrimitiveType.class);
    assertThat(arrayType.describe()).isEqualTo("array[int[1..100], 5..10]");
  }

  @Test
  void shouldParseArrayOfObjects() {
    DataType type = parser.parse("array[object[line_item], 1..50]");

    assertThat(type).isInstanceOf(ArrayType.class);
    ArrayType arrayType = (ArrayType) type;
    assertThat(arrayType.getMinLength()).isEqualTo(1);
    assertThat(arrayType.getMaxLength()).isEqualTo(50);
    assertThat(arrayType.getElementType()).isInstanceOf(ObjectType.class);
    ObjectType elementType = (ObjectType) arrayType.getElementType();
    assertThat(elementType.getStructureName()).isEqualTo("line_item");
  }

  @Test
  void shouldParseArrayWithZeroMinLength() {
    DataType type = parser.parse("array[char[1..20], 0..5]");

    assertThat(type).isInstanceOf(ArrayType.class);
    ArrayType arrayType = (ArrayType) type;
    assertThat(arrayType.getMinLength()).isEqualTo(0);
    assertThat(arrayType.getMaxLength()).isEqualTo(5);
  }

  @Test
  void shouldFailOnNullTypeString() {
    assertThatThrownBy(() -> parser.parse(null))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void shouldFailOnEmptyTypeString() {
    assertThatThrownBy(() -> parser.parse("   "))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("cannot be null or empty");
  }

  @Test
  void shouldFailOnInvalidSyntax() {
    assertThatThrownBy(() -> parser.parse("invalid[syntax"))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("Unsupported type syntax");
  }

  @Test
  void shouldFailOnEmptyEnum() {
    assertThatThrownBy(() -> parser.parse("enum[]"))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("must have at least one value");
  }

  @Test
  void shouldFailOnInvalidArrayLength() {
    assertThatThrownBy(() -> parser.parse("array[int[1..10], -1..5]"))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("Invalid array length constraints");
  }

  @Test
  void shouldFailOnArrayMaxLessThanMin() {
    assertThatThrownBy(() -> parser.parse("array[int[1..10], 10..5]"))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("Invalid array length constraints");
  }
}
