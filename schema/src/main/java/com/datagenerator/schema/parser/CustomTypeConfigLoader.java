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

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.schema.exception.SchemaParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads user-defined Datafaker types from a YAML config into the {@link DatafakerRegistry}, so
 * structures can reference Datafaker generators that are not hard-coded as built-ins.
 *
 * <p>Datafaker exposes far more providers than the built-in registrations; this config lets users
 * expose any of them by mapping a SeedStream type key to a Datafaker method path.
 *
 * <pre>
 * # datafaker-types.yaml
 * types:
 *   beer_style: beer.style             # faker.beer().style()
 *   pokemon:    pokemon.name            # faker.pokemon().name()
 *   full_addr:  address.fullAddress     # faker.address().fullAddress()
 *   iso_msg_id: "regex:[A-Z0-9]{10,35}" # generates strings matching the regex
 * aliases:
 *   beerstyle:  beer_style
 * </pre>
 *
 * <p>A value prefixed with {@code regex:} is registered as a regex-pattern generator (see {@link
 * DatafakerRegistry#registerRegex}), letting structures declare patterned fields (structured IDs,
 * ISO references) without code. All other values are dot-separated no-arg method chains.
 */
@Slf4j
public class CustomTypeConfigLoader {

  private static final String REGEX_PREFIX = "regex:";

  private final YAMLMapper yaml = new YAMLMapper();

  /**
   * Loads and registers all types and aliases from the config file.
   *
   * @return the number of types registered
   * @throws SchemaParseException if the file cannot be read or an expression is invalid
   */
  public int load(Path configFile) {
    JsonNode root;
    try {
      root = yaml.readTree(configFile.toFile());
    } catch (IOException e) {
      throw new SchemaParseException("Failed to read Datafaker types config: " + configFile, e);
    }
    if (root == null || root.isMissingNode() || !root.isObject()) {
      throw new SchemaParseException("Empty or invalid Datafaker types config: " + configFile);
    }

    int registered = 0;
    JsonNode types = root.path("types");
    if (types.isObject()) {
      for (Map.Entry<String, JsonNode> entry : types.properties()) {
        registerType(entry.getKey(), entry.getValue().asText(), configFile);
        registered++;
      }
    }

    JsonNode aliases = root.path("aliases");
    if (aliases.isObject()) {
      for (Map.Entry<String, JsonNode> entry : aliases.properties()) {
        DatafakerRegistry.registerAlias(entry.getKey(), entry.getValue().asText());
      }
    }

    log.info("Registered {} custom Datafaker type(s) from {}", registered, configFile);
    return registered;
  }

  /**
   * Register a single type. A {@code regex:}-prefixed value becomes a {@code regexify} generator;
   * anything else is a no-arg method chain.
   */
  private void registerType(String key, String rawValue, Path configFile) {
    String value = rawValue.trim();
    boolean isRegex = value.startsWith(REGEX_PREFIX);
    try {
      if (isRegex) {
        DatafakerRegistry.registerRegex(key, value.substring(REGEX_PREFIX.length()).trim());
      } else {
        DatafakerRegistry.registerExpression(key, value);
      }
    } catch (IllegalArgumentException e) {
      String hint =
          isRegex
              ? " (see the 'Regex patterns' notes in config/README.md for supported syntax)"
              : "";
      throw new SchemaParseException(
          "Invalid Datafaker type '" + key + "' in " + configFile + ": " + e.getMessage() + hint,
          e);
    }
  }
}
