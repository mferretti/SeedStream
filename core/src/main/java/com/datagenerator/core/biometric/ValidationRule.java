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

import java.util.Map;
import java.util.Optional;

/**
 * A single validation rule applied to a biometric record.
 *
 * <p>Implementations check one aspect of a biometric record and return an error message if the
 * check fails, or an empty Optional if the record passes.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface ValidationRule {

  /**
   * Checks a biometric record against this rule.
   *
   * @param record the parsed JSON record as a map of field names to values
   * @return an Optional containing the violation message, or empty if the record is valid
   */
  Optional<String> check(Map<String, Object> record);
}
