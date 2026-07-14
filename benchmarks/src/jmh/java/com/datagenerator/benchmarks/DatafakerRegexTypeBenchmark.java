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
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.semantic.DatafakerGenerator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for config-declarable regex types — the {@code regex:} prefix in a {@code
 * --faker-types} YAML, registered through {@link DatafakerRegistry#registerRegex}.
 *
 * <p>This is a different code path from {@link DatafakerRegexpBenchmark}, which measures
 * Datafaker's own {@code Faker.regexify()} and its expression cache. {@code registerRegex} parses
 * the pattern once with RgxGen at registration and calls {@code compiled.generate(random)} per
 * value — Faker is bypassed entirely on the hot path. {@link #regexifyBaseline()} exists to
 * quantify the difference between the two, which is what justifies depending on RgxGen directly.
 *
 * <p><b>Scenarios</b> — one per pattern shape an author is likely to write:
 *
 * <ul>
 *   <li>literal prefix + fixed digits ({@code ORD-\d{8}}) — the common "sequence ID" case
 *   <li>bounded char class ({@code [A-Z0-9]{10,35}}) — variable-length ISO reference
 *   <li>alternation ({@code (INV|CRN|DBN)-[0-9]{6}}) — does branching cost extra?
 *   <li>unbounded quantifier ({@code [a-z]+}) — RgxGen caps repetition at 100; worst case
 *   <li>SEPA message id — a real pattern from the DORA/GDPR SEPA use case
 *   <li>long structured (IBAN-shaped) — realistic ISO 20022 complexity
 * </ul>
 *
 * <p>Datafaker and primitive baselines are included so the regex numbers can be placed against the
 * two families already in {@code docs/PERFORMANCE.md} without cross-run comparison.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DatafakerRegexTypeBenchmark {

  static final String PATTERN_LITERAL_DIGITS = "ORD-\\d{8}";
  static final String PATTERN_BOUNDED_CLASS = "[A-Z0-9]{10,35}";
  static final String PATTERN_ALTERNATION = "(INV|CRN|DBN)-[0-9]{6}";
  static final String PATTERN_UNBOUNDED = "[a-z]+";
  static final String PATTERN_SEPA_MSG_ID = "SEPA[0-9]{8}[A-Z0-9]{6}";
  static final String PATTERN_IBAN_LIKE = "[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}";

  private Random random;
  private DatafakerGenerator datafakerGenerator;
  private DataGeneratorFactory factory;

  private Faker faker;
  private CharGenerator charGenerator;
  private PrimitiveType charType;

  private CustomDatafakerType literalDigitsType;
  private CustomDatafakerType boundedClassType;
  private CustomDatafakerType alternationType;
  private CustomDatafakerType unboundedType;
  private CustomDatafakerType sepaMsgIdType;
  private CustomDatafakerType ibanLikeType;
  private CustomDatafakerType nameType;

  @Setup
  public void setup() {
    random = new Random(12345L);
    datafakerGenerator = new DatafakerGenerator();

    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, null);

    // Register through the shipped API — the same call CustomTypeConfigLoader makes for a
    // `regex:`-prefixed value, so this measures the production path rather than raw RgxGen.
    DatafakerRegistry.registerRegex("bench_literal_digits", PATTERN_LITERAL_DIGITS);
    DatafakerRegistry.registerRegex("bench_bounded_class", PATTERN_BOUNDED_CLASS);
    DatafakerRegistry.registerRegex("bench_alternation", PATTERN_ALTERNATION);
    DatafakerRegistry.registerRegex("bench_unbounded", PATTERN_UNBOUNDED);
    DatafakerRegistry.registerRegex("bench_sepa_msg_id", PATTERN_SEPA_MSG_ID);
    DatafakerRegistry.registerRegex("bench_iban_like", PATTERN_IBAN_LIKE);

    literalDigitsType = new CustomDatafakerType("bench_literal_digits");
    boundedClassType = new CustomDatafakerType("bench_bounded_class");
    alternationType = new CustomDatafakerType("bench_alternation");
    unboundedType = new CustomDatafakerType("bench_unbounded");
    sepaMsgIdType = new CustomDatafakerType("bench_sepa_msg_id");
    ibanLikeType = new CustomDatafakerType("bench_iban_like");
    nameType = new CustomDatafakerType("name");

    // Baselines
    faker = new Faker(Locale.ENGLISH, random);
    faker.regexify(PATTERN_BOUNDED_CLASS); // pre-warm Datafaker's cache — measure the warm path
    charGenerator = new CharGenerator();
    charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "10", "35");

    GeneratorContext.enter(factory, "en");
  }

  // ── Regex types (DatafakerRegistry.registerRegex → RgxGen) ────────────────

  @Benchmark
  public String regexLiteralPlusDigits() {
    return (String) datafakerGenerator.generate(random, literalDigitsType);
  }

  @Benchmark
  public String regexBoundedCharClass() {
    return (String) datafakerGenerator.generate(random, boundedClassType);
  }

  @Benchmark
  public String regexAlternation() {
    return (String) datafakerGenerator.generate(random, alternationType);
  }

  @Benchmark
  public String regexUnboundedCapped() {
    return (String) datafakerGenerator.generate(random, unboundedType);
  }

  @Benchmark
  public String regexSepaMsgId() {
    return (String) datafakerGenerator.generate(random, sepaMsgIdType);
  }

  @Benchmark
  public String regexLongStructured() {
    return (String) datafakerGenerator.generate(random, ibanLikeType);
  }

  // ── Baselines ─────────────────────────────────────────────────────────────

  /** Same pattern as {@link #regexBoundedCharClass()}, via Datafaker's shaded RgxGen. */
  @Benchmark
  public String regexifyBaseline() {
    return faker.regexify(PATTERN_BOUNDED_CLASS);
  }

  /** Cheapest realistic Datafaker type, for placing regex cost against the semantic family. */
  @Benchmark
  public String datafakerNameBaseline() {
    return (String) datafakerGenerator.generate(random, nameType);
  }

  /** Primitive string of comparable length — the floor a regex type is measured against. */
  @Benchmark
  public String primitiveCharBaseline() {
    return (String) charGenerator.generate(random, charType);
  }
}
