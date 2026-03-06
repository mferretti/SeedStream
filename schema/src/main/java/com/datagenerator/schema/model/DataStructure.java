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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Value;

/**
 * Represents a complete data structure definition loaded from YAML. Example: address.yaml with
 * fields like name, city, etc.
 */
@Value
public class DataStructure {
  @NotNull String name;

  String geolocation;

  @NotEmpty @Valid Map<String, FieldDefinition> data;

  @JsonCreator
  public DataStructure(
      @JsonProperty("name") String name,
      @JsonProperty("geolocation") String geolocation,
      @JsonProperty("data") Map<String, FieldDefinition> data) {
    this.name = name;
    this.geolocation = geolocation;
    this.data = data;
  }
}
