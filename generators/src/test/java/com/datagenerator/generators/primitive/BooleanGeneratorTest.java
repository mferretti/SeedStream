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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BooleanGeneratorTest {

  private static final Random RANDOM = new Random(42L);

  private final BooleanGenerator generator = new BooleanGenerator();
  private final PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);

  @Test
  void shouldGenerateBoolean() {
    Object value = generator.generate(new Random(1L), boolType);
    assertThat(value).isInstanceOf(Boolean.class);
  }

  @Test
  void shouldGenerateBothTrueAndFalse() {
    Random random = new Random(42L);
    var values =
        IntStream.range(0, 200)
            .mapToObj(i -> (Boolean) generator.generate(random, boolType))
            .collect(Collectors.toSet());
    assertThat(values).containsExactlyInAnyOrder(true, false);
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    Random r1 = new Random(99L);
    Random r2 = new Random(99L);
    assertThat(generator.generate(r1, boolType)).isEqualTo(generator.generate(r2, boolType));
  }

  @Test
  void shouldThrowWhenWrongType() {
    PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5");
    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, charType))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("BooleanGenerator");
  }

  @Test
  void shouldSupportBooleanKindOnly() {
    assertThat(generator.supports(boolType)).isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "0", "1"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5"))).isFalse();
  }
}
