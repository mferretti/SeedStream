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

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.TypeParser;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@code serial[min..max]} yields exactly the values of {@code serial[min]} and draws no RNG. */
class SerialCeilingGeneratorTest {
  private final SerialGenerator generator = new SerialGenerator();
  private final TypeParser parser = new TypeParser();

  @AfterEach
  void cleanUp() {
    RecordIndex.clear();
  }

  private static Random rnd(long seed) {
    return new Random(seed);
  }

  private Object gen(String type, long index, Random random) {
    RecordIndex.holder()[0] = index;
    DataType parsed = parser.parse(type);
    return generator.generate(random, parsed);
  }

  @Test
  void shouldMatchPlainSerialWhenCeilingStartsAtOne() {
    for (long i = 0; i < 10; i++) {
      assertThat(gen("serial[1..10]", i, rnd(7))).isEqualTo(gen("serial", i, rnd(7)));
    }
    assertThat(gen("serial[1..10]", 9, rnd(7))).isEqualTo(10L);
  }

  @Test
  void shouldMatchSerialMinWhenCeilingHasCustomMin() {
    for (long i = 0; i <= 100; i++) {
      assertThat(gen("serial[100..200]", i, rnd(7))).isEqualTo(gen("serial[100]", i, rnd(7)));
    }
    assertThat(gen("serial[100..200]", 100, rnd(7))).isEqualTo(200L);
  }

  @Test
  void shouldSupportNegativeMinWhenCeilingPresent() {
    assertThat(gen("serial[-10..10]", 0, rnd(7))).isEqualTo(-10L);
    assertThat(gen("serial[-10..10]", 20, rnd(7))).isEqualTo(10L);
  }

  @Test
  void shouldNotDrawRandomWhenCeilingPresent() {
    Random used = rnd(99);
    gen("serial[1..10]", 3, used);
    assertThat(used.nextLong()).isEqualTo(rnd(99).nextLong());
  }
}
