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

package com.datagenerator.generators;

import com.datagenerator.core.type.DataType;
import java.util.Random;

/**
 * Base interface for all data generators. Each generator produces values for a specific DataType
 * using a Random instance for deterministic generation.
 *
 * <p><b>Design Principles:</b>
 *
 * <ul>
 *   <li><b>Deterministic:</b> Same Random state → same generated value
 *   <li><b>Thread-safe:</b> Implementations must be thread-safe (Random is passed per call)
 *   <li><b>Type-specific:</b> Each generator handles one DataType variant
 *   <li><b>Stateless:</b> No mutable state (all context in Random and DataType)
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * DataGenerator generator = DataGeneratorFactory.create(dataType);
 * Random random = randomProvider.getRandom();
 * Object value = generator.generate(random, dataType);
 * </pre>
 *
 * @see com.datagenerator.core.seed.RandomProvider
 */
public interface DataGenerator {
  /**
   * Generate a value for the given data type using the provided Random instance.
   *
   * @param random Random instance for deterministic generation
   * @param dataType Type definition with constraints (ranges, values, etc.)
   * @return Generated value (type depends on DataType: String, Integer, Double, LocalDate, etc.)
   */
  Object generate(Random random, DataType dataType);

  /**
   * Check if this generator supports the given DataType.
   *
   * @param dataType Type to check
   * @return true if this generator can handle the type
   */
  boolean supports(DataType dataType);
}
