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

package com.datagenerator.core.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.core.exception.TypeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parsing of {@code serial[min..max]} (#440); the pre-existing forms must be unchanged. */
class SerialCeilingParserTest {
  private final TypeParser parser = new TypeParser();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "serial[1..32767]",
        "serial[-10..10]",
        "serial[100..200]",
        "serial[5..5]",
        "serial[1..2147483647]",
        "serial[-9223372036854775808..9223372036854775807]",
        "serial[0..9223372036854775807]"
      })
  void shouldDescribeRoundTripWhenSerialHasCeiling(String text) {
    DataType type = parser.parse(text);

    assertThat(type).isInstanceOf(SerialType.class);
    assertThat(type.describe()).isEqualTo(text);
  }

  @Test
  void shouldKeepCeilingWhenMinIsOne() {
    // serial[1] collapses to "serial", but serial[1..10] must not lose its ceiling.
    assertThat(parser.parse("serial[1..10]").describe()).isEqualTo("serial[1..10]");
  }

  @Test
  void shouldNotEqualPlainSerialWhenCeilingPresent() {
    assertThat(parser.parse("serial[1..10]")).isNotEqualTo(parser.parse("serial"));
    assertThat(parser.parse("serial[100..200]")).isNotEqualTo(parser.parse("serial[100]"));
  }

  @Test
  void shouldAcceptSurroundingWhitespaceWhenSerialHasCeiling() {
    // Same as unique[min..max]: the whole string is trimmed.
    assertThat(parser.parse("  serial[1..10]  ").describe()).isEqualTo("serial[1..10]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"serial[ 1..10]", "serial[1 ..10]", "serial[1.. 10]", "serial[1..10 ]"})
  void guardShouldRejectWhitespaceInsideCeilingWhenUniqueWouldToo(String text) {
    assertThatThrownBy(() -> parser.parse(text)).isInstanceOf(TypeParseException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "serial[10..1]",
        "serial[a..5]",
        "serial[1..b]",
        "serial[1.5..9]",
        "serial[1..count]",
        "serial[..5]",
        "serial[1..]",
        "serial[1....5]",
        "serial[1..2..3]",
        "serial[99999999999999999999..5]",
        "serial[1..9223372036854775808]",
        "serial[-9223372036854775809..5]"
      })
  void shouldRejectWithSerialMessageWhenCeilingIsInvalid(String text) {
    assertThatThrownBy(() -> parser.parse(text))
        .isExactlyInstanceOf(TypeParseException.class)
        .hasMessageContaining("Invalid serial")
        .hasMessageContaining(text);
  }

  @Test
  void guardShouldKeepExistingSerialFormsUnchanged() {
    assertThat(parser.parse("serial")).isEqualTo(new SerialType(1));
    assertThat(parser.parse("serial[100]")).isEqualTo(new SerialType(100));
    assertThat(parser.parse("serial[-5]")).isEqualTo(new SerialType(-5));
    assertThat(parser.parse("serial").describe()).isEqualTo("serial");
    assertThat(parser.parse("serial[100]").describe()).isEqualTo("serial[100]");
    assertThat(parser.parse("serial[1]").describe()).isEqualTo("serial");
    assertThat(parser.parse("serial[-10]").describe()).isEqualTo("serial[-10]");
  }
}
