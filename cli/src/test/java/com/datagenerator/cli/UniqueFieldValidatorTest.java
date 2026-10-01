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
import com.datagenerator.core.type.TypeParser;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UniqueFieldValidatorTest {
  private static final Map<String, Map<String, String>> STRUCTURES = new LinkedHashMap<>();

  static {
    STRUCTURES.put("acct", fields("account_no", "unique[1..500000]"));
    STRUCTURES.put(
        "pairs", fields("task_id", "unique[pair, 1..200]", "label_id", "unique[pair, 1..12]"));
    STRUCTURES.put("in_array", fields("items", "array[unique[1..10], 1..3]"));
    STRUCTURES.put("serial_in_array", fields("items", "array[serial, 1..3]"));
    STRUCTURES.put("ser", fields("id", "serial"));
    STRUCTURES.put("count_from_5", fields("id", "unique[5..count]"));
    STRUCTURES.put("child", fields("cid", "unique[1..100]"));
    STRUCTURES.put("via_array", fields("kids", "array[object[child], 1..3]"));
    STRUCTURES.put("via_object", fields("kid", "object[child]", "n", "int[1..5]"));
  }

  private static Map<String, String> fields(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  private static UniqueFieldValidator validator(long count) {
    TypeParser parser = new TypeParser();
    StructureRegistry registry =
        new StructureRegistry(
            (name, path, reg) -> {
              Map<String, com.datagenerator.core.type.DataType> out = new LinkedHashMap<>();
              STRUCTURES.get(name).forEach((f, t) -> out.put(f, parser.parse(t)));
              return out;
            },
            count);
    return new UniqueFieldValidator(registry, Path.of("."), count);
  }

  @Test
  void shouldFailWhenCountExceedsDomain() {
    UniqueFieldValidator validator = validator(1_000_000);
    assertThatThrownBy(() -> validator.validate("acct"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "unique[account_no] range 1..500000 holds 500,000 values but --count is 1,000,000."
                + " Widen the range to at least 1..1000000.");
  }

  @Test
  void shouldPassWhenCountEqualsDomain() {
    assertThatCode(() -> validator(500_000).validate("acct")).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWithGroupMessageWhenCompositeDomainTooSmall() {
    UniqueFieldValidator validator = validator(5000);
    assertThatThrownBy(() -> validator.validate("pairs"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "unique group 'pair' (task_id 1..200 × label_id 1..12) holds 2,400 combinations"
                + " but --count is 5,000.");
  }

  @Test
  void shouldFailWhenUniqueIsUnderArray() {
    UniqueFieldValidator validator = validator(1);
    assertThatThrownBy(() -> validator.validate("in_array"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'in_array.items' is inside array[...]");
  }

  @Test
  void shouldFailWhenUniqueIsInObjectNestedInArray() {
    UniqueFieldValidator validator = validator(1);
    assertThatThrownBy(() -> validator.validate("via_array"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'child.cid' is inside array[...]");
  }

  @Test
  void shouldPassWhenUniqueIsInOneToOneObject() {
    assertThatCode(() -> validator(100).validate("via_object")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectSerialUnderArray() {
    UniqueFieldValidator validator = validator(10);
    assertThatThrownBy(() -> validator.validate("serial_in_array"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serial field 'serial_in_array.items'");
  }

  @Test
  void shouldPassSerialWithAnyCount() {
    assertThatCode(() -> validator(10_000_000).validate("ser")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectCountRangeWhenMinAboveOne() {
    UniqueFieldValidator validator = validator(10);
    assertThatThrownBy(() -> validator.validate("count_from_5"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holds 6 values but --count is 10")
        .hasMessageContaining("Use 1..count");
  }
}
