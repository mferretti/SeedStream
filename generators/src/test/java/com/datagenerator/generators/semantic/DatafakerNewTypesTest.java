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
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test suite for 20 new high-priority Datafaker types added in Phase 1. */
class DatafakerNewTypesTest {
  private DatafakerGenerator generator;
  private DataGeneratorFactory factory;
  private Random random;

  @BeforeEach
  void setUp() {
    FakerCache.clear();
    generator = new DatafakerGenerator();
    random = new Random(42L);

    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  private Object generateWithContext(String geolocation, String typeName) {
    try (var ctx = GeneratorContext.enter(factory, geolocation)) {
      return generator.generate(random, new CustomDatafakerType(typeName));
    }
  }

  // ===== Person Extensions =====

  @Test
  void shouldGeneratePrefix() {
    String prefix = (String) generateWithContext("usa", "prefix");
    assertThat(prefix).isNotNull();
    assertThat(prefix).isNotEmpty();
    // Common prefixes: Mr., Mrs., Ms., Dr., etc.
    assertThat(prefix).matches("^[A-Za-z.]+$");
  }

  @Test
  void shouldGenerateSuffix() {
    String suffix = (String) generateWithContext("usa", "suffix");
    assertThat(suffix).isNotNull();
    assertThat(suffix).isNotEmpty();
    // Common suffixes: Jr., Sr., III, etc.
  }

  @Test
  void shouldGeneratePassword() {
    String password = (String) generateWithContext("usa", "password");
    assertThat(password).isNotNull();
    assertThat(password).hasSizeGreaterThanOrEqualTo(8);
    assertThat(password).hasSizeLessThanOrEqualTo(20);
    // Password should contain alphanumeric characters
    assertThat(password).matches("^[A-Za-z0-9]+$");
  }

  @Test
  void shouldGenerateSSN() {
    String ssn = (String) generateWithContext("usa", "ssn");
    assertThat(ssn).isNotNull();
    assertThat(ssn).isNotEmpty();
    // SSN format varies by locale
  }

  // ===== Address Extensions =====

  @Test
  void shouldGenerateLatitude() {
    String latitude = (String) generateWithContext("usa", "latitude");
    assertThat(latitude).isNotNull();
    double lat = Double.parseDouble(latitude);
    assertThat(lat).isBetween(-90.0, 90.0);
  }

  @Test
  void shouldGenerateLongitude() {
    String longitude = (String) generateWithContext("usa", "longitude");
    assertThat(longitude).isNotNull();
    double lon = Double.parseDouble(longitude);
    assertThat(lon).isBetween(-180.0, 180.0);
  }

  @Test
  void shouldGenerateCountryCode() {
    String countryCode = (String) generateWithContext("usa", "country_code");
    assertThat(countryCode).isNotNull();
    assertThat(countryCode).hasSize(2); // ISO 3166-1 alpha-2
    assertThat(countryCode).matches("^[A-Z]{2}$");
  }

  @Test
  void shouldGenerateTimeZone() {
    String timeZone = (String) generateWithContext("usa", "time_zone");
    assertThat(timeZone).isNotNull();
    assertThat(timeZone).isNotEmpty();
    // Time zones like "America/New_York", "Europe/Rome"
    assertThat(timeZone).matches("^[A-Za-z_/]+$");
  }

  // ===== Finance Extensions =====

  @Test
  void shouldGenerateBIC() {
    String bic = (String) generateWithContext("germany", "bic");
    assertThat(bic).isNotNull();
    assertThat(bic).isNotEmpty();
    // BIC/SWIFT code format: 8 or 11 alphanumeric characters
    assertThat(bic).matches("^[A-Z0-9]{8,11}$");
  }

  @Test
  void shouldGenerateCVV() {
    String cvv = (String) generateWithContext("usa", "cvv");
    assertThat(cvv).isNotNull();
    assertThat(cvv).matches("^\\d{3}$"); // 3-digit CVV
    int cvvValue = Integer.parseInt(cvv);
    assertThat(cvvValue).isBetween(100, 999);
  }

  @Test
  void shouldGenerateCreditCardType() {
    String cardType = (String) generateWithContext("usa", "credit_card_type");
    assertThat(cardType).isNotNull();
    assertThat(cardType).isNotEmpty();
    // Common card types: Visa, Mastercard, Discover, Amex, etc.
  }

  @Test
  void shouldGenerateStockMarket() {
    String ticker = (String) generateWithContext("usa", "stock_market");
    assertThat(ticker).isNotNull();
    assertThat(ticker).isNotEmpty();
    // Stock ticker symbols are typically 1-5 uppercase letters
    assertThat(ticker).matches("^[A-Z]{1,5}$");
  }

  // ===== Commerce Types =====

  @Test
  void shouldGenerateProductName() {
    String productName = (String) generateWithContext("usa", "product_name");
    assertThat(productName).isNotNull();
    assertThat(productName).isNotEmpty();
    // Product names like "Ergonomic Steel Chair"
    assertThat(productName).hasSizeGreaterThan(5);
  }

  @Test
  void shouldGenerateDepartment() {
    String department = (String) generateWithContext("usa", "department");
    assertThat(department).isNotNull();
    assertThat(department).isNotEmpty();
    // Department names like "Electronics", "Clothing"
  }

  @Test
  void shouldGenerateColor() {
    String color = (String) generateWithContext("usa", "color");
    assertThat(color).isNotNull();
    assertThat(color).isNotEmpty();
    // Color names like "red", "blue", "sky blue"
    assertThat(color).matches("^[a-z\\s]+$");
  }

  @Test
  void shouldGenerateMaterial() {
    String material = (String) generateWithContext("usa", "material");
    assertThat(material).isNotNull();
    assertThat(material).isNotEmpty();
    // Materials like "Cotton", "Steel", "Plastic"
  }

  @Test
  void shouldGeneratePromotionCode() {
    String promoCode = (String) generateWithContext("usa", "promotion_code");
    assertThat(promoCode).isNotNull();
    assertThat(promoCode).isNotEmpty();
    // Promo codes like "SAVE20", "SaleCool194130"
    assertThat(promoCode).hasSizeGreaterThan(3);
  }

  // ===== Text/Lorem Types =====

  @Test
  void shouldGenerateLoremWord() {
    String word = (String) generateWithContext("usa", "lorem_word");
    assertThat(word).isNotNull();
    assertThat(word).isNotEmpty();
    assertThat(word).matches("^[a-z]+$");
    assertThat(word).hasSizeLessThan(20); // Single word, reasonable length
  }

  @Test
  void shouldGenerateLoremSentence() {
    String sentence = (String) generateWithContext("usa", "lorem_sentence");
    assertThat(sentence).isNotNull();
    assertThat(sentence).hasSizeGreaterThan(10);
    // Sentence should contain multiple words
    assertThat(sentence.split("\\s+")).hasSizeGreaterThan(1);
  }

  @Test
  void shouldGenerateLoremParagraph() {
    String paragraph = (String) generateWithContext("usa", "lorem_paragraph");
    assertThat(paragraph).isNotNull();
    assertThat(paragraph).hasSizeGreaterThan(50);
    // Paragraph should contain multiple sentences or clauses
    assertThat(paragraph.split("\\s+")).hasSizeGreaterThan(10);
  }

  // ===== Determinism Tests =====

  @Test
  void shouldGenerateDeterministicPasswordsWithSameSeed() {
    Random r1 = new Random(12345L);
    Random r2 = new Random(12345L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String pass1 = (String) generator.generate(r1, new CustomDatafakerType("password"));
      FakerCache.clear();
      String pass2 = (String) generator.generate(r2, new CustomDatafakerType("password"));
      assertThat(pass1).isEqualTo(pass2);
    }
  }

  @Test
  void shouldGenerateDeterministicCommerceDataWithSameSeed() {
    Random r1 = new Random(99999L);
    Random r2 = new Random(99999L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String product1 = (String) generator.generate(r1, new CustomDatafakerType("product_name"));
      FakerCache.clear();
      String product2 = (String) generator.generate(r2, new CustomDatafakerType("product_name"));
      assertThat(product1).isEqualTo(product2);
    }
  }

  @Test
  void shouldGenerateDifferentValuesWithDifferentSeeds() {
    Random r1 = new Random(11111L);
    Random r2 = new Random(22222L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String color1 = (String) generator.generate(r1, new CustomDatafakerType("color"));
      String color2 = (String) generator.generate(r2, new CustomDatafakerType("color"));
      // Different seeds should produce different colors (with high probability)
      assertThat(color1).isNotEqualTo(color2);
    }
  }

  // ===== Support Tests =====

  @Test
  void shouldSupportAllNewSemanticTypes() {
    assertThat(generator.supports(new CustomDatafakerType("prefix"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("suffix"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("password"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("ssn"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("latitude"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("longitude"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("country_code"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("time_zone"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("bic"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("cvv"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("credit_card_type"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("stock_market"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("product_name"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("department"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("color"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("material"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("promotion_code"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_word"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_sentence"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_paragraph"))).isTrue();
  }
}
