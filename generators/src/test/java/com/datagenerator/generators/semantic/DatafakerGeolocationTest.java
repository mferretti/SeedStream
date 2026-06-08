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

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive tests for DatafakerGenerator covering multiple geolocations and all semantic types.
 */
class DatafakerGeolocationTest {
  private DatafakerGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    FakerCache.clear(); // Clear cache to ensure clean state
    generator = new DatafakerGenerator();
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, java.nio.file.Paths.get("test"));
  }

  /** Helper to generate with context. */
  private Object generateWithContext(String geolocation, DataType dataType) {
    try (var ctx = GeneratorContext.enter(factory, geolocation)) {
      return generator.generate(new Random(12345L), dataType);
    }
  }

  // ==================================================================================
  // GEOLOCATION TESTS - Test multiple locales
  // ==================================================================================

  static Stream<Arguments> localeNamesWithPattern() {
    return Stream.of(
        Arguments.of("italy", "^[A-Za-zÀ-ÖØ-öø-ÿ\\s'-]+$"),
        Arguments.of("germany", "^[A-Za-zÄÖÜäöüß\\s'-]+$"),
        Arguments.of("france", "^[A-Za-zÀ-ÿ\\s'-]+$"),
        Arguments.of("spain", "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s'-]+$"),
        Arguments.of("brazil", "^[A-Za-zÀ-ÿ\\s'-]+$"),
        Arguments.of("india", "^[A-Za-z\\s'-]+$"),
        Arguments.of("sweden", "^[A-Za-zÅÄÖåäö\\s'-]+$"));
  }

  @ParameterizedTest
  @MethodSource("localeNamesWithPattern")
  void shouldGenerateNameForLocaleMatchingPattern(String locale, String pattern) {
    CustomDatafakerType nameType = new CustomDatafakerType("name");
    String name = (String) generateWithContext(locale, nameType);
    assertThat(name).isNotNull().isNotEmpty().matches(pattern);
  }

  @ParameterizedTest
  @ValueSource(strings = {"japan", "china", "korea", "russia", "saudi arabia", "poland"})
  void shouldGenerateNameForLocaleWithoutRegexConstraint(String locale) {
    CustomDatafakerType nameType = new CustomDatafakerType("name");
    String name = (String) generateWithContext(locale, nameType);
    assertThat(name).isNotNull().isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "australia, city",
    "mexico, address",
    "canada, postal_code",
    "netherlands, city",
    "turkey, city",
  })
  void shouldGenerateDataTypeForLocale(String locale, String dataType) {
    CustomDatafakerType type = new CustomDatafakerType(dataType);
    String result = (String) generateWithContext(locale, type);
    assertThat(result).isNotNull().isNotEmpty();
  }

  // ==================================================================================
  // ALL SEMANTIC TYPES COVERAGE - Test every semantic type we support
  // ==================================================================================

  @Test
  void shouldGenerateAllPersonSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      // Test all person-related semantic types
      results.put("name", (String) generator.generate(random, new CustomDatafakerType("name")));
      results.put(
          "first_name", (String) generator.generate(random, new CustomDatafakerType("first_name")));
      results.put(
          "last_name", (String) generator.generate(random, new CustomDatafakerType("last_name")));
      results.put(
          "full_name", (String) generator.generate(random, new CustomDatafakerType("full_name")));
      results.put(
          "username", (String) generator.generate(random, new CustomDatafakerType("username")));
      results.put("title", (String) generator.generate(random, new CustomDatafakerType("title")));
      results.put(
          "occupation", (String) generator.generate(random, new CustomDatafakerType("occupation")));
    }

    // Verify all generated values
    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllAddressSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          "address", (String) generator.generate(random, new CustomDatafakerType("address")));
      results.put(
          "street_name",
          (String) generator.generate(random, new CustomDatafakerType("street_name")));
      results.put(
          "street_number",
          (String) generator.generate(random, new CustomDatafakerType("street_number")));
      results.put("city", (String) generator.generate(random, new CustomDatafakerType("city")));
      results.put("state", (String) generator.generate(random, new CustomDatafakerType("state")));
      results.put(
          "postal_code",
          (String) generator.generate(random, new CustomDatafakerType("postal_code")));
      results.put(
          "country", (String) generator.generate(random, new CustomDatafakerType("country")));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllContactSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put("email", (String) generator.generate(random, new CustomDatafakerType("email")));
      results.put(
          "phone_number",
          (String) generator.generate(random, new CustomDatafakerType("phone_number")));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify email format
    assertThat(results.get("email")).contains("@");
  }

  @Test
  void shouldGenerateAllFinanceSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          "company", (String) generator.generate(random, new CustomDatafakerType("company")));
      results.put(
          "credit_card",
          (String) generator.generate(random, new CustomDatafakerType("credit_card")));
      results.put("iban", (String) generator.generate(random, new CustomDatafakerType("iban")));
      results.put(
          "currency", (String) generator.generate(random, new CustomDatafakerType("currency")));
      results.put("price", (String) generator.generate(random, new CustomDatafakerType("price")));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllInternetSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put("domain", (String) generator.generate(random, new CustomDatafakerType("domain")));
      results.put("url", (String) generator.generate(random, new CustomDatafakerType("url")));
      results.put("ipv4", (String) generator.generate(random, new CustomDatafakerType("ipv4")));
      results.put("ipv6", (String) generator.generate(random, new CustomDatafakerType("ipv6")));
      results.put(
          "mac_address",
          (String) generator.generate(random, new CustomDatafakerType("mac_address")));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify URL format
    assertThat(results.get("url")).matches("^https?://.*");

    // Verify IPv4 format
    assertThat(results.get("ipv4")).matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    // Verify MAC address format
    assertThat(results.get("mac_address")).matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
  }

  @Test
  void shouldGenerateAllCodeSemanticTypes() {
    Map<String, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put("isbn", (String) generator.generate(random, new CustomDatafakerType("isbn")));
      results.put("uuid", (String) generator.generate(random, new CustomDatafakerType("uuid")));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify ISBN format (ISBN-13)
    assertThat(results.get("isbn")).matches("^\\d{13}$");

    // Verify UUID format
    assertThat(results.get("uuid"))
        .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  }

  // ==================================================================================
  // DETERMINISM TESTS - Ensure same seed produces same results across locales
  // ==================================================================================

  @Test
  void shouldProduceDeterministicResultsAcrossMultipleGeolocations() {
    CustomDatafakerType nameType = new CustomDatafakerType("name");

    List<String> geolocations = List.of("italy", "germany", "france", "spain", "brazil", "japan");

    for (String geolocation : geolocations) {
      Random random1 = new Random(12345L);
      Random random2 = new Random(12345L);

      try (var ctx = GeneratorContext.enter(factory, geolocation)) {
        String name1 = (String) generator.generate(random1, nameType);
        FakerCache.clear(); // Clear cache to allow new Random instance
        String name2 = (String) generator.generate(random2, nameType);

        assertThat(name1).as("Determinism for geolocation: " + geolocation).isEqualTo(name2);
      }
      FakerCache.clear(); // Clear cache between geolocations
    }
  }

  @Test
  void shouldGenerateDifferentValuesForDifferentSeedsInSameLocale() {
    CustomDatafakerType emailType = new CustomDatafakerType("email");
    List<String> emails = new ArrayList<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      for (int seed = 0; seed < 10; seed++) {
        Random random = new Random(seed);
        String email = (String) generator.generate(random, emailType);
        emails.add(email);
      }
    }

    // With 10 different seeds, we should get mostly different emails
    long uniqueCount = emails.stream().distinct().count();
    assertThat(uniqueCount).isGreaterThan(7); // Allow some collisions but expect diversity
  }
}
