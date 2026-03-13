# US-044: Generate Synthetic Fingerprint Minutiae Records

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Task:** TASK-047-biometric-yaml-structures.md

---

## User Story

As a **test data engineer building a fingerprint matching pipeline**,
I want to **generate synthetic fingerprint minutiae records in ISO/IEC 19794-2 JSON format**,
so that I can **populate test databases and validate ingestion without using real biometric data**.

---

## Acceptance Criteria

1. Running `./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_minutiae.yaml --count 100"` produces 100 NDJSON records.
2. Each record contains all required header fields: `record_format`, `version`, `subject_id`, `sample_id`, `finger_position`, `impression_type`, `image_width`, `image_height`, `resolution_dpi`, `quality`.
3. Each record contains a `minutiae` array with 30–80 entries.
4. Each minutia entry contains: `x`, `y`, `angle_deg`, `type`, `quality`.
5. Field values fall within declared ranges (e.g., `type` is `ending` or `bifurcation`, `angle_deg` in [0, 360)).
6. Generation is deterministic: same seed produces identical output across runs.
7. Structure definitions exist in `config/structures/` and are reusable via `object[]` references.

---

## Implementation Notes

- Create the following YAML structure files in `config/structures/`:
  - `fingerprint_minutia.yaml` — single minutia entry (x, y, angle_deg, type, quality)
  - `fingerprint_minutiae.yaml` — full record using `array[object[fingerprint_minutia], 30..80]`
  - `fingerprint_image.yaml` — image metadata record (image_type, width, height, compression, image_file)
- Create `config/jobs/file_fingerprint_minutiae.yaml` and `config/jobs/file_fingerprint_image.yaml`.
- No new Java code required for Phase 1 — the existing type system handles all field types.
- `minutiae_count` is a derived field (array length); it is not a separately generated field.
  The validator (US-047) checks consistency.

---

## ISO Field Mapping

See `docs/internal/BIOMETRIC-DISCUSSION.md` section A.1 for the full field-to-ISO mapping table
and binary encoding notes.

---

## Dependencies

- None (all required types already supported by the type system)

---

## Definition of Done

- [ ] All structure YAML files created and parseable
- [ ] Job configs produce valid NDJSON output
- [ ] Deterministic output verified (same seed → same records)
- [ ] Unit test asserting required fields and value ranges (in `generators` or `schema` module)
