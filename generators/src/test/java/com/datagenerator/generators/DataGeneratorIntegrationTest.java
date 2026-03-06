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

import com.datagenerator.core.seed.RandomProvider;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Integration test demonstrating all generators working together with deterministic RandomProvider.
 */
class DataGeneratorIntegrationTest {

  @Test
  void shouldGenerateAllTypesWithDeterministicResults() {
    RandomProvider randomProvider = new RandomProvider(42L);
    Random random = randomProvider.getRandom();

    // Test char generation
    PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "10");
    DataGenerator charGen = DataGeneratorFactory.createStateless(charType);
    String charValue = (String) charGen.generate(random, charType);
    assertThat(charValue).hasSizeBetween(5, 10).matches("[a-zA-Z]+");

    // Test int generation
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "100");
    DataGenerator intGen = DataGeneratorFactory.createStateless(intType);
    int intValue = (int) intGen.generate(random, intType);
    assertThat(intValue).isBetween(1, 100);

    // Test decimal generation
    PrimitiveType decimalType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
    DataGenerator decimalGen = DataGeneratorFactory.createStateless(decimalType);
    Object decimalValue = decimalGen.generate(random, decimalType);
    assertThat(decimalValue).isNotNull();

    // Test boolean generation
    PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
    DataGenerator boolGen = DataGeneratorFactory.createStateless(boolType);
    boolean boolValue = (boolean) boolGen.generate(random, boolType);
    assertThat(boolValue).isIn(true, false);

    // Test date generation
    PrimitiveType dateType = new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
    DataGenerator dateGen = DataGeneratorFactory.createStateless(dateType);
    Object dateValue = dateGen.generate(random, dateType);
    assertThat(dateValue).isNotNull();

    // Test enum generation
    EnumType enumType = new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING"));
    DataGenerator enumGen = DataGeneratorFactory.createStateless(enumType);
    String enumValue = (String) enumGen.generate(random, enumType);
    assertThat(enumValue).isIn("ACTIVE", "INACTIVE", "PENDING");
  }

  @Test
  void shouldProduceSameValuesWithSameSeed() {
    // Run 1
    RandomProvider provider1 = new RandomProvider(999L);
    Random random1 = provider1.getRandom();
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "1000");
    DataGenerator intGen = DataGeneratorFactory.createStateless(intType);

    int value1_1 = (int) intGen.generate(random1, intType);
    int value1_2 = (int) intGen.generate(random1, intType);
    int value1_3 = (int) intGen.generate(random1, intType);

    // Run 2 with same seed
    RandomProvider provider2 = new RandomProvider(999L);
    Random random2 = provider2.getRandom();

    int value2_1 = (int) intGen.generate(random2, intType);
    int value2_2 = (int) intGen.generate(random2, intType);
    int value2_3 = (int) intGen.generate(random2, intType);

    // Same seed → same sequence
    assertThat(value1_1).isEqualTo(value2_1);
    assertThat(value1_2).isEqualTo(value2_2);
    assertThat(value1_3).isEqualTo(value2_3);
  }

  @Test
  void shouldFindGeneratorsForAllPrimitiveTypes() {
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10")))
        .isTrue();
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(PrimitiveType.Kind.INT, "1", "10")))
        .isTrue();
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(PrimitiveType.Kind.DECIMAL, "1.0", "10.0")))
        .isTrue();
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null)))
        .isTrue();
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31")))
        .isTrue();
    assertThat(
            DataGeneratorFactory.hasStatelessGenerator(
                new PrimitiveType(
                    PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z")))
        .isTrue();
    assertThat(DataGeneratorFactory.hasStatelessGenerator(new EnumType(List.of("A", "B"))))
        .isTrue();
  }
}
