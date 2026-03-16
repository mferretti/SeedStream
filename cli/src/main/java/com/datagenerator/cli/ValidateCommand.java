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

package com.datagenerator.cli;

import com.datagenerator.core.biometric.BiometricValidator;
import com.datagenerator.core.biometric.RecordViolation;
import com.datagenerator.core.biometric.ValidationReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Validates a NDJSON file containing biometric records against ISO/IEC 19794 rules.
 *
 * <p>The modality is auto-detected from the {@code record_format} field of each record:
 *
 * <ul>
 *   <li>{@code "FMR"} — fingerprint minutiae (ISO/IEC 19794-2)
 *   <li>{@code "FAC"} — face image (ISO/IEC 19794-5)
 * </ul>
 *
 * <p>CBEFF-wrapped records (containing a top-level {@code "payload"} key) are unwrapped
 * automatically before validation.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * datagenerator validate output/fingerprint_minutiae.json
 * datagenerator validate output/face_image.json
 * </pre>
 *
 * <p><b>Exit codes:</b>
 *
 * <ul>
 *   <li>0 — all records are valid
 *   <li>1 — one or more violations found
 *   <li>2 — I/O or parse error
 * </ul>
 *
 * @since 1.0
 */
@Slf4j
@Command(
    name = "validate",
    description = "Validate a NDJSON file containing biometric records (FMR / FAC)",
    mixinStandardHelpOptions = true)
public class ValidateCommand implements Callable<Integer> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @Parameters(index = "0", description = "NDJSON file to validate")
  private Path inputFile;

  @Override
  public Integer call() {
    log.info("Validating biometric records in: {}", inputFile);

    ObjectMapper mapper = new ObjectMapper();
    BiometricValidator validator = new BiometricValidator();

    AtomicInteger lineCounter = new AtomicInteger(0);
    List<RecordViolation> allViolations = new ArrayList<>();
    int totalRecords = 0;

    try (var lines = Files.lines(inputFile)) {
      for (var it = lines.iterator(); it.hasNext(); ) {
        String line = it.next();
        int lineNumber = lineCounter.incrementAndGet();
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        totalRecords++;

        Map<String, Object> parsed;
        try {
          parsed = mapper.readValue(trimmed, MAP_TYPE);
        } catch (IOException e) {
          log.warn("Line {}: failed to parse JSON — {}", lineNumber, e.getMessage());
          allViolations.add(
              new RecordViolation(
                  lineNumber, "UNKNOWN", List.of("JSON parse error: " + e.getMessage())));
          continue;
        }

        // CBEFF envelope unwrapping: extract "payload" if present
        Map<String, Object> record = unwrapCbeff(parsed);

        RecordViolation violation = validator.validateSingle(record, lineNumber);
        if (!violation.messages().isEmpty()) {
          allViolations.add(violation);
        }
      }
    } catch (IOException e) {
      log.error("Failed to read file: {}", inputFile, e);
      System.err.println("ERROR: Cannot read file: " + e.getMessage());
      return 2;
    } catch (UncheckedIOException e) {
      log.error("Failed to read file: {}", inputFile, e);
      System.err.println("ERROR: Cannot read file: " + e.getCause().getMessage());
      return 2;
    }

    int validRecords = totalRecords - allViolations.size();
    ValidationReport report =
        new ValidationReport(totalRecords, validRecords, List.copyOf(allViolations));

    // Print summary
    System.out.printf(
        "Validated %d records: %d valid, %d violations%n",
        report.totalRecords(), report.validRecords(), report.violations().size());

    // Print each violation
    for (RecordViolation v : report.violations()) {
      String messages = String.join("; ", v.messages());
      System.out.printf("  Line %d [%s]: %s%n", v.lineNumber(), v.recordFormat(), messages);
    }

    return report.isFullyValid() ? 0 : 1;
  }

  /**
   * Unwraps a CBEFF envelope by extracting the {@code "payload"} map, if present.
   *
   * @param parsed the top-level parsed map
   * @return the inner payload map, or {@code parsed} unchanged if no envelope is detected
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrapCbeff(Map<String, Object> parsed) {
    Object payload = parsed.get("payload");
    if (payload instanceof Map<?, ?> payloadMap) {
      return (Map<String, Object>) payloadMap;
    }
    return parsed;
  }
}
