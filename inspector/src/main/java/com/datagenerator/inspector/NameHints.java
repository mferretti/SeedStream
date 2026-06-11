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

package com.datagenerator.inspector;

import java.util.List;
import java.util.Optional;

/**
 * Name-hint heuristics for unformatted strings. Maps a field name to a candidate {@link
 * com.datagenerator.core.registry.DatafakerRegistry} key based on whole-token matches (never raw
 * substring), so {@code email} matches {@code userEmail} but not {@code email_verified_at}.
 *
 * <p>The returned value is a bare registry key (e.g. {@code email}, {@code first_name}) — the only
 * datafaker syntax {@code TypeParser} accepts. Callers must still validate it against the registry
 * via {@link FakerTypes#canonical(String)} before emitting, so an unregistered key falls back to a
 * primitive rather than producing an unparseable structure.
 *
 * <p>Applied only when no {@code format} and no {@code enum} is present on the schema. First
 * matching rule wins; rule order is the order below. See {@code docs/INSPECT-V1-SPEC.md} §5.
 */
public final class NameHints {

  private NameHints() {}

  /** Returns a candidate datafaker registry key for a field name, or empty if no rule matches. */
  public static Optional<String> forFieldName(String fieldName) {
    List<String> tokens = Names.tokenize(fieldName);
    if (tokens.isEmpty()) {
      return Optional.empty();
    }
    if (tokens.contains("email")) {
      return Optional.of("email");
    }
    if (tokens.contains("phone") || tokens.contains("mobile") || tokens.contains("cell")) {
      return Optional.of("phone_number");
    }
    if (tokens.contains("first") && tokens.contains("name")) {
      return Optional.of("first_name");
    }
    if (tokens.contains("last") && tokens.contains("name")) {
      return Optional.of("last_name");
    }
    if (tokens.contains("city")) {
      return Optional.of("city");
    }
    if (tokens.contains("country")) {
      return Optional.of("country");
    }
    if (tokens.contains("street")) {
      return Optional.of("street_name");
    }
    if (tokens.contains("address")) {
      return Optional.of("address");
    }
    if (tokens.contains("zip") || tokens.contains("postal")) {
      return Optional.of("postal_code");
    }
    if (tokens.contains("uuid") || tokens.contains("guid")) {
      return Optional.of("uuid");
    }
    if (tokens.contains("url") || tokens.contains("uri")) {
      return Optional.of("url");
    }
    return Optional.empty();
  }
}
