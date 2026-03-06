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
