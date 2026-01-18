package com.datagenerator.core.type;

/**
 * Base class for all data types in the generation system. Each type knows how to validate and
 * describe itself.
 */
public sealed interface DataType
    permits PrimitiveType, EnumType, ObjectType, ArrayType, ReferenceType {
  /**
   * Returns a human-readable description of this type (e.g., "char[3..15]", "array[int, 5..10]")
   */
  String describe();
}
