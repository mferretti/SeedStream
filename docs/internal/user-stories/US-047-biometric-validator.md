# US-047: Validate Structural Correctness of Generated Biometric Records

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Task:** TASK-049-biometric-validator.md

---

## User Story

As a **QA engineer consuming generated biometric test data**,
I want to **run a validator that checks structural correctness of generated JSON records**,
so that I can **confirm the test data meets the expected field layout before loading it into
a pipeline**.

---

## Acceptance Criteria

1. A new CLI subcommand `validate --job <job.yaml> --input <file.ndjson>` parses each record and reports any violations.
2. For fingerprint minutiae records, the validator checks:
   - All required header fields are present and of the correct type.
   - `minutiae` array is non-empty (30–80 entries).
   - Every minutia: `x` < `image_width`, `y` < `image_height`, `angle_deg` in [0, 360), `type` in `{ending, bifurcation}`.
   - `minutiae_count` (if present) equals the actual array length.
3. For face image records, the validator checks:
   - All required header fields are present.
   - Landmark coordinates are within image dimensions (`0 <= x < width`, `0 <= y < height`).
   - `face_box` dimensions fit within image (`x + width <= image_width`, `y + height <= image_height`).
   - `pose` angles within declared ranges.
   - `quality` scores in [0, 100].
4. Validation report includes: total records, valid count, violation count, and per-record violation details.
5. Exit code 0 if all valid, non-zero if any violations found.
6. Unit tests cover all validation rules for both modalities.

---

## Implementation Notes

- Add `BiometricValidator` class in `core/src/main/java/com/datagenerator/core/biometric/`.
- Add `ValidateCommand` Picocli subcommand in `cli/` module.
- Validation rules per modality defined as `List<ValidationRule>` (interface with `check(Map<String,Object> record)`).
- `FingerprintMinitiaeValidator` and `FaceImageValidator` implement the per-modality rule sets.
- Modality detected from `record_format` field (`"FMR"` → fingerprint, `"FAC"` → face).
- Use Jackson `ObjectMapper` to parse NDJSON input line by line.

---

## Dependencies

- US-044 (fingerprint structure definitions — provides records to validate)
- US-045 (face structure definitions)

---

## Definition of Done

- [ ] `BiometricValidator` with fingerprint and face rule sets implemented
- [ ] `validate` CLI subcommand working end-to-end
- [ ] Validation report printed to stdout
- [ ] Unit tests for all validation rules (positive and negative cases)
- [ ] Integration test: generate 100 records then validate them (expect zero violations)
- [ ] Spotless compliant, license header applied
