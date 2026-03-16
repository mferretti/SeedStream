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

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiometricValidatorTest {

  private BiometricValidator validator;

  @BeforeEach
  void setUp() {
    validator = new BiometricValidator();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static Map<String, Object> validFingerprintRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put("record_format", "FMR");
    record.put("version", "19794-2:2011");
    record.put("subject_id", "abc12345");
    record.put("sample_id", 3);
    record.put("finger_position", "right_index");
    record.put("impression_type", "live_scan_plain");
    record.put("image_width", 500);
    record.put("image_height", 600);
    record.put("resolution_dpi", "500");
    record.put("quality", 75);
    record.put("minutiae", buildMinutiae(40, 100, 150));
    return record;
  }

  private static Map<String, Object> validFaceRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put("record_format", "FAC");
    record.put("version", "19794-5:2011");
    record.put("subject_id", "abc12345");
    record.put("sample_id", 0);
    record.put("image_type", "still_controlled");
    record.put("width", 640);
    record.put("height", 480);
    record.put("color_space", "RGB");
    record.put("compression", "JPEG");
    record.put("image_file", "face_001.jpg");

    Map<String, Object> faceBox = Map.of("x", 50, "y", 40, "width", 200, "height", 250);
    record.put("face_box", faceBox);

    record.put("landmarks", buildLandmarks(100, 150, 200, 250, 300, 320, 350, 400, 450));
    record.put("pose", Map.of("yaw", 5.0, "pitch", -2.0, "roll", 3.0));
    record.put("quality", Map.of("overall", 85, "occlusion", 10, "illumination", 90));
    return record;
  }

  /**
   * Builds a list of minutiae with the given count. Each minutia uses (x, y) coordinates that are
   * guaranteed to be within the provided image dimensions.
   */
  private static List<Map<String, Object>> buildMinutiae(
      int count, int imageWidth, int imageHeight) {
    List<Map<String, Object>> minutiae = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Map<String, Object> m = new HashMap<>();
      m.put("x", (i * 3) % (imageWidth - 1));
      m.put("y", (i * 5) % (imageHeight - 1));
      m.put("angle_deg", (i * 7.5) % 359.9);
      m.put("type", i % 2 == 0 ? "ending" : "bifurcation");
      m.put("quality", 80);
      minutiae.add(m);
    }
    return minutiae;
  }

  /** Builds a landmarks map with the given x/y pairs for each of the five standard points. */
  private static Map<String, Object> buildLandmarks(
      int lex, int ley, int rex, int rey, int ntx, int nty, int mlx, int mly, int mrx) {
    return Map.of(
        "left_eye", Map.of("x", lex, "y", ley),
        "right_eye", Map.of("x", rex, "y", rey),
        "nose_tip", Map.of("x", ntx, "y", nty),
        "mouth_left", Map.of("x", mlx, "y", mly),
        "mouth_right", Map.of("x", mrx, "y", mly));
  }

  // -------------------------------------------------------------------------
  // Fingerprint tests
  // -------------------------------------------------------------------------

  @Test
  void shouldPassWhenFingerprintRecordIsValid() {
    RecordViolation violation = validator.validateSingle(validFingerprintRecord(), 1);
    assertThat(violation.messages()).isEmpty();
  }

  @Test
  void shouldReportViolationWhenFingerprintRecordFormatMissing() {
    Map<String, Object> record = validFingerprintRecord();
    record.remove("record_format");
    RecordViolation violation = validator.validateSingle(record, 1);
    // record_format missing → falls through to "UNKNOWN" → unknown format violation
    assertThat(violation.messages()).isNotEmpty();
  }

  @Test
  void shouldReportViolationWhenSubjectIdMissing() {
    Map<String, Object> record = validFingerprintRecord();
    record.remove("subject_id");
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("subject_id"));
  }

  @Test
  void shouldReportViolationWhenFingerPositionMissing() {
    Map<String, Object> record = validFingerprintRecord();
    record.remove("finger_position");
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("finger_position"));
  }

  @Test
  void shouldReportViolationWhenMinutiaeEmpty() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("minutiae", new ArrayList<>());
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("non-empty"));
  }

  @Test
  void shouldReportViolationWhenMinutiaeCountBelowMinimum() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("minutiae", buildMinutiae(10, 500, 600));
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("range [30, 80]"));
  }

  @Test
  void shouldReportViolationWhenMinutiaeCountAboveMaximum() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("minutiae", buildMinutiae(90, 500, 600));
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("range [30, 80]"));
  }

  @Test
  void shouldReportViolationWhenMinutiaXExceedsImageWidth() {
    Map<String, Object> record = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 600); // >= image_width 500
    badMinutia.put("y", 100);
    badMinutia.put("angle_deg", 45.0);
    badMinutia.put("type", "ending");
    badMinutia.put("quality", 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    record.put("minutiae", minutiae);

    RecordViolation violation = validator.validateSingle(record, 5);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image_width"));
    assertThat(violation.lineNumber()).isEqualTo(5);
  }

  @Test
  void shouldReportViolationWhenMinutiaYExceedsImageHeight() {
    Map<String, Object> record = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 700); // >= image_height 600
    badMinutia.put("angle_deg", 45.0);
    badMinutia.put("type", "ending");
    badMinutia.put("quality", 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    record.put("minutiae", minutiae);

    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image_height"));
  }

  @Test
  void shouldReportViolationWhenMinutiaAngleIsNegative() {
    Map<String, Object> record = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put("angle_deg", -1.0);
    badMinutia.put("type", "ending");
    badMinutia.put("quality", 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    record.put("minutiae", minutiae);

    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("angle_deg"));
  }

  @Test
  void shouldReportViolationWhenMinutiaAngleIs360OrMore() {
    Map<String, Object> record = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put("angle_deg", 360.0);
    badMinutia.put("type", "ending");
    badMinutia.put("quality", 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    record.put("minutiae", minutiae);

    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("angle_deg"));
  }

  @Test
  void shouldReportViolationWhenMinutiaTypeIsInvalid() {
    Map<String, Object> record = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put("angle_deg", 45.0);
    badMinutia.put("type", "loop"); // invalid
    badMinutia.put("quality", 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    record.put("minutiae", minutiae);

    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("bifurcation"));
  }

  @Test
  void shouldReportViolationWhenFingerprintQualityOutOfRange() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("quality", 150);
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality=150"));
  }

  @Test
  void shouldReportAllViolationsInOneRecordNonFailFast() {
    Map<String, Object> record = new HashMap<>();
    record.put("record_format", "FMR");
    record.put("minutiae", buildMinutiae(5, 500, 600)); // count too low
    record.put("quality", 150); // out of range

    RecordViolation violation = validator.validateSingle(record, 1);
    // Should have: missing required fields + count violation + quality violation
    assertThat(violation.messages()).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void shouldReportAllViolationsForEmptyRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put("record_format", "FMR");

    RecordViolation violation = validator.validateSingle(record, 1);
    // All required fields except record_format are missing
    assertThat(violation.messages()).isNotEmpty();
    assertThat(violation.messages()).anyMatch(m -> m.contains("Missing required fields"));
  }

  // -------------------------------------------------------------------------
  // Face image tests
  // -------------------------------------------------------------------------

  @Test
  void shouldPassWhenFaceRecordIsValid() {
    RecordViolation violation = validator.validateSingle(validFaceRecord(), 1);
    assertThat(violation.messages()).isEmpty();
  }

  @Test
  void shouldReportViolationWhenFaceRequiredFieldMissing() {
    Map<String, Object> record = validFaceRecord();
    record.remove("image_file");
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image_file"));
  }

  @Test
  void shouldReportViolationWhenFaceBoxExceedsImageWidth() {
    Map<String, Object> record = validFaceRecord();
    // x=400 + width=300 = 700 > image width 640
    record.put("face_box", Map.of("x", 400, "y", 40, "width", 300, "height", 200));
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image width"));
  }

  @Test
  void shouldReportViolationWhenFaceBoxExceedsImageHeight() {
    Map<String, Object> record = validFaceRecord();
    // y=300 + height=300 = 600 > image height 480
    record.put("face_box", Map.of("x", 50, "y", 300, "width", 200, "height", 300));
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image height"));
  }

  @Test
  void shouldReportViolationWhenLandmarkMissing() {
    Map<String, Object> record = validFaceRecord();
    Map<String, Object> incompleteLandmarks = new HashMap<>();
    incompleteLandmarks.put("left_eye", Map.of("x", 100, "y", 150));
    incompleteLandmarks.put("right_eye", Map.of("x", 200, "y", 150));
    // nose_tip, mouth_left, mouth_right missing
    record.put("landmarks", incompleteLandmarks);
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("nose_tip"));
  }

  @Test
  void shouldReportViolationWhenLandmarkOutsideImageBounds() {
    Map<String, Object> record = validFaceRecord();
    Map<String, Object> landmarks = new HashMap<>();
    landmarks.put("left_eye", Map.of("x", 700, "y", 150)); // x=700 >= width=640
    landmarks.put("right_eye", Map.of("x", 200, "y", 150));
    landmarks.put("nose_tip", Map.of("x", 300, "y", 320));
    landmarks.put("mouth_left", Map.of("x", 250, "y", 400));
    landmarks.put("mouth_right", Map.of("x", 390, "y", 400));
    record.put("landmarks", landmarks);
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("left_eye.x"));
  }

  @Test
  void shouldReportViolationWhenFaceQualityOutOfRange() {
    Map<String, Object> record = validFaceRecord();
    record.put("quality", Map.of("overall", 200, "occlusion", 10, "illumination", 90));
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality.overall=200"));
  }

  // -------------------------------------------------------------------------
  // Unknown format / general tests
  // -------------------------------------------------------------------------

  @Test
  void shouldReportViolationForUnknownRecordFormat() {
    Map<String, Object> record = Map.of("record_format", "XYZ", "subject_id", "abc");
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("Unknown record_format: XYZ"));
    assertThat(violation.recordFormat()).isEqualTo("XYZ");
  }

  @Test
  void shouldReportViolationWhenRecordFormatAbsent() {
    Map<String, Object> record = new HashMap<>();
    record.put("subject_id", "abc");
    RecordViolation violation = validator.validateSingle(record, 3);
    assertThat(violation.messages()).anyMatch(m -> m.contains("Unknown record_format"));
    assertThat(violation.lineNumber()).isEqualTo(3);
  }

  @Test
  void shouldValidateBatchAndAggregateResults() {
    Map<String, Object> valid1 = validFingerprintRecord();
    Map<String, Object> valid2 = validFaceRecord();
    Map<String, Object> invalid = new HashMap<>();
    invalid.put("record_format", "FMR");
    // missing all other required fields

    ValidationReport report = validator.validate(List.of(valid1, valid2, invalid));
    assertThat(report.totalRecords()).isEqualTo(3);
    assertThat(report.validRecords()).isEqualTo(2);
    assertThat(report.violations()).hasSize(1);
    assertThat(report.isFullyValid()).isFalse();
  }

  @Test
  void shouldReturnEmptyViolationsForFullyValidBatch() {
    ValidationReport report =
        validator.validate(List.of(validFingerprintRecord(), validFaceRecord()));
    assertThat(report.totalRecords()).isEqualTo(2);
    assertThat(report.validRecords()).isEqualTo(2);
    assertThat(report.violations()).isEmpty();
    assertThat(report.isFullyValid()).isTrue();
  }

  @Test
  void shouldReportViolationWhenFingerprintQualityIsNegative() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("quality", -5);
    RecordViolation violation = validator.validateSingle(record, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality=-5"));
  }

  @Test
  void shouldPassWhenMinutiaeCountIsExactlyAtBoundary() {
    Map<String, Object> record = validFingerprintRecord();
    record.put("minutiae", buildMinutiae(30, 500, 600));
    RecordViolation lower = validator.validateSingle(record, 1);
    assertThat(lower.messages()).isEmpty();

    record.put("minutiae", buildMinutiae(80, 500, 600));
    RecordViolation upper = validator.validateSingle(record, 2);
    assertThat(upper.messages()).isEmpty();
  }
}
