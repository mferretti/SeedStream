package com.datagenerator.destinations;

/** Exception thrown when destination operations fail. */
public class DestinationException extends RuntimeException {
  public DestinationException(String message) {
    super(message);
  }

  public DestinationException(String message, Throwable cause) {
    super(message, cause);
  }
}
