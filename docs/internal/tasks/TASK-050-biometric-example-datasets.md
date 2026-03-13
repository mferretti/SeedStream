# TASK-050: Example Biometric Datasets and Documentation

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 2–3h
**Complexity:** Low
**User Stories:** US-048
**Dependencies:** TASK-047, TASK-048 (optional)

---

## Goal

Produce and commit example biometric datasets (small samples) and add a documentation section
to the project README and a dedicated `config/samples/biometric/README.md` explaining the
biometric output format and how to regenerate larger datasets.

---

## Deliverables

### Sample files (`config/samples/biometric/`)

| File | Content | Records | Seed |
|---|---|---|---|
| `fingerprint_minutiae_sample.ndjson` | Fingerprint minutiae records | 10 | 42 |
| `fingerprint_image_sample.ndjson` | Fingerprint image metadata records | 10 | 42 |
| `face_image_sample.ndjson` | Face image container records | 10 | 42 |
| `fingerprint_minutiae_cbeff_sample.ndjson` | CBEFF-wrapped fingerprint records | 10 | 42 |

### `config/samples/biometric/README.md`

Contents:
1. **Synthetic data disclaimer** — all data is synthetic, no real PII or biometric data.
2. **Format description** — field-by-field explanation for each modality (link to BIOMETRIC-DISCUSSION.md).
3. **Regeneration commands** — exact CLI commands to reproduce each sample file.
4. **SHA-256 hashes** — hash of each committed sample file for reproducibility verification.
5. **Full dataset generation** — commands to produce 100-record datasets.
6. **ISO field mapping summary** — condensed table linking generator fields to ISO/IEC equivalents.

### README.md addition

Add a "Biometric Data Generation" section to the main `README.md` referencing the biometric
structures, example commands, and the ISO mapping document.

---

## Acceptance Criteria

- [ ] Sample files committed and parseable as valid NDJSON
- [ ] SHA-256 hashes recorded and reproducible
- [ ] `config/samples/biometric/README.md` contains synthetic data disclaimer
- [ ] Main `README.md` has a biometric section with at least one example command
- [ ] Running the documented regeneration commands produces files with matching hashes

---

## Generation Commands (to execute during implementation)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_minutiae.yaml --count 10 --seed 42 --format json"
./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_image.yaml --count 10 --seed 42 --format json"
./gradlew :cli:run --args="execute --job config/jobs/file_face_image.yaml --count 10 --seed 42 --format json"
./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_minutiae.yaml --count 10 --seed 42 --format cbeff"
sha256sum output/fingerprint_minutiae.ndjson output/fingerprint_image.ndjson output/face_image.ndjson
```

---

## Notes

- Committed samples must be 10 records only (small enough to review in a PR diff).
- Add `config/samples/` to `.gitignore` exceptions if needed to ensure files are tracked.
- The synthetic data disclaimer must appear prominently (first paragraph of the README).
