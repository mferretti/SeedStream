# US-045: Generate Synthetic Face Image Container Records

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Task:** TASK-047-biometric-yaml-structures.md

---

## User Story

As a **test data engineer building an ePassport or face recognition pipeline**,
I want to **generate synthetic face image container records in ISO/IEC 19794-5 JSON format**,
so that I can **test storage, retrieval, and metadata validation without using real face images**.

---

## Acceptance Criteria

1. Running `./gradlew :cli:run --args="execute --job config/jobs/file_face_image.yaml --count 100"` produces 100 NDJSON records.
2. Each record contains all required header fields: `record_format`, `version`, `subject_id`, `sample_id`, `image_type`, `width`, `height`, `color_space`, `compression`, `capture_time`, `image_file`.
3. Each record contains `face_box` (x, y, width, height), `landmarks` (left_eye, right_eye, nose_tip, mouth_left, mouth_right), `pose` (yaw, pitch, roll), and `quality` (overall, occlusion, illumination).
4. All landmark coordinates are within the image dimensions (not necessarily geometrically coherent — that is a validator concern).
5. Generation is deterministic: same seed produces identical output across runs.
6. All nested objects are defined as separate reusable YAML structure files.

---

## Implementation Notes

- Create the following YAML structure files in `config/structures/`:
  - `image_point.yaml` — `{x: int, y: int}` reusable point structure
  - `face_bounding_box.yaml` — `{x, y, width, height}`
  - `face_landmarks.yaml` — five `object[image_point]` fields
  - `face_pose.yaml` — yaw, pitch, roll decimals
  - `face_quality.yaml` — overall, occlusion, illumination integers
  - `face_image.yaml` — full record composing the above
- Create `config/jobs/file_face_image.yaml`.
- No new Java code required for Phase 1.
- `image_file` is a filename placeholder string, not actual image data.

---

## ISO Field Mapping

See `docs/internal/BIOMETRIC-DISCUSSION.md` section A.3 for the full field-to-ISO mapping table.

---

## Dependencies

- None (all required types already supported by the type system)

---

## Definition of Done

- [ ] All structure YAML files created and parseable
- [ ] Job config produces valid NDJSON output
- [ ] Deterministic output verified
- [ ] Unit test asserting required fields and value ranges
