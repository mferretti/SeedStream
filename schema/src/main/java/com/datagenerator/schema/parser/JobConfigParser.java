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

import com.datagenerator.schema.model.JobConfig;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses job definition YAML files into JobConfig objects. Validates the configuration using
 * Jakarta Bean Validation.
 */
@Slf4j
public class JobConfigParser extends AbstractYamlParser<JobConfig> {

  /**
   * Parse a job configuration from a YAML file.
   *
   * @param filePath Path to the YAML file
   * @return Parsed and validated JobConfig
   * @throws com.datagenerator.schema.exception.SchemaParseException if parsing or validation fails
   */
  public JobConfig parse(Path filePath) {
    log.debug("Parsing job config from: {}", filePath);
    JobConfig config = parseFile(filePath, JobConfig.class, "job config");
    log.info(
        "Successfully parsed job config: source={}, type={}", config.getSource(), config.getType());
    return config;
  }
}
