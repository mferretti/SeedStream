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

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.generators.composite.ArrayGenerator;
import com.datagenerator.generators.composite.ObjectGenerator;
import com.datagenerator.generators.composite.ReferenceGenerator;
import com.datagenerator.generators.primitive.BooleanGenerator;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.primitive.DateGenerator;
import com.datagenerator.generators.primitive.DecimalGenerator;
import com.datagenerator.generators.primitive.EnumGenerator;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.TimestampGenerator;
import com.datagenerator.generators.semantic.DatafakerGenerator;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

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

  // O(1) lookup maps — built once at class load / construction time
  private static final EnumMap<PrimitiveType.Kind, DataGenerator> PRIMITIVE_MAP =
      new EnumMap<>(PrimitiveType.Kind.class);
  private static final Map<Class<? extends DataType>, DataGenerator> STATELESS_TYPE_MAP =
      new HashMap<>();

  static {
    PRIMITIVE_MAP.put(PrimitiveType.Kind.CHAR, new CharGenerator());
    PRIMITIVE_MAP.put(PrimitiveType.Kind.INT, new IntegerGenerator());
    PRIMITIVE_MAP.put(PrimitiveType.Kind.DECIMAL, new DecimalGenerator());
    PRIMITIVE_MAP.put(PrimitiveType.Kind.BOOLEAN, new BooleanGenerator());
    PRIMITIVE_MAP.put(PrimitiveType.Kind.DATE, new DateGenerator());
    PRIMITIVE_MAP.put(PrimitiveType.Kind.TIMESTAMP, new TimestampGenerator());

    STATELESS_TYPE_MAP.put(EnumType.class, new EnumGenerator());
    STATELESS_TYPE_MAP.put(CustomDatafakerType.class, new DatafakerGenerator());
    STATELESS_TYPE_MAP.put(ArrayType.class, new ArrayGenerator());
    STATELESS_TYPE_MAP.put(ReferenceType.class, new ReferenceGenerator());
    // NOTE: ObjectGenerator is stateful and added per-instance in the constructor
  }

  private final Map<Class<? extends DataType>, DataGenerator> typeMap;

  /** Create factory with context for stateful generators (e.g., ObjectGenerator). */
  public DataGeneratorFactory(StructureRegistry structureRegistry, Path structuresPath) {
    this.typeMap = new HashMap<>(STATELESS_TYPE_MAP);
    this.typeMap.put(ObjectType.class, new ObjectGenerator(structureRegistry, structuresPath));
  }

  /**
   * Create a generator for the given DataType.
   *
   * @param dataType Type to generate
   * @return Matching generator
   * @throws GeneratorException if no generator supports the type
   */
  public DataGenerator create(DataType dataType) {
    if (dataType instanceof PrimitiveType pt) {
      DataGenerator gen = PRIMITIVE_MAP.get(pt.getKind());
      if (gen != null) return gen;
    } else {
      DataGenerator gen = typeMap.get(dataType.getClass());
      if (gen != null) return gen;
    }
    throw new GeneratorException("No generator found for type: " + dataType.describe());
  }

  /**
   * Check if a generator exists for the given DataType.
   *
   * @param dataType Type to check
   * @return true if a generator exists
   */
  public boolean hasGenerator(DataType dataType) {
    if (dataType instanceof PrimitiveType pt) return PRIMITIVE_MAP.containsKey(pt.getKind());
    return typeMap.containsKey(dataType.getClass());
  }
}
