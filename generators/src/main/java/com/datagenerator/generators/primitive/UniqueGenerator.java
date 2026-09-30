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

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;

/**
 * Generates collision-free integers for {@code unique[...]} fields.
 *
 * <p><b>Determinism:</b> the value is a pure function of (master seed, group, global record index).
 * The record index is mapped through a keyed Feistel bijection over the group's domain, then split
 * into the group's fields by mixed-radix digits. Distinct indices therefore yield distinct tuples,
 * regardless of thread count or chunking.
 *
 * <p><b>No Random draws:</b> the per-record {@link Random} is deliberately never touched.
 * Uniqueness cannot come from independent random draws (they collide), and consuming draws would
 * shift the values of every field declared after a unique field whenever it is added or removed.
 *
 * <p><b>Thread safety:</b> stateless; reads only thread-local context.
 */
public class UniqueGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof UniqueType u)) {
      throw new GeneratorException(
          "UniqueGenerator requires UniqueType, got: " + dataType.getClass().getSimpleName());
    }
    if (!u.isResolved()) {
      throw new GeneratorException(
          "Unresolved " + u.describe() + ": structure was not loaded via StructureRegistry");
    }
    long index = RecordIndex.current();
    if (index < 0) {
      throw new GeneratorException("unique requires the generation engine's record index");
    }
    if (index >= u.getDomain()) {
      throw new GeneratorException(
          "Record index "
              + index
              + " exceeds the domain of "
              + u.getGroupKey()
              + " ("
              + u.getDomain()
              + " values); widen the unique range or lower --count");
    }
    long baseKey = FeistelPermutation.mix64(GeneratorContext.getMasterSeed() ^ u.getGroupHash());
    long k = FeistelPermutation.permute(index, u.getDomain(), u.getHalfBits(), baseKey);
    return u.getMin() + (k / u.getDivisor()) % u.getSize();
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof UniqueType;
  }
}
