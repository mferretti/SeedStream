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

package com.datagenerator.inspector.jsonschema;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.inspector.FakerTypeSuggestionsWriter;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.CustomTypeConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full round-trip for the regex bootstrap loop: a {@code pattern} field is inspected → the
 * suggested custom type is written and registered → re-inspecting upgrades the field's datatype →
 * the registered generator actually produces values that match the pattern. This is the automated
 * counterpart to the manual {@code inspect → --faker-types → execute} flow.
 */
class JsonSchemaRegexRoundTripTest {

  private static final String PATTERN = "^[A-Z]{3}-\\d{3}$";

  // productCode -> snake_case type name "product_code"; kept distinctive to avoid registry clashes.
  private static final String SCHEMA =
      "{"
          + "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\","
          + "  \"title\": \"Product\","
          + "  \"type\": \"object\","
          + "  \"properties\": {"
          + "    \"productCode\": { \"type\": \"string\", \"pattern\": \"^[A-Z]{3}-\\\\d{3}$\" }"
          + "  }"
          + "}";

  @Test
  void regexSuggestionRegistersAndGeneratesMatchingValues(@TempDir Path dir) throws IOException {
    Path schemaFile = dir.resolve("product.schema.json");
    Files.writeString(schemaFile, SCHEMA);

    // 1. inspect surfaces the regex as a faker-type suggestion (field maps to char for now).
    Inspection first = new JsonSchemaInspector().inspect(schemaFile);
    assertThat(first.fakerTypeSuggestions()).containsEntry("product_code", "regex:" + PATTERN);
    assertThat(datatypeOf(first, "product", "productCode")).isEqualTo("char[1..50]");

    // 2. write the companion file and load it, exactly as the CLI + --faker-types would.
    FakerTypeSuggestionsWriter.Outcome outcome =
        new FakerTypeSuggestionsWriter().write(first.fakerTypeSuggestions(), dir, false, null);
    assertThat(outcome.result()).isEqualTo(FakerTypeSuggestionsWriter.Result.WRITTEN);
    assertThat(new CustomTypeConfigLoader().load(outcome.target())).isEqualTo(1);
    assertThat(DatafakerRegistry.isRegistered("product_code")).isTrue();

    // 3. re-inspecting now resolves the field to the registered type by name-hint.
    Inspection second = new JsonSchemaInspector().inspect(schemaFile);
    assertThat(datatypeOf(second, "product", "productCode")).isEqualTo("product_code");

    // 4. the registered generator actually produces pattern-matching values.
    Faker faker = new Faker(Locale.US, new Random(1));
    Random random = new Random(1);
    for (int i = 0; i < 25; i++) {
      assertThat(DatafakerRegistry.generate("product_code", faker, random)).matches(PATTERN);
    }
  }

  private String datatypeOf(Inspection inspection, String structure, String field) {
    DataStructure ds =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(structure))
            .findFirst()
            .orElseThrow();
    return ds.getData().get(field).getDatatype();
  }
}
