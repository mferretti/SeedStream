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
import com.datagenerator.core.type.SerialType;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SerialGeneratorTest {
  private final SerialGenerator generator = new SerialGenerator();

  @AfterEach
  void cleanUp() {
    RecordIndex.clear();
  }

  private Object gen(long min, long index) {
    RecordIndex.holder()[0] = index;
    return generator.generate(new Random(1), new SerialType(min));
  }

  @Test
  void shouldStartAtOneWhenMinIsDefault() {
    assertThat(gen(1, 0)).isEqualTo(1L);
    assertThat(gen(1, 9)).isEqualTo(10L);
  }

  @Test
  void shouldSupportNegativeMin() {
    assertThat(gen(-5, 0)).isEqualTo(-5L);
    assertThat(gen(-5, 7)).isEqualTo(2L);
  }

  @Test
  void shouldThrowWhenRecordIndexIsMissing() {
    assertThatThrownBy(() -> gen(1, -1))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("record index");
  }
}
