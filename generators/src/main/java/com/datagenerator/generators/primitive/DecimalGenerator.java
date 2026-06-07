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
import java.math.RoundingMode;
import java.util.Random;

/**
 * Generates random decimal numbers (decimal[min..max]) within specified bounds.
 *
 * <p><b>Algorithm:</b> Linear interpolation with random factor:
 *
 * <pre>
 * value = min + random.nextDouble() * (max - min)
 * </pre>
 *
 * <p><b>Precision:</b> Returns BigDecimal with scale determined by max precision in min/max values.
 * Rounds HALF_UP to avoid floating-point errors.
 *
 * <p><b>Range:</b> Inclusive on both ends [min, max].
 */
public class DecimalGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    PrimitiveType primitiveType =
        GeneratorValidation.requirePrimitiveKind(
            dataType, PrimitiveType.Kind.DECIMAL, "DecimalGenerator");

    // Parse min/max bounds as BigDecimal for precision
    BigDecimal min = parseDecimal(primitiveType.getMinValue(), "minValue");
    BigDecimal max = parseDecimal(primitiveType.getMaxValue(), "maxValue");

    GeneratorValidation.requireValidRange(min, max, "decimal");

    // Determine scale (decimal places) from max precision in min/max
    int scale = Math.max(min.scale(), max.scale());

    // Generate random value: min + random * (max - min)
    // nextDouble() returns [0.0, 1.0) so max is approached but not guaranteed; acceptable for
    // decimals
    BigDecimal range = max.subtract(min);
    BigDecimal randomFactor = BigDecimal.valueOf(random.nextDouble());
    BigDecimal value = min.add(range.multiply(randomFactor));

    // Round to desired scale
    return value.setScale(scale, RoundingMode.HALF_UP);
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
