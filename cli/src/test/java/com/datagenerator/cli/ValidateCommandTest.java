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

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ValidateCommandTest {

  @TempDir Path tempDir;

  private int execute(Path inputFile) {
    return new CommandLine(new ValidateCommand()).execute(inputFile.toString());
  }

  // ── Valid records ──────────────────────────────────────────────────────────

  @Test
  void shouldReturnZeroForFullyValidFingerprintFile() throws Exception {
    Path file = tempDir.resolve("valid_fmr.ndjson");
    Files.writeString(file, validFingerprintRecord() + "\n" + validFingerprintRecord());

    assertThat(execute(file)).isZero();
  }

  @Test
  void shouldReturnZeroForFullyValidFaceFile() throws Exception {
    Path file = tempDir.resolve("valid_fac.ndjson");
    Files.writeString(file, validFaceRecord());

    assertThat(execute(file)).isZero();
  }

  @Test
  void shouldReturnZeroForEmptyFile() throws Exception {
    Path file = tempDir.resolve("empty.ndjson");
    Files.writeString(file, "");

    assertThat(execute(file)).isZero();
  }

  @Test
  void shouldReturnZeroForFileWithBlankLinesOnly() throws Exception {
    Path file = tempDir.resolve("blanks.ndjson");
    Files.writeString(file, "\n\n  \n\n");

    assertThat(execute(file)).isZero();
  }

  // ── Invalid records ────────────────────────────────────────────────────────

  @Test
  void shouldReturnOneWhenFingerprintRecordHasViolations() throws Exception {
    Path file = tempDir.resolve("invalid_fmr.ndjson");
    // Missing required fields
    Files.writeString(file, "{\"record_format\":\"FMR\"}");

    assertThat(execute(file)).isEqualTo(1);
  }

  @Test
  void shouldReturnOneWhenMixedValidAndInvalidRecords() throws Exception {
    Path file = tempDir.resolve("mixed.ndjson");
    Files.writeString(
        file,
        validFingerprintRecord() + "\n" + "{\"record_format\":\"FMR\"}" + "\n" + validFaceRecord());

    assertThat(execute(file)).isEqualTo(1);
  }

  @Test
  void shouldReturnOneWhenRecordFormatIsUnknown() throws Exception {
    Path file = tempDir.resolve("unknown_format.ndjson");
    Files.writeString(file, "{\"record_format\":\"XYZ\",\"subject_id\":\"abc\"}");

    assertThat(execute(file)).isEqualTo(1);
  }

  @Test
  void shouldCountBadJsonLineAsViolation() throws Exception {
    Path file = tempDir.resolve("bad_json.ndjson");
    Files.writeString(file, "not valid json at all");

    assertThat(execute(file)).isEqualTo(1);
  }

  // ── I/O errors ─────────────────────────────────────────────────────────────

  @Test
  void shouldReturnTwoWhenFileDoesNotExist() {
    Path missing = tempDir.resolve("nonexistent.ndjson");
    assertThat(execute(missing)).isEqualTo(2);
  }

  // ── CBEFF envelope unwrapping ──────────────────────────────────────────────

  @Test
  void shouldReturnZeroWhenCbeffWrappedRecordIsValid() throws Exception {
    Path file = tempDir.resolve("cbeff.ndjson");
    // CBEFF wrapper: top-level "payload" contains the actual biometric record
    String cbeffRecord =
        "{\"cbeff_version\":\"2.0\","
            + "\"format_owner\":\"ISO\","
            + "\"format_type\":\"19794-2-json\","
            + "\"creation_date\":\"2026-01-01T00:00:00Z\","
            + "\"payload\":"
            + validFingerprintRecordMap()
            + "}";
    Files.writeString(file, cbeffRecord);

    assertThat(execute(file)).isZero();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private static String validFingerprintRecord() {
    return "{"
        + "\"record_format\":\"FMR\","
        + "\"version\":\"19794-2:2011\","
        + "\"subject_id\":\"subj001\","
        + "\"sample_id\":1,"
        + "\"finger_position\":\"right_index\","
        + "\"impression_type\":\"live_scan_plain\","
        + "\"image_width\":500,"
        + "\"image_height\":600,"
        + "\"resolution_dpi\":\"500\","
        + "\"quality\":75,"
        + "\"minutiae\":"
        + buildMinutiaeJson(40, 500, 600)
        + "}";
  }

  private static String validFingerprintRecordMap() {
    return validFingerprintRecord();
  }

  private static String validFaceRecord() {
    return "{"
        + "\"record_format\":\"FAC\","
        + "\"version\":\"19794-5:2011\","
        + "\"subject_id\":\"subj002\","
        + "\"sample_id\":0,"
        + "\"image_type\":\"still_controlled\","
        + "\"width\":640,"
        + "\"height\":480,"
        + "\"color_space\":\"RGB\","
        + "\"compression\":\"JPEG\","
        + "\"image_file\":\"face_001.jpg\","
        + "\"face_box\":{\"x\":50,\"y\":40,\"width\":200,\"height\":250},"
        + "\"landmarks\":{"
        + "\"left_eye\":{\"x\":100,\"y\":150},"
        + "\"right_eye\":{\"x\":200,\"y\":150},"
        + "\"nose_tip\":{\"x\":300,\"y\":320},"
        + "\"mouth_left\":{\"x\":250,\"y\":400},"
        + "\"mouth_right\":{\"x\":390,\"y\":400}},"
        + "\"pose\":{\"yaw\":5.0,\"pitch\":-2.0,\"roll\":3.0},"
        + "\"quality\":{\"overall\":85,\"occlusion\":10,\"illumination\":90}"
        + "}";
  }

  private static String buildMinutiaeJson(int count, int imageWidth, int imageHeight) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) sb.append(",");
      int x = (i * 3) % (imageWidth - 1);
      int y = (i * 5) % (imageHeight - 1);
      double angle = (i * 7.5) % 359.9;
      String type = i % 2 == 0 ? "ending" : "bifurcation";
      sb.append(
          String.format(
              "{\"x\":%d,\"y\":%d,\"angle_deg\":%.1f,\"type\":\"%s\",\"quality\":80}",
              x, y, angle, type));
    }
    sb.append("]");
    return sb.toString();
  }
}
