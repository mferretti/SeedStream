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

  private static final String FIELD_RECORD_FORMAT = "record_format";
  private static final String FIELD_SUBJECT_ID = "subject_id";
  private static final String FIELD_FINGER_POSITION = "finger_position";
  private static final String FIELD_ANGLE_DEG = "angle_deg";
  private static final String FIELD_IMAGE_FILE = "image_file";
  private static final String FIELD_FACE_BOX = "face_box";
  private static final String FIELD_LANDMARKS = "landmarks";
  private static final String FIELD_MINUTIAE = "minutiae";
  private static final String FIELD_QUALITY = "quality";
  private static final String FIELD_WIDTH = "width";
  private static final String FIELD_HEIGHT = "height";
  private static final String VAL_ENDING = "ending";
  private static final String LM_LEFT_EYE = "left_eye";
  private static final String LM_RIGHT_EYE = "right_eye";
  private static final String LM_NOSE_TIP = "nose_tip";

  private BiometricValidator validator;

  @BeforeEach
  void setUp() {
    validator = new BiometricValidator();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static Map<String, Object> validFingerprintRecord() {
    Map<String, Object> data = new HashMap<>();
    data.put(FIELD_RECORD_FORMAT, "FMR");
    data.put("version", "19794-2:2011");
    data.put(FIELD_SUBJECT_ID, "abc12345");
    data.put("sample_id", 3);
    data.put(FIELD_FINGER_POSITION, "right_index");
    data.put("impression_type", "live_scan_plain");
    data.put("image_width", 500);
    data.put("image_height", 600);
    data.put("resolution_dpi", "500");
    data.put(FIELD_QUALITY, 75);
    data.put(FIELD_MINUTIAE, buildMinutiae(40, 100, 150));
    return data;
  }

  private static Map<String, Object> validFaceRecord() {
    Map<String, Object> data = new HashMap<>();
    data.put(FIELD_RECORD_FORMAT, "FAC");
    data.put("version", "19794-5:2011");
    data.put(FIELD_SUBJECT_ID, "abc12345");
    data.put("sample_id", 0);
    data.put("image_type", "still_controlled");
    data.put(FIELD_WIDTH, 640);
    data.put(FIELD_HEIGHT, 480);
    data.put("color_space", "RGB");
    data.put("compression", "JPEG");
    data.put(FIELD_IMAGE_FILE, "face_001.jpg");

    Map<String, Object> faceBox = Map.of("x", 50, "y", 40, FIELD_WIDTH, 200, FIELD_HEIGHT, 250);
    data.put(FIELD_FACE_BOX, faceBox);

    data.put(FIELD_LANDMARKS, buildLandmarks(100, 150, 200, 250, 300, 320, 350, 400, 450));
    data.put("pose", Map.of("yaw", 5.0, "pitch", -2.0, "roll", 3.0));
    data.put(FIELD_QUALITY, Map.of("overall", 85, "occlusion", 10, "illumination", 90));
    return data;
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
      m.put(FIELD_ANGLE_DEG, (i * 7.5) % 359.9);
      m.put("type", i % 2 == 0 ? VAL_ENDING : "bifurcation");
      m.put(FIELD_QUALITY, 80);
      minutiae.add(m);
    }
    return minutiae;
  }

  /** Builds a landmarks map with the given x/y pairs for each of the five standard points. */
  @SuppressWarnings("java:S107")
  private static Map<String, Object> buildLandmarks(
      int lex, int ley, int rex, int rey, int ntx, int nty, int mlx, int mly, int mrx) {
    return Map.of(
        LM_LEFT_EYE,
        Map.of("x", lex, "y", ley),
        LM_RIGHT_EYE,
        Map.of("x", rex, "y", rey),
        LM_NOSE_TIP,
        Map.of("x", ntx, "y", nty),
        "mouth_left",
        Map.of("x", mlx, "y", mly),
        "mouth_right",
        Map.of("x", mrx, "y", mly));
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
    Map<String, Object> data = validFingerprintRecord();
    data.remove(FIELD_RECORD_FORMAT);
    RecordViolation violation = validator.validateSingle(data, 1);
    // record_format missing → falls through to "UNKNOWN" → unknown format violation
    assertThat(violation.messages()).isNotEmpty();
  }

  @Test
  void shouldReportViolationWhenSubjectIdMissing() {
    Map<String, Object> data = validFingerprintRecord();
    data.remove(FIELD_SUBJECT_ID);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(FIELD_SUBJECT_ID));
  }

  @Test
  void shouldReportViolationWhenFingerPositionMissing() {
    Map<String, Object> data = validFingerprintRecord();
    data.remove(FIELD_FINGER_POSITION);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(FIELD_FINGER_POSITION));
  }

  @Test
  void shouldReportViolationWhenMinutiaeEmpty() {
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_MINUTIAE, new ArrayList<>());
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("non-empty"));
  }

  @Test
  void shouldReportViolationWhenMinutiaeCountBelowMinimum() {
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_MINUTIAE, buildMinutiae(10, 500, 600));
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("range [30, 80]"));
  }

  @Test
  void shouldReportViolationWhenMinutiaeCountAboveMaximum() {
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_MINUTIAE, buildMinutiae(90, 500, 600));
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("range [30, 80]"));
  }

  @Test
  void shouldReportViolationWhenMinutiaXExceedsImageWidth() {
    Map<String, Object> data = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 600); // >= image_width 500
    badMinutia.put("y", 100);
    badMinutia.put(FIELD_ANGLE_DEG, 45.0);
    badMinutia.put("type", VAL_ENDING);
    badMinutia.put(FIELD_QUALITY, 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    data.put(FIELD_MINUTIAE, minutiae);

    RecordViolation violation = validator.validateSingle(data, 5);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image_width"));
    assertThat(violation.lineNumber()).isEqualTo(5);
  }

  @Test
  void shouldReportViolationWhenMinutiaYExceedsImageHeight() {
    Map<String, Object> data = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 700); // >= image_height 600
    badMinutia.put(FIELD_ANGLE_DEG, 45.0);
    badMinutia.put("type", VAL_ENDING);
    badMinutia.put(FIELD_QUALITY, 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    data.put(FIELD_MINUTIAE, minutiae);

    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image_height"));
  }

  @Test
  void shouldReportViolationWhenMinutiaAngleIsNegative() {
    Map<String, Object> data = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put(FIELD_ANGLE_DEG, -1.0);
    badMinutia.put("type", VAL_ENDING);
    badMinutia.put(FIELD_QUALITY, 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    data.put(FIELD_MINUTIAE, minutiae);

    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(FIELD_ANGLE_DEG));
  }

  @Test
  void shouldReportViolationWhenMinutiaAngleIs360OrMore() {
    Map<String, Object> data = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put(FIELD_ANGLE_DEG, 360.0);
    badMinutia.put("type", VAL_ENDING);
    badMinutia.put(FIELD_QUALITY, 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    data.put(FIELD_MINUTIAE, minutiae);

    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(FIELD_ANGLE_DEG));
  }

  @Test
  void shouldReportViolationWhenMinutiaTypeIsInvalid() {
    Map<String, Object> data = validFingerprintRecord();
    Map<String, Object> badMinutia = new HashMap<>();
    badMinutia.put("x", 100);
    badMinutia.put("y", 100);
    badMinutia.put(FIELD_ANGLE_DEG, 45.0);
    badMinutia.put("type", "loop"); // invalid
    badMinutia.put(FIELD_QUALITY, 80);

    List<Map<String, Object>> minutiae = new ArrayList<>(buildMinutiae(39, 500, 600));
    minutiae.add(badMinutia);
    data.put(FIELD_MINUTIAE, minutiae);

    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("bifurcation"));
  }

  @Test
  void shouldReportViolationWhenFingerprintQualityOutOfRange() {
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_QUALITY, 150);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality=150"));
  }

  @Test
  void shouldReportAllViolationsInOneRecordNonFailFast() {
    Map<String, Object> data = new HashMap<>();
    data.put(FIELD_RECORD_FORMAT, "FMR");
    data.put(FIELD_MINUTIAE, buildMinutiae(5, 500, 600)); // count too low
    data.put(FIELD_QUALITY, 150); // out of range

    RecordViolation violation = validator.validateSingle(data, 1);
    // Should have: missing required fields + count violation + quality violation
    assertThat(violation.messages()).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void shouldReportAllViolationsForEmptyRecord() {
    Map<String, Object> data = new HashMap<>();
    data.put(FIELD_RECORD_FORMAT, "FMR");

    RecordViolation violation = validator.validateSingle(data, 1);
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
    Map<String, Object> data = validFaceRecord();
    data.remove(FIELD_IMAGE_FILE);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(FIELD_IMAGE_FILE));
  }

  @Test
  void shouldReportViolationWhenFaceBoxExceedsImageWidth() {
    Map<String, Object> data = validFaceRecord();
    // x=400 + width=300 = 700 > image width 640
    data.put(FIELD_FACE_BOX, Map.of("x", 400, "y", 40, FIELD_WIDTH, 300, FIELD_HEIGHT, 200));
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image width"));
  }

  @Test
  void shouldReportViolationWhenFaceBoxExceedsImageHeight() {
    Map<String, Object> data = validFaceRecord();
    // y=300 + height=300 = 600 > image height 480
    data.put(FIELD_FACE_BOX, Map.of("x", 50, "y", 300, FIELD_WIDTH, 200, FIELD_HEIGHT, 300));
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("image height"));
  }

  @Test
  void shouldReportViolationWhenLandmarkMissing() {
    Map<String, Object> data = validFaceRecord();
    Map<String, Object> incompleteLandmarks = new HashMap<>();
    incompleteLandmarks.put(LM_LEFT_EYE, Map.of("x", 100, "y", 150));
    incompleteLandmarks.put(LM_RIGHT_EYE, Map.of("x", 200, "y", 150));
    // nose_tip, mouth_left, mouth_right missing
    data.put(FIELD_LANDMARKS, incompleteLandmarks);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains(LM_NOSE_TIP));
  }

  @Test
  void shouldReportViolationWhenLandmarkOutsideImageBounds() {
    Map<String, Object> data = validFaceRecord();
    Map<String, Object> landmarks = new HashMap<>();
    landmarks.put(LM_LEFT_EYE, Map.of("x", 700, "y", 150)); // x=700 >= width=640
    landmarks.put(LM_RIGHT_EYE, Map.of("x", 200, "y", 150));
    landmarks.put(LM_NOSE_TIP, Map.of("x", 300, "y", 320));
    landmarks.put("mouth_left", Map.of("x", 250, "y", 400));
    landmarks.put("mouth_right", Map.of("x", 390, "y", 400));
    data.put(FIELD_LANDMARKS, landmarks);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("left_eye.x"));
  }

  @Test
  void shouldReportViolationWhenFaceQualityOutOfRange() {
    Map<String, Object> data = validFaceRecord();
    data.put(FIELD_QUALITY, Map.of("overall", 200, "occlusion", 10, "illumination", 90));
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality.overall=200"));
  }

  // -------------------------------------------------------------------------
  // Unknown format / general tests
  // -------------------------------------------------------------------------

  @Test
  void shouldReportViolationForUnknownRecordFormat() {
    Map<String, Object> data = Map.of(FIELD_RECORD_FORMAT, "XYZ", FIELD_SUBJECT_ID, "abc");
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("Unknown record_format: XYZ"));
    assertThat(violation.recordFormat()).isEqualTo("XYZ");
  }

  @Test
  void shouldReportViolationWhenRecordFormatAbsent() {
    Map<String, Object> data = new HashMap<>();
    data.put(FIELD_SUBJECT_ID, "abc");
    RecordViolation violation = validator.validateSingle(data, 3);
    assertThat(violation.messages()).anyMatch(m -> m.contains("Unknown record_format"));
    assertThat(violation.lineNumber()).isEqualTo(3);
  }

  @Test
  void shouldValidateBatchAndAggregateResults() {
    Map<String, Object> valid1 = validFingerprintRecord();
    Map<String, Object> valid2 = validFaceRecord();
    Map<String, Object> invalid = new HashMap<>();
    invalid.put(FIELD_RECORD_FORMAT, "FMR");
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
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_QUALITY, -5);
    RecordViolation violation = validator.validateSingle(data, 1);
    assertThat(violation.messages()).anyMatch(m -> m.contains("quality=-5"));
  }

  @Test
  void shouldPassWhenMinutiaeCountIsExactlyAtBoundary() {
    Map<String, Object> data = validFingerprintRecord();
    data.put(FIELD_MINUTIAE, buildMinutiae(30, 500, 600));
    RecordViolation lower = validator.validateSingle(data, 1);
    assertThat(lower.messages()).isEmpty();

    data.put(FIELD_MINUTIAE, buildMinutiae(80, 500, 600));
    RecordViolation upper = validator.validateSingle(data, 2);
    assertThat(upper.messages()).isEmpty();
  }
}
