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

/**
 * Factory providing ISO/IEC 19794-2 fingerprint minutiae validation rules.
 *
 * <p>All rules collect violations without short-circuiting (non-fail-fast), so every rule is
 * evaluated even when earlier rules have already failed.
 *
 * @since 1.0
 */
public final class FingerprintMinutiaeRules {

  private static final Set<String> REQUIRED_FIELDS =
      Set.of(
          "record_format",
          "version",
          "subject_id",
          "sample_id",
          "finger_position",
          "impression_type",
          "image_width",
          "image_height",
          "resolution_dpi",
          "quality",
          "minutiae");

  private static final Set<String> VALID_MINUTIA_TYPES = Set.of("ending", "bifurcation");

  private FingerprintMinutiaeRules() {}

  /**
   * Returns the ordered list of validation rules for fingerprint minutiae records.
   *
   * @return immutable list of {@link ValidationRule} instances
   */
  public static List<ValidationRule> rules() {
    return List.of(
        FingerprintMinutiaeRules::checkRequiredFields,
        FingerprintMinutiaeRules::checkMinutiaeNonEmpty,
        FingerprintMinutiaeRules::checkMinutiaeCount,
        FingerprintMinutiaeRules::checkMinutiaeCoordinates,
        FingerprintMinutiaeRules::checkMinutiaeAngles,
        FingerprintMinutiaeRules::checkMinutiaeTypes,
        FingerprintMinutiaeRules::checkQuality);
  }

  // Rule 1: all required fields are present
  private static Optional<String> checkRequiredFields(Map<String, Object> record) {
    List<String> missing = new ArrayList<>();
    for (String field : REQUIRED_FIELDS) {
      if (!record.containsKey(field)) {
        missing.add(field);
      }
    }
    if (missing.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of("Missing required fields: " + String.join(", ", missing));
  }

  // Rule 2: minutiae is a non-empty List
  private static Optional<String> checkMinutiaeNonEmpty(Map<String, Object> record) {
    Object minutiae = record.get("minutiae");
    if (!(minutiae instanceof List<?> list) || list.isEmpty()) {
      return Optional.of("'minutiae' must be a non-empty list");
    }
    return Optional.empty();
  }

  // Rule 3: minutiae count between 30 and 80
  private static Optional<String> checkMinutiaeCount(Map<String, Object> record) {
    Object minutiae = record.get("minutiae");
    if (!(minutiae instanceof List<?> list)) {
      return Optional.empty(); // covered by rule 2
    }
    int count = list.size();
    if (count < 30 || count > 80) {
      return Optional.of("Minutiae count " + count + " is outside the allowed range [30, 80]");
    }
    return Optional.empty();
  }

  // Rule 4: each minutia x < image_width and y < image_height
  @SuppressWarnings("unchecked")
  private static Optional<String> checkMinutiaeCoordinates(Map<String, Object> record) {
    Object minutiae = record.get("minutiae");
    if (!(minutiae instanceof List<?> list)) {
      return Optional.empty();
    }
    Object widthObj = record.get("image_width");
    Object heightObj = record.get("image_height");
    if (!(widthObj instanceof Number) || !(heightObj instanceof Number)) {
      return Optional.empty();
    }
    int imageWidth = ((Number) widthObj).intValue();
    int imageHeight = ((Number) heightObj).intValue();

    List<String> errors = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      if (!(list.get(i) instanceof Map<?, ?> rawMinutia)) {
        continue;
      }
      Map<String, Object> minutia = (Map<String, Object>) rawMinutia;
      Object xObj = minutia.get("x");
      Object yObj = minutia.get("y");
      if (xObj instanceof Number xNum && xNum.intValue() >= imageWidth) {
        errors.add("minutia[" + i + "].x=" + xNum.intValue() + " >= image_width=" + imageWidth);
      }
      if (yObj instanceof Number yNum && yNum.intValue() >= imageHeight) {
        errors.add("minutia[" + i + "].y=" + yNum.intValue() + " >= image_height=" + imageHeight);
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 5: each minutia angle_deg in [0.0, 360.0)
  @SuppressWarnings("unchecked")
  private static Optional<String> checkMinutiaeAngles(Map<String, Object> record) {
    Object minutiae = record.get("minutiae");
    if (!(minutiae instanceof List<?> list)) {
      return Optional.empty();
    }
    List<String> errors = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      if (!(list.get(i) instanceof Map<?, ?> rawMinutia)) {
        continue;
      }
      Map<String, Object> minutia = (Map<String, Object>) rawMinutia;
      Object angleObj = minutia.get("angle_deg");
      if (angleObj instanceof Number angleNum) {
        double angle = angleNum.doubleValue();
        if (angle < 0.0 || angle >= 360.0) {
          errors.add(
              "minutia["
                  + i
                  + "].angle_deg="
                  + angle
                  + " is outside the allowed range [0.0, 360.0)");
        }
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 6: each minutia type is "ending" or "bifurcation"
  @SuppressWarnings("unchecked")
  private static Optional<String> checkMinutiaeTypes(Map<String, Object> record) {
    Object minutiae = record.get("minutiae");
    if (!(minutiae instanceof List<?> list)) {
      return Optional.empty();
    }
    List<String> errors = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      if (!(list.get(i) instanceof Map<?, ?> rawMinutia)) {
        continue;
      }
      Map<String, Object> minutia = (Map<String, Object>) rawMinutia;
      Object typeObj = minutia.get("type");
      if (typeObj instanceof String type && !VALID_MINUTIA_TYPES.contains(type)) {
        errors.add("minutia[" + i + "].type='" + type + "' is not 'ending' or 'bifurcation'");
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 7: quality in [0, 100]
  private static Optional<String> checkQuality(Map<String, Object> record) {
    Object qualityObj = record.get("quality");
    if (!(qualityObj instanceof Number qualityNum)) {
      return Optional.empty();
    }
    int quality = qualityNum.intValue();
    if (quality < 0 || quality > 100) {
      return Optional.of("quality=" + quality + " is outside the allowed range [0, 100]");
    }
    return Optional.empty();
  }
}
