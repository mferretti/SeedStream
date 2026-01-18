package com.datagenerator.schema.parser;

import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
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
 * Parses data structure definition YAML files into DataStructure objects. Validates the structure
 * using Jakarta Bean Validation.
 */
@Slf4j
public class DataStructureParser {
  private final ObjectMapper yamlMapper;
  private final Validator validator;

  public DataStructureParser() {
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
    this.validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  /**
   * Parse a data structure definition from a YAML file.
   *
   * @param filePath Path to the YAML file
   * @return Parsed and validated DataStructure
   * @throws SchemaParseException if parsing or validation fails
   */
  public DataStructure parse(Path filePath) {
    log.debug("Parsing data structure from: {}", filePath);

    if (!Files.exists(filePath)) {
      throw new SchemaParseException("Data structure file not found: " + filePath);
    }

    try {
      String content = Files.readString(filePath);
      DataStructure structure = yamlMapper.readValue(content, DataStructure.class);

      Set<ConstraintViolation<DataStructure>> violations = validator.validate(structure);
      if (!violations.isEmpty()) {
        String errors =
            violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        throw new SchemaParseException("Validation failed for " + filePath + ": " + errors);
      }

      log.info("Successfully parsed data structure: {}", structure.getName());
      return structure;

    } catch (IOException e) {
      throw new SchemaParseException("Failed to read data structure file: " + filePath, e);
    }
  }
}
