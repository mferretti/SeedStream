package com.datagenerator.core.exception;

/**
 * Thrown when parsing a datatype string fails due to invalid syntax or unsupported type
 * specification.
 */
public class TypeParseException extends RuntimeException {
  public TypeParseException(String message) {
    super(message);
  }

  public TypeParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
