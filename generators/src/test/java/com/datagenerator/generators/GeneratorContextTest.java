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
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratorContextTest {

  private static DataGeneratorFactory newFactory() {
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    return new DataGeneratorFactory(registry, Paths.get("test"));
  }

  @Test
  void shouldNotBeActiveBeforeEnter() {
    assertThat(GeneratorContext.isActive()).isFalse();
  }

  @Test
  void shouldBeActiveAfterEnter() {
    try (var ctx = GeneratorContext.enter(newFactory(), "us")) {
      assertThat(GeneratorContext.isActive()).isTrue();
    }
  }

  @Test
  void shouldNotBeActiveAfterClose() {
    GeneratorContext ctx = GeneratorContext.enter(newFactory(), "us");
    ctx.close();

    assertThat(GeneratorContext.isActive()).isFalse();
  }

  @Test
  void shouldReturnFactoryFromContext() {
    DataGeneratorFactory factory = newFactory();

    try (var ctx = GeneratorContext.enter(factory, "us")) {
      assertThat(GeneratorContext.getFactory()).isSameAs(factory);
    }
  }

  @Test
  void shouldReturnGeolocationFromContext() {
    try (var ctx = GeneratorContext.enter(newFactory(), "italy")) {
      assertThat(GeneratorContext.getGeolocation()).isEqualTo("italy");
    }
  }

  @Test
  void shouldReturnNullGeolocationWhenNotSet() {
    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      assertThat(GeneratorContext.getGeolocation()).isNull();
    }
  }

  @Test
  void shouldReturnJobCountFromContext() {
    try (var ctx = GeneratorContext.enter(newFactory(), "us", 1000L)) {
      assertThat(GeneratorContext.getJobCount()).isEqualTo(1000L);
    }
  }

  @Test
  void shouldReturnZeroJobCountWhenUsingTwoArgEnter() {
    try (var ctx = GeneratorContext.enter(newFactory(), "us")) {
      assertThat(GeneratorContext.getJobCount()).isZero();
    }
  }

  @Test
  void shouldThrowWhenEnteringContextTwice() {
    try (var ctx = GeneratorContext.enter(newFactory(), "us")) {
      var factory = newFactory();
      assertThatThrownBy(() -> GeneratorContext.enter(factory, "uk"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already active");
    }
  }

  @Test
  void shouldThrowGetFactoryWhenNoContextActive() {
    assertThat(GeneratorContext.isActive()).isFalse();

    assertThatThrownBy(GeneratorContext::getFactory)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No GeneratorContext active");
  }

  @Test
  void shouldAllowReenterAfterClose() {
    DataGeneratorFactory f1 = newFactory();
    DataGeneratorFactory f2 = newFactory();

    try (var ctx1 = GeneratorContext.enter(f1, "us")) {
      assertThat(GeneratorContext.getFactory()).isSameAs(f1);
    }

    try (var ctx2 = GeneratorContext.enter(f2, "uk")) {
      assertThat(GeneratorContext.getFactory()).isSameAs(f2);
    }
  }

  @Test
  void shouldPeekNullWhenParentStackIsEmpty() {
    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      assertThat(GeneratorContext.peekParentRecord()).isNull();
    }
  }

  @Test
  void shouldPeekPushedParentRecord() {
    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      Map<String, Object> data = Map.of("id", 1);
      GeneratorContext.pushParentRecord(data);
      assertThat(GeneratorContext.peekParentRecord()).isSameAs(data);
      GeneratorContext.popParentRecord();
    }
  }

  @Test
  void shouldSupportNestedPushPop() {
    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      Map<String, Object> outer = Map.of("id", 1);
      Map<String, Object> inner = Map.of("id", 2);
      GeneratorContext.pushParentRecord(outer);
      GeneratorContext.pushParentRecord(inner);

      assertThat(GeneratorContext.peekParentRecord()).isSameAs(inner);
      GeneratorContext.popParentRecord();
      assertThat(GeneratorContext.peekParentRecord()).isSameAs(outer);
      GeneratorContext.popParentRecord();
      assertThat(GeneratorContext.peekParentRecord()).isNull();
    }
  }

  @Test
  void shouldClearParentStackOnContextClose() {
    Map<String, Object> data = Map.of("id", 99);

    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      GeneratorContext.pushParentRecord(data);
    }

    // Fresh context should start with an empty stack
    try (var ctx = GeneratorContext.enter(newFactory(), null)) {
      assertThat(GeneratorContext.peekParentRecord()).isNull();
    }
  }
}
