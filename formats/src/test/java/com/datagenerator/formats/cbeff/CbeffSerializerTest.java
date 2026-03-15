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

package com.datagenerator.formats.cbeff;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CbeffSerializerTest {

  private CbeffSerializer serializer;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    serializer = new CbeffSerializer();
    mapper = new ObjectMapper();
  }

  @Test
  void shouldReturnFormatName() {
    assertThat(serializer.getFormatName()).isEqualTo("cbeff");
  }

  @Test
  void shouldIncludeRequiredEnvelopeFields() throws Exception {
    Map<String, Object> record = Map.of("name", "Alice");
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    assertThat(envelope.has("cbeff_version")).isTrue();
    assertThat(envelope.has("format_owner")).isTrue();
    assertThat(envelope.has("format_type")).isTrue();
    assertThat(envelope.has("creation_date")).isTrue();
    assertThat(envelope.has("payload")).isTrue();
  }

  @Test
  void shouldUseDefaultFormatOwnerAndType() throws Exception {
    Map<String, Object> record = Map.of("field", "value");
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    assertThat(envelope.get("format_owner").asText())
        .isEqualTo(CbeffSerializer.DEFAULT_FORMAT_OWNER);
    assertThat(envelope.get("format_type").asText()).isEqualTo(CbeffSerializer.DEFAULT_FORMAT_TYPE);
    assertThat(envelope.get("cbeff_version").asText()).isEqualTo(CbeffSerializer.CBEFF_VERSION);
  }

  @Test
  void shouldApplyCustomFormatOwnerAndType() throws Exception {
    CbeffSerializer custom = new CbeffSerializer("ACME-Corp", "19794-2-json");
    Map<String, Object> record = Map.of("field", "value");
    JsonNode envelope = mapper.readTree(custom.serialize(record));

    assertThat(envelope.get("format_owner").asText()).isEqualTo("ACME-Corp");
    assertThat(envelope.get("format_type").asText()).isEqualTo("19794-2-json");
  }

  @Test
  void shouldContainOriginalRecordUnderPayload() throws Exception {
    Map<String, Object> record = Map.of("finger_position", "right_index", "quality", 85);
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    JsonNode payload = envelope.get("payload");
    assertThat(payload.get("finger_position").asText()).isEqualTo("right_index");
    assertThat(payload.get("quality").asInt()).isEqualTo(85);
  }

  @Test
  void shouldPromoteSubjectIdToEnvelope() throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("subject_id", "SUBJ-001");
    record.put("quality", 90);
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    assertThat(envelope.get("subject_id").asText()).isEqualTo("SUBJ-001");
    // subject_id also remains in payload
    assertThat(envelope.get("payload").get("subject_id").asText()).isEqualTo("SUBJ-001");
  }

  @Test
  void shouldNotIncludeSubjectIdInEnvelopeWhenAbsentFromRecord() throws Exception {
    Map<String, Object> record = Map.of("name", "Alice");
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    assertThat(envelope.has("subject_id")).isFalse();
  }

  @Test
  void shouldProduceValidJsonForEmptyRecord() throws Exception {
    Map<String, Object> record = Map.of();
    String output = serializer.serialize(record);

    JsonNode envelope = mapper.readTree(output);
    assertThat(envelope.get("payload").isEmpty()).isTrue();
  }

  @Test
  void shouldIncludeValidIso8601CreationDate() throws Exception {
    Map<String, Object> record = Map.of("field", "value");
    JsonNode envelope = mapper.readTree(serializer.serialize(record));

    String creationDate = envelope.get("creation_date").asText();
    // ISO-8601 instant ends with Z
    assertThat(creationDate).endsWith("Z");
    assertThat(creationDate).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
  }

  @Test
  void shouldProduceValidJsonRoundTrip() throws Exception {
    Map<String, Object> record = Map.of("x", 100, "y", 200, "type", "ending");
    String output = serializer.serialize(record);

    // Must parse without error
    JsonNode envelope = mapper.readTree(output);
    assertThat(envelope.isObject()).isTrue();
    assertThat(envelope.get("payload").get("x").asInt()).isEqualTo(100);
  }
}
