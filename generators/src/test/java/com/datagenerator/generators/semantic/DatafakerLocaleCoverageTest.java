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

package com.datagenerator.generators.semantic;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every built-in Datafaker type must generate a non-blank value for every supported geolocation.
 * Datafaker lacks some data for some locales (e.g. Italian name suffixes); a job must not crash
 * because a structure combines such a type with such a geolocation.
 */
class DatafakerLocaleCoverageTest {

  private DatafakerGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    FakerCache.clear();
    generator = new DatafakerGenerator();
    factory =
        new DataGeneratorFactory(
            new StructureRegistry((name, path, reg) -> Map.of()), Paths.get("test"));
  }

  @AfterEach
  void tearDown() {
    FakerCache.clear();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ar", "ar-ae", "ar-eg", "cs", "da", "de", "de-at", "de-ch", "el", "en-au", "en-ca", "en-gb",
        "en-ie", "en-in", "en-ng", "en-nz", "en-ph", "en-pk", "en-sg", "en-us", "en-za", "es",
        "es-ar", "es-cl", "es-co", "es-mx", "es-pe", "fi", "fr", "he", "hu", "id", "it", "ja", "ko",
        "ms", "nl", "nl-be", "no", "pl", "pt", "pt-br", "ro", "ru", "sk", "sv", "th", "tr", "uk-ua",
        "vi", "zh", "zh-tw"
      })
  // Collects every failing type/locale pair instead of stopping at the first; Datafaker failures
  // are plain RuntimeExceptions.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void shouldGenerateEveryBuiltInTypeWhenGeolocationIsSupported(String geolocation) {
    List<String> failures = new ArrayList<>();
    for (String type : DatafakerRegistry.listTypes()) {
      if (!DatafakerRegistry.isBuiltIn(type)) {
        continue;
      }
      for (long seed = 0; seed < 5; seed++) {
        try (var ctx = GeneratorContext.enter(factory, geolocation)) {
          Object value = generator.generate(new Random(seed), new CustomDatafakerType(type));
          if (value == null || value.toString().isBlank()) {
            failures.add(type + " (blank)");
          }
        } catch (RuntimeException e) {
          failures.add(type + ": " + e.getMessage());
        }
        FakerCache.clear();
      }
    }
    assertThat(failures).as("built-in types failing for geolocation '%s'", geolocation).isEmpty();
  }

  @Test
  void shouldFallBackToEnglishDeterministicallyWhenLocaleLacksTheData() {
    // Datafaker has no Italian name suffixes (#379): en-US data is used instead, same seed -> same
    // value.
    Object first;
    Object second;
    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      first = generator.generate(new Random(7), new CustomDatafakerType("suffix"));
      FakerCache.clear();
      second = generator.generate(new Random(7), new CustomDatafakerType("suffix"));
    }

    assertThat(first).hasToString(second.toString());
    assertThat(first.toString()).matches("Jr\\.|Sr\\.|I|II|III|IV|V|MD|DDS|PhD|DVM");
  }

  @Test
  void shouldRethrowOriginalErrorWhenEnglishFallbackAlsoFails() {
    String type = "coverage_test_always_fails_" + System.nanoTime();
    DatafakerRegistry.register(
        type,
        (faker, random) -> {
          throw new IllegalStateException("no data for " + faker.getContext().getLocale());
        });

    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      assertThatThrownBy(() -> generator.generate(new Random(1), new CustomDatafakerType(type)))
          .isInstanceOf(GeneratorException.class)
          .hasMessageContaining(type)
          .hasMessageContaining("it_IT")
          .hasCauseInstanceOf(IllegalStateException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }
  }
}
