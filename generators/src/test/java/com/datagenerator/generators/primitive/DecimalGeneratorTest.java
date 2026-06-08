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

  private final DecimalGenerator generator = new DecimalGenerator();
  private final Random sharedRandom = new Random(42L);

  @Test
  void shouldGenerateDecimalWithinRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      BigDecimal value = (BigDecimal) generator.generate(random, type);
      assertThat(value).isBetween(BigDecimal.ZERO, new BigDecimal("100.0"));
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
    Random r1 = new Random(55L);
    Random r2 = new Random(55L);

    assertThat(generator.generate(r1, type)).isEqualTo(generator.generate(r2, type));
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

    BigDecimal value = (BigDecimal) generator.generate(sharedRandom, type);
    assertThat(value).isEqualByComparingTo(new BigDecimal("42.5"));
  }

  @Test
  void shouldThrowWhenMinValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, null, "100.0");

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", null);

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinValueIsInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "not-a-number", "100.0");

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "abc");

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "100.0", "0.0");

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("decimal range");
  }

  @Test
  void shouldThrowWhenWrongKind() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");

    assertThatThrownBy(() -> generator.generate(new Random(), type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("DecimalGenerator");
  }

  @Test
  void shouldSupportDecimalKindOnly() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0")))
        .isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "0", "100"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5"))).isFalse();
  }
}
