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
