# TASK-047: Biometric YAML Structure Definitions

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 2–3h
**Complexity:** Low
**User Stories:** US-044, US-045
**Dependencies:** None

---

## Goal

Create all YAML structure definitions and job configurations required to generate fingerprint
minutiae records (ISO/IEC 19794-2 style) and face image container records (ISO/IEC 19794-5 style)
using the existing type system. No new Java code is required.

---

## Deliverables

### Structure files (`config/structures/`)

| File | Purpose |
|---|---|
| `fingerprint_minutia.yaml` | Single minutia entry: x, y, angle_deg, type, quality |
| `fingerprint_minutiae.yaml` | Full fingerprint record with header + minutiae array |
| `fingerprint_image.yaml` | Fingerprint image metadata (ISO/IEC 19794-4 style) |
| `image_point.yaml` | Reusable `{x, y}` point (shared by face landmarks) |
| `face_bounding_box.yaml` | `{x, y, width, height}` face bounding box |
| `face_landmarks.yaml` | Five landmark points using `object[image_point]` |
| `face_pose.yaml` | Yaw, pitch, roll decimal fields |
| `face_quality.yaml` | Overall, occlusion, illumination quality scores |
| `face_image.yaml` | Full face image container record composing all of the above |

### Job files (`config/jobs/`)

| File | Purpose |
|---|---|
| `file_fingerprint_minutiae.yaml` | Generate fingerprint minutiae to file (JSON, seed 42) |
| `file_fingerprint_image.yaml` | Generate fingerprint image metadata to file (JSON, seed 42) |
| `file_face_image.yaml` | Generate face image container records to file (JSON, seed 42) |

---

## Field Specifications

Refer to `docs/internal/BIOMETRIC-DISCUSSION.md` sections A.1, A.2, and A.3 for the full
field tables and type syntax for each structure.

Key type mappings:
- Enums: `enum[value1,value2,...]`
- Coordinate integers: `int[0..999]` for fingerprint, `int[0..2047]` and `int[0..1535]` for face
- Angles: `decimal[0.0..359.9]` for minutiae, `decimal[-30.0..30.0]` etc. for face pose
- Arrays: `array[object[fingerprint_minutia], 30..80]`
- Timestamps: `timestamp[now-365d..now]`
- Image filenames: `char[10..40]` (placeholder, not actual image data)

---

## Acceptance Criteria

- [ ] All 9 structure files parse without error (`./gradlew :schema:test`)
- [ ] All 3 job configs run end-to-end: `./gradlew :cli:run --args="execute --job <job> --count 10"`
- [ ] Output contains all declared fields with correct types
- [ ] Deterministic: same seed → identical NDJSON output

---

## Test Plan

Add a unit test in `schema/src/test/java/.../BiometricStructureParseTest.java`:
- Load each structure file and assert field names and type classes match expectations.
- Assert `fingerprint_minutiae.yaml` has an `ArrayType` field for `minutiae`.
- Assert `face_image.yaml` has `ObjectType` fields for `face_box`, `landmarks`, `pose`, `quality`.

---

## Notes

- `minutiae_count` is NOT a generated field — it will be added as a post-serialisation step
  or computed by the validator. Omit from the structure YAML.
- All structures must carry an Apache 2.0 license comment header in the YAML file comment block.
- Follow existing naming convention: `{entity}.yaml`.
