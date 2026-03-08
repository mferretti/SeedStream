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

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.LocaleMapper;
import java.util.Locale;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Generates realistic locale-specific data using Datafaker library. Supports semantic types like
 * name, email, address, phone, etc.
 *
 * <p><b>Supported Types:</b> Person (name, email, username), Address (city, street, postal_code),
 * Finance (company, iban, price), Internet (url, domain, ipv4), etc.
 *
 * <p><b>Locale Support:</b> Uses geolocation from GeneratorContext to determine Faker locale.
 * Supports 62+ locales (en, it, es, fr, de, pt, ru, zh, ja, ko, ar, etc.)
 *
 * <p><b>Determinism:</b> Seeded Random instance is passed to Faker for reproducible data
 * generation.
 *
 * <p><b>Thread Safety:</b> Thread-safe. Creates Faker instance per generation with thread-local
 * Random.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // With Italian locale
 * GeneratorContext.enter(factory, "italy");
 * DatafakerGenerator gen = new DatafakerGenerator();
 * String name = gen.generate(random, nameType); // → "Mario Rossi"
 * String city = gen.generate(random, cityType); // → "Milano"
 * </pre>
 */
@Slf4j
public class DatafakerGenerator implements DataGenerator {

  @Override
  public boolean supports(DataType type) {
    if (!(type instanceof PrimitiveType primitiveType)) {
      return false;
    }
    return isSemanticType(primitiveType.getKind());
  }

  @Override
  public Object generate(Random random, DataType type) {
    if (!(type instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException("DatafakerGenerator only supports PrimitiveType, got: " + type);
    }

    PrimitiveType.Kind kind = primitiveType.getKind();
    if (!isSemanticType(kind)) {
      throw new GeneratorException("DatafakerGenerator does not support type: " + kind);
    }

    // Get geolocation from context and map to locale
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = LocaleMapper.map(geolocation);

    // Get cached Faker instance for this locale (thread-local cache)
    Faker faker = FakerCache.getOrCreate(locale, random);

    return generateValue(faker, kind);
  }

  /**
   * Generate value using Datafaker based on semantic type.
   *
   * @param faker Datafaker instance with locale and random seed
   * @param kind Semantic type kind
   * @return Generated value
   */
  private String generateValue(Faker faker, PrimitiveType.Kind kind) {
    return switch (kind) {
      // Person types
      case NAME -> faker.name().name();
      case FIRST_NAME -> faker.name().firstName();
      case LAST_NAME -> faker.name().lastName();
      case FULL_NAME -> faker.name().fullName();
      case USERNAME -> generateUsername(faker);
      case TITLE -> faker.name().title();
      case OCCUPATION -> faker.job().title();

      // Address types
      case ADDRESS -> faker.address().fullAddress();
      case STREET_NAME -> faker.address().streetName();
      case STREET_NUMBER -> faker.address().streetAddressNumber();
      case CITY -> faker.address().city();
      case STATE -> faker.address().state();
      case POSTAL_CODE -> faker.address().zipCode();
      case COUNTRY -> faker.address().country();

      // Contact types
      case EMAIL -> faker.internet().emailAddress();
      case PHONE_NUMBER -> faker.phoneNumber().phoneNumber();

      // Finance types
      case COMPANY -> faker.company().name();
      case CREDIT_CARD -> faker.finance().creditCard();
      case IBAN -> faker.finance().iban();
      case CURRENCY -> faker.money().currencyCode();
      case PRICE -> faker.commerce().price();

      // Internet types
      case DOMAIN -> faker.internet().domainName();
      case URL -> faker.internet().url();
      case IPV4 -> faker.internet().ipV4Address();
      case IPV6 -> faker.internet().ipV6Address();
      case MAC_ADDRESS -> faker.internet().macAddress();

      // Code types
      case ISBN -> faker.code().isbn13();
      case UUID -> faker.internet().uuid();

      default ->
          throw new GeneratorException("Type " + kind + " is not supported by DatafakerGenerator");
    };
  }

  /**
   * Check if a PrimitiveType.Kind is a semantic type (Datafaker-based).
   *
   * @param kind The kind to check
   * @return true if semantic type, false if primitive with range
   */
  private boolean isSemanticType(PrimitiveType.Kind kind) {
    return switch (kind) {
      case NAME,
          FIRST_NAME,
          LAST_NAME,
          FULL_NAME,
          USERNAME,
          TITLE,
          OCCUPATION,
          ADDRESS,
          STREET_NAME,
          STREET_NUMBER,
          CITY,
          STATE,
          POSTAL_CODE,
          COUNTRY,
          EMAIL,
          PHONE_NUMBER,
          COMPANY,
          CREDIT_CARD,
          IBAN,
          CURRENCY,
          PRICE,
          DOMAIN,
          URL,
          IPV4,
          IPV6,
          MAC_ADDRESS,
          ISBN,
          UUID ->
          true;
      default -> false;
    };
  }

  /**
   * Generate a username without using deprecated methods.
   *
   * <p>Creates username from first name + random number (e.g., "john1234")
   *
   * @param faker Datafaker instance
   * @return Generated username
   */
  private String generateUsername(Faker faker) {
    String firstName = faker.name().firstName().toLowerCase().replaceAll("[^a-z]", "");
    int number = faker.number().numberBetween(1, 10000);
    return firstName + number;
  }
}
