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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FakerTypesTest {

  /** Known built-in keys (and aliases) resolve to their canonical registry key. */
  @ParameterizedTest
  @CsvSource({
    "email, email",
    "city, city",
    "name, name",
    "uuid, uuid",
    "country, country",
    "zip, postal_code", // alias -> canonical
    "phone, phone_number" // alias -> canonical
  })
  void canonicalResolvesKnownKey(String input, String expected) {
    assertThat(FakerTypes.canonical(input)).hasValue(expected);
  }

  /** Unknown, null, empty, or blank keys yield no canonical value. */
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "definitely_not_a_registered_type_xyz"})
  void canonicalReturnsEmptyForUnresolvableKey(String key) {
    assertThat(FakerTypes.canonical(key)).isEmpty();
  }
}
