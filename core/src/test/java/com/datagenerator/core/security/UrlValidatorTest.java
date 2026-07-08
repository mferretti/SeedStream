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

package com.datagenerator.core.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UrlValidatorTest {

  private static final String CONTEXT = "test URL";

  // ── validate(url, context) — unchanged 2-arg behavior ────────────────────

  @Test
  void shouldAcceptHttpsUrlViaTwoArgOverload() {
    assertThatCode(() -> UrlValidator.validate("https://example.com/path", CONTEXT))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptHttpUrlViaTwoArgOverload() {
    assertThatCode(() -> UrlValidator.validate("http://example.com/path", CONTEXT))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectBlankUrlViaTwoArgOverload() {
    assertThatThrownBy(() -> UrlValidator.validate("  ", CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null or blank");
  }

  @Test
  void shouldRejectNonHttpSchemeViaTwoArgOverload() {
    assertThatThrownBy(() -> UrlValidator.validate("ftp://example.com/path", CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use http or https scheme");
  }

  @Test
  void shouldRejectMissingHostViaTwoArgOverload() {
    assertThatThrownBy(() -> UrlValidator.validate("https:///path", CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing a host");
  }

  // ── validate(url, context, httpsOnly) ─────────────────────────────────────

  @Test
  void shouldAcceptHttpsUrlWhenHttpsOnlyIsTrue() {
    assertThatCode(() -> UrlValidator.validate("https://example.com/path", CONTEXT, true))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectHttpUrlWhenHttpsOnlyIsTrue() {
    assertThatThrownBy(() -> UrlValidator.validate("http://example.com/path", CONTEXT, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(CONTEXT)
        .hasMessageContaining("requires https");
  }

  @Test
  void shouldAcceptHttpUrlWhenHttpsOnlyIsFalse() {
    assertThatCode(() -> UrlValidator.validate("http://example.com/path", CONTEXT, false))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldStillRejectNonHttpSchemeWhenHttpsOnlyIsTrue() {
    assertThatThrownBy(() -> UrlValidator.validate("ftp://example.com/path", CONTEXT, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use http or https scheme");
  }

  @Test
  void shouldRejectBlankUrlWhenHttpsOnlyIsTrue() {
    assertThatThrownBy(() -> UrlValidator.validate(null, CONTEXT, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null or blank");
  }

  // ── isHttps ────────────────────────────────────────────────────────────────

  @Test
  void isHttpsShouldReturnTrueForHttpsUrl() {
    assertThat(UrlValidator.isHttps("https://example.com/path")).isTrue();
  }

  @Test
  void isHttpsShouldReturnFalseForHttpUrl() {
    assertThat(UrlValidator.isHttps("http://example.com/path")).isFalse();
  }

  @Test
  void isHttpsShouldBeCaseInsensitive() {
    assertThat(UrlValidator.isHttps("HTTPS://example.com/path")).isTrue();
  }

  @Test
  void isHttpsShouldReturnFalseForNull() {
    assertThat(UrlValidator.isHttps(null)).isFalse();
  }

  @Test
  void isHttpsShouldReturnFalseForBlank() {
    assertThat(UrlValidator.isHttps("  ")).isFalse();
  }

  @Test
  void isHttpsShouldReturnFalseForMalformedUrl() {
    assertThat(UrlValidator.isHttps("not a url")).isFalse();
  }
}
