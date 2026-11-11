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
import com.datagenerator.core.type.NullableType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.primitive.FeistelPermutation;
import java.util.Random;

/**
 * Generates a {@code nullable: <rate>} field: the inner value, or null with probability {@code
 * rate}.
 *
 * <p><b>No drift:</b> the inner value is always generated first, consuming exactly the draws it
 * consumes without {@code nullable}. The null decision never touches the shared {@link Random}: it
 * is a pure function of (master seed, field path, record index, occurrence of the path within the
 * record), so every other value, thread count and prefix of a run is unchanged.
 *
 * <p><b>Thread safety:</b> stateless; reads only thread-local context.
 */
public class NullableGenerator implements DataGenerator {
  private static final long GOLDEN = 0x9E3779B97F4A7C15L;

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof NullableType n)) {
      throw new GeneratorException(
          "NullableGenerator requires NullableType, got: " + dataType.getClass().getSimpleName());
    }
    DataType inner = n.getInner();
    Object value = GeneratorContext.getFactory().create(inner).generate(random, inner);
    return isNull(n) ? null : value;
  }

  private static boolean isNull(NullableType n) {
    if (n.getRate() >= 1.0) {
      return true;
    }
    long index = RecordIndex.current();
    long occurrence = GeneratorContext.nextOccurrence(n.getPath(), index);
    long h =
        FeistelPermutation.mix64(
            GeneratorContext.getMasterSeed()
                ^ n.getPathHash()
                ^ FeistelPermutation.mix64(index + occurrence * GOLDEN));
    return (h >>> 11) * 0x1.0p-53 < n.getRate();
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof NullableType;
  }
}
