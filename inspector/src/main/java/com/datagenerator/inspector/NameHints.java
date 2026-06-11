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
import java.util.function.Predicate;

/**
 * Name-hint heuristics for unformatted strings. Maps a field name to a candidate {@link
 * com.datagenerator.core.registry.DatafakerRegistry} key based on whole-token matches (never raw
 * substring), so {@code email} matches the {@code email} token in {@code userEmail} or {@code
 * email_verified_at}, but not a bare substring inside a single token such as {@code emailish}.
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

  private record Hint(Predicate<List<String>> matches, String key) {}

  private static final List<Hint> HINTS =
      List.of(
          new Hint(t -> t.contains("email"), "email"),
          new Hint(
              t -> t.contains("phone") || t.contains("mobile") || t.contains("cell"),
              "phone_number"),
          new Hint(t -> t.contains("first") && t.contains("name"), "first_name"),
          new Hint(t -> t.contains("last") && t.contains("name"), "last_name"),
          new Hint(t -> t.contains("city"), "city"),
          new Hint(t -> t.contains("country"), "country"),
          new Hint(t -> t.contains("street"), "street_name"),
          new Hint(t -> t.contains("address"), "address"),
          new Hint(t -> t.contains("zip") || t.contains("postal"), "postal_code"),
          new Hint(t -> t.contains("uuid") || t.contains("guid"), "uuid"),
          new Hint(t -> t.contains("url") || t.contains("uri"), "url"));

  /** Returns a candidate datafaker registry key for a field name, or empty if no rule matches. */
  public static Optional<String> forFieldName(String fieldName) {
    List<String> tokens = Names.tokenize(fieldName);
    if (tokens.isEmpty()) {
      return Optional.empty();
    }
    return HINTS.stream().filter(h -> h.matches().test(tokens)).findFirst().map(Hint::key);
  }
}
