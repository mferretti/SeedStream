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

import com.datagenerator.core.exception.TypeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses datatype strings from YAML into DataType objects. Supports all type syntax: primitives,
 * enums, objects, arrays, references.
 */
public class TypeParser {
  /** Constructs a new TypeParser instance. */
  public TypeParser() {
    // Default constructor
  }

  private static final Pattern PRIMITIVE_PATTERN =
      Pattern.compile("^(char|int|decimal|date|timestamp)\\[([^\\]]+?)\\.\\.([^\\]]+)\\]$");
  private static final Pattern ENUM_PATTERN = Pattern.compile("^enum\\[(.*)\\]$");
  private static final Pattern OBJECT_PATTERN = Pattern.compile("^object\\[([a-z_]+)\\]$");
  private static final Pattern ARRAY_PATTERN =
      Pattern.compile("^array\\[(.+),\\s*(-?\\d+)\\.\\.(-?\\d+)\\]$");
  private static final Pattern REF_PATTERN = Pattern.compile("^ref\\[([a-z_]+)\\.([a-z_]+)\\]$");

  /**
   * Parse a datatype string into a DataType object.
   *
   * @param typeString the type string (e.g., "char[3..15]", "array[int[1..100], 5..10]")
   * @return the parsed DataType
   * @throws TypeParseException if the type string is invalid
   */
  public DataType parse(String typeString) {
    if (typeString == null || typeString.isBlank()) {
      throw new TypeParseException("Type string cannot be null or empty");
    }

    String trimmed = typeString.trim();

    // Boolean (no brackets)
    if ("boolean".equals(trimmed)) {
      return new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
    }

    // Primitives with ranges
    Matcher primitiveMatcher = PRIMITIVE_PATTERN.matcher(trimmed);
    if (primitiveMatcher.matches()) {
      String kind = primitiveMatcher.group(1);
      String min = primitiveMatcher.group(2);
      String max = primitiveMatcher.group(3);
      return new PrimitiveType(
          PrimitiveType.Kind.valueOf(kind.toUpperCase()), min.trim(), max.trim());
    }

    // Enum
    Matcher enumMatcher = ENUM_PATTERN.matcher(trimmed);
    if (enumMatcher.matches()) {
      String valuesStr = enumMatcher.group(1).trim();
      if (valuesStr.isEmpty()) {
        throw new TypeParseException("Enum must have at least one value: %s".formatted(typeString));
      }
      List<String> values =
          Arrays.stream(valuesStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
      if (values.isEmpty()) {
        throw new TypeParseException("Enum must have at least one value: %s".formatted(typeString));
      }
      return new EnumType(values);
    }

    // Object
    Matcher objectMatcher = OBJECT_PATTERN.matcher(trimmed);
    if (objectMatcher.matches()) {
      String structureName = objectMatcher.group(1);
      return new ObjectType(structureName);
    }

    // Reference
    Matcher refMatcher = REF_PATTERN.matcher(trimmed);
    if (refMatcher.matches()) {
      String targetStructure = refMatcher.group(1);
      String targetField = refMatcher.group(2);
      return new ReferenceType(targetStructure, targetField);
    }

    // Array (recursive)
    Matcher arrayMatcher = ARRAY_PATTERN.matcher(trimmed);
    if (arrayMatcher.matches()) {
      String innerTypeStr = arrayMatcher.group(1).trim();
      int minLength = Integer.parseInt(arrayMatcher.group(2));
      int maxLength = Integer.parseInt(arrayMatcher.group(3));

      if (minLength < 0 || maxLength < minLength) {
        throw new TypeParseException(
            "Invalid array length constraints: min=" + minLength + ", max=" + maxLength);
      }

      DataType innerType = parse(innerTypeStr); // Recursive parse
      return new ArrayType(innerType, minLength, maxLength);
    }

    // Semantic types (no brackets) - try before throwing error
    return parseSemanticType(trimmed);
  }

  /**
   * Parse semantic type (Datafaker-based types without brackets).
   *
   * @param typeString the type string (e.g., "name", "email", "address")
   * @return PrimitiveType with semantic kind
   * @throws TypeParseException if not a valid semantic type
   */
  private PrimitiveType parseSemanticType(String typeString) {
    PrimitiveType.Kind kind =
        switch (typeString.toLowerCase()) {
          // Person types
          case "name" -> PrimitiveType.Kind.NAME;
          case "first_name", "firstname" -> PrimitiveType.Kind.FIRST_NAME;
          case "last_name", "lastname" -> PrimitiveType.Kind.LAST_NAME;
          case "full_name", "fullname" -> PrimitiveType.Kind.FULL_NAME;
          case "username" -> PrimitiveType.Kind.USERNAME;
          case "title" -> PrimitiveType.Kind.TITLE;
          case "occupation" -> PrimitiveType.Kind.OCCUPATION;
          case "prefix" -> PrimitiveType.Kind.PREFIX;
          case "suffix" -> PrimitiveType.Kind.SUFFIX;
          case "password" -> PrimitiveType.Kind.PASSWORD;
          case "ssn" -> PrimitiveType.Kind.SSN;

          // Address types
          case "address" -> PrimitiveType.Kind.ADDRESS;
          case "street_name", "streetname" -> PrimitiveType.Kind.STREET_NAME;
          case "street_number", "streetnumber" -> PrimitiveType.Kind.STREET_NUMBER;
          case "city" -> PrimitiveType.Kind.CITY;
          case "state" -> PrimitiveType.Kind.STATE;
          case "postal_code", "postalcode", "zipcode", "zip" -> PrimitiveType.Kind.POSTAL_CODE;
          case "country" -> PrimitiveType.Kind.COUNTRY;
          case "latitude", "lat" -> PrimitiveType.Kind.LATITUDE;
          case "longitude", "lon", "lng", "long" -> PrimitiveType.Kind.LONGITUDE;
          case "country_code", "countrycode" -> PrimitiveType.Kind.COUNTRY_CODE;
          case "time_zone", "timezone" -> PrimitiveType.Kind.TIME_ZONE;

          // Contact types
          case "email" -> PrimitiveType.Kind.EMAIL;
          case "phone_number", "phonenumber", "phone" -> PrimitiveType.Kind.PHONE_NUMBER;

          // Finance types
          case "company" -> PrimitiveType.Kind.COMPANY;
          case "credit_card", "creditcard" -> PrimitiveType.Kind.CREDIT_CARD;
          case "iban" -> PrimitiveType.Kind.IBAN;
          case "currency" -> PrimitiveType.Kind.CURRENCY;
          case "price" -> PrimitiveType.Kind.PRICE;
          case "bic", "swift" -> PrimitiveType.Kind.BIC;
          case "cvv", "cvc" -> PrimitiveType.Kind.CVV;
          case "credit_card_type", "creditcardtype" -> PrimitiveType.Kind.CREDIT_CARD_TYPE;
          case "stock_market", "stockmarket", "stock", "ticker" -> PrimitiveType.Kind.STOCK_MARKET;

          // Internet types
          case "domain" -> PrimitiveType.Kind.DOMAIN;
          case "url" -> PrimitiveType.Kind.URL;
          case "ipv4" -> PrimitiveType.Kind.IPV4;
          case "ipv6" -> PrimitiveType.Kind.IPV6;
          case "mac_address", "macaddress" -> PrimitiveType.Kind.MAC_ADDRESS;

          // Commerce types
          case "product_name", "productname", "product" -> PrimitiveType.Kind.PRODUCT_NAME;
          case "department" -> PrimitiveType.Kind.DEPARTMENT;
          case "color" -> PrimitiveType.Kind.COLOR;
          case "material" -> PrimitiveType.Kind.MATERIAL;
          case "promotion_code", "promotioncode", "promo", "coupon" ->
              PrimitiveType.Kind.PROMOTION_CODE;

          // Text/Lorem types
          case "lorem_word", "loremword" -> PrimitiveType.Kind.LOREM_WORD;
          case "lorem_sentence", "loremsentence" -> PrimitiveType.Kind.LOREM_SENTENCE;
          case "lorem_paragraph", "loremparagraph" -> PrimitiveType.Kind.LOREM_PARAGRAPH;

          // Code types
          case "isbn" -> PrimitiveType.Kind.ISBN;
          case "uuid" -> PrimitiveType.Kind.UUID;

          default -> throw new TypeParseException("Unsupported type syntax: " + typeString);
        };

    return new PrimitiveType(kind, null, null); // No range for semantic types
  }
}
