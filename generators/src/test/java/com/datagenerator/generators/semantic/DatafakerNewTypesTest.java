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

  private static final String TYPE_PASSWORD = "password";
  private static final String TYPE_PRODUCT_NAME = "product_name";
  private static final String TYPE_COLOR = "color";

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
    // Common prefixes: Mr., Mrs., Ms., Dr., etc.
    assertThat(prefix).isNotNull().isNotEmpty().matches("^[A-Za-z.]+$");
  }

  @Test
  void shouldGenerateSuffix() {
    String suffix = (String) generateWithContext("usa", "suffix");
    // Common suffixes: Jr., Sr., III, etc.
    assertThat(suffix).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGeneratePassword() {
    String password = (String) generateWithContext("usa", TYPE_PASSWORD);
    // Password should contain alphanumeric characters
    assertThat(password)
        .isNotNull()
        .hasSizeGreaterThanOrEqualTo(8)
        .hasSizeLessThanOrEqualTo(20)
        .matches("^[A-Za-z0-9]+$");
  }

  @Test
  void shouldGenerateSSN() {
    String ssn = (String) generateWithContext("usa", "ssn");
    // SSN format varies by locale
    assertThat(ssn).isNotNull().isNotEmpty();
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
    assertThat(countryCode).isNotNull().hasSize(2).matches("^[A-Z]{2}$"); // ISO 3166-1 alpha-2
  }

  @Test
  void shouldGenerateTimeZone() {
    String timeZone = (String) generateWithContext("usa", "time_zone");
    // Time zones like "America/New_York", "Europe/Rome"
    assertThat(timeZone).isNotNull().isNotEmpty().matches("^[A-Za-z_/]+$");
  }

  // ===== Finance Extensions =====

  @Test
  void shouldGenerateBIC() {
    String bic = (String) generateWithContext("germany", "bic");
    // BIC/SWIFT code format per ISO 9362: 8 or 11 UPPERCASE alphanumerics.
    // Regression guard: datafaker 2.6.0 Finance.bic() emits a lowercase country code
    // (positions 5-6); DatafakerRegistry.conformantBic() normalizes to uppercase.
    // Upstream:
    // https://github.com/datafaker-net/datafaker/commit/d4267942d61314017cba303f98cd607d071409f4
    assertThat(bic).isNotNull().isNotEmpty().matches("^[A-Z0-9]{8,11}$");
  }

  @Test
  void shouldGenerateCVV() {
    String cvv = (String) generateWithContext("usa", "cvv");
    assertThat(cvv).isNotNull().matches("^\\d{3}$"); // 3-digit CVV
    int cvvValue = Integer.parseInt(cvv);
    assertThat(cvvValue).isBetween(100, 999);
  }

  @Test
  void shouldGenerateCreditCardType() {
    String cardType = (String) generateWithContext("usa", "credit_card_type");
    // Common card types: Visa, Mastercard, Discover, Amex, etc.
    assertThat(cardType).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateStockMarket() {
    String ticker = (String) generateWithContext("usa", "stock_market");
    // Stock ticker symbols are typically 1-5 uppercase letters
    assertThat(ticker).isNotNull().isNotEmpty().matches("^[A-Z]{1,5}$");
  }

  // ===== Commerce Types =====

  @Test
  void shouldGenerateProductName() {
    String productName = (String) generateWithContext("usa", TYPE_PRODUCT_NAME);
    // Product names like "Ergonomic Steel Chair"
    assertThat(productName).isNotNull().isNotEmpty().hasSizeGreaterThan(5);
  }

  @Test
  void shouldGenerateDepartment() {
    String department = (String) generateWithContext("usa", "department");
    // Department names like "Electronics", "Clothing"
    assertThat(department).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGenerateColor() {
    String color = (String) generateWithContext("usa", TYPE_COLOR);
    // Color names like "red", "blue", "sky blue"
    assertThat(color).isNotNull().isNotEmpty().matches("^[a-z\\s]+$");
  }

  @Test
  void shouldGenerateMaterial() {
    String material = (String) generateWithContext("usa", "material");
    // Materials like "Cotton", "Steel", "Plastic"
    assertThat(material).isNotNull().isNotEmpty();
  }

  @Test
  void shouldGeneratePromotionCode() {
    String promoCode = (String) generateWithContext("usa", "promotion_code");
    // Promo codes like "SAVE20", "SaleCool194130"
    assertThat(promoCode).isNotNull().isNotEmpty().hasSizeGreaterThan(3);
  }

  // ===== Text/Lorem Types =====

  @Test
  void shouldGenerateLoremWord() {
    String word = (String) generateWithContext("usa", "lorem_word");
    assertThat(word)
        .isNotNull()
        .isNotEmpty()
        .matches("^[a-z]+$")
        .hasSizeLessThan(20); // Single word, reasonable length
  }

  @Test
  void shouldGenerateLoremSentence() {
    String sentence = (String) generateWithContext("usa", "lorem_sentence");
    assertThat(sentence).isNotNull().hasSizeGreaterThan(10);
    // Sentence should contain multiple words
    assertThat(sentence.split("\\s+")).hasSizeGreaterThan(1);
  }

  @Test
  void shouldGenerateLoremParagraph() {
    String paragraph = (String) generateWithContext("usa", "lorem_paragraph");
    assertThat(paragraph).isNotNull().hasSizeGreaterThan(50);
    // Paragraph should contain multiple sentences or clauses
    assertThat(paragraph.split("\\s+")).hasSizeGreaterThan(10);
  }

  // ===== Determinism Tests =====

  @Test
  void shouldGenerateDeterministicPasswordsWithSameSeed() {
    Random r1 = new Random(12345L);
    Random r2 = new Random(12345L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String pass1 = (String) generator.generate(r1, new CustomDatafakerType(TYPE_PASSWORD));
      FakerCache.clear();
      String pass2 = (String) generator.generate(r2, new CustomDatafakerType(TYPE_PASSWORD));
      assertThat(pass1).isEqualTo(pass2);
    }
  }

  @Test
  void shouldGenerateDeterministicCommerceDataWithSameSeed() {
    Random r1 = new Random(99999L);
    Random r2 = new Random(99999L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String product1 = (String) generator.generate(r1, new CustomDatafakerType(TYPE_PRODUCT_NAME));
      FakerCache.clear();
      String product2 = (String) generator.generate(r2, new CustomDatafakerType(TYPE_PRODUCT_NAME));
      assertThat(product1).isEqualTo(product2);
    }
  }

  @Test
  void shouldGenerateDifferentValuesWithDifferentSeeds() {
    Random r1 = new Random(11111L);
    Random r2 = new Random(22222L);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String color1 = (String) generator.generate(r1, new CustomDatafakerType(TYPE_COLOR));
      String color2 = (String) generator.generate(r2, new CustomDatafakerType(TYPE_COLOR));
      // Different seeds should produce different colors (with high probability)
      assertThat(color1).isNotEqualTo(color2);
    }
  }

  // ===== Support Tests =====

  @Test
  void shouldSupportAllNewSemanticTypes() {
    assertThat(generator.supports(new CustomDatafakerType("prefix"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("suffix"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType(TYPE_PASSWORD))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("ssn"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("latitude"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("longitude"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("country_code"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("time_zone"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("bic"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("cvv"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("credit_card_type"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("stock_market"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType(TYPE_PRODUCT_NAME))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("department"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType(TYPE_COLOR))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("material"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("promotion_code"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_word"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_sentence"))).isTrue();
    assertThat(generator.supports(new CustomDatafakerType("lorem_paragraph"))).isTrue();
  }
}
