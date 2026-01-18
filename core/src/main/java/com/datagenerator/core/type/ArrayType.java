package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents an array/list type with variable length. Contains another data type as the element
 * type and min/max constraints for array length.
 */
@Value
public class ArrayType implements DataType {
  DataType elementType;
  int minLength;
  int maxLength;

  @Override
  public String describe() {
    return "array[" + elementType.describe() + ", " + minLength + ".." + maxLength + "]";
  }
}
