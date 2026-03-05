package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents primitive data types with optional range constraints. Supports: char, int, decimal,
 * boolean, date, timestamp.
 */
@Value
public class PrimitiveType implements DataType {
  Kind kind;
  String minValue; // String to support dates, numbers, etc.
  String maxValue;

  public enum Kind {
    // Primitive types with ranges
    CHAR,
    INT,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIMESTAMP,

    // Person semantic types (Datafaker)
    NAME,
    FIRST_NAME,
    LAST_NAME,
    FULL_NAME,
    USERNAME,
    TITLE,
    OCCUPATION,

    // Address semantic types
    ADDRESS,
    STREET_NAME,
    STREET_NUMBER,
    CITY,
    STATE,
    POSTAL_CODE,
    COUNTRY,

    // Contact semantic types
    EMAIL,
    PHONE_NUMBER,

    // Finance semantic types
    COMPANY,
    CREDIT_CARD,
    IBAN,
    CURRENCY,
    PRICE,

    // Internet semantic types
    DOMAIN,
    URL,
    IPV4,
    IPV6,
    MAC_ADDRESS,

    // Code semantic types
    ISBN,
    UUID
  }

  @Override
  public String describe() {
    if (kind == Kind.BOOLEAN) {
      return "boolean";
    }
    if (minValue == null || maxValue == null) {
      return kind.name().toLowerCase();
    }
    return kind.name().toLowerCase() + "[" + minValue + ".." + maxValue + "]";
  }
}
