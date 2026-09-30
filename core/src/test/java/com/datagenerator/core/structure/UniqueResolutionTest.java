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

package com.datagenerator.core.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.core.exception.TypeParseException;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.UniqueType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UniqueResolutionTest {

  private static UniqueType u(String group, long min, long max) {
    return new UniqueType(group, min, max);
  }

  @Test
  void shouldComputeLayoutWhenTwoFieldGroup() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("task_id", u("pair", 1, 200));
    in.put("other", new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));
    in.put("label_id", u("pair", 1, 12));

    Map<String, DataType> out = StructureRegistry.resolveUniqueGroups("s", in);

    assertThat(out.keySet()).containsExactly("task_id", "other", "label_id");
    UniqueType a = (UniqueType) out.get("task_id");
    UniqueType b = (UniqueType) out.get("label_id");
    assertThat(a.isResolved()).isTrue();
    assertThat(a.getGroupKey()).isEqualTo("s.pair");
    assertThat(a.getDomain()).isEqualTo(2400L);
    assertThat(b.getDomain()).isEqualTo(2400L);
    // Divisors are computed in field-name order: label_id < task_id
    // label_id (first): divisor = 2400 / 12 = 200
    // task_id (second): divisor = 200 / 200 = 1
    assertThat(a.getDivisor()).isEqualTo(1L);
    assertThat(b.getDivisor()).isEqualTo(200L);
    assertThat(a.getSize()).isEqualTo(200L);
    assertThat(b.getSize()).isEqualTo(12L);
    assertThat(a.getHalfBits()).isEqualTo(6); // 4^6 = 4096 >= 2400
    assertThat(a.getGroupHash()).isEqualTo(StructureRegistry.fnv1a64("s.pair"));
  }

  @Test
  void shouldUseFieldNameWhenImplicitGroup() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("a", u(null, 1, 10));
    in.put("b", u(null, 5, 8));

    Map<String, DataType> out = StructureRegistry.resolveUniqueGroups("s", in);

    UniqueType a = (UniqueType) out.get("a");
    UniqueType b = (UniqueType) out.get("b");
    assertThat(a.getGroupKey()).isEqualTo("s.a");
    assertThat(b.getGroupKey()).isEqualTo("s.b");
    assertThat(a.getDomain()).isEqualTo(10L);
    assertThat(b.getDomain()).isEqualTo(4L);
    assertThat(a.getDivisor()).isEqualTo(1L);
  }

  @Test
  void shouldThrowWhenDomainOverflows() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("a", u("g", 0, Long.MAX_VALUE / 2));
    in.put("b", u("g", 0, 10));
    assertThatThrownBy(() -> StructureRegistry.resolveUniqueGroups("s", in))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("too large");
  }

  @Test
  void shouldComputeHalfBitsWhenVariousDomains() {
    assertThat(StructureRegistry.halfBits(1)).isEqualTo(1);
    assertThat(StructureRegistry.halfBits(4)).isEqualTo(1);
    assertThat(StructureRegistry.halfBits(5)).isEqualTo(2);
    assertThat(StructureRegistry.halfBits(1_000_000)).isEqualTo(10);
    assertThat(StructureRegistry.halfBits(1_048_577)).isEqualTo(11);
    assertThat(StructureRegistry.halfBits(Long.MAX_VALUE)).isEqualTo(32);
  }

  @Test
  void shouldHashStablyWhenFnv1a() {
    assertThat(StructureRegistry.fnv1a64("")).isEqualTo(0xcbf29ce484222325L);
    assertThat(StructureRegistry.fnv1a64("a")).isEqualTo(0xaf63dc4c8601ec8cL);
  }

  @Test
  void shouldComputeIdenticalDivisorsRegardlessOfMapIterationOrder() {
    // Create the same unique group in different insertion orders:
    // 1. LinkedHashMap: task_id, label_id (declaration order)
    // 2. HashMap: can have arbitrary iteration order
    // Both should produce identical divisors when resolved (field-name order dominates).
    Map<String, DataType> in1 = new LinkedHashMap<>();
    in1.put("task_id", u("pair", 1, 200));
    in1.put("label_id", u("pair", 1, 12));

    Map<String, DataType> in2 = new HashMap<>();
    in2.put("label_id", u("pair", 1, 12));
    in2.put("task_id", u("pair", 1, 200));

    Map<String, DataType> out1 = StructureRegistry.resolveUniqueGroups("s", in1);
    Map<String, DataType> out2 = StructureRegistry.resolveUniqueGroups("s", in2);

    UniqueType a1 = (UniqueType) out1.get("task_id");
    UniqueType b1 = (UniqueType) out1.get("label_id");
    UniqueType a2 = (UniqueType) out2.get("task_id");
    UniqueType b2 = (UniqueType) out2.get("label_id");

    // Both maps must produce identical divisors (field-name order ensures this)
    assertThat(a1.getDivisor()).isEqualTo(a2.getDivisor()).isEqualTo(1L);
    assertThat(b1.getDivisor()).isEqualTo(b2.getDivisor()).isEqualTo(200L);
  }

  @Test
  void shouldResolveCountRangeWhenJobCountKnown() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("f", new UniqueType(null, 1, 0, true, null));
    UniqueType r = (UniqueType) StructureRegistry.resolveUniqueGroups("s", in, 7).get("f");
    assertThat(r.getSize()).isEqualTo(7L);
    assertThat(r.getDomain()).isEqualTo(7L);
    assertThat(r.getMax()).isEqualTo(7L);
  }

  @Test
  void shouldThrowWhenCountRangeAndJobCountUnknown() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("f", new UniqueType(null, 1, 0, true, null));
    assertThatThrownBy(() -> StructureRegistry.resolveUniqueGroups("s", in))
        .isInstanceOf(TypeParseException.class)
        .hasMessageContaining("needs the job --count");
  }

  @Test
  void shouldThrowWhenJobCountBelowCountRangeMin() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("f", new UniqueType(null, 10, 0, true, null));
    assertThatThrownBy(() -> StructureRegistry.resolveUniqueGroups("s", in, 5))
        .isInstanceOf(TypeParseException.class);
  }

  @Test
  void shouldMultiplyFixedAndCountMembersWhenMixedGroup() {
    Map<String, DataType> in = new LinkedHashMap<>();
    in.put("a", new UniqueType("g", 1, 10));
    in.put("b", new UniqueType("g", 1, 0, true, null));
    Map<String, DataType> out = StructureRegistry.resolveUniqueGroups("s", in, 5);
    assertThat(((UniqueType) out.get("a")).getDomain()).isEqualTo(50L);
    assertThat(((UniqueType) out.get("a")).getDivisor()).isEqualTo(5L);
    assertThat(((UniqueType) out.get("b")).getDivisor()).isEqualTo(1L);
  }
}
