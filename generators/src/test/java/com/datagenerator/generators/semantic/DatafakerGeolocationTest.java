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
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

  @Test
  void shouldGenerateItalianNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("italy", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Italian names typically use Latin characters
    assertThat(name).matches("^[A-Za-zÀ-ÖØ-öø-ÿ\\s'-]+$");
  }

  @Test
  void shouldGenerateGermanNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("germany", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    assertThat(name).matches("^[A-Za-zÄÖÜäöüß\\s'-]+$");
  }

  @Test
  void shouldGenerateFrenchNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("france", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    assertThat(name).matches("^[A-Za-zÀ-ÿ\\s'-]+$");
  }

  @Test
  void shouldGenerateSpanishNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("spain", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    assertThat(name).matches("^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s'-]+$");
  }

  @Test
  void shouldGenerateBrazilianNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("brazil", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Brazilian Portuguese names
    assertThat(name).matches("^[A-Za-zÀ-ÿ\\s'-]+$");
  }

  @Test
  void shouldGenerateJapaneseNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("japan", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Japanese names can include Kanji, Hiragana, Katakana, or romanized text
  }

  @Test
  void shouldGenerateChineseNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("china", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Chinese names can include Chinese characters or romanized text
  }

  @Test
  void shouldGenerateKoreanNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("korea", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Korean names can include Hangul or romanized text
  }

  @Test
  void shouldGenerateRussianNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("russia", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Russian names can include Cyrillic or romanized text
  }

  @Test
  void shouldGenerateArabicNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("saudi arabia", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Arabic names can include Arabic script or romanized text
  }

  @Test
  void shouldGenerateIndianNames() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("india", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Indian names typically romanized
    assertThat(name).matches("^[A-Za-z\\s'-]+$");
  }

  @Test
  void shouldGenerateAustralianData() {
    PrimitiveType cityType = new PrimitiveType(PrimitiveType.Kind.CITY, null, null);
    String city = (String) generateWithContext("australia", cityType);

    assertThat(city).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateMexicanData() {
    PrimitiveType addressType = new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null);
    String address = (String) generateWithContext("mexico", addressType);

    assertThat(address).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateCanadianData() {
    PrimitiveType postalCodeType = new PrimitiveType(PrimitiveType.Kind.POSTAL_CODE, null, null);
    String postalCode = (String) generateWithContext("canada", postalCodeType);

    assertThat(postalCode).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateDutchData() {
    PrimitiveType cityType = new PrimitiveType(PrimitiveType.Kind.CITY, null, null);
    String city = (String) generateWithContext("netherlands", cityType);

    assertThat(city).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateSwedishData() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("sweden", nameType);

    assertThat(name).isNotNull().isNotEmpty();
    // Swedish names can include special characters
    assertThat(name).matches("^[A-Za-zÅÄÖåäö\\s'-]+$");
  }

  @Test
  void shouldGeneratePolishData() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("poland", nameType);

    assertThat(name).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateTurkishData() {
    PrimitiveType cityType = new PrimitiveType(PrimitiveType.Kind.CITY, null, null);
    String city = (String) generateWithContext("turkey", cityType);

    assertThat(city).isNotNull().isNotEmpty();
  }

  // ==================================================================================
  // ALL SEMANTIC TYPES COVERAGE - Test every semantic type we support
  // ==================================================================================

  @Test
  void shouldGenerateAllPersonSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      // Test all person-related semantic types
      results.put(
          PrimitiveType.Kind.NAME,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.NAME, null, null)));
      results.put(
          PrimitiveType.Kind.FIRST_NAME,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.FIRST_NAME, null, null)));
      results.put(
          PrimitiveType.Kind.LAST_NAME,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.LAST_NAME, null, null)));
      results.put(
          PrimitiveType.Kind.FULL_NAME,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.FULL_NAME, null, null)));
      results.put(
          PrimitiveType.Kind.USERNAME,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.USERNAME, null, null)));
      results.put(
          PrimitiveType.Kind.TITLE,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.TITLE, null, null)));
      results.put(
          PrimitiveType.Kind.OCCUPATION,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.OCCUPATION, null, null)));
    }

    // Verify all generated values
    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllAddressSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          PrimitiveType.Kind.ADDRESS,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null)));
      results.put(
          PrimitiveType.Kind.STREET_NAME,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.STREET_NAME, null, null)));
      results.put(
          PrimitiveType.Kind.STREET_NUMBER,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.STREET_NUMBER, null, null)));
      results.put(
          PrimitiveType.Kind.CITY,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.CITY, null, null)));
      results.put(
          PrimitiveType.Kind.STATE,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.STATE, null, null)));
      results.put(
          PrimitiveType.Kind.POSTAL_CODE,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.POSTAL_CODE, null, null)));
      results.put(
          PrimitiveType.Kind.COUNTRY,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.COUNTRY, null, null)));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllContactSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          PrimitiveType.Kind.EMAIL,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null)));
      results.put(
          PrimitiveType.Kind.PHONE_NUMBER,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null)));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify email format
    assertThat(results.get(PrimitiveType.Kind.EMAIL)).contains("@");
  }

  @Test
  void shouldGenerateAllFinanceSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          PrimitiveType.Kind.COMPANY,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.COMPANY, null, null)));
      results.put(
          PrimitiveType.Kind.CREDIT_CARD,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.CREDIT_CARD, null, null)));
      results.put(
          PrimitiveType.Kind.IBAN,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.IBAN, null, null)));
      results.put(
          PrimitiveType.Kind.CURRENCY,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.CURRENCY, null, null)));
      results.put(
          PrimitiveType.Kind.PRICE,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.PRICE, null, null)));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });
  }

  @Test
  void shouldGenerateAllInternetSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          PrimitiveType.Kind.DOMAIN,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.DOMAIN, null, null)));
      results.put(
          PrimitiveType.Kind.URL,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.URL, null, null)));
      results.put(
          PrimitiveType.Kind.IPV4,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.IPV4, null, null)));
      results.put(
          PrimitiveType.Kind.IPV6,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.IPV6, null, null)));
      results.put(
          PrimitiveType.Kind.MAC_ADDRESS,
          (String)
              generator.generate(
                  random, new PrimitiveType(PrimitiveType.Kind.MAC_ADDRESS, null, null)));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify URL format
    assertThat(results.get(PrimitiveType.Kind.URL)).matches("^https?://.*");

    // Verify IPv4 format
    assertThat(results.get(PrimitiveType.Kind.IPV4))
        .matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    // Verify MAC address format
    assertThat(results.get(PrimitiveType.Kind.MAC_ADDRESS))
        .matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
  }

  @Test
  void shouldGenerateAllCodeSemanticTypes() {
    Map<PrimitiveType.Kind, String> results = new HashMap<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);

      results.put(
          PrimitiveType.Kind.ISBN,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.ISBN, null, null)));
      results.put(
          PrimitiveType.Kind.UUID,
          (String)
              generator.generate(random, new PrimitiveType(PrimitiveType.Kind.UUID, null, null)));
    }

    results.forEach(
        (kind, value) -> {
          assertThat(value).as("Type: " + kind).isNotNull().isNotEmpty();
        });

    // Verify ISBN format (ISBN-13)
    assertThat(results.get(PrimitiveType.Kind.ISBN)).matches("^\\d{13}$");

    // Verify UUID format
    assertThat(results.get(PrimitiveType.Kind.UUID))
        .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  }

  // ==================================================================================
  // DETERMINISM TESTS - Ensure same seed produces same results across locales
  // ==================================================================================

  @Test
  void shouldProduceDeterministicResultsAcrossMultipleGeolocations() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);

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
    PrimitiveType emailType = new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null);
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
