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
import com.datagenerator.generators.LocaleMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.datafaker.Faker;
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
  private static final String STYPE_EMAIL = "email";

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
    assertThat(name).matches(pattern);
  }

  @ParameterizedTest
  @CsvSource({
    "japan, HAN|HIRAGANA|KATAKANA",
    "china, HAN",
    "korea, HANGUL",
    "russia, CYRILLIC",
    "saudi arabia, ARABIC",
    "poland, LATIN"
  })
  void shouldWriteNamesInTheLocaleScript(String locale, String scripts) {
    Set<Character.UnicodeScript> allowed =
        Arrays.stream(scripts.split("\\|"))
            .map(Character.UnicodeScript::valueOf)
            .collect(Collectors.toSet());

    String name = (String) generateWithContext(locale, new CustomDatafakerType("name"));

    assertThat(name.codePoints().filter(Character::isLetter))
        .as("letters of '%s'", name)
        .isNotEmpty()
        .allMatch(cp -> allowed.contains(Character.UnicodeScript.of(cp)));
  }

  @ParameterizedTest
  @CsvSource({"australia, city", "mexico, address", "netherlands, city", "turkey, city"})
  void shouldUseTheLocaleProviderForAddressTypes(String locale, String dataType) {
    String result = (String) generateWithContext(locale, new CustomDatafakerType(dataType));

    Faker reference = new Faker(LocaleMapper.map(locale), new Random(12345L));
    String expected =
        "city".equals(dataType) ? reference.address().city() : reference.address().fullAddress();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldGenerateCanadianPostalCodeFormat() {
    String postal = (String) generateWithContext("canada", new CustomDatafakerType("postal_code"));

    assertThat(postal).matches("^[A-Z]\\d[A-Z] ?\\d[A-Z]\\d$");
  }

  // ==================================================================================
  // IBAN LOCALE AWARENESS (issue #173)
  // ==================================================================================

  private static final String IBAN_PATTERN = "^[A-Z]{2}\\d{2}[A-Z0-9]+$";

  @ParameterizedTest
  @CsvSource({"italy, IT", "germany, DE", "france, FR"})
  void shouldGenerateIbanForLocaleCountry(String geolocation, String expectedCountry) {
    String iban = (String) generateWithContext(geolocation, new CustomDatafakerType("iban"));
    assertThat(iban).isNotNull().matches(IBAN_PATTERN).startsWith(expectedCountry);
  }

  @Test
  void shouldFallBackToValidIbanWhenLocaleCountryHasNoIbanFormat() {
    // Japan (Locale.JAPAN, country JP) has no IBAN format in Datafaker → falls back to a
    // random-country IBAN rather than throwing. Still a structurally valid IBAN.
    String iban = (String) generateWithContext("japan", new CustomDatafakerType("iban"));
    assertThat(iban).isNotNull().matches(IBAN_PATTERN);
  }

  @Test
  void shouldGenerateValidRandomIbanIndependentOfLocale() {
    String iban = (String) generateWithContext("italy", new CustomDatafakerType("random_iban"));
    assertThat(iban).isNotNull().matches(IBAN_PATTERN);
  }

  @Test
  void shouldResolveRandomIbanAlias() {
    String iban =
        (String) generateWithContext("italy", new CustomDatafakerType("random_locale_iban"));
    assertThat(iban).isNotNull().matches(IBAN_PATTERN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"iban", "random_iban", "sepa_iban"})
  void shouldGenerateIbanTypeDeterministicallyForSameSeed(String ibanType) {
    String first = (String) generateWithContext("italy", new CustomDatafakerType(ibanType));
    FakerCache.clear();
    String second = (String) generateWithContext("italy", new CustomDatafakerType(ibanType));
    assertThat(second).isEqualTo(first);
  }

  private static final Set<String> SEPA_COUNTRIES =
      Set.of(
          "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT",
          "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO",
          "CH", "MC", "SM", "GB", "GI", "AD", "VA");

  @Test
  void shouldGenerateSepaIbanWithinSepaZone() {
    // Multiple draws under one context (seeded Random advances) → varied SEPA countries.
    Set<String> countries = new HashSet<>();
    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      Random random = new Random(12345L);
      CustomDatafakerType type = new CustomDatafakerType("sepa_iban");
      for (int i = 0; i < 50; i++) {
        String iban = (String) generator.generate(random, type);
        assertThat(iban).matches(IBAN_PATTERN);
        countries.add(iban.substring(0, 2));
      }
    }
    assertThat(countries).isSubsetOf(SEPA_COUNTRIES).hasSizeGreaterThan(1);
  }

  // BIC: 8 or 11 uppercase alphanumerics; country in positions 5-6 (0-based index 4-5).
  private static final String BIC_PATTERN = "^[A-Z0-9]{8}([A-Z0-9]{3})?$";

  @ParameterizedTest
  @CsvSource({"italy, IT", "germany, DE", "france, FR", "usa, US"})
  void shouldGenerateBicForLocaleCountry(String geolocation, String expectedCountry) {
    // #177: bic must carry the locale country in positions 5-6, not a random one.
    String bic = (String) generateWithContext(geolocation, new CustomDatafakerType("bic"));
    assertThat(bic).isNotNull().matches(BIC_PATTERN);
    assertThat(bic.substring(4, 6)).isEqualTo(expectedCountry);
  }

  @Test
  void shouldGenerateRandomBicIndependentOfLocale() {
    String bic = (String) generateWithContext("italy", new CustomDatafakerType("random_bic"));
    assertThat(bic).isNotNull().matches(BIC_PATTERN);
  }

  @ParameterizedTest
  @CsvSource({"italy, EUR", "germany, EUR", "usa, USD", "uk, GBP", "japan, JPY"})
  void shouldGenerateLocaleCurrencyForLocaleCountry(String geolocation, String expectedCurrency) {
    // #177: locale_currency ties the ISO 4217 code to the locale country.
    String currency =
        (String) generateWithContext(geolocation, new CustomDatafakerType("locale_currency"));
    assertThat(currency).isEqualTo(expectedCurrency);
  }

  @Test
  void shouldKeepCurrencyLocaleInsensitive() {
    // currency stays a random ISO 4217 code (a system may transact many currencies), so italy
    // is not pinned to EUR — that's what locale_currency is for.
    String currency = (String) generateWithContext("italy", new CustomDatafakerType("currency"));
    assertThat(currency).isNotNull().matches("^[A-Z]{3}$");
  }

  // ==================================================================================
  // ALL SEMANTIC TYPES COVERAGE - Test every semantic type we support
  // ==================================================================================

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
    CustomDatafakerType emailType = new CustomDatafakerType(STYPE_EMAIL);
    List<String> emails = new ArrayList<>();

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      for (int seed = 0; seed < 10; seed++) {
        // Each iteration builds a fresh Random on this thread — clear first (see FakerCache).
        FakerCache.clear();
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
