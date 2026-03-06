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

package com.datagenerator.schema.exception;

/**
 * Thrown when YAML schema parsing fails.
 *
 * <p>This exception occurs when parsing job configuration files or data structure definitions fails
 * due to invalid YAML syntax, missing required fields, or validation errors.
 *
 * <p><b>Common Causes:</b>
 *
 * <ul>
 *   <li><b>File not found:</b> Config file doesn't exist at specified path
 *   <li><b>Invalid YAML syntax:</b> Indentation errors, missing colons, unmatched quotes
 *   <li><b>Missing required fields:</b> {@code source}, {@code type}, or {@code conf} missing from
 *       job config
 *   <li><b>Validation failures:</b> Hibernate Validator constraints violated (e.g., {@code @NotNull},
 *       {@code @Min})
 *   <li><b>Type mismatches:</b> Expected string but found number, or vice versa
 * </ul>
 *
 * <p><b>Job Config Requirements:</b>
 *
 * <pre>
 * # Minimum valid job config
 * source: user.yaml    # Required: data structure file name
 * type: file           # Required: destination type (file, kafka, database)
 * seed:                # Optional: can be embedded, file, env, or remote
 *   type: embedded
 *   value: 12345
 * conf:                # Required: destination-specific configuration
 *   path: output/data
 * </pre>
 *
 * <p><b>Data Structure Requirements:</b>
 *
 * <pre>
 * # Minimum valid data structure
 * name: user                    # Required: structure name
 * geolocation: usa              # Optional: for locale-aware generation
 * data:                         # Required: field definitions
 *   user_id:
 *     datatype: uuid            # Required: valid datatype spec
 * </pre>
 *
 * <p><b>Resolution Steps:</b>
 *
 * <ol>
 *   <li>Verify file exists: {@code ls -la config/jobs/my_job.yaml}
 *   <li>Validate YAML syntax using online YAML validator or {@code yamllint}
 *   <li>Check indentation (spaces, not tabs) - YAML is indentation-sensitive
 *   <li>Ensure all required fields are present
 *   <li>Review error message for specific field/line number
 *   <li>Compare with working examples in {@code config/jobs/} directory
 * </ol>
 *
 * <p><b>Example Errors:</b>
 *
 * <pre>
 * # Missing colon
 * source user.yaml      # Invalid: should be "source: user.yaml"
 *
 * # Indentation error
 * data:
 * name:                 # Invalid: not indented under data:
 *   datatype: char[5..20]
 *
 * # Missing required field
 * source: user.yaml
 * # Missing required "type" field - throws SchemaParseException
 * </pre>
 *
 * @see com.datagenerator.schema.parser.JobConfigParser
 * @see com.datagenerator.schema.parser.DataStructureParser
 * @since 1.0
 */
public class SchemaParseException extends RuntimeException {
  /**
   * Constructs a new schema parse exception with the specified detail message.
   *
   * @param message the detail message explaining the parse error
   */
  public SchemaParseException(String message) {
    super(message);
  }

  /**
   * Constructs a new schema parse exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the parse error
   * @param cause the underlying cause (e.g., YAMLException, ValidationException)
   */
  public SchemaParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
