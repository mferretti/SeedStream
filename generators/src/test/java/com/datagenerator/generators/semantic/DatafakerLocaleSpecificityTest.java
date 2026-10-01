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
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Asserts output that only the requested locale can produce (not regexes that English also
 * matches): the value set must differ from en_US for the same seed, plus a locale-specific shape.
 */
class DatafakerLocaleSpecificityTest {

  private static final int N = 60;
  private static final long SEED = 4242L;
  private static final Pattern ACCENTED = Pattern.compile("[À-ÿ]");

  private final DatafakerGenerator generator = new DatafakerGenerator();
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    FakerCache.clear();
    factory =
        new DataGeneratorFactory(
            new StructureRegistry((name, path, reg) -> Map.of()), Path.of("test"));
  }

  @AfterEach
  void tearDown() {
    FakerCache.clear();
  }

  private List<String> draw(String geolocation, String type, long seed) {
    FakerCache.clear();
    CustomDatafakerType dataType = new CustomDatafakerType(type);
    Random random = new Random(seed);
    try (var ctx = GeneratorContext.enter(factory, geolocation)) {
      return IntStream.range(0, N)
          .mapToObj(i -> (String) generator.generate(random, dataType))
          .toList();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "italy, city",
    "italy, first_name",
    "germany, city",
    "germany, phone_number",
    "uk, postal_code",
    "france, first_name",
    "france, phone_number",
    "japan, first_name",
    "russia, first_name",
  })
  void shouldProduceDifferentValuesThanUsWhenGeolocationIsSet(String geolocation, String type) {
    List<String> local = draw(geolocation, type, SEED);
    List<String> us = draw("usa", type, SEED);

    assertThat(local).hasSize(N).isNotEqualTo(us);
    // Not merely a reshuffle: the majority of distinct values must be absent from the US set.
    long foreign = local.stream().distinct().filter(v -> !us.contains(v)).count();
    assertThat(foreign).isGreaterThan(local.stream().distinct().count() / 2);
  }

  @Test
  void shouldProduceFiveDigitCapWhenGeolocationIsItaly() {
    assertThat(draw("italy", "postal_code", SEED)).allMatch(v -> v.matches("\\d{5}"));
  }

  @Test
  void shouldProduceFiveDigitPlzWhenGeolocationIsGermany() {
    assertThat(draw("germany", "postal_code", SEED)).allMatch(v -> v.matches("\\d{5}"));
  }

  @Test
  void shouldProduceFiveDigitCodeWhenGeolocationIsFrance() {
    assertThat(draw("france", "postal_code", SEED)).allMatch(v -> v.matches("\\d{5}"));
  }

  @Test
  void shouldProduceUkPostcodeShapeWhenGeolocationIsUk() {
    // Outward code (letters+digit[+letter/digit]) + inward code (digit + 2 letters); US ZIPs are
    // all digits and can never match.
    assertThat(draw("uk", "postal_code", SEED))
        .allMatch(v -> v.matches("[A-Z]{1,2}\\d[A-Z\\d]? ?\\d[A-Z]{2}"));
  }

  @Test
  void shouldProduceCjkNamesWhenGeolocationIsJapan() {
    assertThat(draw("japan", "first_name", SEED))
        .allMatch(v -> v.codePoints().allMatch(c -> isJapaneseScript(c) || c == ' '));
  }

  @Test
  void shouldProduceCyrillicNamesWhenGeolocationIsRussia() {
    assertThat(draw("russia", "first_name", SEED))
        .allMatch(
            v ->
                v.codePoints()
                    .allMatch(
                        c ->
                            Character.UnicodeScript.CYRILLIC.equals(Character.UnicodeScript.of(c))
                                || c == '-'
                                || c == ' '));
  }

  @Test
  void shouldProduceAccentedNamesWhenGeolocationIsFrance() {
    // US names are plain ASCII; French first names include accented letters within N draws.
    assertThat(draw("france", "first_name", SEED)).anyMatch(v -> ACCENTED.matcher(v).find());
    assertThat(draw("usa", "first_name", SEED)).noneMatch(v -> ACCENTED.matcher(v).find());
  }

  @Test
  void shouldProduceItalianPhonePrefixWhenGeolocationIsItaly() {
    List<String> phones = draw("italy", "phone_number", SEED);
    List<String> us = draw("usa", "phone_number", SEED);

    assertThat(phones)
        .isNotEqualTo(us)
        // Italian numbers use a leading 0/3 (landline/mobile) national prefix.
        .allMatch(v -> v.replaceAll("^\\+39\\s*", "").matches("[03].*"));
  }

  @Test
  void shouldThrowWithUnsupportedGeolocationMessageWhenGeolocationIsUnknown() {
    CustomDatafakerType type = new CustomDatafakerType("city");
    Random random = new Random(SEED);

    try (var ctx = GeneratorContext.enter(factory, "atlantis")) {
      assertThatThrownBy(() -> generator.generate(random, type))
          .isInstanceOf(GeneratorException.class)
          .hasMessageContaining("Unsupported geolocation 'atlantis'");
    }
  }

  @Test
  void shouldFallBackToUsDataWhenGeolocationIsNullOrBlank() {
    List<String> us = draw("usa", "city", SEED);

    assertThat(draw(null, "city", SEED)).isEqualTo(us);
    assertThat(draw("  ", "city", SEED)).isEqualTo(us);
  }

  private static boolean isJapaneseScript(int cp) {
    Character.UnicodeScript s = Character.UnicodeScript.of(cp);
    return s == Character.UnicodeScript.HAN
        || s == Character.UnicodeScript.HIRAGANA
        || s == Character.UnicodeScript.KATAKANA
        || cp == 'ー';
  }
}
