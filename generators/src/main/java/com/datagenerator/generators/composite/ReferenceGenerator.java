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

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;

/**
 * Generates foreign key values by sampling a random long from an explicit ID pool range.
 *
 * <p><b>Syntax variants:</b>
 *
 * <ul>
 *   <li>{@code ref[user.id, 1..1000]} — static range [1, 1000]
 *   <li>{@code ref[user.id, 1..count]} — upper bound resolves to the current job's {@code --count}
 *       at runtime; the range automatically tracks parent table size without hardcoding
 * </ul>
 *
 * <p><b>Thread Safety:</b> Stateless; Random is passed per call.
 */
public class ReferenceGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof ReferenceType ref)) {
      throw new GeneratorException(
          "ReferenceGenerator requires ReferenceType, got: " + dataType.getClass().getSimpleName());
    }
    if (ref.getMin() == null) {
      throw new GeneratorException(
          "ref["
              + ref.getTargetStructure()
              + "."
              + ref.getTargetField()
              + "] requires an ID pool range. Use: ref["
              + ref.getTargetStructure()
              + "."
              + ref.getTargetField()
              + ", min..max] or ref["
              + ref.getTargetStructure()
              + "."
              + ref.getTargetField()
              + ", min..count]");
    }

    long min = ref.getMin();
    long max;
    if (ref.isMaxIsCount()) {
      max = GeneratorContext.getJobCount();
      if (max <= 0) {
        throw new GeneratorException(
            "ref["
                + ref.describe()
                + "] uses 'count' but no job count was set in GeneratorContext. "
                + "Ensure the engine passes count via GeneratorContext.enter(factory, geo, count).");
      }
    } else {
      if (ref.getMax() == null) {
        throw new GeneratorException(
            "ref["
                + ref.getTargetStructure()
                + "."
                + ref.getTargetField()
                + "] requires an ID pool range. Use: ref["
                + ref.getTargetStructure()
                + "."
                + ref.getTargetField()
                + ", min..max] or ref["
                + ref.getTargetStructure()
                + "."
                + ref.getTargetField()
                + ", min..count]");
      }
      max = ref.getMax();
    }

    if (min > max) {
      throw new GeneratorException(
          "ref[%s] resolved range [%d..%d] is invalid (min > max)"
              .formatted(ref.describe(), min, max));
    }

    return random.nextLong(min, max + 1);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof ReferenceType;
  }
}
