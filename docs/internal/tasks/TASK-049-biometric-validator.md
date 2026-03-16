# TASK-049: BiometricValidator Component and `validate` CLI Command

**Status:** ✅ Complete
**Completion Date:** March 15, 2026
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 5–7h
**Complexity:** Medium
**User Stories:** US-047
**Dependencies:** TASK-047

---

## Goal

Implement a `BiometricValidator` that reads generated NDJSON files and checks structural
correctness of fingerprint minutiae and face image records against the rules defined in
`docs/internal/BIOMETRIC-DISCUSSION.md`. Add a `validate` CLI subcommand.

---

## New Classes

### `ValidationRule` (interface, `core/biometric/`)

```java
@FunctionalInterface
public interface ValidationRule {
    Optional<String> check(Map<String, Object> record);
}
```

Returns `Optional.empty()` if the rule passes, or `Optional.of("violation message")`.

### `BiometricValidator` (`core/biometric/`)

- Detects modality from `record_format` field (`"FMR"` → fingerprint, `"FAC"` → face).
- Applies the appropriate rule list.
- Returns a `ValidationReport` (record counts, violation list).

### `FingerprintMinutiaeRules` (`core/biometric/`)

Rules to implement:
1. Required fields present: `record_format`, `version`, `subject_id`, `finger_position`, `impression_type`, `image_width`, `image_height`, `resolution_dpi`, `quality`, `minutiae`.
2. `minutiae` is a non-empty list.
3. `minutiae` count is between 30 and 80.
4. For each minutia: `x` < `image_width`, `y` < `image_height`.
5. For each minutia: `angle_deg` in [0.0, 360.0).
6. For each minutia: `type` is `"ending"` or `"bifurcation"`.
7. If `minutiae_count` field is present: equals actual array length.
8. `quality` in [0, 100].

### `FaceImageRules` (`core/biometric/`)

Rules to implement:
1. Required fields present: `record_format`, `version`, `subject_id`, `image_type`, `width`, `height`, `color_space`, `compression`, `image_file`.
2. `face_box` present with `x`, `y`, `width`, `height`.
3. Face box fits within image: `face_box.x + face_box.width <= width`, `face_box.y + face_box.height <= height`.
4. `landmarks` present with all five points (left_eye, right_eye, nose_tip, mouth_left, mouth_right).
5. All landmark coordinates within image bounds.
6. `pose` yaw in [-30, 30], pitch in [-20, 20], roll in [-15, 15] (if present).
7. `quality` scores in [0, 100] (if present).

### `ValidationReport` (record, `core/biometric/`)

```java
public record ValidationReport(
    int totalRecords,
    int validRecords,
    List<RecordViolation> violations
) {}

public record RecordViolation(int lineNumber, String recordFormat, List<String> messages) {}
```

### `ValidateCommand` (`cli/`)

```
Usage: datagenerator validate --input <file.ndjson> [--modality fingerprint|face]
```

- Reads NDJSON line by line.
- Calls `BiometricValidator` on each parsed record.
- Prints summary report to stdout.
- Exits with code 0 if no violations, 1 otherwise.

---

## Acceptance Criteria

- ✅ `validate` subcommand registered in `DataGeneratorCli`
- ✅ Fingerprint rules: all 8 rules implemented and tested
- ✅ Face rules: all 7 rules implemented and tested
- ✅ Unknown `record_format` produces a clear error, not a NullPointerException
- ✅ Unit tests in `core/src/test/java/.../BiometricValidatorTest.java` (28 test cases)
- ✅ Integration test: generate 100 fingerprint + 100 face records → validate → zero violations
- ✅ Exit code 0 on clean validation, 1 on any violation
- ✅ Spotless compliant, Apache 2.0 license header

---

## Test Plan

**Unit tests (`BiometricValidatorTest`):**
- Valid fingerprint record passes all rules
- Valid face record passes all rules
- Missing required field triggers violation
- Minutia x >= image_width triggers violation
- Angle < 0 or >= 360 triggers violation
- Invalid minutia type triggers violation
- `minutiae_count` mismatch triggers violation
- Face box exceeds image bounds triggers violation
- Landmark outside image bounds triggers violation
- Unknown `record_format` → violation (graceful degradation)
- Multiple violations in one record → all reported
- Empty input file → report with 0 records

**Integration test (`BiometricValidatorIT`):**
- Generate 100 fingerprint records with `--seed 42` → validate → assert 0 violations
- Generate 100 face records with `--seed 42` → validate → assert 0 violations

---

## Notes

- Use Jackson `ObjectMapper` to parse each NDJSON line to `Map<String, Object>`.
- All rule violations are collected (non-fail-fast) so a single record can report multiple issues.
- The validator is read-only and has no dependency on the generator modules; place it in `core/`.
