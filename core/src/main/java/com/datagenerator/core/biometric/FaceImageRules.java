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
 * Factory providing ISO/IEC 19794-5 face image validation rules.
 *
 * <p>All rules collect violations without short-circuiting (non-fail-fast), so every rule is
 * evaluated even when earlier rules have already failed.
 *
 * @since 1.0
 */
public final class FaceImageRules {

  private static final Set<String> REQUIRED_FIELDS =
      Set.of(
          "record_format",
          "version",
          "subject_id",
          "sample_id",
          "image_type",
          "width",
          "height",
          "color_space",
          "compression",
          "image_file");

  private static final Set<String> FACE_BOX_KEYS = Set.of("x", "y", "width", "height");
  private static final Set<String> LANDMARK_KEYS =
      Set.of("left_eye", "right_eye", "nose_tip", "mouth_left", "mouth_right");

  private FaceImageRules() {}

  /**
   * Returns the ordered list of validation rules for face image records.
   *
   * @return immutable list of {@link ValidationRule} instances
   */
  public static List<ValidationRule> rules() {
    return List.of(
        FaceImageRules::checkRequiredFields,
        FaceImageRules::checkFaceBox,
        FaceImageRules::checkFaceBoxFitsImage,
        FaceImageRules::checkLandmarksPresent,
        FaceImageRules::checkLandmarkBounds,
        FaceImageRules::checkPose,
        FaceImageRules::checkQuality);
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

  // Rule 2: face_box is a Map with keys x, y, width, height
  private static Optional<String> checkFaceBox(Map<String, Object> record) {
    Object faceBoxObj = record.get("face_box");
    if (!(faceBoxObj instanceof Map<?, ?> faceBox)) {
      return Optional.of("'face_box' must be a map with keys: x, y, width, height");
    }
    List<String> missing = new ArrayList<>();
    for (String key : FACE_BOX_KEYS) {
      if (!faceBox.containsKey(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      return Optional.of("'face_box' is missing keys: " + String.join(", ", missing));
    }
    return Optional.empty();
  }

  // Rule 3: face_box fits within image dimensions
  @SuppressWarnings("unchecked")
  private static Optional<String> checkFaceBoxFitsImage(Map<String, Object> record) {
    Object faceBoxObj = record.get("face_box");
    if (!(faceBoxObj instanceof Map<?, ?> rawFaceBox)) {
      return Optional.empty(); // covered by rule 2
    }
    Map<String, Object> faceBox = (Map<String, Object>) rawFaceBox;
    Object imageWidthObj = record.get("width");
    Object imageHeightObj = record.get("height");
    if (!(imageWidthObj instanceof Number) || !(imageHeightObj instanceof Number)) {
      return Optional.empty();
    }
    int imageWidth = ((Number) imageWidthObj).intValue();
    int imageHeight = ((Number) imageHeightObj).intValue();

    List<String> errors = new ArrayList<>();

    Object boxXObj = faceBox.get("x");
    Object boxWidthObj = faceBox.get("width");
    if (boxXObj instanceof Number boxX && boxWidthObj instanceof Number boxWidth) {
      int right = boxX.intValue() + boxWidth.intValue();
      if (right > imageWidth) {
        errors.add(
            "face_box.x("
                + boxX.intValue()
                + ") + face_box.width("
                + boxWidth.intValue()
                + ")="
                + right
                + " exceeds image width="
                + imageWidth);
      }
    }

    Object boxYObj = faceBox.get("y");
    Object boxHeightObj = faceBox.get("height");
    if (boxYObj instanceof Number boxY && boxHeightObj instanceof Number boxHeight) {
      int bottom = boxY.intValue() + boxHeight.intValue();
      if (bottom > imageHeight) {
        errors.add(
            "face_box.y("
                + boxY.intValue()
                + ") + face_box.height("
                + boxHeight.intValue()
                + ")="
                + bottom
                + " exceeds image height="
                + imageHeight);
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 4: landmarks is a Map with all five keys
  private static Optional<String> checkLandmarksPresent(Map<String, Object> record) {
    Object landmarksObj = record.get("landmarks");
    if (!(landmarksObj instanceof Map<?, ?> landmarks)) {
      return Optional.of(
          "'landmarks' must be a map with keys: " + String.join(", ", LANDMARK_KEYS));
    }
    List<String> missing = new ArrayList<>();
    for (String key : LANDMARK_KEYS) {
      if (!landmarks.containsKey(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      return Optional.of("'landmarks' is missing keys: " + String.join(", ", missing));
    }
    return Optional.empty();
  }

  // Rule 5: all landmark x/y values are within image bounds
  @SuppressWarnings("unchecked")
  private static Optional<String> checkLandmarkBounds(Map<String, Object> record) {
    Object landmarksObj = record.get("landmarks");
    if (!(landmarksObj instanceof Map<?, ?> rawLandmarks)) {
      return Optional.empty();
    }
    Map<String, Object> landmarks = (Map<String, Object>) rawLandmarks;
    Object imageWidthObj = record.get("width");
    Object imageHeightObj = record.get("height");
    if (!(imageWidthObj instanceof Number) || !(imageHeightObj instanceof Number)) {
      return Optional.empty();
    }
    int imageWidth = ((Number) imageWidthObj).intValue();
    int imageHeight = ((Number) imageHeightObj).intValue();

    List<String> errors = new ArrayList<>();
    for (String landmarkName : LANDMARK_KEYS) {
      Object pointObj = landmarks.get(landmarkName);
      if (!(pointObj instanceof Map<?, ?> rawPoint)) {
        continue;
      }
      Map<String, Object> point = (Map<String, Object>) rawPoint;
      Object xObj = point.get("x");
      Object yObj = point.get("y");
      if (xObj instanceof Number xNum && xNum.intValue() >= imageWidth) {
        errors.add(
            "landmarks."
                + landmarkName
                + ".x="
                + xNum.intValue()
                + " >= image width="
                + imageWidth);
      }
      if (yObj instanceof Number yNum && yNum.intValue() >= imageHeight) {
        errors.add(
            "landmarks."
                + landmarkName
                + ".y="
                + yNum.intValue()
                + " >= image height="
                + imageHeight);
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 6: pose values within allowed ranges if present
  @SuppressWarnings("unchecked")
  private static Optional<String> checkPose(Map<String, Object> record) {
    Object poseObj = record.get("pose");
    if (!(poseObj instanceof Map<?, ?> rawPose)) {
      return Optional.empty(); // pose is optional
    }
    Map<String, Object> pose = (Map<String, Object>) rawPose;
    List<String> errors = new ArrayList<>();

    Object yawObj = pose.get("yaw");
    if (yawObj instanceof Number yaw) {
      double v = yaw.doubleValue();
      if (v < -30.0 || v > 30.0) {
        errors.add("pose.yaw=" + v + " is outside the allowed range [-30.0, 30.0]");
      }
    }
    Object pitchObj = pose.get("pitch");
    if (pitchObj instanceof Number pitch) {
      double v = pitch.doubleValue();
      if (v < -20.0 || v > 20.0) {
        errors.add("pose.pitch=" + v + " is outside the allowed range [-20.0, 20.0]");
      }
    }
    Object rollObj = pose.get("roll");
    if (rollObj instanceof Number roll) {
      double v = roll.doubleValue();
      if (v < -15.0 || v > 15.0) {
        errors.add("pose.roll=" + v + " is outside the allowed range [-15.0, 15.0]");
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }

  // Rule 7: quality values in [0, 100] if present
  @SuppressWarnings("unchecked")
  private static Optional<String> checkQuality(Map<String, Object> record) {
    Object qualityObj = record.get("quality");
    if (!(qualityObj instanceof Map<?, ?> rawQuality)) {
      return Optional.empty(); // quality is optional
    }
    Map<String, Object> quality = (Map<String, Object>) rawQuality;
    List<String> errors = new ArrayList<>();

    for (String field : List.of("overall", "occlusion", "illumination")) {
      Object valObj = quality.get(field);
      if (valObj instanceof Number num) {
        int v = num.intValue();
        if (v < 0 || v > 100) {
          errors.add("quality." + field + "=" + v + " is outside the allowed range [0, 100]");
        }
      }
    }
    return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
  }
}
