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

import com.datagenerator.core.registry.DatafakerRegistry;
import java.util.Optional;

/**
 * Bridges inspector mapping to the runtime {@link DatafakerRegistry}. A datafaker datatype is valid
 * in a SeedStream structure only if its bare key is registered (see {@code core/type/TypeParser}).
 * This guards every emitted key so the inspector can never produce a structure that fails to parse,
 * and it transparently picks up custom types loaded into the registry before inspection.
 */
public final class FakerTypes {

  private FakerTypes() {}

  /**
   * Returns the canonical registered key (resolving aliases like {@code zip -> postal_code}) when
   * the given key is registered, otherwise empty.
   */
  public static Optional<String> canonical(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return DatafakerRegistry.isRegistered(key)
        ? Optional.of(DatafakerRegistry.getCanonicalName(key))
        : Optional.empty();
  }
}
