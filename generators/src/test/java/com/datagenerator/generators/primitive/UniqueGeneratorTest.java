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
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UniqueGeneratorTest {
  private final UniqueGenerator generator = new UniqueGenerator();
  private final DataGeneratorFactory factory =
      new DataGeneratorFactory(new StructureRegistry((n, p, r) -> Map.of()), Path.of("."));

  @AfterEach
  void cleanUp() {
    RecordIndex.clear();
    GeneratorContext.exit();
  }

  private static UniqueType resolvedSingle(long min, long max) {
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("f", new UniqueType(null, min, max));
    return (UniqueType) StructureRegistry.resolveUniqueGroups("s", fields).get("f");
  }

  private long gen(UniqueType t, long index) {
    RecordIndex.holder()[0] = index;
    return (Long) generator.generate(new Random(1), t);
  }

  @Test
  void shouldProduceDistinctValuesInRangeWhenGeneratingTenThousand() {
    UniqueType t = resolvedSingle(100_000, 999_999);
    GeneratorContext.enter(factory, null, 10_000, 42L);
    Set<Long> seen = new HashSet<>();
    for (long i = 0; i < 10_000; i++) {
      long v = gen(t, i);
      assertThat(v).isBetween(100_000L, 999_999L);
      assertThat(seen.add(v)).isTrue();
    }
  }

  @Test
  void shouldCoverEveryPairExactlyOnceWhenCompositeCountEqualsDomain() {
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("a", new UniqueType("pair", 1, 200));
    fields.put("b", new UniqueType("pair", 1, 12));
    Map<String, DataType> r = StructureRegistry.resolveUniqueGroups("s", fields);
    GeneratorContext.enter(factory, null, 2400, 7L);
    Set<String> pairs = new HashSet<>();
    for (long i = 0; i < 2400; i++) {
      long a = gen((UniqueType) r.get("a"), i);
      long b = gen((UniqueType) r.get("b"), i);
      assertThat(a).isBetween(1L, 200L);
      assertThat(b).isBetween(1L, 12L);
      pairs.add(a + ":" + b);
    }
    assertThat(pairs).hasSize(2400);
  }

  @Test
  void shouldNotConsumeRandomDrawsWhenGenerating() {
    UniqueType t = resolvedSingle(1, 1000);
    GeneratorContext.enter(factory, null, 10, 1L);
    RecordIndex.holder()[0] = 3;
    Random random = new Random(99);
    generator.generate(random, t);
    // Seeded RNG needed for determinism
    Random untouched = new Random(99); // nosemgrep
    assertThat(random.longs(5).toArray()).containsExactly(untouched.longs(5).toArray());
  }

  @Test
  void shouldThrowWhenIndexAtOrBeyondDomain() {
    UniqueType t = resolvedSingle(1, 10);
    GeneratorContext.enter(factory, null, 11, 1L);
    assertThatThrownBy(() -> gen(t, 10)).isInstanceOf(GeneratorException.class);
  }

  @Test
  void shouldThrowWhenRecordIndexIsMissing() {
    UniqueType t = resolvedSingle(1, 10);
    GeneratorContext.enter(factory, null, 10, 1L);
    assertThatThrownBy(() -> gen(t, -1))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("record index");
  }

  @Test
  void shouldThrowWhenTypeIsUnresolved() {
    GeneratorContext.enter(factory, null, 10, 1L);
    assertThatThrownBy(() -> gen(new UniqueType(null, 1, 10), 0))
        .isInstanceOf(GeneratorException.class);
  }

  @Test
  void shouldBeReproducibleWhenSeedIsSameAndDifferWhenSeedDiffers() {
    UniqueType t = resolvedSingle(1, 1_000_000);
    long[] a = sequence(t, 5L);
    long[] b = sequence(t, 5L);
    long[] c = sequence(t, 6L);
    assertThat(a).containsExactly(b);
    assertThat(a).isNotEqualTo(c);
  }

  private long[] sequence(UniqueType t, long seed) {
    GeneratorContext.exit();
    GeneratorContext.enter(factory, null, 50, seed);
    long[] out = new long[50];
    for (int i = 0; i < out.length; i++) {
      out[i] = gen(t, i);
    }
    return out;
  }

  @Test
  void shouldProducePermutationOfOneToCountWhenMaxIsCount() {
    for (int count : new int[] {1, 7, 100}) {
      Map<String, DataType> fields = new LinkedHashMap<>();
      fields.put("f", new UniqueType(null, 1, 0, true, null));
      UniqueType t =
          (UniqueType) StructureRegistry.resolveUniqueGroups("s", fields, count).get("f");
      GeneratorContext.enter(factory, null, count, 42L);
      Set<Long> seen = new HashSet<>();
      for (long i = 0; i < count; i++) {
        seen.add(gen(t, i));
      }
      assertThat(seen).hasSize(count).allMatch(v -> v >= 1 && v <= count);
      GeneratorContext.exit();
    }
  }
}
