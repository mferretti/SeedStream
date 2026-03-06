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

import com.datagenerator.core.type.EnumType;
import com.datagenerator.generators.GeneratorException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnumGeneratorTest {
  private final EnumGenerator generator = new EnumGenerator();

  @Test
  void shouldGenerateValueFromEnumList() {
    EnumType type = new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING"));
    Random random = new Random();

    String value = (String) generator.generate(random, type);
    assertThat(value).isIn("ACTIVE", "INACTIVE", "PENDING");
  }

  @Test
  void shouldGenerateDeterministicSequenceWithSameSeed() {
    EnumType type = new EnumType(List.of("A", "B", "C", "D", "E"));
    Random random1 = new Random(42L);
    Random random2 = new Random(42L);

    String value1 = (String) generator.generate(random1, type);
    String value2 = (String) generator.generate(random2, type);

    assertThat(value1).isEqualTo(value2);
  }

  @Test
  void shouldCoverAllValuesWithSufficientSamples() {
    EnumType type = new EnumType(List.of("RED", "GREEN", "BLUE"));
    Random random = new Random(42L);
    Set<String> values = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      values.add((String) generator.generate(random, type));
    }

    // With 100 samples from 3 values, we should see all 3
    assertThat(values).containsExactlyInAnyOrder("RED", "GREEN", "BLUE");
  }

  @Test
  void shouldHandleSingleValueEnum() {
    EnumType type = new EnumType(List.of("ONLY_VALUE"));
    Random random = new Random();

    String value = (String) generator.generate(random, type);
    assertThat(value).isEqualTo("ONLY_VALUE");
  }

  @Test
  void shouldThrowExceptionForEmptyEnum() {
    EnumType type = new EnumType(List.of());
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("EnumType has no values");
  }

  @Test
  void shouldSupportEnumType() {
    EnumType enumType = new EnumType(List.of("A", "B"));

    assertThat(generator.supports(enumType)).isTrue();
  }
}
