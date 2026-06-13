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

package com.datagenerator.schema.parser;

import com.datagenerator.schema.exception.SchemaParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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

abstract class AbstractYamlParser<T> {

  protected static final ObjectMapper yamlMapper =
      // Reject unknown fields at config parse time to surface typos early.
      new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  protected static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  protected T parseFile(Path filePath, Class<T> type, String description) {
    if (!Files.exists(filePath)) {
      throw new SchemaParseException(description + " file not found: " + filePath);
    }
    try {
      String content = Files.readString(filePath);
      T result = yamlMapper.readValue(content, type);
      Set<ConstraintViolation<T>> violations = validator.validate(result);
      if (!violations.isEmpty()) {
        String errors =
            violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        throw new SchemaParseException("Validation failed for %s: %s".formatted(filePath, errors));
      }
      return result;
    } catch (SchemaParseException e) {
      throw e;
    } catch (IOException e) {
      throw new SchemaParseException(
          "Failed to read " + description + " file: %s".formatted(filePath), e);
    }
  }
}
