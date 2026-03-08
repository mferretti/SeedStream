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

  // ===== New Semantic Types Tests (Phase 1) =====

  @Test
  void shouldParsePrefix() {
    DataType type = parser.parse("prefix");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    PrimitiveType primitiveType = (PrimitiveType) type;
    assertThat(primitiveType.getKind()).isEqualTo(PrimitiveType.Kind.PREFIX);
    assertThat(primitiveType.getMinValue()).isNull();
    assertThat(primitiveType.getMaxValue()).isNull();
  }

  @Test
  void shouldParseSuffix() {
    DataType type = parser.parse("suffix");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.SUFFIX);
  }

  @Test
  void shouldParsePassword() {
    DataType type = parser.parse("password");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.PASSWORD);
  }

  @Test
  void shouldParseSSN() {
    DataType type = parser.parse("ssn");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.SSN);
  }

  @Test
  void shouldParseLatitude() {
    DataType type = parser.parse("latitude");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LATITUDE);
  }

  @Test
  void shouldParseLatitudeAlias() {
    DataType type = parser.parse("lat");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LATITUDE);
  }

  @Test
  void shouldParseLongitude() {
    DataType type = parser.parse("longitude");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LONGITUDE);
  }

  @Test
  void shouldParseLongitudeAliases() {
    assertThat(((PrimitiveType) parser.parse("lon")).getKind())
        .isEqualTo(PrimitiveType.Kind.LONGITUDE);
    assertThat(((PrimitiveType) parser.parse("lng")).getKind())
        .isEqualTo(PrimitiveType.Kind.LONGITUDE);
    assertThat(((PrimitiveType) parser.parse("long")).getKind())
        .isEqualTo(PrimitiveType.Kind.LONGITUDE);
  }

  @Test
  void shouldParseCountryCode() {
    DataType type = parser.parse("country_code");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.COUNTRY_CODE);
  }

  @Test
  void shouldParseCountryCodeAlias() {
    DataType type = parser.parse("countrycode");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.COUNTRY_CODE);
  }

  @Test
  void shouldParseTimeZone() {
    DataType type = parser.parse("time_zone");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.TIME_ZONE);
  }

  @Test
  void shouldParseTimeZoneAlias() {
    DataType type = parser.parse("timezone");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.TIME_ZONE);
  }

  @Test
  void shouldParseBIC() {
    DataType type = parser.parse("bic");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.BIC);
  }

  @Test
  void shouldParseBICAliasSwift() {
    DataType type = parser.parse("swift");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.BIC);
  }

  @Test
  void shouldParseCVV() {
    DataType type = parser.parse("cvv");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.CVV);
  }

  @Test
  void shouldParseCVVAliasCVC() {
    DataType type = parser.parse("cvc");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.CVV);
  }

  @Test
  void shouldParseCreditCardType() {
    DataType type = parser.parse("credit_card_type");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.CREDIT_CARD_TYPE);
  }

  @Test
  void shouldParseCreditCardTypeAlias() {
    DataType type = parser.parse("creditcardtype");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.CREDIT_CARD_TYPE);
  }

  @Test
  void shouldParseStockMarket() {
    DataType type = parser.parse("stock_market");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.STOCK_MARKET);
  }

  @Test
  void shouldParseStockMarketAliases() {
    assertThat(((PrimitiveType) parser.parse("stockmarket")).getKind())
        .isEqualTo(PrimitiveType.Kind.STOCK_MARKET);
    assertThat(((PrimitiveType) parser.parse("stock")).getKind())
        .isEqualTo(PrimitiveType.Kind.STOCK_MARKET);
    assertThat(((PrimitiveType) parser.parse("ticker")).getKind())
        .isEqualTo(PrimitiveType.Kind.STOCK_MARKET);
  }

  @Test
  void shouldParseProductName() {
    DataType type = parser.parse("product_name");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.PRODUCT_NAME);
  }

  @Test
  void shouldParseProductNameAliases() {
    assertThat(((PrimitiveType) parser.parse("productname")).getKind())
        .isEqualTo(PrimitiveType.Kind.PRODUCT_NAME);
    assertThat(((PrimitiveType) parser.parse("product")).getKind())
        .isEqualTo(PrimitiveType.Kind.PRODUCT_NAME);
  }

  @Test
  void shouldParseDepartment() {
    DataType type = parser.parse("department");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.DEPARTMENT);
  }

  @Test
  void shouldParseColor() {
    DataType type = parser.parse("color");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.COLOR);
  }

  @Test
  void shouldParseMaterial() {
    DataType type = parser.parse("material");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.MATERIAL);
  }

  @Test
  void shouldParsePromotionCode() {
    DataType type = parser.parse("promotion_code");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.PROMOTION_CODE);
  }

  @Test
  void shouldParsePromotionCodeAliases() {
    assertThat(((PrimitiveType) parser.parse("promotioncode")).getKind())
        .isEqualTo(PrimitiveType.Kind.PROMOTION_CODE);
    assertThat(((PrimitiveType) parser.parse("promo")).getKind())
        .isEqualTo(PrimitiveType.Kind.PROMOTION_CODE);
    assertThat(((PrimitiveType) parser.parse("coupon")).getKind())
        .isEqualTo(PrimitiveType.Kind.PROMOTION_CODE);
  }

  @Test
  void shouldParseLoremWord() {
    DataType type = parser.parse("lorem_word");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_WORD);
  }

  @Test
  void shouldParseLoremWordAlias() {
    DataType type = parser.parse("loremword");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_WORD);
  }

  @Test
  void shouldParseLoremSentence() {
    DataType type = parser.parse("lorem_sentence");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_SENTENCE);
  }

  @Test
  void shouldParseLoremSentenceAlias() {
    DataType type = parser.parse("loremsentence");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_SENTENCE);
  }

  @Test
  void shouldParseLoremParagraph() {
    DataType type = parser.parse("lorem_paragraph");
    assertThat(type).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_PARAGRAPH);
  }

  @Test
  void shouldParseLoremParagraphAlias() {
    DataType type = parser.parse("loremparagraph");
    assertThat(((PrimitiveType) type).getKind()).isEqualTo(PrimitiveType.Kind.LOREM_PARAGRAPH);
  }
}
