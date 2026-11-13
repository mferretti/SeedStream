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
 * alias for output, optional {@code nullable} rate (the probability in [0, 1] that the field is
 * null, absent = never) and an optional text cap: {@code max_length} (&gt;= 1) counted in {@code
 * length_unit} ({@code chars} = Unicode code points, the default, or {@code bytes} = UTF-8).
 */
@Value
public class FieldDefinition {
  @NotNull String datatype;

  String alias;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  Double nullable;

  /** Cap on the text length, or null for none. */
  Integer maxLength;

  /** Whether {@link #maxLength} counts UTF-8 bytes (true) or code points (false). */
  boolean lengthInBytes;

  @SuppressWarnings("checkstyle:HiddenField")
  public FieldDefinition(
      String datatype, String alias, Double nullable, Integer maxLength, boolean lengthInBytes) {
    this.datatype = datatype;
    this.alias = alias;
    this.nullable = nullable;
    this.maxLength = maxLength;
    this.lengthInBytes = lengthInBytes;
  }

  @SuppressWarnings("checkstyle:HiddenField")
  public FieldDefinition(String datatype, String alias, Double nullable) {
    this(datatype, alias, nullable, null, false);
  }

  @SuppressWarnings("checkstyle:HiddenField")
  public FieldDefinition(String datatype, String alias) {
    this(datatype, alias, null);
  }

  /** Reads the optional keys as raw values so that only a YAML number / known unit is accepted. */
  @JsonCreator
  static FieldDefinition fromYaml(
      @JsonProperty("datatype") String datatype,
      @JsonProperty("alias") String alias,
      @JsonProperty("nullable") Object nullable,
      @JsonProperty("max_length") Object maxLength,
      @JsonProperty("length_unit") Object lengthUnit) {
    if (nullable != null && !(nullable instanceof Number)) {
      throw new IllegalArgumentException(
          "nullable must be a number between 0 and 1, got: " + nullable);
    }
    Integer cap = null;
    if (maxLength != null) {
      if (!(maxLength instanceof Integer || maxLength instanceof Long)
          || ((Number) maxLength).longValue() < 1
          || ((Number) maxLength).longValue() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("max_length must be an integer >= 1, got: " + maxLength);
      }
      cap = ((Number) maxLength).intValue();
    }
    boolean bytes = false;
    if (lengthUnit != null) {
      if (cap == null) {
        throw new IllegalArgumentException("length_unit requires max_length");
      }
      if ("bytes".equals(lengthUnit)) {
        bytes = true;
      } else if (!"chars".equals(lengthUnit)) {
        throw new IllegalArgumentException(
            "length_unit must be 'chars' or 'bytes', got: " + lengthUnit);
      }
    }
    return new FieldDefinition(
        datatype, alias, nullable == null ? null : ((Number) nullable).doubleValue(), cap, bytes);
  }
}
