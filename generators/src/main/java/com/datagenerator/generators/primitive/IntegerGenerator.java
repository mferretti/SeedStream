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
import java.util.Random;

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

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException(
          "IntegerGenerator requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (primitiveType.getKind() != PrimitiveType.Kind.INT) {
      throw new GeneratorException(
          "IntegerGenerator requires INT type, got: " + primitiveType.getKind());
    }

    // Parse min/max bounds
    int min = parseInt(primitiveType.getMinValue(), "minValue");
    int max = parseInt(primitiveType.getMaxValue(), "maxValue");

    if (min > max) {
      throw new GeneratorException(
          "Invalid int range: minValue (" + min + ") > maxValue (" + max + ")");
    }

    // Generate random integer in range [min, max]
    // Use long arithmetic to avoid overflow when (max - min + 1) doesn't fit in int
    long range = (long) max - min + 1;
    if (range <= 0 || range > Integer.MAX_VALUE) {
      // Range too large or wraps around - use fallback
      return min + (random.nextInt() & 0x7FFFFFFF) % (int) range;
    }

    return min + random.nextInt((int) range);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.INT;
  }

  private int parseInt(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException("Missing required field: " + fieldName + " for int type");
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new GeneratorException(
          "Invalid " + fieldName + " for int type: " + value + " (expected integer)", e);
    }
  }
}
