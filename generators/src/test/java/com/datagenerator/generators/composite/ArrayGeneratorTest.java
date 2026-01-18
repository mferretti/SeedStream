package com.datagenerator.generators.composite;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArrayGeneratorTest {
  private ArrayGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    generator = new ArrayGenerator();
    // Create factory with mock registry (not needed for primitive arrays, but required by context)
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  /** Helper to generate with context. */
  private Object generateWithContext(ArrayType arrayType, Random random) {
    try (var ctx = GeneratorContext.enter(factory)) {
      return generator.generate(random, arrayType);
    }
  }

  @Test
  void shouldGenerateArrayWithinLengthRange() {
    // array[int[1..100], 5..10]
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"), 5, 10);
    Random random = new Random(42L);

    @SuppressWarnings("unchecked")
    List<Integer> array = (List<Integer>) generateWithContext(arrayType, random);

    assertThat(array).hasSizeBetween(5, 10);
    assertThat(array).allMatch(value -> value >= 1 && value <= 100);
  }

  @Test
  void shouldGenerateDeterministicArrayWithSameSeed() {
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"), 5, 5);
    Random random1 = new Random(999L);
    Random random2 = new Random(999L);

    @SuppressWarnings("unchecked")
    List<Integer> array1 = (List<Integer>) generateWithContext(arrayType, random1);
    @SuppressWarnings("unchecked")
    List<Integer> array2 = (List<Integer>) generateWithContext(arrayType, random2);

    assertThat(array1).isEqualTo(array2); // Same seed → same array content
  }

  @Test
  void shouldGenerateArraysOfStrings() {
    // array[char[3..10], 2..5]
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"), 2, 5);
    Random random = new Random(42L);

    @SuppressWarnings("unchecked")
    List<String> array = (List<String>) generateWithContext(arrayType, random);

    assertThat(array).hasSizeBetween(2, 5);
    assertThat(array).allMatch(str -> str.length() >= 3 && str.length() <= 10);
  }

  @Test
  void shouldGenerateArraysOfEnums() {
    // array[enum[A,B,C], 3..3]
    ArrayType arrayType =
        new ArrayType(new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING")), 3, 3);
    Random random = new Random(42L);

    @SuppressWarnings("unchecked")
    List<String> array = (List<String>) generateWithContext(arrayType, random);

    assertThat(array).hasSize(3);
    assertThat(array).allMatch(value -> List.of("ACTIVE", "INACTIVE", "PENDING").contains(value));
  }

  @Test
  void shouldGenerateNestedArrays() {
    // array[array[int[1..10], 2..3], 1..2] - nested arrays
    ArrayType innerArrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), 2, 3);
    ArrayType outerArrayType = new ArrayType(innerArrayType, 1, 2);
    Random random = new Random(42L);

    @SuppressWarnings("unchecked")
    List<List<Integer>> nestedArray =
        (List<List<Integer>>) generateWithContext(outerArrayType, random);

    assertThat(nestedArray).hasSizeBetween(1, 2);
    nestedArray.forEach(
        innerList -> {
          assertThat(innerList).hasSizeBetween(2, 3);
          assertThat(innerList).allMatch(value -> value >= 1 && value <= 10);
        });
  }

  @Test
  void shouldHandleMinimumLengthZero() {
    // array[int[1..10], 0..2] - can be empty
    ArrayType arrayType = new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), 0, 2);
    Random random = new Random(42L);

    for (int i = 0; i < 20; i++) {
      @SuppressWarnings("unchecked")
      List<Integer> array = (List<Integer>) generateWithContext(arrayType, random);
      assertThat(array).hasSizeBetween(0, 2);
    }
  }

  @Test
  void shouldHandleFixedLengthArray() {
    // array[int[1..100], 5..5] - always length 5
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"), 5, 5);
    Random random = new Random();

    @SuppressWarnings("unchecked")
    List<Integer> array = (List<Integer>) generateWithContext(arrayType, random);

    assertThat(array).hasSize(5);
  }

  @Test
  void shouldGenerateDifferentLengthsAcrossCalls() {
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), 1, 10);
    Random random = new Random(42L);
    Set<Integer> observedLengths = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      @SuppressWarnings("unchecked")
      List<Integer> array = (List<Integer>) generateWithContext(arrayType, random);
      observedLengths.add(array.size());
    }

    // With 100 samples from [1, 10], we should see multiple different lengths
    assertThat(observedLengths.size()).isGreaterThan(5);
  }

  @Test
  void shouldThrowExceptionForNegativeMinLength() {
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), -1, 5);
    Random random = new Random();

    assertThatThrownBy(() -> generateWithContext(arrayType, random))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Array length must be non-negative");
  }

  @Test
  void shouldThrowExceptionWhenMinGreaterThanMax() {
    ArrayType arrayType =
        new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), 10, 5);
    Random random = new Random();

    assertThatThrownBy(() -> generateWithContext(arrayType, random))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Invalid array length range");
  }

  @Test
  void shouldSupportArrayType() {
    ArrayType arrayType = new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"), 1, 5);
    PrimitiveType primitiveType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "10");

    assertThat(generator.supports(arrayType)).isTrue();
    assertThat(generator.supports(primitiveType)).isFalse();
  }
}
