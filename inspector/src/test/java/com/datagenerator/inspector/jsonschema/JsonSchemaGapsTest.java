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

package com.datagenerator.inspector.jsonschema;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonSchemaGapsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{ \"oneOf\": [] }",
        "{ \"anyOf\": [] }",
        "{ \"allOf\": [] }",
        "{ \"if\": {}, \"then\": {} }",
        "{ \"patternProperties\": {} }",
        "{ \"additionalProperties\": true }",
        "{ \"const\": 5 }",
        "{ \"not\": {} }",
        "{ \"type\": \"array\", \"items\": [ { \"type\": \"string\" } ] }",
        "{ \"$ref\": \"other.json#/$defs/Foo\" }",
        "{ \"$ref\": \"https://example.com/x.json\" }"
      })
  void shouldFlagUnsupportedConstructs(String json) throws Exception {
    assertThat(JsonSchemaGaps.unsupported(node(json), "current")).isPresent();
  }

  @Test
  void shouldFlagRecursiveLocalRef() throws Exception {
    Optional<String> reason =
        JsonSchemaGaps.unsupported(node("{ \"$ref\": \"#/$defs/Tree\" }"), "tree");
    assertThat(reason).isPresent();
    assertThat(reason.get()).contains("recursive");
  }

  @Test
  void shouldNotFlagPlainLocalRef() throws Exception {
    // a non-recursive local $ref is mapped normally by SchemaTypeMapper, not a gap
    assertThat(JsonSchemaGaps.unsupported(node("{ \"$ref\": \"#/$defs/LineItem\" }"), "order"))
        .isEmpty();
  }

  @Test
  void shouldNotFlagAdditionalPropertiesFalseOrSchema() throws Exception {
    assertThat(JsonSchemaGaps.unsupported(node("{ \"additionalProperties\": false }"), null))
        .isEmpty();
    assertThat(
            JsonSchemaGaps.unsupported(
                node("{ \"additionalProperties\": { \"type\": \"string\" } }"), null))
        .isEmpty();
  }

  @Test
  void shouldNotFlagOrdinaryScalar() throws Exception {
    assertThat(JsonSchemaGaps.unsupported(node("{ \"type\": \"string\" }"), null)).isEmpty();
  }

  @Test
  void shouldDetectSchemaLevelKeywords() throws Exception {
    String schema =
        "{ \"type\": \"object\", \"properties\": { \"a\": { \"type\": \"string\" } },"
            + " \"if\": {}, \"then\": {}, \"else\": {}, \"dependentSchemas\": {} }";
    assertThat(JsonSchemaGaps.unsupportedObjectKeywords(node(schema)))
        .containsExactly("if", "then", "else", "dependentSchemas");
  }

  @Test
  void shouldDetectAdditionalPropertiesTrueAtObjectLevel() throws Exception {
    assertThat(JsonSchemaGaps.unsupportedObjectKeywords(node("{ \"additionalProperties\": true }")))
        .containsExactly("additionalProperties:true");
  }

  @Test
  void shouldReturnNoSchemaLevelKeywordsForPlainObject() throws Exception {
    assertThat(
            JsonSchemaGaps.unsupportedObjectKeywords(
                node("{ \"type\": \"object\", \"properties\": {} }")))
        .isEmpty();
  }

  @Test
  void shouldNotFlagAllOfAtObjectLevel() throws Exception {
    // allOf is an exact merge, not a lossy relationship — it must not warn.
    assertThat(JsonSchemaGaps.unsupportedObjectKeywords(node("{ \"allOf\": [] }"))).isEmpty();
  }

  @Test
  void shouldCollectConditionallyConstrainedFields() throws Exception {
    String schema =
        "{ \"type\": \"object\","
            + " \"if\": { \"properties\": { \"isMember\": { \"const\": true } } },"
            + " \"then\": { \"properties\": { \"membershipNumber\": { \"minLength\": 10 } } } }";
    assertThat(JsonSchemaGaps.conditionallyConstrainedFields(node(schema)))
        .containsExactlyInAnyOrder("isMember", "membershipNumber");
  }

  private JsonNode node(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
