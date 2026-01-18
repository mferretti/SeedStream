package com.datagenerator.schema.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/**
 * Represents a field definition in a data structure. Contains the datatype specification and
 * optional alias for output.
 */
@Value
public class FieldDefinition {
  @NotNull String datatype;

  String alias;

  @JsonCreator
  public FieldDefinition(
      @JsonProperty("datatype") String datatype, @JsonProperty("alias") String alias) {
    this.datatype = datatype;
    this.alias = alias;
  }
}
