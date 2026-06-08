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

package com.datagenerator.generators.composite;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ParentReferenceType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParentReferenceGeneratorTest {

  private ParentReferenceGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    generator = new ParentReferenceGenerator();
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  @Test
  void shouldSupportParentReferenceType() {
    assertThat(generator.supports(new ParentReferenceType("id"))).isTrue();
  }

  @Test
  void shouldNotSupportOtherTypes() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"))).isFalse();
  }

  @Test
  void shouldReturnIntegerParentFieldValue() {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      GeneratorContext.pushParentRecord(Map.of("id", 42));
      try {
        Object result = generator.generate(new Random(1L), new ParentReferenceType("id"));
        assertThat(result).isEqualTo(42);
      } finally {
        GeneratorContext.popParentRecord();
      }
    }
  }

  @Test
  void shouldReturnStringParentFieldValue() {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      GeneratorContext.pushParentRecord(Map.of("code", "X100"));
      try {
        Object result = generator.generate(new Random(1L), new ParentReferenceType("code"));
        assertThat(result).isEqualTo("X100");
      } finally {
        GeneratorContext.popParentRecord();
      }
    }
  }

  @Test
  void shouldReadFromInnermostStackEntry() {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      GeneratorContext.pushParentRecord(Map.of("id", 1));
      GeneratorContext.pushParentRecord(Map.of("id", 99));
      try {
        Object result = generator.generate(new Random(1L), new ParentReferenceType("id"));
        assertThat(result).isEqualTo(99);
      } finally {
        GeneratorContext.popParentRecord();
        GeneratorContext.popParentRecord();
      }
    }
  }

  @Test
  void shouldThrowWhenStackIsEmpty() {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      var rnd = new Random(1L);
      var refType = new ParentReferenceType("id");
      assertThatThrownBy(() -> generator.generate(rnd, refType))
          .isInstanceOf(GeneratorException.class)
          .hasMessageContaining("ref[parent.id]");
    }
  }

  @Test
  void shouldThrowWhenFieldMissingFromParentRecord() {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      GeneratorContext.pushParentRecord(Map.of("name", "Alice"));
      try {
        var rnd = new Random(1L);
        var refType = new ParentReferenceType("id");
        assertThatThrownBy(() -> generator.generate(rnd, refType))
            .isInstanceOf(GeneratorException.class)
            .hasMessageContaining("id");
      } finally {
        GeneratorContext.popParentRecord();
      }
    }
  }

  @Test
  void shouldThrowWhenNoContextActive() {
    var rnd = new Random(1L);
    var refType = new ParentReferenceType("id");
    assertThatThrownBy(() -> generator.generate(rnd, refType))
        .isInstanceOf(GeneratorException.class);
  }
}
