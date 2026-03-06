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

import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates variable-length arrays with elements of a specified inner type.
 *
 * <p><b>Algorithm:</b>
 *
 * <ol>
 *   <li>Generate random array length in [minLength, maxLength]
 *   <li>Get generator for inner type via factory (recursive delegation)
 *   <li>Generate N elements using inner type generator
 *   <li>Return as List
 * </ol>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * ArrayType type = new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"), 5, 10);
 * ArrayGenerator generator = new ArrayGenerator();
 * List result = (List) generator.generate(random, type);
 * // Returns List with 5-10 integer elements
 * </pre>
 *
 * <p><b>Thread Safety:</b> Thread-safe (stateless, Random passed as parameter).
 */
public class ArrayGenerator implements DataGenerator {
  /** Constructs a new ArrayGenerator instance. */
  public ArrayGenerator() {
    // Default constructor
  }

  /**
   * Generates a variable-length array with elements of the specified inner type.
   *
   * @param random Random instance for deterministic generation
   * @param dataType Array type definition (must be ArrayType)
   * @return List of generated elements
   * @throws GeneratorException if dataType is not ArrayType or generation fails
   */
  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof ArrayType arrayType)) {
      throw new GeneratorException(
          "ArrayGenerator requires ArrayType, got: " + dataType.getClass().getSimpleName());
    }

    // Generate array length in [minLength, maxLength]
    int minLength = arrayType.getMinLength();
    int maxLength = arrayType.getMaxLength();

    if (minLength < 0 || maxLength < 0) {
      throw new GeneratorException(
          "Array length must be non-negative: [%d, %d]".formatted(minLength, maxLength));
    }

    if (minLength > maxLength) {
      throw new GeneratorException(
          "Invalid array length range: minLength (%d) > maxLength (%d)"
              .formatted(minLength, maxLength));
    }

    int length = minLength + random.nextInt(maxLength - minLength + 1);

    // Get generator for element type (recursive delegation)
    DataType elementType = arrayType.getElementType();
    DataGenerator elementGenerator = GeneratorContext.getFactory().create(elementType);

    // Generate N elements
    List<Object> elements = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      elements.add(elementGenerator.generate(random, elementType));
    }

    return elements;
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof ArrayType;
  }
}
