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
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for DatafakerGenerator with TypeParser. Demonstrates end-to-end semantic type
 * parsing and generation.
 */
class DatafakerIntegrationTest {
  private TypeParser typeParser;
  private DataGeneratorFactory factory;
  private Random random;

  @BeforeEach
  void setUp() {
    FakerCache.clear(); // Clear cache to ensure clean state
    typeParser = new TypeParser();
    random = new Random(12345L);

    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  @Test
  void shouldParseAndGenerateSemanticTypes() {
    // Parse semantic type without brackets
    DataType nameType = typeParser.parse("name");
    assertThat(nameType).isInstanceOf(CustomDatafakerType.class);
    assertThat(((CustomDatafakerType) nameType).getTypeName()).isEqualTo("name");

    // Generate value using DatafakerGenerator
    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      DataGenerator generator = factory.create(nameType);
      assertThat(generator).isInstanceOf(DatafakerGenerator.class);

      String name = (String) generator.generate(random, nameType);
      assertThat(name).isNotNull();
      assertThat(name).isNotEmpty();
    }
  }

  @Test
  void shouldParseAllSemanticTypeSyntaxVariants() {
    // Underscore variants
    assertThat(typeParser.parse("first_name")).isInstanceOf(CustomDatafakerType.class);
    assertThat(typeParser.parse("last_name")).isInstanceOf(CustomDatafakerType.class);
    assertThat(typeParser.parse("phone_number")).isInstanceOf(CustomDatafakerType.class);

    // No underscore variants
    assertThat(typeParser.parse("firstname")).isInstanceOf(CustomDatafakerType.class);
    assertThat(typeParser.parse("lastname")).isInstanceOf(CustomDatafakerType.class);
    assertThat(typeParser.parse("phonenumber")).isInstanceOf(CustomDatafakerType.class);

    // Alias variants
    assertThat(typeParser.parse("zip")).isInstanceOf(CustomDatafakerType.class);
    assertThat(typeParser.parse("phone")).isInstanceOf(CustomDatafakerType.class);
  }

  @Test
  void shouldGenerateRealisticDataForItalianLocale() {
    // Parse multiple semantic types
    DataType nameType = typeParser.parse("name");
    DataType emailType = typeParser.parse("email");
    DataType cityType = typeParser.parse("city");
    DataType phoneType = typeParser.parse("phone_number");

    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      DataGenerator nameGen = factory.create(nameType);
      DataGenerator emailGen = factory.create(emailType);
      DataGenerator cityGen = factory.create(cityType);
      DataGenerator phoneGen = factory.create(phoneType);

      String name = (String) nameGen.generate(random, nameType);
      String email = (String) emailGen.generate(random, emailType);
      String city = (String) cityGen.generate(random, cityType);
      String phone = (String) phoneGen.generate(random, phoneType);

      // Verify all generated values are valid
      assertThat(name).matches("^[A-Za-zÀ-ÖØ-öø-ÿ\\s'-]+$");
      assertThat(email).contains("@");
      assertThat(city).isNotEmpty();
      assertThat(phone).matches("^[\\d\\s()+-]+$");
    }
  }

  @Test
  void shouldGenerateFinancialData() {
    DataType companyType = typeParser.parse("company");
    DataType ibanType = typeParser.parse("iban");
    DataType priceType = typeParser.parse("price");

    try (var ctx = GeneratorContext.enter(factory, "germany")) {
      DataGenerator companyGen = factory.create(companyType);
      DataGenerator ibanGen = factory.create(ibanType);
      DataGenerator priceGen = factory.create(priceType);

      String company = (String) companyGen.generate(random, companyType);
      String iban = (String) ibanGen.generate(random, ibanType);
      String price = (String) priceGen.generate(random, priceType);

      assertThat(company).isNotEmpty();
      assertThat(iban).matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$");
      assertThat(price).isNotEmpty(); // Price format can vary (e.g., "42.99" or "15.5")
    }
  }

  @Test
  void shouldGenerateInternetData() {
    DataType urlType = typeParser.parse("url");
    DataType domainType = typeParser.parse("domain");
    DataType ipv4Type = typeParser.parse("ipv4");
    DataType uuidType = typeParser.parse("uuid");

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      String url = (String) factory.create(urlType).generate(random, urlType);
      String domain = (String) factory.create(domainType).generate(random, domainType);
      String ipv4 = (String) factory.create(ipv4Type).generate(random, ipv4Type);
      String uuid = (String) factory.create(uuidType).generate(random, uuidType);

      assertThat(url).matches("^https?://.*");
      assertThat(domain).contains(".");
      assertThat(ipv4).matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
      assertThat(uuid).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
  }

  @Test
  void shouldSupportMixOfPrimitiveAndSemanticTypes() {
    // Primitive with range
    DataType ageType = typeParser.parse("int[18..99]");
    assertThat(ageType).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) ageType).getMinValue()).isEqualTo("18");
    assertThat(((PrimitiveType) ageType).getMaxValue()).isEqualTo("99");

    // Semantic type
    DataType nameType = typeParser.parse("name");
    assertThat(nameType).isInstanceOf(CustomDatafakerType.class);
    assertThat(((CustomDatafakerType) nameType).getTypeName()).isEqualTo("name");

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Both should generate successfully
      Integer age = (Integer) factory.create(ageType).generate(random, ageType);
      String name = (String) factory.create(nameType).generate(random, nameType);

      assertThat(age).isBetween(18, 99);
      assertThat(name).isNotEmpty();
    }
  }

  @Test
  void shouldGenerateConsistentDataWithSameSeed() {
    DataType nameType = typeParser.parse("name");

    Random random1 = new Random(42L);
    Random random2 = new Random(42L);

    String name1;
    try (var ctx = GeneratorContext.enter(factory, "france")) {
      name1 = (String) factory.create(nameType).generate(random1, nameType);
    }

    FakerCache.clear(); // Clear cache to allow new Random instance

    String name2;
    try (var ctx = GeneratorContext.enter(factory, "france")) {
      name2 = (String) factory.create(nameType).generate(random2, nameType);
    }

    assertThat(name1).isEqualTo(name2);
  }

  @Test
  void shouldHandleVariousLocales() {
    DataType nameType = typeParser.parse("name");
    DataType cityType = typeParser.parse("city");

    String[] locales = {"usa", "italy", "germany", "france", "spain", "japan", "china", "brazil"};

    for (String locale : locales) {
      try (var ctx = GeneratorContext.enter(factory, locale)) {
        String name = (String) factory.create(nameType).generate(random, nameType);
        String city = (String) factory.create(cityType).generate(random, cityType);

        assertThat(name).isNotEmpty();
        assertThat(city).isNotEmpty();
      }
    }
  }
}
