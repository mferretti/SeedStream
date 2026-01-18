package com.datagenerator.core.type;

import java.util.List;
import lombok.Value;

/** Represents an enumeration type with a fixed set of allowed values. */
@Value
public class EnumType implements DataType {
  List<String> values;

  @Override
  public String describe() {
    return "enum[" + String.join(",", values) + "]";
  }
}
