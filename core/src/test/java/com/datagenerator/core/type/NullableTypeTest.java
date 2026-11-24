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

package com.datagenerator.core.type;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.core.structure.StructureRegistry;
import org.junit.jupiter.api.Test;

/** Wrapping, hashing and description of the {@code nullable: <rate>} decorator type. */
class NullableTypeTest {

  @Test
  void shouldHashPathWhenConstructed() {
    NullableType type = new NullableType(new SerialType(1), 0.25, "user.nickname");

    assertThat(type.getPathHash()).isEqualTo(StructureRegistry.fnv1a64("user.nickname"));
  }

  @Test
  void shouldProduceSameHashWhenConstructedTwiceWithSamePath() {
    NullableType first = new NullableType(new SerialType(1), 0.5, "user.nickname");
    NullableType second = new NullableType(new SerialType(1), 0.5, "user.nickname");

    assertThat(first.getPathHash()).isEqualTo(second.getPathHash());
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldProduceDifferentHashWhenPathsDiffer() {
    NullableType a = new NullableType(new SerialType(1), 0.5, "user.nickname");
    NullableType b = new NullableType(new SerialType(1), 0.5, "user.email");

    assertThat(a.getPathHash()).isNotEqualTo(b.getPathHash());
  }

  @Test
  void shouldKeepInnerRateAndPathWhenConstructed() {
    SerialType inner = new SerialType(3);
    NullableType type = new NullableType(inner, 0.1, "t.c");

    assertThat(type.getInner()).isSameAs(inner);
    assertThat(type.getRate()).isEqualTo(0.1);
    assertThat(type.getPath()).isEqualTo("t.c");
  }

  @Test
  void shouldDescribeInnerTypeWithNullableRateWhenDescribed() {
    NullableType type = new NullableType(new SerialType(1), 0.25, "t.c");

    assertThat(type.describe()).isEqualTo("serial nullable 0.25");
  }

  @Test
  void shouldUnwrapToInnerTypeWhenUnwrapped() {
    SerialType inner = new SerialType(1);
    NullableType type = new NullableType(inner, 0.25, "t.c");

    assertThat(type.unwrap()).isSameAs(inner);
  }

  @Test
  void shouldUnwrapThroughNestedNullableWhenWrappedTwice() {
    SerialType inner = new SerialType(1);
    NullableType outer = new NullableType(new NullableType(inner, 0.1, "t.c"), 0.2, "t.c");

    assertThat(outer.unwrap()).isSameAs(inner);
  }
}
