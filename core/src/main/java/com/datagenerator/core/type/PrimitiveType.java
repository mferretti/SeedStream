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
    CHAR,
    INT,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIMESTAMP
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
