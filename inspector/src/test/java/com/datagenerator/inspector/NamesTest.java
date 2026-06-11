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

package com.datagenerator.inspector;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NamesTest {

  // --- tokenize() ---

  @Test
  void tokenizeCamelCase() {
    assertThat(Names.tokenize("firstName")).containsExactly("first", "name");
  }

  @Test
  void tokenizePascalCase() {
    assertThat(Names.tokenize("LineItem")).containsExactly("line", "item");
  }

  @Test
  void tokenizeSnakeCase() {
    assertThat(Names.tokenize("postal_code")).containsExactly("postal", "code");
  }

  @Test
  void tokenizeKebabCase() {
    assertThat(Names.tokenize("postal-code")).containsExactly("postal", "code");
  }

  @Test
  void tokenizeMixedCamelAndUnderscore() {
    assertThat(Names.tokenize("street_Address")).containsExactly("street", "address");
  }

  @Test
  void tokenizeAcronymWord() {
    // A trailing capitalized word splits off the acronym run: HTTPSClient -> ["https", "client"]
    assertThat(Names.tokenize("HTTPSClient")).containsExactly("https", "client");
  }

  @Test
  void tokenizeAlreadyLowercase() {
    assertThat(Names.tokenize("email")).containsExactly("email");
  }

  @Test
  void tokenizeEmptyStringReturnsEmptyList() {
    assertThat(Names.tokenize("")).isEmpty();
  }

  @Test
  void tokenizeBlankStringReturnsEmptyList() {
    assertThat(Names.tokenize("   ")).isEmpty();
  }

  @Test
  void tokenizeNullReturnsEmptyList() {
    assertThat(Names.tokenize(null)).isEmpty();
  }

  @Test
  void tokenizeMultipleSeparatorsCollapsed() {
    assertThat(Names.tokenize("first__last")).containsExactly("first", "last");
  }

  // --- toSnakeCase() ---

  @Test
  void toSnakeCaseFromCamelCase() {
    assertThat(Names.toSnakeCase("firstName")).isEqualTo("first_name");
  }

  @Test
  void toSnakeCaseFromPascalCase() {
    assertThat(Names.toSnakeCase("LineItem")).isEqualTo("line_item");
  }

  @Test
  void toSnakeCaseFromKebabCase() {
    assertThat(Names.toSnakeCase("postal-code")).isEqualTo("postal_code");
  }

  @Test
  void toSnakeCaseAlreadySnake() {
    assertThat(Names.toSnakeCase("postal_code")).isEqualTo("postal_code");
  }

  @Test
  void toSnakeCaseNullReturnsNull() {
    assertThat(Names.toSnakeCase(null)).isNull();
  }

  @Test
  void toSnakeCaseBlankReturnsBlank() {
    assertThat(Names.toSnakeCase("   ")).isEqualTo("   ");
  }
}
