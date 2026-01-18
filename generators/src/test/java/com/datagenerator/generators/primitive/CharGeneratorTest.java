package com.datagenerator.generators.primitive;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.GeneratorException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CharGeneratorTest {
  private final CharGenerator generator = new CharGenerator();

  @Test
  void shouldGenerateStringWithinLengthRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "10");
    Random random = new Random(42L);

    for (int i = 0; i < 100; i++) {
      String value = (String) generator.generate(random, type);
      assertThat(value).hasSizeBetween(5, 10);
      assertThat(value).matches("[a-zA-Z]+"); // Only letters
    }
  }

  @Test
  void shouldGenerateDeterministicSequenceWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "5");
    Random random1 = new Random(999L);
    Random random2 = new Random(999L);

    String value1 = (String) generator.generate(random1, type);
    String value2 = (String) generator.generate(random2, type);

    assertThat(value1).isEqualTo(value2);
  }

  @Test
  void shouldGenerateDifferentStringsWithDifferentSeeds() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "10", "10");
    Random random1 = new Random(42L);
    Random random2 = new Random(99L);

    Set<String> values = new HashSet<>();
    values.add((String) generator.generate(random1, type));
    values.add((String) generator.generate(random2, type));

    assertThat(values).hasSize(2); // Different seeds → different values
  }

  @Test
  void shouldHandleMinimumLength() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "1");
    Random random = new Random();

    String value = (String) generator.generate(random, type);
    assertThat(value).hasSize(1);
  }

  @Test
  void shouldHandleZeroLength() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "0", "0");
    Random random = new Random();

    String value = (String) generator.generate(random, type);
    assertThat(value).isEmpty();
  }

  @Test
  void shouldThrowExceptionWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "10", "5");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Invalid char range");
  }

  @Test
  void shouldThrowExceptionForMissingMinValue() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, null, "10");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Missing required field: minValue");
  }

  @Test
  void shouldThrowExceptionForInvalidLengthFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "abc", "10");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("Invalid minValue");
  }

  @Test
  void shouldThrowExceptionForNegativeLength() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.CHAR, "-5", "10");
    Random random = new Random();

    assertThatThrownBy(() -> generator.generate(random, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("must be >= 0");
  }

  @Test
  void shouldSupportCharType() {
    PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10");
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "10");

    assertThat(generator.supports(charType)).isTrue();
    assertThat(generator.supports(intType)).isFalse();
  }
}
