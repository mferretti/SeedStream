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

package com.datagenerator.core.biometric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared validation helpers for biometric record rule sets. */
final class BiometricValidationUtils {

  private BiometricValidationUtils() {}

  /**
   * Returns a violation message if any field in {@code required} is absent from {@code record}, or
   * {@link Optional#empty()} when all are present.
   */
  static Optional<String> checkRequiredFields(Set<String> required, Map<String, Object> record) {
    List<String> missing = new ArrayList<>();
    for (String field : required) {
      if (!record.containsKey(field)) {
        missing.add(field);
      }
    }
    return missing.isEmpty()
        ? Optional.empty()
        : Optional.of("Missing required fields: " + String.join(", ", missing));
  }
}
