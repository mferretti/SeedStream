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
 * Name-hint heuristics for unformatted strings. Maps a field name to a Datafaker expression based
 * on whole-token matches (never raw substring), so {@code email} matches {@code userEmail} but not
 * {@code email_verified_at}.
 *
 * <p>Applied only when no {@code format} and no {@code enum} is present on the schema. First
 * matching rule wins; rule order is the order below. Finite set for v1 — see {@code
 * docs/INSPECT-V1-SPEC.md} §5.
 */
public final class NameHints {

  private NameHints() {}

  /** Returns the Datafaker datatype for a field name, or empty if no rule matches. */
  public static Optional<String> forFieldName(String fieldName) {
    List<String> tokens = Names.tokenize(fieldName);
    if (tokens.isEmpty()) {
      return Optional.empty();
    }
    if (tokens.contains("email")) {
      return Optional.of("datafaker[internet.emailAddress]");
    }
    if (tokens.contains("phone") || tokens.contains("mobile") || tokens.contains("cell")) {
      return Optional.of("datafaker[phoneNumber.cellPhone]");
    }
    if (tokens.contains("first") && tokens.contains("name")) {
      return Optional.of("datafaker[name.firstName]");
    }
    if (tokens.contains("last") && tokens.contains("name")) {
      return Optional.of("datafaker[name.lastName]");
    }
    if (tokens.contains("city")) {
      return Optional.of("datafaker[address.city]");
    }
    if (tokens.contains("country")) {
      return Optional.of("datafaker[address.country]");
    }
    if (tokens.contains("street") || tokens.contains("address")) {
      return Optional.of("datafaker[address.streetAddress]");
    }
    if (tokens.contains("zip") || tokens.contains("postal")) {
      return Optional.of("datafaker[address.zipCode]");
    }
    if (tokens.contains("uuid") || tokens.contains("guid")) {
      return Optional.of("datafaker[internet.uuid]");
    }
    if (tokens.contains("url") || tokens.contains("uri")) {
      return Optional.of("datafaker[internet.url]");
    }
    return Optional.empty();
  }
}
