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
