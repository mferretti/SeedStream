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
