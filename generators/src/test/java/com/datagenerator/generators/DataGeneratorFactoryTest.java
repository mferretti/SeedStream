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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.ParentReferenceType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ReferenceType;
import com.datagenerator.core.type.SerialType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.core.type.UniqueType;
import com.datagenerator.generators.composite.ArrayGenerator;
import com.datagenerator.generators.composite.ObjectGenerator;
import com.datagenerator.generators.composite.ParentReferenceGenerator;
import com.datagenerator.generators.composite.ReferenceGenerator;
import com.datagenerator.generators.primitive.BooleanGenerator;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.primitive.DateGenerator;
import com.datagenerator.generators.primitive.DecimalGenerator;
import com.datagenerator.generators.primitive.EnumGenerator;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.SerialGenerator;
import com.datagenerator.generators.primitive.TimestampGenerator;
import com.datagenerator.generators.primitive.UniqueGenerator;
import com.datagenerator.generators.semantic.DatafakerGenerator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DataGeneratorFactoryTest {

  private static DataGeneratorFactory newFactory() {
    return new DataGeneratorFactory(
        new StructureRegistry((name, path, reg) -> Map.of()), Path.of("test"));
  }

  static Stream<Arguments> typesAndGenerators() {
    return Stream.of(
        Arguments.of(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "5"), CharGenerator.class),
        Arguments.of(new PrimitiveType(PrimitiveType.Kind.INT, "1", "5"), IntegerGenerator.class),
        Arguments.of(
            new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "1.0"), DecimalGenerator.class),
        Arguments.of(
            new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null), BooleanGenerator.class),
        Arguments.of(
            new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2020-12-31"),
            DateGenerator.class),
        Arguments.of(
            new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-1d", "now"),
            TimestampGenerator.class),
        Arguments.of(new EnumType(List.of("A", "B")), EnumGenerator.class),
        Arguments.of(new ObjectType("address"), ObjectGenerator.class),
        Arguments.of(
            new ArrayType(new PrimitiveType(PrimitiveType.Kind.INT, "1", "2"), 1, 2),
            ArrayGenerator.class),
        Arguments.of(new ReferenceType("customer", "id", 1L, 10L, false), ReferenceGenerator.class),
        Arguments.of(new ParentReferenceType("id"), ParentReferenceGenerator.class),
        Arguments.of(new UniqueType(null, 1, 10), UniqueGenerator.class),
        Arguments.of(new SerialType(1), SerialGenerator.class),
        Arguments.of(new CustomDatafakerType("city"), DatafakerGenerator.class));
  }

  @ParameterizedTest
  @MethodSource("typesAndGenerators")
  void shouldReturnExactGeneratorClassWhenTypeIsSupported(
      DataType type, Class<? extends DataGenerator> expected) {
    DataGenerator generator = newFactory().create(type);

    assertThat(generator).isExactlyInstanceOf(expected);
    assertThat(generator.supports(type)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("typesAndGenerators")
  void shouldReportGeneratorAvailableWhenTypeIsSupported(
      DataType type, Class<? extends DataGenerator> expected) {
    assertThat(newFactory().hasGenerator(type)).as(expected.getSimpleName()).isTrue();
  }

  @Test
  void shouldNotMixUpPrimitiveKindsWhenCreatingGenerators() {
    DataGeneratorFactory factory = newFactory();
    for (PrimitiveType.Kind kind : PrimitiveType.Kind.values()) {
      DataGenerator generator = factory.create(new PrimitiveType(kind, "1", "2"));
      for (PrimitiveType.Kind other : PrimitiveType.Kind.values()) {
        assertThat(generator.supports(new PrimitiveType(other, "1", "2")))
            .as("%s generator supports %s", kind, other)
            .isEqualTo(kind == other);
      }
    }
  }

  @Test
  void shouldReuseSameInstanceWhenCreatingSameKindTwice() {
    DataGeneratorFactory factory = newFactory();
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "1", "5");

    assertThat(factory.create(type)).isSameAs(factory.create(type));
    assertThat(factory.create(new EnumType(List.of("A"))))
        .isSameAs(factory.create(new EnumType(List.of("B"))));
  }

  @Test
  void shouldCreateDistinctObjectGeneratorWhenFactoriesDiffer() {
    ObjectType type = new ObjectType("x");

    assertThat(newFactory().create(type)).isNotSameAs(newFactory().create(type));
  }

  private static DataGeneratorFactory factoryWith(Map<String, Map<String, DataType>> structures) {
    return new DataGeneratorFactory(
        new StructureRegistry((name, path, reg) -> structures.get(name)), Path.of("test"));
  }

  @Test
  void shouldPassPreflightWhenEveryNestedConstraintIsValid() {
    TypeParser parser = new TypeParser();
    DataGeneratorFactory factory =
        factoryWith(
            Map.of(
                "parent",
                Map.of("kids", parser.parse("array[object[child], 1..3]")),
                "child",
                Map.of(
                    "n", parser.parse("int[1..9]"),
                    "d", parser.parse("date[2020-01-01..2020-12-31]"))));

    assertThatCode(() -> factory.preflight(new ObjectType("parent"))).doesNotThrowAnyException();
  }

  @Test
  void shouldFailPreflightWhenNestedFieldHasInvalidRange() {
    TypeParser parser = new TypeParser();
    DataGeneratorFactory factory =
        factoryWith(
            Map.of(
                "parent",
                Map.of("kids", parser.parse("array[object[child], 1..3]")),
                "child",
                Map.of("n", parser.parse("int[9..1]"))));

    assertThatThrownBy(() -> factory.preflight(new ObjectType("parent")))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("min (9) > max (1)");
  }

  @Test
  void shouldFailPreflightWhenArrayLengthRangeIsInverted() {
    DataGeneratorFactory factory = newFactory();
    // Built directly: TypeParser already rejects inverted lengths, preflight must too.
    DataType array = new ArrayType(new TypeParser().parse("int[1..2]"), 5, 1);

    assertThatThrownBy(() -> factory.preflight(array))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("array length");
  }

  @Test
  void shouldTerminatePreflightWhenStructureReferencesItself() {
    DataGeneratorFactory factory =
        factoryWith(
            Map.of(
                "node",
                Map.of(
                    "v", new TypeParser().parse("int[1..3]"),
                    "children", new TypeParser().parse("array[object[node], 0..2]"))));

    assertTimeoutPreemptively(
        Duration.ofSeconds(5), () -> factory.preflight(new ObjectType("node")));
  }
}
