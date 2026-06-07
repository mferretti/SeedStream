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

import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratorValidationTest {

  // ── requirePrimitiveKind ────────────────────────────────────────────────────

  @Test
  void shouldReturnPrimitiveTypeWhenKindMatches() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");

    PrimitiveType result =
        GeneratorValidation.requirePrimitiveKind(type, PrimitiveType.Kind.INT, "TestGenerator");

    assertThat(result).isSameAs(type);
  }

  @Test
  void shouldThrowWhenKindDoesNotMatch() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.INT, "0", "100");

    assertThatThrownBy(
            () ->
                GeneratorValidation.requirePrimitiveKind(
                    type, PrimitiveType.Kind.BOOLEAN, "BooleanGenerator"))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("BooleanGenerator")
        .hasMessageContaining("BOOLEAN")
        .hasMessageContaining("INT");
  }

  @Test
  void shouldThrowWhenNotPrimitiveType() {
    EnumType enumType = new EnumType(List.of("A", "B"));

    assertThatThrownBy(
            () ->
                GeneratorValidation.requirePrimitiveKind(
                    enumType, PrimitiveType.Kind.INT, "IntegerGenerator"))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("IntegerGenerator")
        .hasMessageContaining("PrimitiveType");
  }

  // ── requireValidRange ───────────────────────────────────────────────────────

  @Test
  void shouldPassWhenMinEqualsMax() {
    assertThatCode(() -> GeneratorValidation.requireValidRange(5, 5, "int"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenMinLessThanMax() {
    assertThatCode(() -> GeneratorValidation.requireValidRange(1, 100, "int"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldThrowWhenMinGreaterThanMax() {
    assertThatThrownBy(() -> GeneratorValidation.requireValidRange(100, 1, "int"))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("int range")
        .hasMessageContaining("100")
        .hasMessageContaining("1");
  }

  @Test
  void shouldThrowWithCorrectTypeNameInMessage() {
    assertThatThrownBy(() -> GeneratorValidation.requireValidRange(10.0, 5.0, "decimal"))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("decimal range");
  }
}
