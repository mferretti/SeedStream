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
import com.datagenerator.generators.LocaleMapper;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Wiring contract for built-in Datafaker types: each type name must produce exactly what its
 * Datafaker provider produces for the same locale and seed. A non-empty string would also pass if
 * {@code company} were wired to the name provider or the geolocation were ignored; this does not.
 *
 * <p>Types with their own logic (IBAN/BIC per country, locale currency, regex, phone formatting,
 * ...) have dedicated tests elsewhere.
 */
class DatafakerProviderWiringTest {

  private static final long SEED = 20261001L;

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

  private static Arguments wire(String type, Function<Faker, Object> provider) {
    return Arguments.of(type, provider);
  }

  static Stream<Arguments> wirings() {
    return Stream.of(
        wire("name", f -> f.name().name()),
        wire("first_name", f -> f.name().firstName()),
        wire("last_name", f -> f.name().lastName()),
        wire("full_name", f -> f.name().fullName()),
        wire("username", f -> f.credentials().username()),
        wire("title", f -> f.name().title()),
        wire("occupation", f -> f.job().title()),
        wire("prefix", f -> f.name().prefix()),
        wire("suffix", f -> f.name().suffix()),
        wire("password", f -> f.credentials().password()),
        wire("ssn", f -> f.idNumber().valid()),
        wire("address", f -> f.address().fullAddress()),
        wire("street_name", f -> f.address().streetName()),
        wire("street_number", f -> f.address().streetAddressNumber()),
        wire("city", f -> f.address().city()),
        wire("state", f -> f.address().state()),
        wire("country", f -> f.address().country()),
        wire("latitude", f -> f.address().latitude()),
        wire("country_code", f -> f.address().countryCode()),
        wire("time_zone", f -> f.address().timeZone()),
        wire("email", f -> f.internet().emailAddress()),
        wire("company", f -> f.company().name()),
        wire("credit_card", f -> f.finance().creditCard()),
        wire("credit_card_type", f -> f.business().creditCardType()),
        wire("random_iban", f -> f.finance().iban()),
        wire("currency", f -> f.money().currencyCode()),
        wire("price", f -> f.commerce().price()),
        wire("domain", f -> f.internet().domainName()),
        wire("url", f -> f.internet().url()),
        wire("mac_address", f -> f.internet().macAddress()),
        wire("department", f -> f.commerce().department()),
        wire("color", f -> f.color().name()),
        wire("material", f -> f.commerce().material()),
        wire("lorem_word", f -> f.lorem().word()),
        wire("lorem_sentence", f -> f.lorem().sentence()),
        wire("lorem_paragraph", f -> f.lorem().paragraph()),
        wire("uuid", f -> f.internet().uuid()));
  }

  static Stream<Arguments> wiringsPerLocale() {
    return wirings()
        .flatMap(
            w ->
                Stream.of("usa", "italy", "japan")
                    // Datafaker has no Italian name suffixes: covered by the en-US fallback test
                    // in DatafakerLocaleCoverageTest (#379).
                    .filter(geo -> !("italy".equals(geo) && "suffix".equals(w.get()[0])))
                    .map(geo -> Arguments.of(geo, w.get()[0], w.get()[1])));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("wiringsPerLocale")
  void shouldProduceExactlyTheMappedProviderOutputForLocaleAndSeed(
      String geolocation, String type, Function<Faker, Object> provider) {
    Object generated;
    try (var ctx = GeneratorContext.enter(factory, geolocation)) {
      generated = generator.generate(new Random(SEED), new CustomDatafakerType(type));
    }

    Faker reference = new Faker(LocaleMapper.map(geolocation), new Random(SEED));
    assertThat(generated).hasToString(String.valueOf(provider.apply(reference)));
  }
}
