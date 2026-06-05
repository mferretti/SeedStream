# TASK-051: Biometric ISO Field Mapping Documentation

**Status:** ✅ Complete (June 5, 2026, v0.6.0)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 2–3h
**Complexity:** Low
**User Stories:** US-048
**Dependencies:** TASK-047

---

## Goal

Produce `docs/BIOMETRIC-FIELD-MAPPING.md` — a developer-facing reference that maps every
generated field to its ISO/IEC 19794 equivalent, documents binary encoding notes, and
lists known limitations.

---

## Deliverables

### `docs/BIOMETRIC-FIELD-MAPPING.md`

Sections:
1. **Overview** — purpose, ISO standards covered, scope (JSON only, binary optional).
2. **Fingerprint Minutiae (ISO/IEC 19794-2)** — complete field table with columns:
   `Generator Field`, `JSON Type`, `ISO Field Name`, `Binary Encoding`, `Notes`.
3. **Fingerprint Image Metadata (ISO/IEC 19794-4)** — same table format.
4. **Face Image Container (ISO/IEC 19794-5)** — same table format.
5. **CBEFF Envelope** — envelope fields and mapping to CBEFF 2.x concepts.
6. **Known Limitations** — image data is placeholder, landmark coherence not enforced by
   generator, binary conformance is approximate.
7. **References** — links to IEC/ISO webstore, ICAO 9303, OSS implementations.

---

## Acceptance Criteria

- [x] `docs/BIOMETRIC-FIELD-MAPPING.md` created and renders correctly on GitHub
- [x] Every field in every YAML structure has a row in the mapping table
- [x] Known limitations section explicitly states what is out of scope
- [x] References section includes all links from BIOMETRIC-DISCUSSION.md section C/E

---

## Notes

- This document is the developer reference; `docs/internal/BIOMETRIC-DISCUSSION.md` remains
  the internal design discussion.
- Keep the mapping tables in sync with the YAML structures if those change.
