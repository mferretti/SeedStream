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

  /** Primitive type kinds including standard types and semantic (Datafaker) types. */
  public enum Kind {
    /** Character string type with length range. */
    CHAR,
    /** Integer type with numeric range. */
    INT,
    /** Decimal number type with numeric range. */
    DECIMAL,
    /** Boolean type (true/false). */
    BOOLEAN,
    /** Date type with date range. */
    DATE,
    /** Timestamp type with datetime range. */
    TIMESTAMP,

    // Person semantic types (Datafaker)
    /** Person's name (locale-aware). */
    NAME,
    /** Person's first name (locale-aware). */
    FIRST_NAME,
    /** Person's last name (locale-aware). */
    LAST_NAME,
    /** Person's full name (locale-aware). */
    FULL_NAME,
    /** Username (locale-aware). */
    USERNAME,
    /** Job title (locale-aware). */
    TITLE,
    /** Occupation (locale-aware). */
    OCCUPATION,
    /** Name prefix (Mr., Mrs., Dr., etc.) (locale-aware). */
    PREFIX,
    /** Name suffix (Jr., Sr., III, etc.) (locale-aware). */
    SUFFIX,
    /** Password (8-20 characters with mixed case, numbers, special chars). */
    PASSWORD,
    /** Social Security Number (locale-aware format). */
    SSN,

    // Address semantic types
    /** Full street address (locale-aware). */
    ADDRESS,
    /** Street name only (locale-aware). */
    STREET_NAME,
    /** Street number (locale-aware). */
    STREET_NUMBER,
    /** City name (locale-aware). */
    CITY,
    /** State or province (locale-aware). */
    STATE,
    /** Postal/ZIP code (locale-aware). */
    POSTAL_CODE,
    /** Country name (locale-aware). */
    COUNTRY,
    /** Latitude coordinate (-90 to 90). */
    LATITUDE,
    /** Longitude coordinate (-180 to 180). */
    LONGITUDE,
    /** Country code (ISO 3166-1 alpha-2, e.g., "US", "IT"). */
    COUNTRY_CODE,
    /** Time zone (e.g., "America/New_York", "Europe/Rome"). */
    TIME_ZONE,

    // Contact semantic types
    /** Email address (locale-aware). */
    EMAIL,
    /** Phone number (locale-aware). */
    PHONE_NUMBER,

    // Finance semantic types
    /** Company name (locale-aware). */
    COMPANY,
    /** Credit card number. */
    CREDIT_CARD,
    /** IBAN (International Bank Account Number). */
    IBAN,
    /** Currency code (e.g., USD, EUR). */
    CURRENCY,
    /** Price with currency formatting. */
    PRICE,
    /** BIC/SWIFT code (Bank Identifier Code). */
    BIC,
    /** Credit card CVV/CVC code (3-4 digits). */
    CVV,
    /** Credit card type (Visa, Mastercard, Amex, etc.). */
    CREDIT_CARD_TYPE,
    /** Stock market ticker symbol (e.g., AAPL, GOOGL). */
    STOCK_MARKET,

    // Internet semantic types
    /** Internet domain name. */
    DOMAIN,
    /** Full URL with protocol. */
    URL,
    /** IPv4 address. */
    IPV4,
    /** IPv6 address. */
    IPV6,
    /** MAC address. */
    MAC_ADDRESS,

    // Commerce semantic types
    /** Product name (e.g., "Ergonomic Steel Chair"). */
    PRODUCT_NAME,
    /** Department name (e.g., "Electronics", "Clothing"). */
    DEPARTMENT,
    /** Color name (e.g., "Red", "Blue"). */
    COLOR,
    /** Material name (e.g., "Cotton", "Steel", "Plastic"). */
    MATERIAL,
    /** Promotion/coupon code (e.g., "SAVE20"). */
    PROMOTION_CODE,

    // Text/Lorem semantic types
    /** Single lorem ipsum word. */
    LOREM_WORD,
    /** Lorem ipsum sentence (multiple words). */
    LOREM_SENTENCE,
    /** Lorem ipsum paragraph (multiple sentences). */
    LOREM_PARAGRAPH,

    // Code semantic types
    /** ISBN book identifier. */
    ISBN,
    /** UUID (Universally Unique Identifier). */
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
