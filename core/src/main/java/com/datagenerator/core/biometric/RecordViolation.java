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
 * Represents all violations found in a single biometric record.
 *
 * @param lineNumber the 1-based line number of the record in the input NDJSON file
 * @param recordFormat the detected modality identifier (e.g., {@code "FMR"} or {@code "FAC"})
 * @param messages the list of violation messages collected from all failed rules
 * @since 1.0
 */
public record RecordViolation(int lineNumber, String recordFormat, List<String> messages) {

  /** Defensive copy to prevent external mutation of the messages list. */
  public RecordViolation {
    messages = List.copyOf(messages);
  }
}
