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
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.semantic.DatafakerGenerator;
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
 * Benchmarks for Datafaker semantic generators. Measures realistic data generation performance
 * (names, addresses, emails, etc.).
 *
 * <p><b>Expected Performance:</b> ~7K ops/sec based on initial findings (significantly lower than
 * primitives due to Datafaker overhead)
 *
 * <p><b>Scenarios:</b> name, email, phone, address, city, company
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DatafakerGeneratorsBenchmark {

  private Random random;
  private DatafakerGenerator datafakerGenerator;
  private DataGeneratorFactory factory;

  private CustomDatafakerType nameType;
  private CustomDatafakerType emailType;
  private CustomDatafakerType phoneType;
  private CustomDatafakerType addressType;
  private CustomDatafakerType cityType;
  private CustomDatafakerType companyType;

  @Setup
  public void setup() {
    random = new Random(12345L);
    datafakerGenerator = new DatafakerGenerator();

    // Create registry with empty loader (not needed for Datafaker)
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, null);

    // Initialize types
    nameType = new CustomDatafakerType("name");
    emailType = new CustomDatafakerType("email");
    phoneType = new CustomDatafakerType("phonenumber");
    addressType = new CustomDatafakerType("address");
    cityType = new CustomDatafakerType("city");
    companyType = new CustomDatafakerType("company");

    // Initialize GeneratorContext with English locale
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
  public String benchmarkNameGeneration() {
    return (String) datafakerGenerator.generate(random, nameType);
  }

  @Benchmark
  public String benchmarkEmailGeneration() {
    return (String) datafakerGenerator.generate(random, emailType);
  }

  @Benchmark
  public String benchmarkPhoneGeneration() {
    return (String) datafakerGenerator.generate(random, phoneType);
  }

  @Benchmark
  public String benchmarkAddressGeneration() {
    return (String) datafakerGenerator.generate(random, addressType);
  }

  @Benchmark
  public String benchmarkCityGeneration() {
    return (String) datafakerGenerator.generate(random, cityType);
  }

  @Benchmark
  public String benchmarkCompanyGeneration() {
    return (String) datafakerGenerator.generate(random, companyType);
  }
}
