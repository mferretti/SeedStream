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

package com.datagenerator.core.type;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.exception.TypeParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    assertThat(refType.getMin()).isNull();
    assertThat(refType.getMax()).isNull();
    assertThat(refType.isMaxIsCount()).isFalse();
    assertThat(refType.describe()).isEqualTo("ref[user.id]");
  }

  @Test
  void shouldParseReferenceTypeWithStaticRange() {
    DataType type = parser.parse("ref[order.user_id, 1..1000]");

    assertThat(type).isInstanceOf(ReferenceType.class);
    ReferenceType refType = (ReferenceType) type;
    assertThat(refType.getTargetStructure()).isEqualTo("order");
    assertThat(refType.getTargetField()).isEqualTo("user_id");
    assertThat(refType.getMin()).isEqualTo(1L);
    assertThat(refType.getMax()).isEqualTo(1000L);
    assertThat(refType.isMaxIsCount()).isFalse();
    assertThat(refType.describe()).isEqualTo("ref[order.user_id, 1..1000]");
  }

  @Test
  void shouldParseReferenceTypeWithCountBound() {
    DataType type = parser.parse("ref[customer.id, 1..count]");

    assertThat(type).isInstanceOf(ReferenceType.class);
    ReferenceType refType = (ReferenceType) type;
    assertThat(refType.getTargetStructure()).isEqualTo("customer");
    assertThat(refType.getTargetField()).isEqualTo("id");
    assertThat(refType.getMin()).isEqualTo(1L);
    assertThat(refType.getMax()).isNull();
    assertThat(refType.isMaxIsCount()).isTrue();
    assertThat(refType.describe()).isEqualTo("ref[customer.id, 1..count]");
  }

  @Test
  void shouldRejectReferenceTypeWithInvalidRange() {
    assertThatThrownBy(() -> parser.parse("ref[user.id, 100..1]"))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("min")
        .hasMessageContaining("max");
  }

  @Test
  void shouldParseParentReferenceType() {
    DataType type = parser.parse("ref[parent.id]");

    assertThat(type).isInstanceOf(ParentReferenceType.class);
    ParentReferenceType prt = (ParentReferenceType) type;
    assertThat(prt.getTargetField()).isEqualTo("id");
    assertThat(prt.describe()).isEqualTo("ref[parent.id]");
  }

  @Test
  void shouldParseParentReferenceTypeWithUnderscoreField() {
    DataType type = parser.parse("ref[parent.author_id]");

    assertThat(type).isInstanceOf(ParentReferenceType.class);
    assertThat(((ParentReferenceType) type).getTargetField()).isEqualTo("author_id");
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
    assertThat(arrayType.getMinLength()).isZero();
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

  // ===== New Semantic Types Tests (Phase 1) =====

  @Test
  void shouldParsePrefix() {
    DataType type = parser.parse("prefix");
    assertThat(type).isInstanceOf(CustomDatafakerType.class);
    CustomDatafakerType customType = (CustomDatafakerType) type;
    assertThat(customType.getTypeName()).isEqualTo("prefix");
  }

  @ParameterizedTest
  @CsvSource({
    "suffix, suffix",
    "password, password",
    "ssn, ssn",
    "latitude, latitude",
    "lat, latitude",
    "longitude, longitude",
    "country_code, country_code",
    "countrycode, country_code",
    "time_zone, time_zone",
    "timezone, time_zone",
    "bic, bic",
    "swift, bic",
    "cvv, cvv",
    "cvc, cvv",
    "credit_card_type, credit_card_type",
    "creditcardtype, credit_card_type",
    "stock_market, stock_market",
    "product_name, product_name",
    "department, department",
    "color, color",
    "material, material",
    "promotion_code, promotion_code",
    "lorem_word, lorem_word",
    "loremword, lorem_word",
    "lorem_sentence, lorem_sentence",
    "loremsentence, lorem_sentence",
    "lorem_paragraph, lorem_paragraph",
    "loremparagraph, lorem_paragraph",
  })
  void shouldParseCustomDatafakerType(String input, String expectedTypeName) {
    DataType type = parser.parse(input);
    assertThat(type).isInstanceOf(CustomDatafakerType.class);
    assertThat(((CustomDatafakerType) type).getTypeName()).isEqualTo(expectedTypeName);
  }

  @Test
  void shouldParseLongitudeAliases() {
    assertThat(((CustomDatafakerType) parser.parse("lon")).getTypeName()).isEqualTo("longitude");
    assertThat(((CustomDatafakerType) parser.parse("lng")).getTypeName()).isEqualTo("longitude");
    assertThat(((CustomDatafakerType) parser.parse("long")).getTypeName()).isEqualTo("longitude");
  }

  @Test
  void shouldParseStockMarketAliases() {
    assertThat(((CustomDatafakerType) parser.parse("stockmarket")).getTypeName())
        .isEqualTo("stock_market");
    assertThat(((CustomDatafakerType) parser.parse("stock")).getTypeName())
        .isEqualTo("stock_market");
    assertThat(((CustomDatafakerType) parser.parse("ticker")).getTypeName())
        .isEqualTo("stock_market");
  }

  @Test
  void shouldParseProductNameAliases() {
    assertThat(((CustomDatafakerType) parser.parse("productname")).getTypeName())
        .isEqualTo("product_name");
    assertThat(((CustomDatafakerType) parser.parse("product")).getTypeName())
        .isEqualTo("product_name");
  }

  @Test
  void shouldParsePromotionCodeAliases() {
    assertThat(((CustomDatafakerType) parser.parse("promotioncode")).getTypeName())
        .isEqualTo("promotion_code");
    assertThat(((CustomDatafakerType) parser.parse("promo")).getTypeName())
        .isEqualTo("promotion_code");
    assertThat(((CustomDatafakerType) parser.parse("coupon")).getTypeName())
        .isEqualTo("promotion_code");
  }
}
