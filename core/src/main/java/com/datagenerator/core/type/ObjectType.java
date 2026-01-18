package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a nested object type that references another structure definition. The structure is
 * loaded from {structuresPath}/{structureName}.yaml.
 */
@Value
public class ObjectType implements DataType {
  String structureName;

  @Override
  public String describe() {
    return "object[" + structureName + "]";
  }
}
