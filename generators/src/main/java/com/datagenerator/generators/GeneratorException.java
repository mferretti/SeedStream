package com.datagenerator.generators;

/**
 * Exception thrown when data generation fails due to invalid type configuration, constraint
 * violations, or generator errors.
 */
public class GeneratorException extends RuntimeException {
  public GeneratorException(String message) {
    super(message);
  }

  public GeneratorException(String message, Throwable cause) {
    super(message, cause);
  }
}
