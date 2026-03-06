/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.core.exception;

/**
 * Thrown when parsing a datatype string fails due to invalid syntax or unsupported type.
 *
 * <p>This exception occurs when the {@link com.datagenerator.core.type.TypeParser} encounters a
 * datatype specification that doesn't match the expected syntax or references an unknown type.
 *
 * <p><b>Common Causes:</b>
 *
 * <ul>
 *   <li><b>Syntax errors:</b> Missing brackets, incorrect range format, typos
 *   <li><b>Unsupported types:</b> Type name not recognized by the parser
 *   <li><b>Invalid ranges:</b> Min > max, non-numeric values in numeric ranges
 *   <li><b>Malformed arrays:</b> Missing element type or length specification
 *   <li><b>Bad enum syntax:</b> Missing comma separators, empty values
 * </ul>
 *
 * <p><b>Valid Datatype Examples:</b>
 *
 * <pre>
 * char[5..20]           // String with length 5 to 20
 * int[1..100]           // Integer from 1 to 100
 * decimal[0.0..999.99]  // Decimal with 2 decimal places
 * date[2020-01-01..2025-12-31]  // Date range
 * timestamp[now-30d..now]       // Recent timestamps
 * enum[red,green,blue]          // Enum values
 * array[int[1..10], 5..10]      // Array of 5-10 integers
 * object[user]                  // Nested structure
 * </pre>
 *
 * <p><b>Resolution Steps:</b>
 *
 * <ol>
 *   <li>Check datatype syntax matches the examples above
 *   <li>Ensure brackets are balanced and commas are properly placed
 *   <li>Verify range min is less than or equal to max
 *   <li>For nested types, ensure referenced structures exist
 *   <li>Refer to documentation for supported type list
 * </ol>
 *
 * <p><b>Example Errors:</b>
 *
 * <pre>
 * int[100..1]      // Invalid: min > max
 * char[5-20]       // Invalid: use ".." not "-" for ranges
 * enum[red green]  // Invalid: missing comma separator
 * array[int[1..10] // Invalid: missing closing bracket
 * object[]         // Invalid: missing structure name
 * </pre>
 *
 * @see com.datagenerator.core.type.TypeParser
 * @see com.datagenerator.core.type.DataType
 * @since 1.0
 */
public class TypeParseException extends RuntimeException {
  /**
   * Constructs a new type parse exception with the specified detail message.
   *
   * @param message the detail message explaining the parse error
   */
  public TypeParseException(String message) {
    super(message);
  }

  /**
   * Constructs a new type parse exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the parse error
   * @param cause the underlying cause (if any)
   */
  public TypeParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
