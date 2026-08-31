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
import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DecimalGeneratorTest {

  private static final String DECIMAL_MAX = "100.0";
  private static final Random RANDOM = new Random(42L);

  private final DecimalGenerator generator = new DecimalGenerator();

  @Test
  void shouldGenerateDecimalWithinRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", DECIMAL_MAX);
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      BigDecimal value = (BigDecimal) generator.generate(random, type);
      assertThat(value).isBetween(BigDecimal.ZERO, new BigDecimal(DECIMAL_MAX));
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", DECIMAL_MAX);
    Random r1 = new Random(55L);
    Random r2 = new Random(55L);

    assertThat(generator.generate(r1, type)).isEqualTo(generator.generate(r2, type));
  }

  @Test
  void shouldReachBothInclusiveBounds() {
    // Small grid (0.0, 0.1, 0.2) so both endpoints are hit within a modest number of draws.
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "0.2");
    Random random = new Random(7L);
    BigDecimal min = new BigDecimal("0.0");
    BigDecimal max = new BigDecimal("0.2");

    boolean hitMin = false;
    boolean hitMax = false;
    for (int i = 0; i < 500; i++) {
      BigDecimal value = (BigDecimal) generator.generate(random, type);
      assertThat(value).isBetween(min, max);
      if (value.compareTo(min) == 0) {
        hitMin = true;
      }
      if (value.compareTo(max) == 0) {
        hitMax = true;
      }
    }

    assertThat(hitMax).as("inclusive max (0.2) must be reachable").isTrue();
    assertThat(hitMin).as("inclusive min (0.0) must be reachable").isTrue();
  }

  @Test
  void shouldPreserveScaleFromMaxPrecision() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.00", "100.000");
    Random random = new Random(42L);

    BigDecimal value = (BigDecimal) generator.generate(random, type);
    // scale = max(scale("0.00")==2, scale("100.000")==3) = 3
    assertThat(value.scale()).isEqualTo(3);
  }

  @Test
  void shouldHandleNegativeRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "-50.5", "-10.5");
    Random random = new Random(42L);

    for (int i = 0; i < 50; i++) {
      BigDecimal value = (BigDecimal) generator.generate(random, type);
      assertThat(value).isBetween(new BigDecimal("-50.5"), new BigDecimal("-10.5"));
    }
  }

  @Test
  void shouldHandleSingleValueRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "42.5", "42.5");

    BigDecimal value = (BigDecimal) generator.generate(RANDOM, type);
    assertThat(value).isEqualByComparingTo(new BigDecimal("42.5"));
  }

  @Test
  void shouldThrowWhenMinValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, null, DECIMAL_MAX);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", null);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinValueIsInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "not-a-number", DECIMAL_MAX);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "abc");

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, DECIMAL_MAX, "0.0");

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("decimal range");
  }

  @Test
  void shouldThrowWhenWrongKind() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("DecimalGenerator");
  }

  @Test
  void shouldGenerateWithinRangeWhenGridExceedsLongRange() {
    // scale=18 over [0, 100] gives steps = 100 * 10^18 = 1e20, whose bitLength (~67) exceeds the
    // 63-bit threshold, forcing the continuous-interpolation fallback (DecimalGenerator.java:72)
    // instead of the common long-draw path.
    PrimitiveType type =
        new PrimitiveType(
            PrimitiveType.Kind.DECIMAL, "0.000000000000000000", "100.000000000000000000");
    Random random = new Random(42L);
    BigDecimal min = new BigDecimal("0.000000000000000000");
    BigDecimal max = new BigDecimal("100.000000000000000000");

    for (int i = 0; i < 200; i++) {
      BigDecimal value = (BigDecimal) generator.generate(random, type);
      assertThat(value).isBetween(min, max);
      assertThat(value.scale()).isEqualTo(18);
    }
  }

  @Test
  void shouldSupportDecimalKindOnly() {
    assertThat(
            generator.supports(new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", DECIMAL_MAX)))
        .isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "0", "100"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5"))).isFalse();
  }
}
