# US-048: Generate and Commit Example Biometric Datasets

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Task:** TASK-050-biometric-example-datasets.md

---

## User Story

As a **developer evaluating the data generator**,
I want to **generate example biometric datasets with a single command and find committed samples
in the repository**,
so that I can **quickly understand the output format and start integrating without running the
generator myself**.

---

## Acceptance Criteria

1. A single command produces 100 fingerprint minutiae records and 100 face image records:
   ```
   ./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_minutiae.yaml --count 100 --seed 42"
   ./gradlew :cli:run --args="execute --job config/jobs/file_face_image.yaml --count 100 --seed 42"
   ```
2. Sample output files (10 records each) are committed to `config/samples/biometric/`:
   - `fingerprint_minutiae_sample.ndjson` (10 records, seed 42)
   - `face_image_sample.ndjson` (10 records, seed 42)
3. A `config/samples/biometric/README.md` explains the format, how to regenerate the full dataset,
   and notes that all data is synthetic.
4. Generation is reproducible: running the same command yields files with identical SHA-256 hashes.
5. The README explicitly states: *"All data is synthetic and contains no real PII or real
   biometric data."*

---

## Implementation Notes

- Depends on US-044 and US-045 for structure definitions and job configs.
- Sample files are generated, spot-checked, and committed manually (not auto-generated in CI).
- SHA-256 hashes recorded in the README for reproducibility verification.

---

## Dependencies

- US-044 (fingerprint structure + job config)
- US-045 (face image structure + job config)

---

## Definition of Done

- [ ] Both job configs produce valid output
- [ ] Sample files committed to `config/samples/biometric/`
- [ ] README with synthetic data disclaimer, field descriptions, and regeneration instructions
- [ ] SHA-256 hashes documented for reproducibility
