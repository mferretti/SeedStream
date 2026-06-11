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
    Map<String, Object> fieldRecord = new FieldRecord(SCHEMA);
    fieldRecord.put("id", 42);
    fieldRecord.put("name", "Milan");

    assertThat(fieldRecord).containsEntry("id", 42).containsEntry("name", "Milan");
    assertThat(fieldRecord.get("city")).isNull(); // declared but unset
    assertThat(fieldRecord.get("missing")).isNull();
  }

  @Test
  void shouldPreserveSchemaFieldOrder() {
    Map<String, Object> fieldRecord = new FieldRecord(SCHEMA);
    // Insert out of schema order; iteration must still follow schema order
    fieldRecord.put("city", "Rome");
    fieldRecord.put("id", 1);
    fieldRecord.put("name", "Jane");

    assertThat(fieldRecord.keySet()).containsExactly("id", "name", "city");
  }

  @Test
  void shouldBehaveLikeEquivalentLinkedHashMap() {
    Map<String, Object> fieldRecord = new FieldRecord(SCHEMA);
    fieldRecord.put("id", 7);
    fieldRecord.put("name", "Ada");
    fieldRecord.put("city", "Turin");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("id", 7);
    expected.put("name", "Ada");
    expected.put("city", "Turin");

    assertThat(fieldRecord)
        .isEqualTo(expected)
        .hasSize(3)
        .containsKey("name")
        .doesNotContainKey("missing");
  }

  @Test
  void shouldRejectKeysNotInSchema() {
    Map<String, Object> fieldRecord = new FieldRecord(SCHEMA);
    assertThatThrownBy(() -> fieldRecord.put("unknown", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }
}
