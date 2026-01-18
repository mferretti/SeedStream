package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a foreign key reference to another structure's field. Used to create relationships
 * between generated records.
 */
@Value
public class ReferenceType implements DataType {
  String targetStructure;
  String targetField;

  @Override
  public String describe() {
    return "ref[" + targetStructure + "." + targetField + "]";
  }
}
