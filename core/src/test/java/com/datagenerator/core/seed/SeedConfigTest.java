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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeedConfig")
class SeedConfigTest {

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
}
