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

package com.datagenerator.formats.json;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSerializerSpecialCharsTest {

  private final JsonSerializer serializer = new JsonSerializer();
  private final ObjectMapper reader =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void shouldEscapeControlCharactersSoOutputIsSingleLineAndRoundTrips() throws Exception {
    String value = "a\nb\r\nc\td\u0000e\u0001f\u001fg\b\f\"\\/";

    String json = serializer.serialize(map("v", value));

    assertThat(json).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
    assertThat(json.chars().filter(c -> c < 0x20)).isEmpty();
    assertThat(json)
        .contains(
            "\\n", "\\r\\n", "\\t", "\\u0000", "\\u0001", "\\u001F", "\\b", "\\f", "\\\"", "\\\\");
    assertThat(reader.readTree(json).get("v").asText()).isEqualTo(value);
  }

  @Test
  void shouldRoundTripUnicodeEmojiAndSeparators() throws Exception {
    String value = "caffè 日本語 🚀    é";

    String json = serializer.serialize(map("v", value, "kéy🚀", 1));

    JsonNode node = reader.readTree(json);
    assertThat(node.get("v").asText()).isEqualTo(value);
    assertThat(node.get("kéy🚀").asInt()).isEqualTo(1);
  }

  @Test
  void shouldEncodeUnicodeAsUtf8BytesInSerializeToBytes() throws Exception {
    String value = "è🚀";

    byte[] bytes = serializer.serializeToBytes(map("v", value));

    assertThat(reader.readTree(bytes).get("v").asText()).isEqualTo(value);
  }

  @Test
  void shouldKeepBigDecimalScaleAndPrecisionAsNumbers() throws Exception {
    String json =
        serializer.serialize(
            map(
                "a", new BigDecimal("0.10"),
                "b", new BigDecimal("12345678901234567890.123456789"),
                "c", new BigDecimal("-9999999999999999999999999999.99")));

    JsonNode node = reader.readTree(json);
    assertThat(node.get("a").isNumber()).isTrue();
    assertThat(node.get("a").decimalValue()).isEqualByComparingTo("0.10");
    assertThat(json).contains("\"a\":0.10");
    assertThat(node.get("b").decimalValue())
        .isEqualTo(new BigDecimal("12345678901234567890.123456789"));
    assertThat(node.get("c").decimalValue())
        .isEqualTo(new BigDecimal("-9999999999999999999999999999.99"));
  }

  @Test
  void shouldNotUseScientificNotationAmbiguouslyForLargeScaleDecimals() throws Exception {
    BigDecimal big = new BigDecimal("1E+30");

    JsonNode node = reader.readTree(serializer.serialize(map("v", big)));

    assertThat(node.get("v").decimalValue()).isEqualByComparingTo(big);
  }

  @Test
  void shouldSerializeNestedNullsInMapsAndLists() throws Exception {
    Map<String, Object> inner = map("x", null, "y", Arrays.asList(1, null, "z"));

    String json = serializer.serialize(map("top", null, "nested", inner));

    JsonNode node = reader.readTree(json);
    assertThat(node.has("top")).isTrue();
    assertThat(node.get("top").isNull()).isTrue();
    assertThat(node.at("/nested/x").isNull()).isTrue();
    assertThat(node.at("/nested/y/0").asInt()).isEqualTo(1);
    assertThat(node.at("/nested/y/1").isNull()).isTrue();
    assertThat(node.at("/nested/y/2").asText()).isEqualTo("z");
    assertThat(json).isEqualTo("{\"top\":null,\"nested\":{\"x\":null,\"y\":[1,null,\"z\"]}}");
  }

  @Test
  void shouldEscapeSpecialCharactersInKeys() throws Exception {
    String key = "a\"b\\c\nd";

    JsonNode node = reader.readTree(serializer.serialize(map(key, 1)));

    assertThat(node.get(key).asInt()).isEqualTo(1);
  }
}
