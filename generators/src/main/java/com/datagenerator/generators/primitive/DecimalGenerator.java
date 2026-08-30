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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates random decimal numbers (decimal[min..max]) within specified bounds.
 *
 * <p><b>Algorithm:</b> Discrete uniform draw over the grid of representable values at the field's
 * scale. With {@code steps = (max - min) * 10^scale}, a uniform integer {@code k in [0, steps]} is
 * drawn and mapped to {@code min + k * 10^-scale}. This makes <b>both</b> {@code min} (k=0) and
 * {@code max} (k=steps) reachable, unlike a {@code nextDouble()} interpolation whose {@code [0,1)}
 * factor can never hit the upper bound.
 *
 * <p><b>Precision:</b> Returns BigDecimal with scale determined by max precision in min/max values.
 *
 * <p><b>Range:</b> Inclusive on both ends [min, max].
 */
public class DecimalGenerator implements DataGenerator {

  private record Bounds(BigDecimal min, BigDecimal range, int scale, BigInteger steps) {}

  private final Map<PrimitiveType, Bounds> boundsCache = new ConcurrentHashMap<>();

  @Override
  public Object generate(Random random, DataType dataType) {
    PrimitiveType primitiveType =
        GeneratorValidation.requirePrimitiveKind(
            dataType, PrimitiveType.Kind.DECIMAL, "DecimalGenerator");

    Bounds b = boundsCache.computeIfAbsent(primitiveType, this::parseBounds);

    BigInteger steps = b.steps();
    BigDecimal value;
    if (steps.signum() == 0) {
      // min == max: the only representable value is min itself.
      value = b.min();
    } else if (steps.bitLength() < 63) {
      // Common path: the grid fits in a long. Draw a uniform integer k in [0, steps] (inclusive)
      // so both min (k=0) and max (k=steps) are reachable, then map back to the decimal grid.
      long k = random.nextLong(steps.longValueExact() + 1);
      value = b.min().add(BigDecimal.valueOf(k).movePointLeft(b.scale()));
    } else {
      // Pathological: (max-min)*10^scale exceeds long range. Fall back to continuous interpolation
      // (upper bound then only approached, not guaranteed) rather than allocate a BigInteger draw.
      BigDecimal randomFactor = BigDecimal.valueOf(random.nextDouble());
      value = b.min().add(b.range().multiply(randomFactor));
    }

    return value.setScale(b.scale(), RoundingMode.HALF_UP);
  }

  private Bounds parseBounds(PrimitiveType primitiveType) {
    BigDecimal min = parseDecimal(primitiveType.getMinValue(), "minValue");
    BigDecimal max = parseDecimal(primitiveType.getMaxValue(), "maxValue");
    GeneratorValidation.requireValidRange(min, max, "decimal");
    int scale = Math.max(min.scale(), max.scale());
    BigDecimal range = max.subtract(min);
    // Number of discrete increments of 10^-scale between min and max (exact: range's scale <=
    // scale).
    BigInteger steps = range.movePointRight(scale).toBigIntegerExact();
    return new Bounds(min, range, scale, steps);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.DECIMAL;
  }

  private BigDecimal parseDecimal(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException(
          "Missing required field: %s for decimal type".formatted(fieldName));
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw new GeneratorException(
          "Invalid " + fieldName + " for decimal type: " + value + " (expected decimal number)", e);
    }
  }
}
