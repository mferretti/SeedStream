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

package com.datagenerator.generators.primitive;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.GeneratorException;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DateGeneratorTest {

  private static final String DATE_MIN = "2020-01-01";
  private static final String DATE_MAX = "2025-12-31";
  private static final Random RANDOM = new Random(42L);

  private final DateGenerator generator = new DateGenerator();

  @Test
  void shouldGenerateDateWithinRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MIN, DATE_MAX);
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      LocalDate value = (LocalDate) generator.generate(random, type);
      assertThat(value).isBetween(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31));
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MIN, DATE_MAX);
    Random r1 = new Random(99L);
    Random r2 = new Random(99L);

    assertThat(generator.generate(r1, type)).isEqualTo(generator.generate(r2, type));
  }

  @Test
  void shouldReturnSameDateWhenMinEqualsMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, "2024-06-15", "2024-06-15");

    LocalDate result = (LocalDate) generator.generate(RANDOM, type);
    assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
  }

  @Test
  void shouldThrowWhenMinValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, null, DATE_MAX);

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MIN, null);

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinValueHasInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, "not-a-date", DATE_MAX);

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue")
        .hasMessageContaining("yyyy-MM-dd");
  }

  @Test
  void shouldThrowWhenMaxValueHasInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MIN, "31/12/2025");

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MAX, DATE_MIN);

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("date range");
  }

  @Test
  void shouldThrowWhenWrongKind() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");

    assertThatThrownBy(() -> generator.generate(RANDOM, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("DateGenerator");
  }

  @Test
  void shouldSupportDateKindOnly() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.DATE, DATE_MIN, DATE_MAX)))
        .isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "0", "100"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5"))).isFalse();
  }
}
