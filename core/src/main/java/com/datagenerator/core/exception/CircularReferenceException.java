package com.datagenerator.core.exception;

/**
 * Thrown when a circular reference is detected in nested object types (e.g., structure A contains
 * object[B], B contains object[A]).
 */
public class CircularReferenceException extends RuntimeException {
  public CircularReferenceException(String message) {
    super(message);
  }

  public CircularReferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
