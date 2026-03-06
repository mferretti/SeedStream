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
 * Generates random boolean values.
 *
 * <p><b>Algorithm:</b> Uses Random.nextBoolean() for uniform distribution (50% true, 50% false).
 *
 * <p><b>Future Enhancement:</b> Support weighted distributions (e.g., 70% true, 30% false).
 */
public class BooleanGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException(
          "BooleanGenerator requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (primitiveType.getKind() != PrimitiveType.Kind.BOOLEAN) {
      throw new GeneratorException(
          "BooleanGenerator requires BOOLEAN type, got: " + primitiveType.getKind());
    }

    return random.nextBoolean();
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.BOOLEAN;
  }
}
