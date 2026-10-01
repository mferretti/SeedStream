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

package com.datagenerator.core.seed;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeedConfig")
class SeedConfigTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("AuthConfig toString should exclude bearer token")
  void shouldExcludeBearerTokenFromToString() {
    SeedConfig.RemoteSeed.AuthConfig authConfig =
        new SeedConfig.RemoteSeed.AuthConfig("bearer", "SECRET_TOKEN", null, null, null, null);
    String toString = authConfig.toString();
    assertThat(toString).doesNotContain("SECRET_TOKEN");
  }

  @Test
  @DisplayName("AuthConfig toString should exclude basic password")
  void shouldExcludeBasicPasswordFromToString() {
    SeedConfig.RemoteSeed.AuthConfig authConfig =
        new SeedConfig.RemoteSeed.AuthConfig("basic", null, "user", "SECRET_PASSWORD", null, null);
    String toString = authConfig.toString();
    assertThat(toString).doesNotContain("SECRET_PASSWORD");
  }

  @Test
  @DisplayName("AuthConfig toString should exclude api_key value")
  void shouldExcludeApiKeyValueFromToString() {
    SeedConfig.RemoteSeed.AuthConfig authConfig =
        new SeedConfig.RemoteSeed.AuthConfig(
            "api_key", null, null, null, "X-API-Key", "SECRET_VALUE");
    String toString = authConfig.toString();
    assertThat(toString).doesNotContain("SECRET_VALUE");
  }

  @Test
  @DisplayName("AuthConfig toString should include non-sensitive fields")
  void shouldIncludeNonSensitiveFields() {
    SeedConfig.RemoteSeed.AuthConfig authConfig =
        new SeedConfig.RemoteSeed.AuthConfig(
            "basic", null, "testuser", "SECRET_PASSWORD", null, null);
    String toString = authConfig.toString();
    assertThat(toString).contains("basic").contains("testuser");
  }

  @Test
  void shouldDeserializeEachSeedTypeByTypeDiscriminator() throws Exception {
    assertThat(MAPPER.readValue("{\"type\":\"embedded\",\"value\":-5}", SeedConfig.class))
        .isEqualTo(new SeedConfig.EmbeddedSeed("embedded", -5L));
    assertThat(MAPPER.readValue("{\"type\":\"file\",\"path\":\"/s\"}", SeedConfig.class))
        .isEqualTo(new SeedConfig.FileSeed("file", "/s"));
    assertThat(MAPPER.readValue("{\"type\":\"env\",\"name\":\"SEED\"}", SeedConfig.class))
        .isEqualTo(new SeedConfig.EnvSeed("env", "SEED"));
  }

  @Test
  void shouldDeserializeRemoteSeedWithAuth() throws Exception {
    String json =
        "{\"type\":\"remote\",\"url\":\"https://h/s\",\"auth\":{\"type\":\"api_key\","
            + "\"key\":\"X-K\",\"value\":\"v\"}}";

    SeedConfig.RemoteSeed remote = (SeedConfig.RemoteSeed) MAPPER.readValue(json, SeedConfig.class);

    assertThat(remote.getUrl()).isEqualTo("https://h/s");
    assertThat(remote.getAuth().getType()).isEqualTo("api_key");
    assertThat(remote.getAuth().getKey()).isEqualTo("X-K");
    assertThat(remote.getAuth().getValue()).isEqualTo("v");
    assertThat(remote.getAuth().getToken()).isNull();
  }

  @Test
  void shouldRejectUnknownSeedType() {
    assertThatThrownBy(() -> MAPPER.readValue("{\"type\":\"vault\"}", SeedConfig.class))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageContaining("vault");
  }

  @Test
  void shouldNotLeakAuthSecretsThroughRemoteSeedToString() {
    SeedConfig.RemoteSeed remote =
        new SeedConfig.RemoteSeed(
            "remote",
            "https://h/s",
            new SeedConfig.RemoteSeed.AuthConfig("bearer", "TOP_SECRET", null, null, null, null));

    assertThat(remote.toString()).contains("https://h/s").doesNotContain("TOP_SECRET");
  }
}
