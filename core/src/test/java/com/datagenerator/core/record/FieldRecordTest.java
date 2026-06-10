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

package com.datagenerator.core.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldRecordTest {

  private static final RecordSchema SCHEMA = new RecordSchema(new String[] {"id", "name", "city"});

  @Test
  void shouldStoreAndRetrieveValuesByKey() {
    Map<String, Object> record = new FieldRecord(SCHEMA);
    record.put("id", 42);
    record.put("name", "Milan");

    assertThat(record.get("id")).isEqualTo(42);
    assertThat(record.get("name")).isEqualTo("Milan");
    assertThat(record.get("city")).isNull(); // declared but unset
    assertThat(record.get("missing")).isNull();
  }

  @Test
  void shouldPreserveSchemaFieldOrder() {
    Map<String, Object> record = new FieldRecord(SCHEMA);
    // Insert out of schema order; iteration must still follow schema order
    record.put("city", "Rome");
    record.put("id", 1);
    record.put("name", "Jane");

    assertThat(record.keySet()).containsExactly("id", "name", "city");
  }

  @Test
  void shouldBehaveLikeEquivalentLinkedHashMap() {
    Map<String, Object> record = new FieldRecord(SCHEMA);
    record.put("id", 7);
    record.put("name", "Ada");
    record.put("city", "Turin");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("id", 7);
    expected.put("name", "Ada");
    expected.put("city", "Turin");

    assertThat(record).isEqualTo(expected);
    assertThat(record).hasSize(3);
    assertThat(record.containsKey("name")).isTrue();
    assertThat(record.containsKey("missing")).isFalse();
  }

  @Test
  void shouldRejectKeysNotInSchema() {
    Map<String, Object> record = new FieldRecord(SCHEMA);
    assertThatThrownBy(() -> record.put("unknown", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }
}
