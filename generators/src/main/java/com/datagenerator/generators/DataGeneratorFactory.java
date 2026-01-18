package com.datagenerator.generators;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.generators.composite.ArrayGenerator;
import com.datagenerator.generators.composite.ObjectGenerator;
import com.datagenerator.generators.primitive.BooleanGenerator;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.primitive.DateGenerator;
import com.datagenerator.generators.primitive.DecimalGenerator;
import com.datagenerator.generators.primitive.EnumGenerator;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.TimestampGenerator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating appropriate data generators based on DataType.
 *
 * <p><b>Design:</b> Registry of generators, supporting both stateless (primitives) and stateful
 * (composite) generators. Factory iterates registered generators to find matching one.
 *
 * <p><b>Performance:</b> Stateless generators are singletons. Stateful generators (ObjectGenerator)
 * are created with dependencies.
 *
 * <p><b>Extensibility:</b> Add new generators by registering in static initializer or via
 * registerGenerator().
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * // For primitive/array types
 * DataGenerator generator = DataGeneratorFactory.create(primitiveType);
 * Object value = generator.generate(random, primitiveType);
 *
 * // For object types (requires context)
 * DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);
 * DataGenerator generator = factory.create(objectType);
 * Object value = generator.generate(random, objectType);
 * </pre>
 */
public class DataGeneratorFactory {
  private static final List<DataGenerator> STATELESS_GENERATORS = new ArrayList<>();

  static {
    // Register all primitive generators (stateless singletons)
    STATELESS_GENERATORS.add(new CharGenerator());
    STATELESS_GENERATORS.add(new IntegerGenerator());
    STATELESS_GENERATORS.add(new DecimalGenerator());
    STATELESS_GENERATORS.add(new BooleanGenerator());
    STATELESS_GENERATORS.add(new DateGenerator());
    STATELESS_GENERATORS.add(new TimestampGenerator());
    STATELESS_GENERATORS.add(new EnumGenerator());

    // Register composite generators (stateless)
    STATELESS_GENERATORS.add(new ArrayGenerator());

    // NOTE: ObjectGenerator is stateful and created with context
  }

  // Instance state for stateful generators
  private final StructureRegistry structureRegistry;
  private final Path structuresPath;
  private final List<DataGenerator> allGenerators;

  /** Create factory with context for stateful generators (e.g., ObjectGenerator). */
  public DataGeneratorFactory(StructureRegistry structureRegistry, Path structuresPath) {
    this.structureRegistry = structureRegistry;
    this.structuresPath = structuresPath;

    // Combine stateless and stateful generators
    this.allGenerators = new ArrayList<>(STATELESS_GENERATORS);
    this.allGenerators.add(new ObjectGenerator(structureRegistry, structuresPath));
  }

  /**
   * Create a generator for the given DataType (instance method - supports all types).
   *
   * @param dataType Type to generate
   * @return Matching generator
   * @throws GeneratorException if no generator supports the type
   */
  public DataGenerator create(DataType dataType) {
    for (DataGenerator generator : allGenerators) {
      if (generator.supports(dataType)) {
        return generator;
      }
    }

    throw new GeneratorException("No generator found for type: " + dataType.describe());
  }

  /**
   * Static factory method for stateless generators only (primitives, arrays of primitives).
   *
   * <p><b>Note:</b> Cannot create ObjectGenerator - use instance method with context.
   *
   * @param dataType Type to generate
   * @return Matching generator
   * @throws GeneratorException if no generator supports the type
   * @deprecated Use instance method create() with context for full support
   */
  @Deprecated
  public static DataGenerator createStateless(DataType dataType) {
    for (DataGenerator generator : STATELESS_GENERATORS) {
      if (generator.supports(dataType)) {
        return generator;
      }
    }

    throw new GeneratorException("No stateless generator found for type: " + dataType.describe());
  }

  /**
   * Check if a generator exists for the given DataType (instance method).
   *
   * @param dataType Type to check
   * @return true if a generator exists
   */
  public boolean hasGenerator(DataType dataType) {
    return allGenerators.stream().anyMatch(g -> g.supports(dataType));
  }

  /**
   * Check if a stateless generator exists (static method).
   *
   * @param dataType Type to check
   * @return true if a stateless generator exists
   */
  public static boolean hasStatelessGenerator(DataType dataType) {
    return STATELESS_GENERATORS.stream().anyMatch(g -> g.supports(dataType));
  }
}
