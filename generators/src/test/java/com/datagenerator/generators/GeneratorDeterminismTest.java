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
import com.datagenerator.generators.semantic.FakerCache;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Seed determinism (same seed same sequence, different seed different sequence) and bounds. */
class GeneratorDeterminismTest {

  private static final int N = 200;

  @BeforeEach
  void setUp() {
    FakerCache.clear();
  }

  @AfterEach
  void tearDown() {
    FakerCache.clear();
  }

  static DataGeneratorFactory newFactory() {
    return new DataGeneratorFactory(
        new StructureRegistry((name, path, reg) -> Map.of()), Path.of("test"));
  }

  static List<Object> sequence(DataType type, long seed, int n) {
    FakerCache.clear();
    DataGeneratorFactory factory = newFactory();
    DataGenerator generator = factory.create(type);
    Random random = new Random(seed);
    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      return IntStream.range(0, n).mapToObj(i -> generator.generate(random, type)).toList();
    }
  }

  static PrimitiveType prim(Kind kind, String min, String max) {
    return new PrimitiveType(kind, min, max);
  }

  static Stream<Arguments> allTypes() {
    return Stream.of(
        Arguments.of("int", prim(Kind.INT, "1", "1000000")),
        Arguments.of("decimal", prim(Kind.DECIMAL, "0.00", "1000.00")),
        Arguments.of("char", prim(Kind.CHAR, "5", "15")),
        Arguments.of("date", prim(Kind.DATE, "2020-01-01", "2025-12-31")),
        Arguments.of(
            "timestamp", prim(Kind.TIMESTAMP, "2020-01-01T00:00:00", "2025-12-31T00:00:00")),
        Arguments.of("enum", new EnumType(List.of("A", "B", "C", "D", "E", "F"))),
        Arguments.of("boolean", prim(Kind.BOOLEAN, null, null)),
        Arguments.of("array", new ArrayType(prim(Kind.INT, "1", "1000"), 1, 10)),
        Arguments.of("name", new CustomDatafakerType("name")),
        Arguments.of("city", new CustomDatafakerType("city")),
        Arguments.of("email", new CustomDatafakerType("email")),
        Arguments.of("phone_number", new CustomDatafakerType("phone_number")),
        Arguments.of("address", new CustomDatafakerType("address")),
        Arguments.of("iban", new CustomDatafakerType("iban")),
        Arguments.of("uuid", new CustomDatafakerType("uuid")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allTypes")
  void shouldProduceIdenticalSequenceWhenSeedIsSame(String name, DataType type) {
    assertThat(sequence(type, 42L, N)).isEqualTo(sequence(type, 42L, N));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allTypes")
  void shouldProduceDifferentSequenceWhenSeedDiffers(String name, DataType type) {
    List<Object> a = sequence(type, 42L, N);
    List<Object> b = sequence(type, 43L, N);

    assertThat(a).isNotEqualTo(b);
    // Not a one-off collision: a good share of positions must differ.
    long differing = IntStream.range(0, N).filter(i -> !a.get(i).equals(b.get(i))).count();
    assertThat(differing).isGreaterThan(N / 4);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allTypes")
  void shouldContinueSequenceWhenRandomIsSharedAcrossCalls(String name, DataType type) {
    // 2N draws from one Random must equal first-N + next-N: no hidden per-call reseeding.
    List<Object> whole = sequence(type, 7L, 2 * N);

    assertThat(whole.subList(0, N)).isEqualTo(sequence(type, 7L, N));
    assertThat(whole.subList(0, N)).isNotEqualTo(whole.subList(N, 2 * N));
  }

  // ---------------------------------------------------------------- bounds: min == max

  static Stream<Arguments> degenerateRanges() {
    return Stream.of(
        Arguments.of(prim(Kind.INT, "7", "7"), 7),
        Arguments.of(prim(Kind.INT, "-5", "-5"), -5),
        Arguments.of(prim(Kind.DECIMAL, "1.50", "1.50"), new BigDecimal("1.50")),
        Arguments.of(
            prim(Kind.DATE, "2024-02-29", "2024-02-29"), LocalDate.of(2024, Month.FEBRUARY, 29)),
        Arguments.of(
            prim(Kind.TIMESTAMP, "2024-02-29T12:30:00", "2024-02-29T12:30:00"),
            Instant.parse("2024-02-29T12:30:00Z")),
        Arguments.of(new EnumType(List.of("ONLY")), "ONLY"));
  }

  @ParameterizedTest
  @MethodSource("degenerateRanges")
  void shouldReturnExactlyThatValueWhenMinEqualsMax(DataType type, Object expected) {
    assertThat(sequence(type, 1L, 50)).hasSize(50).containsOnly(expected);
  }

  @Test
  void shouldReturnFixedLengthStringWhenCharMinEqualsMax() {
    assertThat(sequence(prim(Kind.CHAR, "4", "4"), 1L, 50))
        .allSatisfy(v -> assertThat((String) v).hasSize(4));
  }

  @Test
  void shouldReturnExactLengthWhenArrayMinEqualsMax() {
    assertThat(sequence(new ArrayType(prim(Kind.INT, "1", "9"), 3, 3), 1L, 50))
        .allSatisfy(v -> assertThat((List<?>) v).hasSize(3));
  }

  // ---------------------------------------------------------------- bounds: both ends reached

  @Test
  void shouldHitBothEndsWhenIntRangeIsTwoWide() {
    assertThat(distinct(prim(Kind.INT, "1", "2"))).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void shouldHitBothEndsWhenIntRangeIsNegativeToZero() {
    assertThat(distinct(prim(Kind.INT, "-1", "0"))).containsExactlyInAnyOrder(-1, 0);
  }

  @Test
  void shouldHitBothEndsWhenIntRangeSitsAtIntegerLimits() {
    assertThat(distinct(prim(Kind.INT, "2147483646", "2147483647")))
        .containsExactlyInAnyOrder(2147483646, 2147483647);
    assertThat(distinct(prim(Kind.INT, "-2147483648", "-2147483647")))
        .containsExactlyInAnyOrder(-2147483648, -2147483647);
  }

  @Test
  void shouldStayInsideBoundsWhenIntRangeSpansWholeIntSpace() {
    List<Object> values = sequence(prim(Kind.INT, "-2147483648", "2147483647"), 5L, N);

    // Whole-space draws must use both signs (a broken nextLong narrowing would collapse to one)
    assertThat(values).anyMatch(v -> (Integer) v < 0).anyMatch(v -> (Integer) v > 0);
  }

  @Test
  void shouldHitBothEndsWhenDecimalRangeHasOneStep() {
    assertThat(distinct(prim(Kind.DECIMAL, "0.0", "0.1")))
        .containsExactlyInAnyOrder(new BigDecimal("0.0"), new BigDecimal("0.1"));
  }

  @Test
  void shouldHitBothEndsWhenCharLengthRangeIsOneToTwo() {
    Set<Integer> lengths = new HashSet<>();
    sequence(prim(Kind.CHAR, "1", "2"), 3L, N).forEach(v -> lengths.add(((String) v).length()));

    assertThat(lengths).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void shouldHitBothEndsWhenDateRangeIsTwoDays() {
    assertThat(distinct(prim(Kind.DATE, "2020-01-01", "2020-01-02")))
        .containsExactlyInAnyOrder(
            LocalDate.of(2020, Month.JANUARY, 1), LocalDate.of(2020, Month.JANUARY, 2));
  }

  @Test
  void shouldHitBothEndsWhenTimestampRangeIsOneSecond() {
    assertThat(distinct(prim(Kind.TIMESTAMP, "2020-01-01T00:00:00", "2020-01-01T00:00:01")))
        .containsExactlyInAnyOrder(
            Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-01T00:00:01Z"));
  }

  @Test
  void shouldHitBothValuesWhenBoolean() {
    assertThat(distinct(prim(Kind.BOOLEAN, null, null))).containsExactlyInAnyOrder(true, false);
  }

  @Test
  void shouldHitEveryValueWhenEnum() {
    assertThat(distinct(new EnumType(List.of("A", "B", "C"))))
        .containsExactlyInAnyOrder("A", "B", "C");
  }

  @Test
  void shouldHitBothLengthsWhenArrayLengthRangeIsOneToTwo() {
    Set<Integer> sizes = new HashSet<>();
    sequence(new ArrayType(prim(Kind.BOOLEAN, null, null), 1, 2), 9L, N)
        .forEach(v -> sizes.add(((List<?>) v).size()));

    assertThat(sizes).containsExactlyInAnyOrder(1, 2);
  }

  private static Set<Object> distinct(DataType type) {
    return new HashSet<>(sequence(type, 11L, N));
  }
}
