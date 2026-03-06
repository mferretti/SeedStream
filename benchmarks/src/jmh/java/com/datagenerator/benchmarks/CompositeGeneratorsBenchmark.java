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

package com.datagenerator.benchmarks;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.composite.ArrayGenerator;
import com.datagenerator.generators.composite.ObjectGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for composite generators (Object and Array). Measures nested structure generation
 * performance.
 *
 * <p><b>Expected Performance:</b> Lower than primitives due to recursive generation and object
 * allocation overhead
 *
 * <p><b>Scenarios:</b> Simple object (3 fields), nested object, small array, large array
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CompositeGeneratorsBenchmark {

  private Random random;
  private ArrayGenerator arrayGenerator;
  private ObjectGenerator objectGenerator;
  private DataGeneratorFactory factory;
  private StructureRegistry registry;

  private ArrayType smallArrayType;
  private ArrayType largeArrayType;
  private ObjectType simpleObjectType;

  @Setup
  public void setup() {
    random = new Random(12345L);
    arrayGenerator = new ArrayGenerator();

    // Register a simple structure for testing
    Map<String, DataType> simpleStructure = new LinkedHashMap<>();
    simpleStructure.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "999999"));
    simpleStructure.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "15"));
    simpleStructure.put("active", new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));

    // Create registry with mock loader that returns our simple structure
    registry = new StructureRegistry((name, path, reg) -> simpleStructure);
    factory = new DataGeneratorFactory(registry, null);

    objectGenerator = new ObjectGenerator(registry, null);

    // Initialize types
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "100");
    smallArrayType = new ArrayType(intType, 5, 10);
    largeArrayType = new ArrayType(intType, 50, 100);
    simpleObjectType = new ObjectType("simple");

    // Initialize GeneratorContext
    GeneratorContext.enter(factory, "en");
  }

  @TearDown
  public void tearDown() {
    if (GeneratorContext.isActive()) {
      try (var ctx = GeneratorContext.enter(factory, "en")) {
        // Context will auto-close
      } catch (Exception e) {
        // Already closed
      }
    }
  }

  @Benchmark
  public List<Object> benchmarkSmallArray() {
    return (List<Object>) arrayGenerator.generate(random, smallArrayType);
  }

  @Benchmark
  public List<Object> benchmarkLargeArray() {
    return (List<Object>) arrayGenerator.generate(random, largeArrayType);
  }

  @Benchmark
  public Map<String, Object> benchmarkSimpleObject() {
    return (Map<String, Object>) objectGenerator.generate(random, simpleObjectType);
  }
}
