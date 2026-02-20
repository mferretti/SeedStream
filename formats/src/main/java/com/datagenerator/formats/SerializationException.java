package com.datagenerator.formats;

/** Exception thrown when serialization fails. */
public class SerializationException extends RuntimeException {
  public SerializationException(String message) {
    super(message);
  }

  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
