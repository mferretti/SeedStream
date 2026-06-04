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

import com.datagenerator.formats.json.JsonSerializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks targeting the datafaker expression/regexp cache layer.
 *
 * <p>Measures the performance difference between generating data with the released datafaker
 * (per-instance expression cache) versus a local build where expression caches are static and
 * shared across all Faker instances.
 *
 * <p><b>Scenarios:</b>
 *
 * <ul>
 *   <li>Regexify with warm (reused) Faker — cache already hot, both versions similar
 *   <li>Regexify with fresh Faker per invocation — static cache benefits 2.6.0-SNAPSHOT
 *   <li>Expression lookup with fresh Faker per invocation — static RECIPE_MAP benefits
 *       2.6.0-SNAPSHOT
 *   <li>Full JSON generation with complex datafaker fields including regexify
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DatafakerRegexpBenchmark {

  // Patterns used across scenarios — compile cost is the key differentiator
  private static final String PATTERN_SHORT = "[A-Z]{2}[0-9]{6}";
  private static final String PATTERN_MEDIUM = "[A-Z]{2}-[0-9]{4}-[a-z]{3}-[0-9]{2}";
  private static final String PATTERN_UUID_LIKE =
      "[A-Z0-9]{8}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{12}";

  // Expression-style (goes through resolveExpression + regexify)
  private static final String EXPRESSION_REGEXIFY = "#{regexify '[A-Z]{2}[0-9]{4}[a-z]{2}'}";
  private static final String EXPRESSION_NAME = "#{Name.firstName} #{Name.lastName}";

  private Random random;
  private Faker warmFaker;
  private JsonSerializer jsonSerializer;

  @Setup(Level.Trial)
  public void setup() {
    random = new Random(42L);
    warmFaker = new Faker(Locale.ENGLISH, random);
    jsonSerializer = new JsonSerializer();

    // Pre-warm the static cache (if present in 2.6.0-SNAPSHOT) by calling each pattern once.
    // This mirrors production: static cache is populated by the first caller.
    warmFaker.regexify(PATTERN_SHORT);
    warmFaker.regexify(PATTERN_MEDIUM);
    warmFaker.regexify(PATTERN_UUID_LIKE);
    warmFaker.expression(EXPRESSION_REGEXIFY);
    warmFaker.expression(EXPRESSION_NAME);
  }

  // ── Warm Faker (single instance reused) ──────────────────────────────────

  @Benchmark
  public String regexifyWarm_short() {
    return warmFaker.regexify(PATTERN_SHORT);
  }

  @Benchmark
  public String regexifyWarm_medium() {
    return warmFaker.regexify(PATTERN_MEDIUM);
  }

  @Benchmark
  public String regexifyWarm_uuidLike() {
    return warmFaker.regexify(PATTERN_UUID_LIKE);
  }

  @Benchmark
  public String expressionWarm_regexify() {
    return warmFaker.expression(EXPRESSION_REGEXIFY);
  }

  @Benchmark
  public String expressionWarm_name() {
    return warmFaker.expression(EXPRESSION_NAME);
  }

  // ── Fresh Faker per invocation ────────────────────────────────────────────
  // Key scenario: 2.6.0-SNAPSHOT static L1 cache avoids regex recompilation;
  // 2.5.4 recompiles on every new Faker instance.

  @Benchmark
  public String regexifyFresh_short() {
    return new Faker(Locale.ENGLISH, new Random(42L)).regexify(PATTERN_SHORT);
  }

  @Benchmark
  public String regexifyFresh_medium() {
    return new Faker(Locale.ENGLISH, new Random(42L)).regexify(PATTERN_MEDIUM);
  }

  @Benchmark
  public String regexifyFresh_uuidLike() {
    return new Faker(Locale.ENGLISH, new Random(42L)).regexify(PATTERN_UUID_LIKE);
  }

  @Benchmark
  public String expressionFresh_regexify() {
    return new Faker(Locale.ENGLISH, new Random(42L)).expression(EXPRESSION_REGEXIFY);
  }

  @Benchmark
  public String expressionFresh_name() {
    return new Faker(Locale.ENGLISH, new Random(42L)).expression(EXPRESSION_NAME);
  }

  // ── Full JSON pipeline with complex datafaker fields ──────────────────────
  // End-to-end: generate a realistic record with mixed fields (primitives +
  // datafaker names/emails + regexify codes), then serialize to JSON.

  @Benchmark
  public String jsonComplexWithRegexp_warmFaker() {
    Map<String, Object> record = buildComplexRecord(warmFaker);
    return jsonSerializer.serialize(record);
  }

  @Benchmark
  public String jsonComplexWithRegexp_freshFaker() {
    Faker fresh = new Faker(Locale.ENGLISH, new Random(42L));
    Map<String, Object> record = buildComplexRecord(fresh);
    return jsonSerializer.serialize(record);
  }

  private Map<String, Object> buildComplexRecord(Faker faker) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", random.nextInt(1_000_000));
    record.put("firstName", faker.name().firstName());
    record.put("lastName", faker.name().lastName());
    record.put("email", faker.internet().emailAddress());
    record.put("phone", faker.phoneNumber().phoneNumber());
    record.put("address", faker.address().fullAddress());
    record.put("company", faker.company().name());
    // Regexify fields: product codes, reference numbers
    record.put("productCode", faker.regexify(PATTERN_SHORT));
    record.put("orderId", faker.regexify(PATTERN_MEDIUM));
    record.put("trackingId", faker.regexify(PATTERN_UUID_LIKE));
    record.put(
        "birthDate",
        LocalDate.of(1980 + random.nextInt(40), 1 + random.nextInt(12), 1 + random.nextInt(28)));
    record.put("registeredAt", Instant.now());
    record.put("balance", new BigDecimal(String.valueOf(random.nextDouble() * 100_000)));
    record.put("active", random.nextBoolean());
    return record;
  }
}
