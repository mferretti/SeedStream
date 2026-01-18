package com.datagenerator.core.exception;

/** Thrown when seed resolution fails (file not found, invalid format, network error, etc.). */
public class SeedResolutionException extends RuntimeException {
  public SeedResolutionException(String message) {
    super(message);
  }

  public SeedResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
