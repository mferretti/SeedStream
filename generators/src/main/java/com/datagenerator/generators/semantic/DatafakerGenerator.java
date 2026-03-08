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

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.LocaleMapper;
import java.util.Locale;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Generates realistic locale-specific data using Datafaker library via DatafakerRegistry.
 *
 * <p><b>Supported Types:</b> All types registered in {@link DatafakerRegistry} including:
 *
 * <ul>
 *   <li>Person (name, email, username)
 *   <li>Address (city, street, postal_code)
 *   <li>Finance (company, iban, price)
 *   <li>Internet (url, domain, ipv4)
 *   <li>Commerce (product_name, department, color)
 *   <li>Text (lorem_word, lorem_sentence, lorem_paragraph)
 *   <li>Code (isbn, uuid)
 *   <li>Custom types (pokemon, beer_style, etc.)
 * </ul>
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
 * String name = gen.generate(random, new CustomDatafakerType("name")); // → "Mario Rossi"
 * String city = gen.generate(random, new CustomDatafakerType("city")); // → "Milano"
 * </pre>
 */
@Slf4j
public class DatafakerGenerator implements DataGenerator {

  @Override
  public boolean supports(DataType type) {
    return type instanceof CustomDatafakerType;
  }

  @Override
  public Object generate(Random random, DataType type) {
    if (!(type instanceof CustomDatafakerType customType)) {
      throw new GeneratorException(
          "DatafakerGenerator only supports CustomDatafakerType, got: " + type);
    }

    // Get geolocation from context and map to locale
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = LocaleMapper.map(geolocation);

    // Get cached Faker instance for this locale (thread-local cache)
    Faker faker = FakerCache.getOrCreate(locale, random);

    // Generate value via registry
    return DatafakerRegistry.generate(customType.getTypeName(), faker, random);
  }
}
