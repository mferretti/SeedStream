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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.type.CycleType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.generators.GeneratorException;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CycleGeneratorTest {
  private static final List<String> VALUES = List.of("A", "B", "C");

  private final CycleGenerator generator = new CycleGenerator();
  private final Random random = new Random(1);

  @AfterEach
  void cleanUp() {
    RecordIndex.clear();
  }

  private Object gen(List<String> values, long index) {
    return gen(values, index, random);
  }

  private Object gen(List<String> values, long index, Random rng) {
    RecordIndex.holder()[0] = index;
    return generator.generate(rng, new CycleType(values));
  }

  @Test
  void shouldEmitValueAtIndexModuloSizeWhenIndexIsSet() {
    List<Object> emitted =
        LongStream.range(0, 2L * VALUES.size() + 2).mapToObj(i -> gen(VALUES, i)).toList();

    assertThat(emitted).containsExactly("A", "B", "C", "A", "B", "C", "A", "B");
  }

  @Test
  void shouldReturnStringWhenGenerating() {
    assertThat(gen(VALUES, 1)).isInstanceOf(String.class).isEqualTo("B");
  }

  @Test
  void shouldKeepDeclaredOrderWhenValuesAreNotSorted() {
    List<String> values = List.of("zeta", "alpha", "mid");

    assertThat(gen(values, 0)).isEqualTo("zeta");
    assertThat(gen(values, 1)).isEqualTo("alpha");
    assertThat(gen(values, 2)).isEqualTo("mid");
  }

  @Test
  void shouldAlwaysEmitTheValueWhenThereIsASingleValue() {
    List<String> single = List.of("x");

    assertThat(LongStream.range(0, 5).mapToObj(i -> gen(single, i))).containsOnly("x");
  }

  @Test
  void shouldNotOverflowWhenIndexIsHuge() {
    assertThat(gen(VALUES, Long.MAX_VALUE))
        .isEqualTo(VALUES.get((int) (Long.MAX_VALUE % VALUES.size())));
  }

  private static List<Number> nextDraws(Random r) {
    return List.of(r.nextLong(), r.nextInt(), r.nextLong());
  }

  @Test
  void shouldConsumeNoDrawsFromTheRandomWhenGenerating() {
    Random used = new Random(42L);
    Random untouched = new Random(42L);

    for (long i = 0; i < 10; i++) {
      gen(VALUES, i, used);
    }

    assertThat(nextDraws(used)).containsExactlyElementsOf(nextDraws(untouched));
  }

  @Test
  void shouldThrowWhenRecordIndexIsMissing() {
    assertThatThrownBy(() -> gen(VALUES, -1))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("cycle")
        .hasMessageContaining("record index");
  }

  @Test
  void shouldSupportOnlyCycleType() {
    assertThat(generator.supports(new CycleType(VALUES))).isTrue();
    assertThat(generator.supports(new EnumType(VALUES))).isFalse();
  }

  @Test
  void shouldRejectWhenTypeIsNotCycle() {
    RecordIndex.holder()[0] = 0;

    assertThatThrownBy(() -> generator.generate(random, new EnumType(VALUES)))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("CycleType");
  }
}
