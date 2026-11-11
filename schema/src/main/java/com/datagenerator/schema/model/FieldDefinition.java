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

package com.datagenerator.schema.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/**
 * Represents a field definition in a data structure. Contains the datatype specification, optional
 * alias for output and optional {@code nullable} rate: the probability in [0, 1] that the field is
 * null (absent = never).
 */
@Value
public class FieldDefinition {
  @NotNull String datatype;

  String alias;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  Double nullable;

  @SuppressWarnings("checkstyle:HiddenField")
  public FieldDefinition(String datatype, String alias, Double nullable) {
    this.datatype = datatype;
    this.alias = alias;
    this.nullable = nullable;
  }

  @SuppressWarnings("checkstyle:HiddenField")
  public FieldDefinition(String datatype, String alias) {
    this(datatype, alias, null);
  }

  /** Reads {@code nullable} as a raw value so that only a YAML number is accepted. */
  @JsonCreator
  static FieldDefinition fromYaml(
      @JsonProperty("datatype") String datatype,
      @JsonProperty("alias") String alias,
      @JsonProperty("nullable") Object nullable) {
    if (nullable != null && !(nullable instanceof Number)) {
      throw new IllegalArgumentException(
          "nullable must be a number between 0 and 1, got: " + nullable);
    }
    return new FieldDefinition(
        datatype, alias, nullable == null ? null : ((Number) nullable).doubleValue());
  }
}
