package com.datagenerator.schema.model;

import com.datagenerator.core.seed.SeedConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/**
 * Represents a complete job definition loaded from YAML. Contains source reference, destination
 * type, seed config, structures path, and destination-specific configuration.
 */
@Value
public class JobConfig {
  @NotNull String source;

  @NotNull String type;

  @Valid SeedConfig seed;

  String
      structuresPath; // Optional path for loading nested structures (default: config/structures/)

  @NotNull JsonNode conf; // Destination-specific config (parsed based on type)

  @JsonCreator
  public JobConfig(
      @JsonProperty("source") String source,
      @JsonProperty("type") String type,
      @JsonProperty("seed") SeedConfig seed,
      @JsonProperty("structures_path") String structuresPath,
      @JsonProperty("conf") JsonNode conf) {
    this.source = source;
    this.type = type;
    this.seed = seed;
    this.structuresPath = structuresPath;
    this.conf = conf;
  }
}
