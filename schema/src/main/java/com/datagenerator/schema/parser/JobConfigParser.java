package com.datagenerator.schema.parser;

import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.JobConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses job definition YAML files into JobConfig objects. Validates the configuration using
 * Jakarta Bean Validation.
 */
@Slf4j
public class JobConfigParser {
  private final ObjectMapper yamlMapper;
  private final Validator validator;

  public JobConfigParser() {
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
    this.validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  /**
   * Parse a job configuration from a YAML file.
   *
   * @param filePath Path to the YAML file
   * @return Parsed and validated JobConfig
   * @throws SchemaParseException if parsing or validation fails
   */
  public JobConfig parse(Path filePath) {
    log.debug("Parsing job config from: {}", filePath);

    if (!Files.exists(filePath)) {
      throw new SchemaParseException("Job config file not found: " + filePath);
    }

    try {
      String content = Files.readString(filePath);
      JobConfig config = yamlMapper.readValue(content, JobConfig.class);

      Set<ConstraintViolation<JobConfig>> violations = validator.validate(config);
      if (!violations.isEmpty()) {
        String errors =
            violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        throw new SchemaParseException("Validation failed for " + filePath + ": " + errors);
      }

      log.info(
          "Successfully parsed job config: source={}, type={}",
          config.getSource(),
          config.getType());
      return config;

    } catch (IOException e) {
      throw new SchemaParseException("Failed to read job config file: " + filePath, e);
    }
  }
}
