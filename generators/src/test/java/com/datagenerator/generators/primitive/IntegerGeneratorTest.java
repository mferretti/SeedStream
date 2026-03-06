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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegerGeneratorTest {
  private final IntegerGenerator generator = new IntegerGenerator();

  @Test
  void shouldGenerateIntegerWithinRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "10", "20");
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      int value = (int) generator.generate(random, type);
      assertThat(value).isBetween(10, 20);
    }
  }

  @Test
  void shouldGenerateDeterministicSequenceWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "1", "100");
    Random random1 = new Random(999L);
    Random random2 = new Random(999L);

    int value1 = (int) generator.generate(random1, type);
    int value2 = (int) generator.generate(random2, type);

    assertThat(value1).isEqualTo(value2);
  }

  @Test
  void shouldCoverRangeWithSufficientSamples() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "1", "10");
    Random random = new Random(42L);
    Set<Integer> values = new HashSet<>();

    for (int i = 0; i < 500; i++) {
      values.add((int) generator.generate(random, type));
    }

    // With 500 samples from [1,10], we should see all 10 values
    assertThat(values).hasSizeGreaterThanOrEqualTo(10);
  }

  @Test
  void shouldHandleSingleValueRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "42", "42");
    Random random = new Random();

    int value = (int) generator.generate(random, type);
    assertThat(value).isEqualTo(42);
  }

  @Test
  void shouldHandleNegativeRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "-100", "-50");
    Random random = new Random(42L);

    for (int i = 0; i < 50; i++) {
      int value = (int) generator.generate(random, type);
      assertThat(value).isBetween(-100, -50);
    }
  }

  @Test
  void shouldHandleLargeRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "1000000");
    Random random = new Random(42L);

    for (int i = 0; i < 50; i++) {
      int value = (int) generator.generate(random, type);
      assertThat(value).isBetween(0, 1000000);
    }
  }

  @Test
  void shouldThrowExceptionWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "100", "50");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Invalid int range");
  }

  @Test
  void shouldThrowExceptionForMissingMinValue() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, null, "100");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Missing required field: minValue");
  }

  @Test
  void shouldThrowExceptionForInvalidNumberFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "abc", "100");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Invalid minValue");
  }

  @Test
  void shouldSupportIntType() {
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "10");
    PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10");

    assertThat(generator.supports(intType)).isTrue();
    assertThat(generator.supports(charType)).isFalse();
  }
}
