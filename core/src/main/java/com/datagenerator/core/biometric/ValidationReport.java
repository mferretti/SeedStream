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

import java.util.List;

/**
 * Aggregated result of validating one or more biometric records.
 *
 * @param totalRecords the total number of records that were inspected
 * @param validRecords the number of records that passed all validation rules
 * @param violations the list of violations, one entry per invalid record
 * @since 1.0
 */
public record ValidationReport(
    int totalRecords, int validRecords, List<RecordViolation> violations) {

  /** Defensive copy to prevent external mutation of the violations list. */
  public ValidationReport {
    violations = List.copyOf(violations);
  }

  /** Returns {@code true} when every record passed validation. */
  public boolean isFullyValid() {
    return violations.isEmpty();
  }
}
