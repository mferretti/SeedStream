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

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.SerialType;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.SerialGenerator;
import com.datagenerator.generators.primitive.UniqueGenerator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for UniqueGenerator. Validates performance of collision-free unique field generation
 * using Feistel-permutation-based mapping across varying domain sizes.
 *
 * <p><b>Target:</b> Each generator should achieve >10M ops/sec (100ns per operation)
 *
 * <p><b>Scenarios:</b> Domain sizes ranging from power-of-four (best case) to large primes (worst
 * case, requiring cycle-walking).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class UniqueGeneratorBenchmark {

  @Param({"4096", "2400", "1000000", "4097", "1048577"})
  public long domain;

  private Random random;
  private UniqueGenerator generator;
  private UniqueType resolvedType;
  private long[] idx;
  private long counter;
  private IntegerGenerator intGenerator;
  private PrimitiveType intType;
  private SerialGenerator serialGenerator;
  private SerialType serialType;

  @Setup
  public void setup() {
    random = new Random(12345L);
    generator = new UniqueGenerator();

    // Build a resolved UniqueType for a single-field group with range 1..domain
    Map<String, DataType> fields =
        StructureRegistry.resolveUniqueGroups(
            "bench", Map.of("id", new UniqueType(null, 1, domain)));
    resolvedType = (UniqueType) fields.get("id");

    // Enter GeneratorContext with master seed
    GeneratorContext.enter(null, null, domain, 12345L);

    // Get the RecordIndex holder
    idx = RecordIndex.holder();
    counter = 0;

    // Baseline: IntegerGenerator for comparison
    intGenerator = new IntegerGenerator();
    intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", String.valueOf(domain));

    serialGenerator = new SerialGenerator();
    serialType = new SerialType(1L);
  }

  @Benchmark
  public Object benchmarkUniqueGenerator(Blackhole blackhole) {
    idx[0] = counter;
    if (++counter == domain) {
      counter = 0;
    }
    Object result = generator.generate(random, resolvedType);
    blackhole.consume(result);
    return result;
  }

  @Benchmark
  public Integer integerBaseline(Blackhole blackhole) {
    Integer result = (Integer) intGenerator.generate(random, intType);
    blackhole.consume(result);
    return result;
  }

  @Benchmark
  public Object serialGenerator(Blackhole blackhole) {
    idx[0] = counter;
    if (++counter == domain) {
      counter = 0;
    }
    Object result = serialGenerator.generate(random, serialType);
    blackhole.consume(result);
    return result;
  }

  @TearDown
  public void tearDown() {
    GeneratorContext.exit();
    RecordIndex.clear();
  }
}
