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

/**
 * Validates biometric records against ISO/IEC 19794 rules.
 *
 * <p>Supports two modalities detected automatically from the {@code record_format} field:
 *
 * <ul>
 *   <li>{@code "FMR"} — fingerprint minutiae records (ISO/IEC 19794-2)
 *   <li>{@code "FAC"} — face image records (ISO/IEC 19794-5)
 * </ul>
 *
 * <p>All applicable rules are evaluated for each record; violations are collected without
 * short-circuiting so callers receive a complete picture of every problem.
 *
 * @since 1.0
 */
public class BiometricValidator {

  /**
   * Validates a batch of biometric records parsed from NDJSON.
   *
   * <p>Records are validated in order; their 1-based line numbers correspond to their position in
   * the input list (index + 1).
   *
   * @param records the list of parsed records (each record is a {@code Map<String, Object>})
   * @return a {@link ValidationReport} summarising all results
   */
  public ValidationReport validate(List<Map<String, Object>> records) {
    List<RecordViolation> violations = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      RecordViolation violation = validateSingle(records.get(i), i + 1);
      if (!violation.messages().isEmpty()) {
        violations.add(violation);
      }
    }
    int validCount = records.size() - violations.size();
    return new ValidationReport(records.size(), validCount, List.copyOf(violations));
  }

  /**
   * Validates a single biometric record.
   *
   * <p>The modality is determined from the {@code record_format} field. If the field is absent or
   * unrecognised, a single violation is returned immediately.
   *
   * @param record the parsed record map
   * @param lineNumber the 1-based line number in the source file (used for reporting)
   * @return a {@link RecordViolation} — its {@code messages()} list is empty when the record is
   *     valid
   */
  public RecordViolation validateSingle(Map<String, Object> record, int lineNumber) {
    Object formatObj = record.get("record_format");
    String format = formatObj instanceof String s ? s : "UNKNOWN";

    List<ValidationRule> rules =
        switch (format) {
          case "FMR" -> FingerprintMinutiaeRules.rules();
          case "FAC" -> FaceImageRules.rules();
          default -> {
            String msg = "Unknown record_format: " + format;
            yield List.of(r -> Optional.of(msg));
          }
        };

    List<String> messages = new ArrayList<>();
    for (ValidationRule rule : rules) {
      rule.check(record).ifPresent(messages::add);
    }
    return new RecordViolation(lineNumber, format, List.copyOf(messages));
  }
}
