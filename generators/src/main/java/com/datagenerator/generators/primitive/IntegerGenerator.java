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

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import com.datagenerator.generators.GeneratorValidation;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates random integers (int[min..max]) within specified bounds.
 *
 * <p><b>Algorithm:</b> Uses Random.nextInt() with range transformation to avoid bias:
 *
 * <pre>
 * value = min + random.nextInt(max - min + 1)
 * </pre>
 *
 * <p><b>Range:</b> Inclusive on both ends [min, max].
 *
 * <p><b>Performance:</b> Constant time, no allocations.
 */
public class IntegerGenerator implements DataGenerator {

  private record Bounds(int min, int max) {}

  private final Map<PrimitiveType, Bounds> boundsCache = new ConcurrentHashMap<>();

  @Override
  public Object generate(Random random, DataType dataType) {
    PrimitiveType primitiveType =
        GeneratorValidation.requirePrimitiveKind(
            dataType, PrimitiveType.Kind.INT, "IntegerGenerator");

    Bounds b = boundsCache.computeIfAbsent(primitiveType, this::parseBounds);

    // Generate random integer in range [min, max]
    // Use long arithmetic to avoid overflow when (max - min + 1) doesn't fit in int
    long range = (long) b.max() - b.min() + 1;
    if (range <= 0 || range > Integer.MAX_VALUE) {
      // Range exceeds int capacity — rejection sampling for uniform distribution
      int result;
      long limit = (long) Integer.MAX_VALUE + 1 - ((long) Integer.MAX_VALUE + 1) % range;
      do {
        result = random.nextInt() & Integer.MAX_VALUE;
      } while (result >= limit);
      return b.min() + (int) (result % range);
    }

    return b.min() + random.nextInt((int) range);
  }

  private Bounds parseBounds(PrimitiveType primitiveType) {
    int min = parseInt(primitiveType.getMinValue(), "minValue");
    int max = parseInt(primitiveType.getMaxValue(), "maxValue");
    GeneratorValidation.requireValidRange(min, max, "int");
    return new Bounds(min, max);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.INT;
  }

  private int parseInt(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException("Missing required field: %s for int type".formatted(fieldName));
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new GeneratorException(
          "Invalid " + fieldName + " for int type: " + value + " (expected integer)", e);
    }
  }
}
