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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.PrimitiveType.Kind;
import com.datagenerator.generators.composite.ArrayGenerator;
import com.datagenerator.generators.primitive.BooleanGenerator;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.primitive.DateGenerator;
import com.datagenerator.generators.primitive.DecimalGenerator;
import com.datagenerator.generators.primitive.EnumGenerator;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.TimestampGenerator;
import com.datagenerator.generators.semantic.DatafakerGenerator;
import com.datagenerator.generators.semantic.FakerCache;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One generator instance shared by 8 threads (each with its own seeded Random) must reproduce the
 * exact per-seed sequences of a single-threaded run. Each round uses a cold generator so the
 * first-touch bounds-cache race is exercised.
 */
class GeneratorConcurrencyTest {

  private static final int THREADS = 8;
  private static final int N = 300;
  private static final int ROUNDS = 25;

  private static PrimitiveType prim(Kind kind, String min, String max) {
    return new PrimitiveType(kind, min, max);
  }

  private static DataGeneratorFactory newFactory() {
    return new DataGeneratorFactory(
        new StructureRegistry((name, path, reg) -> Map.of()), Path.of("test"));
  }

  /** Same type for every thread. */
  private static Arguments shared(String name, Supplier<DataGenerator> gen, DataType type) {
    return Arguments.of(name, gen, (IntFunction<DataType>) t -> type);
  }

  static Stream<Arguments> cases() {
    return Stream.of(
        shared("int", IntegerGenerator::new, prim(Kind.INT, "-1000", "1000000")),
        shared("decimal", DecimalGenerator::new, prim(Kind.DECIMAL, "0.00", "9999.99")),
        shared("char", CharGenerator::new, prim(Kind.CHAR, "3", "20")),
        shared("date", DateGenerator::new, prim(Kind.DATE, "2000-01-01", "2030-12-31")),
        shared(
            "timestamp",
            TimestampGenerator::new,
            prim(Kind.TIMESTAMP, "2000-01-01T00:00:00", "2030-12-31T00:00:00")),
        shared("enum", EnumGenerator::new, new EnumType(List.of("A", "B", "C", "D", "E"))),
        shared("boolean", BooleanGenerator::new, prim(Kind.BOOLEAN, null, null)),
        shared("array", ArrayGenerator::new, new ArrayType(prim(Kind.INT, "1", "500"), 0, 8)),
        shared("name", DatafakerGenerator::new, new CustomDatafakerType("name")),
        shared("city", DatafakerGenerator::new, new CustomDatafakerType("city")),
        shared("email", DatafakerGenerator::new, new CustomDatafakerType("email")),
        // Different bounds per thread on ONE generator: a bounds cache keyed wrongly (or a single
        // shared slot) would hand one thread another thread's range.
        Arguments.of(
            "int-per-thread-range",
            (Supplier<DataGenerator>) IntegerGenerator::new,
            (IntFunction<DataType>) t -> prim(Kind.INT, "0", String.valueOf(10 + t * 1000))),
        Arguments.of(
            "decimal-per-thread-range",
            (Supplier<DataGenerator>) DecimalGenerator::new,
            (IntFunction<DataType>) t -> prim(Kind.DECIMAL, "0.0", t + ".5")),
        Arguments.of(
            "date-per-thread-range",
            (Supplier<DataGenerator>) DateGenerator::new,
            (IntFunction<DataType>)
                t -> prim(Kind.DATE, "2020-01-01", "2020-01-" + String.format("%02d", t + 2))),
        Arguments.of(
            "timestamp-per-thread-range",
            (Supplier<DataGenerator>) TimestampGenerator::new,
            (IntFunction<DataType>)
                t ->
                    prim(
                        Kind.TIMESTAMP,
                        "2020-01-01T00:00:00",
                        "2020-01-01T00:00:" + String.format("%02d", t + 10))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void shouldMatchSingleThreadedSequencesWhenGeneratorIsSharedAcross8Threads(
      String name, Supplier<DataGenerator> newGenerator, IntFunction<DataType> typeForThread)
      throws Exception {
    List<List<Object>> expected = new ArrayList<>();
    for (int t = 0; t < THREADS; t++) {
      expected.add(run(newGenerator.get(), typeForThread.apply(t), 1000L + t));
    }
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        DataGenerator sharedGenerator = newGenerator.get(); // cold bounds cache each round
        CyclicBarrier start = new CyclicBarrier(THREADS);
        List<Future<List<Object>>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
          int thread = t;
          futures.add(
              pool.submit(
                  () -> {
                    start.await();
                    return run(sharedGenerator, typeForThread.apply(thread), 1000L + thread);
                  }));
        }
        for (int t = 0; t < THREADS; t++) {
          assertThat(get(futures.get(t)))
              .as("%s: round %d, thread %d", name, round, t)
              .isEqualTo(expected.get(t));
        }
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static List<Object> get(Future<List<Object>> future) throws InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      throw new AssertionError("worker failed", e.getCause());
    }
  }

  private static List<Object> run(DataGenerator generator, DataType type, long seed) {
    FakerCache.clear();
    try (var ctx = GeneratorContext.enter(newFactory(), "italy")) {
      Random random = new Random(seed);
      return IntStream.range(0, N).mapToObj(i -> generator.generate(random, type)).toList();
    } finally {
      FakerCache.clear();
    }
  }
}
