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

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.SelfReferenceType;
import com.datagenerator.core.type.SerialType;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.primitive.FeistelPermutation;
import com.datagenerator.generators.primitive.UniqueGenerator;
import java.util.Random;

/**
 * Generates {@code ref[t.c]} inside {@code t}: record {@code i} gets the key of a uniformly random
 * earlier record {@code j} in {@code [0, i)}; record 0 gets its own key (a {@code nullable} field
 * turns it into NULL, see {@link NullableGenerator}).
 *
 * <p><b>Determinism:</b> {@code j} is a pure hash of (master seed, field path, {@code i}), and the
 * key of record {@code j} is recomputed rather than looked up, so no state is kept and the record's
 * shared {@link Random} is never touched: adding or removing this field shifts no other field, and
 * the output is independent of thread count and prefix-stable.
 *
 * <p><b>Ordering:</b> a referenced row must be written before the referencing one; the engine's
 * single writer guarantees record order.
 */
public class SelfReferenceGenerator implements DataGenerator {
  private static final long GOLDEN = 0x9E3779B97F4A7C15L;

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof SelfReferenceType s)) {
      throw new GeneratorException(
          "SelfReferenceGenerator requires SelfReferenceType, got: "
              + dataType.getClass().getSimpleName());
    }
    long i = RecordIndex.current();
    if (i < 0) {
      throw new GeneratorException("self-reference requires the generation engine's record index");
    }
    if (i == 0) {
      return keyAt(s, 0);
    }
    long h =
        FeistelPermutation.mix64(
            GeneratorContext.getMasterSeed()
                ^ s.getPathHash()
                ^ FeistelPermutation.mix64(i * GOLDEN));
    return keyAt(s, Math.unsignedMultiplyHigh(h, i)); // uniform in [0, i)
  }

  private static long keyAt(SelfReferenceType s, long index) {
    return switch (s.getKeyType()) {
      case SerialType k -> Math.addExact(k.getMin(), index);
      case UniqueType k -> UniqueGenerator.valueAt(k, index);
      case null, default ->
          throw new GeneratorException(s.describe() + ": key is not serial or unique");
    };
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof SelfReferenceType;
  }
}
