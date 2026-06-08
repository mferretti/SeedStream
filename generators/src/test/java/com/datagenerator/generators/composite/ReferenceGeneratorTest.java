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

package com.datagenerator.generators.composite;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReferenceGeneratorTest {
  private ReferenceGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new ReferenceGenerator();
  }

  @Test
  void shouldSupportReferenceType() {
    assertThat(generator.supports(new ReferenceType("user", "id", 1L, 1000L, false))).isTrue();
  }

  @Test
  void shouldNotSupportOtherTypes() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"))).isFalse();
  }

  @Test
  void shouldGenerateValueWithinStaticRange() {
    ReferenceType type = new ReferenceType("user", "id", 1L, 1000L, false);
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      long value = (long) generator.generate(random, type);
      assertThat(value).isBetween(1L, 1000L);
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    ReferenceType type = new ReferenceType("order", "user_id", 1L, 500L, false);

    long value1 = (long) generator.generate(new Random(99L), type);
    long value2 = (long) generator.generate(new Random(99L), type);

    assertThat(value1).isEqualTo(value2);
  }

  @Test
  void shouldProduceDifferentValuesWithDifferentSeeds() {
    ReferenceType type = new ReferenceType("user", "id", 1L, 1_000_000L, false);

    long value1 = (long) generator.generate(new Random(1L), type);
    long value2 = (long) generator.generate(new Random(2L), type);

    assertThat(value1).isNotEqualTo(value2);
  }

  @Test
  void shouldIncludeBoundaryValues() {
    ReferenceType type = new ReferenceType("user", "id", 5L, 5L, false);
    long value = (long) generator.generate(new Random(42L), type);

    assertThat(value).isEqualTo(5L);
  }

  @Test
  void shouldFailWhenNoRangeDefined() {
    ReferenceType type = new ReferenceType("user", "id", null, null, false);

    var rnd = new Random(42L);
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("min..max");
  }

  @Test
  void shouldFailForWrongType() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "1", "10");

    var rnd = new Random(42L);
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("ReferenceType");
  }

  @Test
  void shouldResolveCountFromContext() {
    ReferenceType type = new ReferenceType("customer", "id", 1L, null, true);
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    DataGeneratorFactory factory = new DataGeneratorFactory(registry, Paths.get("test"));

    try (var ctx = GeneratorContext.enter(factory, null, 200L)) {
      Random random = new Random(42L);
      for (int i = 0; i < 50; i++) {
        long value = (long) generator.generate(random, type);
        assertThat(value).isBetween(1L, 200L);
      }
    }
  }

  @Test
  void shouldFailWhenMaxIsCountButNoJobCountSet() {
    ReferenceType type = new ReferenceType("customer", "id", 1L, null, true);
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    DataGeneratorFactory factory = new DataGeneratorFactory(registry, Paths.get("test"));

    // enter with jobCount=0 (default)
    try (var ctx = GeneratorContext.enter(factory, null)) {
      var rnd = new Random(42L);
      assertThatThrownBy(() -> generator.generate(rnd, type))
          .isInstanceOf(GeneratorException.class)
          .hasMessageContaining("no job count was set");
    }
  }
}
