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

import com.datagenerator.core.registry.DatafakerRegistry;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Registration cost of a regex type — {@link DatafakerRegistry#registerRegex}, which parses the
 * pattern with RgxGen once and caches the compiled generator.
 *
 * <p>Reported as time-per-call rather than throughput because the number only matters as a one-off:
 * it is paid at {@code --faker-types} load, not per generated record. It exists to back that claim
 * with a measurement instead of an assertion. Compare against the per-record cost in {@link
 * DatafakerRegexTypeBenchmark} to see how few records it takes to amortise.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DatafakerRegexCompileBenchmark {

  @Benchmark
  public void compileLiteralPlusDigits() {
    DatafakerRegistry.registerRegex(
        "bench_compile_literal", DatafakerRegexTypeBenchmark.PATTERN_LITERAL_DIGITS);
  }

  @Benchmark
  public void compileBoundedCharClass() {
    DatafakerRegistry.registerRegex(
        "bench_compile_bounded", DatafakerRegexTypeBenchmark.PATTERN_BOUNDED_CLASS);
  }

  @Benchmark
  public void compileLongStructured() {
    DatafakerRegistry.registerRegex(
        "bench_compile_iban", DatafakerRegexTypeBenchmark.PATTERN_IBAN_LIKE);
  }
}
