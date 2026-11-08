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

package com.datagenerator.cli;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.TypeParser;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Startup ceiling check for {@code serial[min..max]} (#440), next to the unique domain check. */
class SerialCeilingValidatorTest {
  private static final Map<String, Map<String, String>> STRUCTURES = new LinkedHashMap<>();

  static {
    STRUCTURES.put("small", Map.of("id", "serial[1..32767]"));
    STRUCTURES.put("ten", Map.of("id", "serial[1..10]"));
    STRUCTURES.put("single", Map.of("id", "serial[5..5]"));
    STRUCTURES.put("neg", Map.of("id", "serial[-10..10]"));
    STRUCTURES.put("full", Map.of("id", "serial[-9223372036854775808..9223372036854775807]"));
    STRUCTURES.put("zero_max", Map.of("id", "serial[0..9223372036854775807]"));
    STRUCTURES.put("open", Map.of("id", "serial[100]"));
    STRUCTURES.put("near_max", Map.of("id", "serial[9223372036854775800..9223372036854775806]"));
    STRUCTURES.put("child", Map.of("cid", "serial[1..100]"));
    STRUCTURES.put("via_object", Map.of("kid", "object[child]"));
    STRUCTURES.put("via_array", Map.of("kids", "array[object[child], 1..3]"));
    STRUCTURES.put("in_array", Map.of("items", "array[serial[1..50], 1..3]"));
  }

  private static UniqueFieldValidator validator(long count) {
    TypeParser parser = new TypeParser();
    StructureRegistry registry =
        new StructureRegistry(
            (name, path, reg) -> {
              Map<String, DataType> out = new LinkedHashMap<>();
              STRUCTURES.get(name).forEach((f, t) -> out.put(f, parser.parse(t)));
              return out;
            },
            count);
    return new UniqueFieldValidator(registry, Path.of("."), count);
  }

  @Test
  void shouldRejectWithFullMessageWhenCountExceedsCeiling() {
    UniqueFieldValidator validator = validator(40_000);
    assertThatThrownBy(() -> validator.validate("small"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "field 'id': serial[1..32767] holds 32,767 values but --count is 40,000");
  }

  /** The suggested range must not wrap past Long.MAX_VALUE into a negative number. */
  @Test
  void shouldSuggestARangeWithoutOverflowNearLongMax() {
    assertThatThrownBy(() -> validator(10).validate("near_max"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holds 7 values but --count is 10")
        .hasMessageContaining("9223372036854775809")
        .hasMessageNotContaining("..-");
  }

  @Test
  void shouldAcceptWhenCountEqualsCeiling() {
    assertThatCode(() -> validator(32_767).validate("small")).doesNotThrowAnyException();
    assertThatCode(() -> validator(10).validate("ten")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectWhenCountIsOneAboveCeiling() {
    assertThatThrownBy(() -> validator(32_768).validate("small"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial[1..32767] holds 32,767 values but --count is 32,768");
    assertThatThrownBy(() -> validator(11).validate("ten"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial[1..10] holds 10 values but --count is 11");
  }

  @Test
  void shouldWorkWhenRangeHasSingleValue() {
    assertThatCode(() -> validator(1).validate("single")).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(2).validate("single"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial[5..5] holds 1 values but --count is 2");
  }

  @Test
  void shouldCountBothEndsWhenMinIsNegative() {
    assertThatCode(() -> validator(21).validate("neg")).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(22).validate("neg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial[-10..10] holds 21 values but --count is 22");
  }

  @Test
  void shouldNotOverflowWhenRangeSpansWholeLongDomain() {
    assertThatCode(() -> validator(5).validate("full")).doesNotThrowAnyException();
    assertThatCode(() -> validator(1_000_000).validate("full")).doesNotThrowAnyException();
    assertThatCode(() -> validator(Long.MAX_VALUE).validate("full")).doesNotThrowAnyException();
  }

  @Test
  void shouldNotOverflowWhenMaxIsLongMax() {
    assertThatCode(() -> validator(5).validate("zero_max")).doesNotThrowAnyException();
    assertThatCode(() -> validator(1_000_000).validate("zero_max")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectWhenCeilingIsInOneToOneObject() {
    assertThatCode(() -> validator(100).validate("via_object")).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(101).validate("via_object"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cid")
        .hasMessageContaining("serial[1..100] holds 100 values but --count is 101");
  }

  @Test
  void shouldRejectCeilingUnderArray() {
    assertThatThrownBy(() -> validator(1).validate("in_array"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial field 'in_array.items' is inside array[...]");
    assertThatThrownBy(() -> validator(1).validate("via_array"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial field 'child.cid' is inside array[...]");
  }

  @Test
  void guardShouldPassSerialWithoutCeilingForAnyCount() {
    assertThatCode(() -> validator(10_000_000).validate("open")).doesNotThrowAnyException();
  }
}
