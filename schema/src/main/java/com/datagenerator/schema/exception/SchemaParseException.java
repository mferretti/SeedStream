package com.datagenerator.schema.exception;

/**
 * Exception thrown when YAML schema parsing fails due to invalid format, missing required fields,
 * or validation errors.
 */
public class SchemaParseException extends RuntimeException {
  public SchemaParseException(String message) {
    super(message);
  }

  public SchemaParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
